import { from } from 'rxjs';
import { liveQuery } from 'dexie';
import { FotografiaResponse } from '../../features/inspecciones/models/fotografia.models';
import { StorageService } from './storage.service';
import { Injectable, inject } from '@angular/core';
import { sgpDb, LocalPhoto } from '../db/sgp-db';
export type FotografiaPendiente = LocalPhoto &
  Required<Pick<LocalPhoto, 'propietarioId' | 'idempotencyKey' | 'metadatos'>>;
@Injectable({ providedIn: 'root' })
export class FotografiasLocalesService {
  private readonly storage = inject(StorageService);
  observarInventario(propietarioId: string, inspeccionId: string) {
    return from(
      liveQuery(async () => ({
        pendientes: await this.pendientes(propietarioId, inspeccionId),
        confirmadas: await this.inventario(propietarioId, inspeccionId),
      })),
    );
  }
  async guardar(foto: FotografiaPendiente): Promise<void> {
    if (
      !foto.blob.size ||
      foto.blob.size > 400 * 1024 ||
      !['image/jpeg', 'image/webp'].includes(foto.mime)
    )
      throw new Error('compresion');
    await this.storage.prepararFotografia(foto.blob.size);
    try {
      await sgpDb.transaction('rw', sgpDb.fotos, sgpDb.cola_sync, sgpDb.inspecciones, async () => {
        const local = await sgpDb.inspecciones.get(foto.inspeccionId);
        if (
          !local ||
          local.estado !== 'BORRADOR' ||
          local.propietarioId !== foto.propietarioId ||
          local.intencionFinal?.estado === 'PENDIENTE' ||
          local.intencionFinal?.estado === 'CONFIRMADA'
        )
          throw new Error('offline.hu061.notEditable');
        const anterior = await sgpDb.fotos.get(foto.id);
        if (anterior && anterior.propietarioId !== foto.propietarioId)
          throw new Error('propietario');
        // Repetir guardar nunca sustituye bytes ni reinicia una operación ya existente.
        if (anterior) {
          if (
            anterior.inspeccionId !== foto.inspeccionId ||
            anterior.idempotencyKey !== foto.idempotencyKey
          )
            throw new Error('conflicto');
          return;
        }
        const fotos = await sgpDb.fotos.where('inspeccionId').equals(foto.inspeccionId).toArray();
        const inventario = new Set((local?.fotosConfirmadas ?? []).map((item) => item.id));
        for (const item of fotos) inventario.add(item.id);
        if (inventario.size >= 60) throw new Error('limite');
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
    } catch (error) {
      if (/quota/i.test(String((error as { name?: string })?.name)))
        throw new Error('espacioInsuficiente');
      throw error;
    }
  }

  async eliminar(id: string, propietarioId: string): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.fotos, sgpDb.cola_sync, sgpDb.inspecciones, async () => {
      const foto = await sgpDb.fotos.get(id);
      if (!foto || foto.propietarioId !== propietarioId) throw new Error('propietario');
      const local = await sgpDb.inspecciones.get(foto.inspeccionId);
      if (
        !local ||
        local.propietarioId !== propietarioId ||
        local.estado !== 'BORRADOR' ||
        ['PENDIENTE', 'CONFIRMADA'].includes(local.intencionFinal?.estado ?? '')
      )
        throw new Error('estado');
      const operaciones = await sgpDb.cola_sync.where('entityId').equals(id).toArray();
      // Un envío iniciado puede haber sido confirmado remotamente aunque se perdiera la respuesta.
      if (
        !operaciones.length ||
        operaciones.some(
          (item) =>
            item.propietarioId !== propietarioId ||
            item.estado === 'ENVIANDO' ||
            item.intentos > 0 ||
            item.iniciadoEn,
        )
      )
        throw new Error('envioIncierto');
      await sgpDb.cola_sync.bulkDelete(operaciones.map((item) => item.id));
      await sgpDb.fotos.delete(id);
    });
  }

  async recuperarCola(id: string, propietarioId: string): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.fotos, sgpDb.cola_sync, sgpDb.inspecciones, async () => {
      const foto = await sgpDb.fotos.get(id);
      const local = foto ? await sgpDb.inspecciones.get(foto.inspeccionId) : undefined;
      if (
        !foto ||
        foto.propietarioId !== propietarioId ||
        !foto.metadatos ||
        !foto.idempotencyKey ||
        !foto.blob.size ||
        !local ||
        local.propietarioId !== propietarioId ||
        local.estado !== 'BORRADOR'
      )
        throw new Error('datos');
      if ((await sgpDb.cola_sync.where('entityId').equals(id).count()) > 0) return;
      // Se reutiliza la identidad original incluso si el servidor ya recibió la imagen.
      await sgpDb.cola_sync.put({
        id,
        tipo: 'FOTO',
        entityId: id,
        propietarioId,
        idempotencyKey: foto.idempotencyKey,
        estado: 'PENDIENTE',
        intentos: 0,
        creadoEn: new Date().toISOString(),
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
  async confirmar(
    id: string,
    propietarioId: string,
    respuesta?: FotografiaResponse,
  ): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.fotos, sgpDb.cola_sync, sgpDb.inspecciones, async () => {
      const foto = await sgpDb.fotos.get(id);
      if (foto?.propietarioId !== propietarioId) throw new Error('propietario');
      if (respuesta) {
        const local = await sgpDb.inspecciones.get(foto.inspeccionId);
        if (local?.propietarioId === propietarioId)
          await sgpDb.inspecciones.update(local.id, {
            fotosConfirmadas: [
              ...(local.fotosConfirmadas ?? []).filter((item) => item.id !== respuesta.id),
              respuesta,
            ],
          });
      }
      await sgpDb.fotos.delete(id);
      await sgpDb.cola_sync.delete(id);
    });
  }

  async inventario(propietarioId: string, inspeccionId: string): Promise<FotografiaResponse[]> {
    const local = await sgpDb.inspecciones.get(inspeccionId);
    return local?.propietarioId === propietarioId ? (local.fotosConfirmadas ?? []) : [];
  }
  async guardarInventario(
    propietarioId: string,
    inspeccionId: string,
    fotos: FotografiaResponse[],
  ): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.inspecciones, async () => {
      const local = await sgpDb.inspecciones.get(inspeccionId);
      if (local?.propietarioId === propietarioId)
        await sgpDb.inspecciones.update(inspeccionId, { fotosConfirmadas: fotos });
    });
  }
  async guardarContexto(
    propietarioId: string,
    inspeccionId: string,
    puenteId: string,
    estado: string,
    payload: unknown,
  ): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.inspecciones, async () => {
      const existente = await sgpDb.inspecciones.get(inspeccionId);
      if (existente) {
        if (existente.propietarioId !== propietarioId) throw new Error('propietario');
        return; // El contexto visual nunca sustituye las respuestas ni confirma una edición.
      }
      await sgpDb.inspecciones.put({
        id: inspeccionId,
        propietarioId,
        puenteId,
        estado,
        payload,
        actualizadoEn: new Date().toISOString(),
        sincronizado: true,
      });
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
