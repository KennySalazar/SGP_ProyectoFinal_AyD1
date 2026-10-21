import { inject, Injectable, InjectionToken } from '@angular/core';
import { sgpDb, SgpDatabase } from '../../../offline/db/sgp-db';
import { PreparacionSalida, ResumenSalida, SalidaRequest } from '../models/salida.models';
import { uuidV7 } from '../../../offline/services/uuid-v7';

export const SALIDA_DB = new InjectionToken<SgpDatabase>('SALIDA_DB', {
  providedIn: 'root',
  factory: () => sgpDb,
});

@Injectable({ providedIn: 'root' })
export class SalidaLocalService {
  private readonly db = inject(SALIDA_DB);
  private clave(usuario: string, id: string): string {
    return `salida:${usuario}:${id}`;
  }

  ultima(usuario: string): Promise<ResumenSalida | undefined> {
    return this.db.preparaciones_salida.get(usuario);
  }

  async solicitud(usuario: string, cuerpo: SalidaRequest) {
    return this.db.transaction('rw', this.db.solicitudes_salida, async () => {
      const anterior = await this.db.solicitudes_salida.get(usuario);
      if (anterior && JSON.stringify(anterior.cuerpo) === JSON.stringify(cuerpo)) return anterior;
      const nueva = { propietarioId: usuario, clave: uuidV7(), cuerpo: structuredClone(cuerpo) };
      await this.db.solicitudes_salida.put(nueva);
      return nueva;
    });
  }

  async descartarSolicitud(usuario: string, clave: string): Promise<void> {
    await this.db.transaction('rw', this.db.solicitudes_salida, async () => {
      if ((await this.db.solicitudes_salida.get(usuario))?.clave === clave)
        await this.db.solicitudes_salida.delete(usuario);
    });
  }

  async guardar(
    usuario: string,
    salida: PreparacionSalida,
    paso: (indice: number) => void = () => {},
  ): Promise<ResumenSalida> {
    if (
      salida.usuarioId !== usuario ||
      !salida.paquete?.formulario?.id ||
      salida.paquete.formulario.esquema == null ||
      !Array.isArray(salida.paquete.puentes) ||
      !Array.isArray(salida.paquete.inspecciones) ||
      !Array.isArray(salida.paquete.asignaciones) ||
      salida.numeroPuentes !== salida.paquete.puentes.length ||
      new Set(salida.paquete.puentes.map((p) => p.id)).size !== salida.paquete.puentes.length ||
      salida.paquete.inspecciones.some(
        (i) => !salida.paquete.puentes.some((p) => p.id === i.puenteId),
      )
    )
      throw new Error('La descarga no corresponde a la cuenta o esta incompleta.');
    const resumen: ResumenSalida = {
      propietarioId: usuario,
      id: salida.id,
      dispositivoId: salida.dispositivoId,
      preparadoEn: salida.preparadoEn,
      versionFormularioId: salida.paquete.formulario.id,
      codigoVersionFormulario: salida.paquete.formulario.codigo,
      numeroPuentes: salida.paquete.puentes.length,
      asignaciones: salida.paquete.asignaciones,
    };
    await this.db.transaction(
      'rw',
      [
        this.db.puentes,
        this.db.esquemas_formulario,
        this.db.inspecciones,
        this.db.preparaciones_salida,
        this.db.solicitudes_salida,
      ],
      async () => {
        await this.borrarPreparados(usuario);
        paso(1);
        await this.db.esquemas_formulario.put({
          id: this.clave(usuario, salida.paquete.formulario.id),
          propietarioId: usuario,
          origen: 'PREPARACION',
          origenId: salida.paquete.formulario.id,
          version: salida.paquete.formulario.codigo,
          schema: salida.paquete.formulario.esquema,
          activo: true,
        });
        paso(2);
        await this.db.puentes.bulkPut(
          salida.paquete.puentes.map((p) => ({
            id: this.clave(usuario, p.id),
            propietarioId: usuario,
            origen: 'PREPARACION' as const,
            origenId: p.id,
            codigo: p.codigo,
            actualizadoEn: salida.preparadoEn,
            payload: p,
          })),
        );
        paso(3);
        await this.db.inspecciones.bulkPut(
          salida.paquete.inspecciones.map((i) => ({
            id: this.clave(usuario, i.id),
            propietarioId: usuario,
            origen: 'PREPARACION' as const,
            origenId: i.id,
            puenteId: i.puenteId,
            estado: 'PUBLICADA',
            actualizadoEn: i.publicadoEn,
            payload: i,
            sincronizado: true,
          })),
        );
        await this.db.preparaciones_salida.put(resumen);
        await this.db.solicitudes_salida.delete(usuario);
      },
    );
    return resumen;
  }

  async pendientes(usuario: string): Promise<boolean> {
    const [inspecciones, fotos, cola] = await Promise.all([
      this.db.inspecciones.toArray(),
      this.db.fotos.toArray(),
      this.db.cola_sync.toArray(),
    ]);
    if (
      inspecciones.some(
        (i) => !i.sincronizado && (!i.propietarioId || i.propietarioId === usuario),
      ) ||
      fotos.some((f) => !f.sincronizado && (!f.propietarioId || f.propietarioId === usuario))
    )
      return true;
    return cola.some((c) => {
      const registro =
        c.tipo === 'FOTO'
          ? fotos.find((f) => f.id === c.entityId)
          : inspecciones.find((i) => i.id === c.entityId);
      return !registro?.propietarioId || registro.propietarioId === usuario;
    });
  }

  async quitar(usuario: string): Promise<void> {
    await this.db.transaction(
      'rw',
      [
        this.db.puentes,
        this.db.esquemas_formulario,
        this.db.inspecciones,
        this.db.fotos,
        this.db.cola_sync,
        this.db.preparaciones_salida,
        this.db.solicitudes_salida,
      ],
      async () => {
        if (await this.pendientes(usuario))
          throw new Error(
            'Hay inspecciones o archivos pendientes de sincronizar. Conserva los datos y sincroniza antes de quitarlos.',
          );
        await this.borrarPreparados(usuario);
        await this.db.preparaciones_salida.delete(usuario);
        await this.db.solicitudes_salida.delete(usuario);
      },
    );
  }

  private async borrarPreparados(usuario: string): Promise<void> {
    const propios = (r: { propietarioId?: string; origen?: string }) =>
      r.propietarioId === usuario && r.origen === 'PREPARACION';
    const referencias = new Set(
      (await this.db.inspecciones.toArray())
        .filter((i) => i.propietarioId === usuario && i.origen !== 'PREPARACION')
        .map((i) => (i.payload as { versionFormularioId?: string } | null)?.versionFormularioId),
    );
    await this.db.puentes.filter(propios).delete();
    await this.db.inspecciones.filter(propios).delete();
    await this.db.esquemas_formulario
      .filter((s) => propios(s) && !referencias.has(s.origenId))
      .delete();
  }
}
