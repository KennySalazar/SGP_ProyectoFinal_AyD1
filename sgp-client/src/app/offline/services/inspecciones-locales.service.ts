import { Injectable } from '@angular/core';
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

  async guardarBorradorEnCache(inspeccion: LocalInspection): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
      const pendientes = await sgpDb.cola_sync.where('entityId').equals(inspeccion.id).toArray();
      await sgpDb.inspecciones.put({
        ...inspeccion,
        sincronizado: pendientes.length === 0,
      });
    });
  }

  async guardarBorradorLocal(
    inspeccion: LocalInspection,
    request: GuardarBorradorRequest,
    estado: 'PENDIENTE' | 'ENVIANDO' = 'PENDIENTE',
  ): Promise<{ id: string; idempotencyKey: string }> {
    return sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
      const existenteLocal = await sgpDb.inspecciones.get(inspeccion.id);
      const versionConfirmada =
        existenteLocal && existenteLocal.propietarioId === inspeccion.propietarioId
          ? (existenteLocal.payload as BorradorLocalPayload | undefined)?.borrador?.version
          : undefined;
      const borradorEntrante = (inspeccion.payload as BorradorLocalPayload).borrador;
      await sgpDb.inspecciones.put({
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
      await sgpDb.cola_sync.delete(idOperacion);
      const local = await sgpDb.inspecciones.get(inspeccionId);
      if (!local || local.propietarioId !== propietarioId) return;
      const payload = local.payload as BorradorLocalPayload;
      const pendientes = await sgpDb.cola_sync.where('entityId').equals(inspeccionId).toArray();
      await sgpDb.inspecciones.put({
        ...local,
        sincronizado: pendientes.length === 0,
        payload: {
          ...payload,
          borrador: {
            ...respuesta,
            datos: payload.borrador?.datos,
            fechaInspeccion: payload.borrador?.fechaInspeccion,
          },
        },
      });
    });
  }

  async marcarOperacionParaIntervencion(id: string, mensaje: string): Promise<void> {
    await sgpDb.cola_sync.update(id, { estado: 'INTERVENCION', ultimoError: mensaje });
  }

  async marcarOperacionPendiente(id: string, mensaje: string): Promise<void> {
    await sgpDb.cola_sync.update(id, { estado: 'PENDIENTE', ultimoError: mensaje });
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
    return sgpDb.inspecciones.get(id);
  }

  /**
   * Elimina una inspección local de Dexie si no ha sido sincronizada.
   */
  async eliminarInspeccionLocal(id: string): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
      await sgpDb.inspecciones.delete(id);
      await sgpDb.cola_sync.where('entityId').equals(id).delete();
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
          !cola.some((item) => item.entityId === inspeccion.id) &&
          !fotos.some((foto) => foto.inspeccionId === inspeccion.id && !foto.sincronizado),
      );
      await sgpDb.inspecciones.bulkDelete(eliminables.map((inspeccion) => inspeccion.id));
      return eliminables.length;
    });
  }

  /**
   * Obtiene la versión activa de formulario guardada localmente en Dexie.
   */
  async obtenerEsquemaActivoLocal(): Promise<LocalFormSchema | undefined> {
    const activos = (await sgpDb.esquemas_formulario.toArray()).filter((item) => item.activo);
    if (activos.length > 0) return activos[0];
    const todos = await sgpDb.esquemas_formulario.toArray();
    return todos.length > 0 ? todos[todos.length - 1] : undefined;
  }

  /**
   * Obtiene un esquema de formulario por su version/id desde Dexie.
   */
  async obtenerEsquemaLocal(idOVersion: string): Promise<LocalFormSchema | undefined> {
    const porId = await sgpDb.esquemas_formulario.get(idOVersion);
    if (porId) return porId;
    const porVersion = await sgpDb.esquemas_formulario.where('version').equals(idOVersion).first();
    return porVersion;
  }

  /**
   * Guarda un esquema en Dexie.
   */
  async guardarEsquemaLocal(esquema: LocalFormSchema): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.esquemas_formulario, async () => {
      const existente = await sgpDb.esquemas_formulario.get(esquema.id);
      if (esquema.activo) {
        await sgpDb.esquemas_formulario.toCollection().modify({ activo: false });
      }
      await sgpDb.esquemas_formulario.put({
        ...esquema,
        activo: esquema.activo || existente?.activo === true,
      });
    });
  }

  async guardarPuenteLocal(bridge: LocalBridge): Promise<void> {
    await sgpDb.puentes.put(bridge);
  }

  /**
   * Obtiene los puentes preparados en Dexie.
   */
  async obtenerPuentesPreparados(): Promise<LocalBridge[]> {
    return sgpDb.puentes.toArray();
  }

  /**
   * Obtiene los datos permanentes del puente desde la última inspección publicada o datos de salida en Dexie.
   */
  async obtenerDatosPermanentesLocal(puenteId: string): Promise<DatosPermanentesResponse | null> {
    // Buscar en inspecciones si hay una publicada guardada para el puente
    const inspecciones = await sgpDb.inspecciones.where('puenteId').equals(puenteId).toArray();
    const publicada = inspecciones.find((i) => i.estado === 'PUBLICADA');
    if (publicada && publicada.payload) {
      const payload = publicada.payload as { datosPermanentes?: DatosPermanentesResponse };
      if (payload.datosPermanentes) {
        return payload.datosPermanentes;
      }
    }

    // Si el puente guardó datos en su payload
    const puenteLocal = await sgpDb.puentes.get(puenteId);
    if (puenteLocal && puenteLocal.payload) {
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
    const versionId = versionFormularioId || 'version-preparada-local';
    const inspeccionId = solicitud.id || 'inspeccion-local';

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
