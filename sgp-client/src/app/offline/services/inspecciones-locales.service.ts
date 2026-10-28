import { from } from 'rxjs';
import { liveQuery } from 'dexie';
import { AuthStore } from '../../core/services/auth.store';
import { Injectable, inject } from '@angular/core';
import { sgpDb, LocalInspection, LocalBridge, LocalFormSchema, SyncQueueItem } from '../db/sgp-db';
import {
  IniciarInspeccionRequest,
  IniciarInspeccionResponse,
  InspeccionBorradorResponse,
  DatosPermanentesResponse,
  GuardarBorradorRequest,
} from '../../features/inspecciones/models/inspeccion.models';
import { uuidV7 } from './uuid-v7';

export interface BorradorLocalPayload {
  borrador: InspeccionBorradorResponse;
  versionFormularioId: string;
  dispositivoId: string;
  autorId: string;
  sinGps?: boolean;
  solicitudInicio?: IniciarInspeccionRequest;
}

@Injectable({ providedIn: 'root' })
export class InspeccionesLocalesService {
  private readonly auth = inject(AuthStore);
  observarInspeccion(id: string) {
    return from(liveQuery(() => sgpDb.inspecciones.get(id)));
  }

  /**
   * Guarda o actualiza una inspección local en Dexie.
   * Lanza un error si se excede la cuota de almacenamiento.
   */
  async guardarInspeccionLocal(inspeccion: LocalInspection): Promise<void> {
    try {
      await sgpDb.inspecciones.put(inspeccion);
    } catch (err: unknown) {
      if (
        (err as { name?: string })?.name === 'QuotaExceededError' ||
        (err as { message?: string })?.message?.includes('quota')
      ) {
        throw new Error('Almacenamiento local insuficiente para guardar los cambios.');
      }
      throw err;
    }
  }

  private exigirEditable(local: LocalInspection | undefined, propietarioId?: string): void {
    if (
      local &&
      (local.propietarioId !== propietarioId ||
        local.intencionFinal?.estado === 'PENDIENTE' ||
        local.intencionFinal?.estado === 'CONFIRMADA')
    ) {
      throw new Error('offline.hu061.locked');
    }
  }

  async encolarAccionFinal(
    id: string,
    accion: 'enviar' | 'publicar',
    request: GuardarBorradorRequest,
  ): Promise<void> {
    const usuario = this.auth.user();
    if (
      !usuario?.active ||
      !usuario.activated ||
      !usuario.verified ||
      (accion === 'enviar'
        ? usuario.role !== 'ESTUDIANTE'
        : !['CATEDRATICO', 'PROFESIONAL_EXTERNO'].includes(usuario.role)) ||
      (usuario.role === 'PROFESIONAL_EXTERNO' && !usuario.colegiadoVerificado)
    )
      throw new Error('offline.hu061.forbidden');
    await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, sgpDb.fotos, async () => {
      const local = await sgpDb.inspecciones.get(id);
      if (!local || local.propietarioId !== usuario.id || local.estado !== 'BORRADOR')
        throw new Error('offline.hu061.notEditable');
      if (local.intencionFinal?.estado === 'PENDIENTE') return;
      const fotos = await sgpDb.fotos.where('inspeccionId').equals(id).toArray();
      const inventario = new Map<string, string>(
        (local.fotosConfirmadas ?? []).map((foto) => [foto.id, foto.categoria]),
      );
      for (const foto of fotos)
        if (foto.propietarioId === usuario.id && foto.metadatos && foto.blob.size > 0)
          inventario.set(foto.id, foto.metadatos.categoria);
      if (
        inventario.size > 60 ||
        ['ACCESO', 'SUPERESTRUCTURA', 'SUBESTRUCTURA', 'CAUCE'].some(
          (categoria) => ![...inventario.values()].includes(categoria),
        )
      )
        throw new Error('offline.hu061.invalidPhotos');
      const pendientes = await sgpDb.cola_sync.where('entityId').equals(id).toArray();
      if (pendientes.some((item) => item.estado === 'INTERVENCION'))
        throw new Error('offline.hu061.resolveErrors');
      await this.guardarBorradorLocal(
        {
          ...local,
          actualizacionLocalPendiente: false,
          sincronizado: false,
          payload: {
            ...(local.payload as BorradorLocalPayload),
            borrador: {
              ...(local.payload as BorradorLocalPayload).borrador,
              datos: request.datos,
              fechaInspeccion:
                request.fechaInspeccion ??
                (local.payload as BorradorLocalPayload).borrador.fechaInspeccion,
            },
          },
        },
        request,
      );
      await sgpDb.cola_sync.put({
        id: uuidV7(),
        tipo: 'INSPECCION',
        entityId: id,
        propietarioId: usuario.id,
        operacion: accion === 'enviar' ? 'ENVIAR_INSPECCION' : 'PUBLICAR_INSPECCION',
        payload: null,
        idempotencyKey: uuidV7(),
        intentos: 0,
        creadoEn: await this.siguienteCreadoEn(),
        estado: 'PENDIENTE',
      });
      await sgpDb.inspecciones.update(id, {
        sincronizado: false,
        intencionFinal: { accion, estado: 'PENDIENTE' },
      });
    });
  }

  async guardarBorradorEnCache(inspeccion: LocalInspection): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
      this.exigirEditable(await sgpDb.inspecciones.get(inspeccion.id), inspeccion.propietarioId);
      const pendientes = await sgpDb.cola_sync.where('entityId').equals(inspeccion.id).toArray();
      await sgpDb.inspecciones.put({
        ...(await sgpDb.inspecciones.get(inspeccion.id)),
        ...inspeccion,
        sincronizado: pendientes.length === 0 && !inspeccion.actualizacionLocalPendiente,
      });
    });
  }

  async cachearBorradorCanonico(
    borrador: InspeccionBorradorResponse,
    propietarioId: string,
  ): Promise<void> {
    const existente = await sgpDb.inspecciones.get(borrador.id);
    const payloadExistente =
      existente?.payload && typeof existente.payload === 'object'
        ? (existente.payload as Partial<BorradorLocalPayload>)
        : {};
    await this.guardarBorradorEnCache({
      id: borrador.id,
      puenteId: borrador.puenteId,
      propietarioId,
      estado: borrador.estado,
      actualizadoEn:
        borrador.actualizadoEn ??
        borrador.ultimaActividadEn ??
        borrador.creadoEn ??
        new Date().toISOString(),
      payload: {
        ...payloadExistente,
        borrador,
        versionFormularioId: borrador.versionFormularioId,
        dispositivoId: borrador.dispositivoId,
        autorId: propietarioId,
      },
      sincronizado: true,
      actualizacionLocalPendiente: false,
    });
  }

  async actualizarBorradorCanonicoSiSinPendientes(
    borrador: InspeccionBorradorResponse,
    propietarioId: string,
  ): Promise<boolean> {
    return sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
      const pendientes = await sgpDb.cola_sync.where('entityId').equals(borrador.id).toArray();
      if (pendientes.length > 0) return false;

      const existente = await sgpDb.inspecciones.get(borrador.id);
      if (
        existente?.actualizacionLocalPendiente ||
        existente?.intencionFinal?.estado === 'PENDIENTE'
      )
        return false;
      if (existente && existente.propietarioId !== propietarioId) return false;
      const payloadExistente =
        existente?.payload && typeof existente.payload === 'object'
          ? (existente.payload as Partial<BorradorLocalPayload>)
          : {};
      await sgpDb.inspecciones.put({
        ...existente,
        id: borrador.id,
        puenteId: borrador.puenteId,
        propietarioId,
        estado: borrador.estado,
        actualizadoEn:
          borrador.actualizadoEn ??
          borrador.ultimaActividadEn ??
          borrador.creadoEn ??
          new Date().toISOString(),
        payload: {
          ...payloadExistente,
          borrador,
          versionFormularioId: borrador.versionFormularioId,
          dispositivoId: borrador.dispositivoId,
          autorId: propietarioId,
        },
        sincronizado: true,
        actualizacionLocalPendiente: false,
        intencionFinal:
          borrador.estado === 'BORRADOR' &&
          existente?.intencionFinal?.accion === 'enviar' &&
          existente.intencionFinal.estado === 'CONFIRMADA'
            ? undefined
            : existente?.intencionFinal,
      });
      return true;
    });
  }

  async guardarBorradorLocal(
    inspeccion: LocalInspection,
    request: GuardarBorradorRequest,
    estado: 'PENDIENTE' | 'ENVIANDO' = 'PENDIENTE',
  ): Promise<{ id: string; idempotencyKey: string }> {
    return sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
      const existenteLocal = await sgpDb.inspecciones.get(inspeccion.id);
      this.exigirEditable(existenteLocal, inspeccion.propietarioId);
      const versionConfirmada =
        existenteLocal && existenteLocal.propietarioId === inspeccion.propietarioId
          ? (existenteLocal.payload as BorradorLocalPayload | undefined)?.borrador?.version
          : undefined;
      const borradorEntrante = (inspeccion.payload as BorradorLocalPayload).borrador;
      await sgpDb.inspecciones.put({
        ...existenteLocal,
        ...inspeccion,
        payload:
          versionConfirmada !== undefined && borradorEntrante.version < versionConfirmada
            ? {
                ...(inspeccion.payload as BorradorLocalPayload),
                borrador: { ...borradorEntrante, version: versionConfirmada },
              }
            : inspeccion.payload,
      });
      const existentes = await sgpDb.cola_sync.where('entityId').equals(inspeccion.id).toArray();
      const duplicada = existentes.find(
        (item) =>
          item.operacion === 'GUARDAR_BORRADOR' &&
          JSON.stringify(item.payload) === JSON.stringify(request) &&
          (item.estado ?? 'PENDIENTE') !== 'INTERVENCION',
      );
      if (duplicada) return { id: duplicada.id, idempotencyKey: duplicada.idempotencyKey };
      const nueva: SyncQueueItem = {
        id: uuidV7(),
        tipo: 'INSPECCION',
        entityId: inspeccion.id,
        propietarioId: inspeccion.propietarioId,
        operacion: 'GUARDAR_BORRADOR',
        payload: request,
        idempotencyKey: uuidV7(),
        intentos: 0,
        creadoEn: await this.siguienteCreadoEn(),
        estado,
        peticionFijada: estado === 'ENVIANDO',
        iniciadoEn: estado === 'ENVIANDO' ? new Date().toISOString() : undefined,
      };
      await sgpDb.cola_sync.put(nueva);
      return { id: nueva.id, idempotencyKey: nueva.idempotencyKey };
    });
  }

  async confirmarGuardado(
    idOperacion: string,
    inspeccionId: string,
    propietarioId: string,
    respuesta: InspeccionBorradorResponse,
  ): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
      const operacionConfirmada = await sgpDb.cola_sync.get(idOperacion);
      await sgpDb.cola_sync.delete(idOperacion);
      const local = await sgpDb.inspecciones.get(inspeccionId);
      if (!local || local.propietarioId !== propietarioId) return;
      const payload = local.payload as BorradorLocalPayload;
      const pendientes = await sgpDb.cola_sync.where('entityId').equals(inspeccionId).toArray();
      const edicionesPosteriores = pendientes
        .filter(
          (item) =>
            item.operacion === 'GUARDAR_BORRADOR' &&
            item.propietarioId === propietarioId &&
            operacionConfirmada !== undefined &&
            item.creadoEn > operacionConfirmada.creadoEn,
        )
        .sort((a, b) => b.creadoEn.localeCompare(a.creadoEn));
      const datosPosteriores =
        local.actualizacionLocalPendiente &&
        operacionConfirmada?.payload &&
        JSON.stringify(payload.borrador.datos) !==
          JSON.stringify((operacionConfirmada.payload as GuardarBorradorRequest).datos)
          ? payload.borrador.datos
          : (edicionesPosteriores[0]?.payload as GuardarBorradorRequest | undefined)?.datos;
      await sgpDb.inspecciones.put({
        ...local,
        sincronizado: pendientes.length === 0,
        actualizacionLocalPendiente: !!datosPosteriores && edicionesPosteriores.length === 0,
        payload: {
          ...payload,
          borrador: {
            ...respuesta,
            datos: datosPosteriores ?? respuesta.datos,
            fechaInspeccion: datosPosteriores
              ? payload.borrador?.fechaInspeccion
              : respuesta.fechaInspeccion,
          },
        },
      });
    });
  }

  async marcarOperacionParaIntervencion(
    id: string,
    mensaje: string,
    codigoError?: string,
  ): Promise<void> {
    await sgpDb.cola_sync.update(id, {
      estado: 'INTERVENCION',
      ultimoError: mensaje,
      codigoError,
    });
  }

  async marcarOperacionPendiente(id: string, mensaje: string): Promise<void> {
    await sgpDb.cola_sync.update(id, {
      estado: 'PENDIENTE',
      ultimoError: mensaje,
      codigoError: undefined,
    });
  }

  async tieneConflictoConcurrencia(inspeccionId: string, propietarioId?: string): Promise<boolean> {
    if (!propietarioId) return false;
    const operaciones = await sgpDb.cola_sync.where('entityId').equals(inspeccionId).toArray();
    return operaciones.some(
      (item) =>
        item.propietarioId === propietarioId &&
        item.operacion === 'GUARDAR_BORRADOR' &&
        item.estado === 'INTERVENCION' &&
        item.codigoError === 'concurrencia_conflicto',
    );
  }

  async encolarInicio(
    propietarioId: string,
    solicitud: IniciarInspeccionRequest,
  ): Promise<{ id: string; idempotencyKey: string }> {
    return sgpDb.transaction('rw', sgpDb.cola_sync, async () => {
      const existente = (
        await sgpDb.cola_sync.where('entityId').equals(solicitud.id!).toArray()
      ).find(
        (item) => item.operacion === 'CREAR_INSPECCION' && item.idempotencyKey === solicitud.id,
      );
      if (existente) return { id: existente.id, idempotencyKey: existente.idempotencyKey };
      const id = uuidV7();
      const idempotencyKey = solicitud.id || uuidV7();
      await sgpDb.cola_sync.put({
        id,
        tipo: 'INSPECCION',
        entityId: solicitud.id || id,
        propietarioId,
        operacion: 'CREAR_INSPECCION',
        payload: solicitud,
        idempotencyKey,
        intentos: 0,
        creadoEn: await this.siguienteCreadoEn(),
        estado: 'ENVIANDO',
        iniciadoEn: new Date().toISOString(),
      });
      return { id, idempotencyKey };
    });
  }

  async confirmarInicio(id: string, inspeccionId: string, propietarioId: string): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
      await sgpDb.cola_sync.delete(id);
      const local = await sgpDb.inspecciones.get(inspeccionId);
      if (!local || local.propietarioId !== propietarioId) return;
      const pendientes = await sgpDb.cola_sync.where('entityId').equals(inspeccionId).toArray();
      await sgpDb.inspecciones.update(inspeccionId, { sincronizado: pendientes.length === 0 });
    });
  }

  /**
   * Obtiene una inspección local por su ID.
   */
  async obtenerInspeccionLocal(id: string): Promise<LocalInspection | undefined> {
    const local = await sgpDb.inspecciones.get(id);
    if (!local || !this.auth.user()?.id || local.propietarioId !== this.auth.user()?.id)
      return undefined;

    if (typeof local.payload !== 'object' || local.payload === null) return local;
    const raw = local.payload as Record<string, unknown>;
    const envuelto = 'borrador' in raw && raw['borrador'] !== null;
    const borrador = (envuelto ? raw['borrador'] : raw) as InspeccionBorradorResponse | undefined;
    const datosLocales = borrador?.datos;
    if (datosLocales !== undefined && datosLocales !== null && Object.keys(datosLocales).length > 0)
      return local;
    if (!local.propietarioId) return local;

    const pendientes = await sgpDb.cola_sync.where('entityId').equals(id).toArray();
    const ultimaOperacion = pendientes
      .filter(
        (item) =>
          item.operacion === 'GUARDAR_BORRADOR' && item.propietarioId === local.propietarioId,
      )
      .sort((a, b) => b.creadoEn.localeCompare(a.creadoEn))[0];
    const datosEnCola = (ultimaOperacion?.payload as GuardarBorradorRequest | undefined)?.datos;
    if (!datosEnCola || Object.keys(datosEnCola).length === 0 || !borrador) return local;

    return {
      ...local,
      payload: envuelto
        ? { ...raw, borrador: { ...borrador, datos: datosEnCola } }
        : { ...borrador, datos: datosEnCola },
    };
  }

  /** Solo una creación que nunca se intentó enviar puede descartarse sin contactar al servidor. */
  async esBorradorSoloLocal(id: string): Promise<boolean> {
    const local = await this.obtenerInspeccionLocal(id);
    const operaciones = await sgpDb.cola_sync.where('entityId').equals(id).toArray();
    const creaciones = operaciones.filter((item) => item.operacion === 'CREAR_INSPECCION');
    if (
      creaciones.some((item) => item.estado !== 'PENDIENTE' || item.intentos > 0 || item.iniciadoEn)
    )
      throw new Error('creacionPendiente');
    return !!local && !local.sincronizado && local.estado === 'BORRADOR' && creaciones.length > 0;
  }

  /** Retira atómicamente los datos locales tras confirmar que el borrador remoto ya no existe. */
  async confirmarEliminacionRemota(id: string, propietarioId: string): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, sgpDb.fotos, async () => {
      const local = await sgpDb.inspecciones.get(id);
      const fotos = await sgpDb.fotos.where('inspeccionId').equals(id).toArray();
      const ids = new Set([id, ...fotos.map((foto) => foto.id)]);
      const operaciones = (await sgpDb.cola_sync.toArray()).filter((item) =>
        ids.has(item.entityId),
      );
      if (
        this.auth.user()?.id !== propietarioId ||
        (local && local.propietarioId !== propietarioId) ||
        fotos.some((foto) => foto.propietarioId !== propietarioId) ||
        operaciones.some((item) => item.propietarioId !== propietarioId)
      )
        throw new Error('offline.hu061.notEditable');
      if (operaciones.some((item) => item.operacion === 'CREAR_INSPECCION'))
        throw new Error('creacionPendiente');
      await sgpDb.cola_sync.bulkDelete(operaciones.map((item) => item.id));
      await sgpDb.fotos.bulkDelete(fotos.map((foto) => foto.id));
      await sgpDb.inspecciones.delete(id);
    });
  }

  /**
   * Elimina una inspección local de Dexie si no ha sido sincronizada.
   */
  async eliminarInspeccionLocal(id: string, exigirCreacionPendiente = false): Promise<void> {
    const propietarioId = this.auth.user()?.id;
    await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, sgpDb.fotos, async () => {
      const local = await sgpDb.inspecciones.get(id);
      if (
        !propietarioId ||
        local?.propietarioId !== propietarioId ||
        local.estado !== 'BORRADOR' ||
        ['PENDIENTE', 'CONFIRMADA'].includes(local.intencionFinal?.estado ?? '')
      )
        throw new Error('offline.hu061.notEditable');
      const fotos = await sgpDb.fotos.where('inspeccionId').equals(id).toArray();
      const ids = new Set([id, ...fotos.map((foto) => foto.id)]);
      const operaciones = (await sgpDb.cola_sync.toArray()).filter((item) =>
        ids.has(item.entityId),
      );
      if (
        exigirCreacionPendiente &&
        (local.sincronizado || !operaciones.some((item) => item.operacion === 'CREAR_INSPECCION'))
      )
        throw new Error('creacionPendiente');
      if (
        fotos.some((foto) => foto.propietarioId !== propietarioId) ||
        operaciones.some(
          (item) =>
            item.propietarioId !== propietarioId ||
            item.estado === 'ENVIANDO' ||
            item.intentos > 0 ||
            item.iniciadoEn,
        )
      )
        throw new Error(exigirCreacionPendiente ? 'creacionPendiente' : 'envioIncierto');
      await sgpDb.cola_sync.bulkDelete(operaciones.map((item) => item.id));
      await sgpDb.fotos.bulkDelete(fotos.map((foto) => foto.id));
      await sgpDb.inspecciones.delete(id);
    });
  }

  /**
   * Obtiene las inspecciones locales del usuario, pendientes o ya sincronizadas.
   */
  async listarPorUsuario(autorId: string): Promise<LocalInspection[]> {
    const todas = await sgpDb.inspecciones.toArray();
    return todas.filter((i) => i.propietarioId === autorId);
  }

  async liberarInspeccionesSincronizadas(propietarioId: string): Promise<number> {
    return sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, sgpDb.fotos, async () => {
      const inspecciones = await sgpDb.inspecciones.toArray();
      const cola = await sgpDb.cola_sync.toArray();
      const fotos = await sgpDb.fotos.toArray();
      const eliminables = inspecciones.filter(
        (inspeccion) =>
          inspeccion.propietarioId === propietarioId &&
          inspeccion.sincronizado &&
          !inspeccion.actualizacionLocalPendiente &&
          inspeccion.intencionFinal?.estado !== 'PENDIENTE' &&
          inspeccion.intencionFinal?.estado !== 'RECHAZADA' &&
          !cola.some(
            (item) =>
              item.entityId === inspeccion.id ||
              fotos.some(
                (foto) => foto.inspeccionId === inspeccion.id && foto.id === item.entityId,
              ),
          ) &&
          !fotos.some(
            (foto) =>
              foto.inspeccionId === inspeccion.id &&
              (!foto.sincronizado || foto.propietarioId !== propietarioId),
          ),
      );
      const ids = new Set(eliminables.map((inspeccion) => inspeccion.id));
      await sgpDb.fotos.bulkDelete(
        fotos.filter((foto) => ids.has(foto.inspeccionId)).map((foto) => foto.id),
      );
      await sgpDb.inspecciones.bulkDelete([...ids]);
      return eliminables.length;
    });
  }

  /**
   * Obtiene la versión activa de formulario guardada localmente en Dexie.
   */
  async obtenerEsquemaActivoLocal(): Promise<LocalFormSchema | undefined> {
    const todos = (await sgpDb.esquemas_formulario.toArray()).filter(
      (item) => !!this.auth.user()?.id && item.propietarioId === this.auth.user()?.id,
    );
    const activos = todos.filter((item) => item.activo);
    if (activos.length > 0) return activos[0];
    return todos.length > 0 ? todos[todos.length - 1] : undefined;
  }

  /**
   * Obtiene un esquema de formulario por su version/id desde Dexie.
   */
  async obtenerEsquemaLocal(idOVersion: string): Promise<LocalFormSchema | undefined> {
    const propios = (await sgpDb.esquemas_formulario.toArray()).filter(
      (item) => !!this.auth.user()?.id && item.propietarioId === this.auth.user()?.id,
    );
    return propios.find(
      (item) =>
        item.id === idOVersion || item.origenId === idOVersion || item.version === idOVersion,
    );
  }

  /**
   * Guarda un esquema en Dexie.
   */
  async guardarEsquemaLocal(esquema: LocalFormSchema): Promise<void> {
    const propietarioId = esquema.propietarioId ?? this.auth.user()?.id;
    if (!propietarioId) return;
    const origenId = esquema.origenId ?? esquema.id;
    const id = `cache:${propietarioId}:${origenId}`;
    await sgpDb.transaction('rw', sgpDb.esquemas_formulario, async () => {
      const existente = await sgpDb.esquemas_formulario.get(id);
      if (esquema.activo) {
        await sgpDb.esquemas_formulario.toCollection().modify((item) => {
          if (item.propietarioId === propietarioId) item.activo = false;
        });
      }
      await sgpDb.esquemas_formulario.put({
        ...esquema,
        id,
        origenId,
        propietarioId,
        activo: esquema.activo || existente?.activo === true,
      });
    });
  }

  async guardarPuenteLocal(bridge: LocalBridge): Promise<void> {
    const propietarioId = bridge.propietarioId ?? this.auth.user()?.id;
    if (!propietarioId) return;
    const origenId = bridge.origenId ?? bridge.id;
    await sgpDb.puentes.put({
      ...bridge,
      id: `cache:${propietarioId}:${origenId}`,
      origenId,
      propietarioId,
    });
  }

  /**
   * Obtiene los puentes preparados en Dexie.
   */
  async obtenerPuentesPreparados(): Promise<LocalBridge[]> {
    return (await sgpDb.puentes.toArray()).filter(
      (item) => !!this.auth.user()?.id && item.propietarioId === this.auth.user()?.id,
    );
  }

  /**
   * Obtiene los datos permanentes del puente desde la última inspección publicada o datos de salida en Dexie.
   */
  async obtenerDatosPermanentesLocal(puenteId: string): Promise<DatosPermanentesResponse | null> {
    // Buscar en inspecciones si hay una publicada guardada para el puente
    const inspecciones = await sgpDb.inspecciones.where('puenteId').equals(puenteId).toArray();
    const publicada = inspecciones.find(
      (i) =>
        i.estado === 'PUBLICADA' &&
        !!this.auth.user()?.id &&
        i.propietarioId === this.auth.user()?.id,
    );
    if (publicada && publicada.payload) {
      const payload = publicada.payload as { datosPermanentes?: DatosPermanentesResponse };
      if (payload.datosPermanentes) {
        return payload.datosPermanentes;
      }
    }

    // Si el puente guardó datos en su payload
    const puenteLocal = (await this.obtenerPuentesPreparados()).find(
      (item) => (item.origenId ?? item.id) === puenteId,
    );
    if (puenteLocal && puenteLocal.propietarioId === this.auth.user()?.id && puenteLocal.payload) {
      const p = puenteLocal.payload as { datosPermanentes?: DatosPermanentesResponse };
      if (p.datosPermanentes) {
        return p.datosPermanentes;
      }
    }

    return null;
  }

  /**
   * Inicializa y guarda una inspección local sin conexión a partir de la solicitud de inicio.
   */
  async iniciarInspeccionLocal(
    solicitud: IniciarInspeccionRequest,
    autorId: string,
    rolAutor: string,
    nombreAutor: string,
    nombrePuente: string,
    codigoPuente: string,
    departamentoNombre: string,
    municipioNombre?: string,
    versionFormularioId?: string,
    datosPermanentes?: DatosPermanentesResponse | null,
    sinGps?: boolean,
  ): Promise<IniciarInspeccionResponse> {
    const hoyIso = new Date().toISOString();
    const versionId = solicitud.versionFormularioId || versionFormularioId;
    if (!versionId) throw new Error('offline.hu061.prepareFirst');
    solicitud = { ...solicitud, versionFormularioId: versionId };
    solicitud = { ...solicitud, id: solicitud.id ?? uuidV7() };
    const inspeccionId = solicitud.id!;

    const borradorResponse: InspeccionBorradorResponse = {
      id: inspeccionId,
      puenteId: solicitud.puenteId,
      versionFormularioId: versionId,
      codigoVersionFormulario: versionId,
      nombrePuente: nombrePuente,
      codigoPuente: codigoPuente,
      nombreAutor: nombreAutor,
      autorId: autorId,
      dispositivoId: solicitud.dispositivoId,
      rolAutor: rolAutor,
      fechaInspeccion: solicitud.fechaInspeccion,
      estado: 'BORRADOR',
      // Coincide con la versión inicial asignada por @Version en el backend.
      version: 0,
      datos: {},
      identificacionCapturada: {
        nombre_puente: nombrePuente,
        codigo: codigoPuente,
        nombre_inspector: nombreAutor,
        fecha_inspeccion: solicitud.fechaInspeccion,
      },
      datosPermanentes: datosPermanentes ?? null,
      acompanantes: (solicitud.acompanantes ?? []).map((a, idx) => ({
        id: a.id || `acomp-${idx}`,
        usuarioId: a.usuarioId,
        nombre: a.nombre,
        colegiado: a.colegiado,
        numeroColegiado: a.numeroColegiado,
      })),
      creadoEn: hoyIso,
      actualizadoEn: hoyIso,
      progreso: {
        totalCamposRequeridos: 10,
        totalCamposCompletados: 0,
        porcentajeTotal: 0,
        secciones: [],
      },
    };

    const localPayload: BorradorLocalPayload = {
      borrador: borradorResponse,
      versionFormularioId: versionId,
      dispositivoId: solicitud.dispositivoId,
      autorId: autorId,
      sinGps: !!sinGps,
      solicitudInicio: solicitud,
    };

    const localItem: LocalInspection = {
      id: inspeccionId,
      puenteId: solicitud.puenteId,
      propietarioId: autorId,
      estado: 'BORRADOR',
      actualizadoEn: hoyIso,
      payload: localPayload,
      sincronizado: false,
    };

    const operationId = uuidV7();
    await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
      await sgpDb.inspecciones.put(localItem);
      const pendiente = (
        await sgpDb.cola_sync.where('entityId').equals(inspeccionId).toArray()
      ).find(
        (item) => item.operacion === 'CREAR_INSPECCION' && item.idempotencyKey === solicitud.id,
      );
      if (pendiente) {
        await sgpDb.cola_sync.put({
          ...pendiente,
          propietarioId: autorId,
          payload: solicitud,
          estado: 'PENDIENTE',
        });
      } else
        await sgpDb.cola_sync.put({
          id: operationId,
          tipo: 'INSPECCION',
          entityId: inspeccionId,
          propietarioId: autorId,
          operacion: 'CREAR_INSPECCION',
          payload: solicitud,
          idempotencyKey: solicitud.id || operationId,
          intentos: 0,
          creadoEn: await this.siguienteCreadoEn(),
          estado: 'PENDIENTE',
        });
    });

    const inicioResponse: IniciarInspeccionResponse = {
      id: inspeccionId,
      puenteId: solicitud.puenteId,
      versionFormularioId: versionId,
      codigoVersionFormulario: versionId,
      nombrePuente: nombrePuente,
      codigoPuente: codigoPuente,
      nombreAutor: nombreAutor,
      rolAutor: rolAutor,
      fechaInspeccion: solicitud.fechaInspeccion,
      estado: 'BORRADOR',
      autorId: autorId,
      dispositivoId: solicitud.dispositivoId,
      advertenciaGps: false,
      datosPermanentesConfirmados: !!solicitud.confirmarDatosPermanentes,
      datosPermanentes: datosPermanentes ?? null,
      acompanantes: borradorResponse.acompanantes,
      creadoEn: hoyIso,
    };

    return inicioResponse;
  }

  private async siguienteCreadoEn(): Promise<string> {
    const ultimo = await sgpDb.cola_sync.orderBy('creadoEn').last();
    const ahora = Date.now();
    const siguiente = ultimo ? Math.max(ahora, Date.parse(ultimo.creadoEn) + 1) : ahora;
    return new Date(siguiente).toISOString();
  }
}
