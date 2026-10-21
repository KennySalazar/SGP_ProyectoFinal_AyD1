import { Injectable } from '@angular/core';
import { sgpDb, LocalPhoto } from '../db/sgp-db';
export type FotografiaPendiente = LocalPhoto &
  Required<Pick<LocalPhoto, 'propietarioId' | 'idempotencyKey' | 'metadatos'>>;
@Injectable({ providedIn: 'root' })
export class FotografiasLocalesService {
  async guardar(foto: FotografiaPendiente): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.fotos, sgpDb.cola_sync, async () => {
      const anterior = await sgpDb.fotos.get(foto.id);
      if (anterior && anterior.propietarioId !== foto.propietarioId) throw new Error('propietario');
      await sgpDb.fotos.put(foto);
      const ultimo = await sgpDb.cola_sync.orderBy('creadoEn').last();
      const ahora = Date.now();
      await sgpDb.cola_sync.put({
        id: foto.id,
        tipo: 'FOTO',
        entityId: foto.id,
        propietarioId: foto.propietarioId,
        idempotencyKey: foto.idempotencyKey,
        intentos: 0,
        creadoEn: new Date(
          Math.max(ahora, ultimo ? Date.parse(ultimo.creadoEn) + 1 : ahora),
        ).toISOString(),
      });
    });
  }
  async pendientes(propietarioId: string, inspeccionId: string): Promise<FotografiaPendiente[]> {
    const fotos = await sgpDb.fotos.where('inspeccionId').equals(inspeccionId).toArray();
    return fotos.filter(
      (foto): foto is FotografiaPendiente =>
        foto.propietarioId === propietarioId &&
        !foto.sincronizado &&
        !!foto.metadatos &&
        !!foto.idempotencyKey,
    );
  }
  async confirmar(id: string, propietarioId: string): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.fotos, sgpDb.cola_sync, async () => {
      const foto = await sgpDb.fotos.get(id);
      if (foto?.propietarioId !== propietarioId) throw new Error('propietario');
      await sgpDb.fotos.delete(id);
      await sgpDb.cola_sync.delete(id);
    });
  }

  async guardarContexto(
    propietarioId: string,
    inspeccionId: string,
    puenteId: string,
    estado: string,
    payload: unknown,
  ): Promise<void> {
    await sgpDb.inspecciones.put({
      id: inspeccionId,
      propietarioId,
      puenteId,
      estado,
      payload,
      actualizadoEn: new Date().toISOString(),
      sincronizado: true,
    });
  }
  async contextoPendiente(propietarioId: string, puenteId: string): Promise<unknown | null> {
    const contextos = await sgpDb.inspecciones.where('puenteId').equals(puenteId).toArray();
    for (const contexto of contextos
      .filter((c) => c.propietarioId === propietarioId)
      .sort((a, b) => b.actualizadoEn.localeCompare(a.actualizadoEn))) {
      if ((await this.pendientes(propietarioId, contexto.id)).length) return contexto.payload;
    }
    return null;
  }
}
