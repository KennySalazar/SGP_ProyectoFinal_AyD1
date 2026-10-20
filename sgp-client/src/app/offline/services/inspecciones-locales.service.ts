import { Injectable } from '@angular/core';
import { sgpDb, LocalInspection, LocalBridge, LocalFormSchema } from '../db/sgp-db';
import {
  IniciarInspeccionRequest,
  IniciarInspeccionResponse,
  InspeccionBorradorResponse,
  DatosPermanentesResponse,
} from '../../features/inspecciones/models/inspeccion.models';

export interface BorradorLocalPayload {
  borrador: InspeccionBorradorResponse;
  versionFormularioId: string;
  dispositivoId: string;
  autorId: string;
  sinGps?: boolean;
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
    await sgpDb.inspecciones.delete(id);
  }

  /**
   * Obtiene todas las inspecciones locales creadas por el usuario que estén pendientes de sincronizar.
   */
  async listarPendientesPorUsuario(autorId: string): Promise<LocalInspection[]> {
    const todas = await sgpDb.inspecciones.toArray();
    return todas.filter((i) => i.propietarioId === autorId && i.sincronizado === false);
  }

  /**
   * Obtiene la versión activa de formulario guardada localmente en Dexie.
   */
  async obtenerEsquemaActivoLocal(): Promise<LocalFormSchema | undefined> {
    const activos = await sgpDb.esquemas_formulario.where('activo').equals(1).toArray();
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
    await sgpDb.esquemas_formulario.put(esquema);
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
      version: 1,
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

    await this.guardarInspeccionLocal(localItem);

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
}
