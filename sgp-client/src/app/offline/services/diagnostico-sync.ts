import { LocalInspection, LocalPhoto, SyncQueueItem } from '../db/sgp-db';

export interface ElementoDiagnostico {
  id: string;
  entityId: string;
  inspeccionId?: string;
  tipo: 'INSPECCION' | 'FOTO';
  operacion: string;
  estado: 'PENDIENTE' | 'ENVIANDO' | 'INTERVENCION';
  intentos: number;
  iniciadoEn?: string;
  motivo?: string;
  reintentable: boolean;
}
export interface ResumenSincronizacion {
  propietarioId?: string;
  listo: boolean;
  inspecciones: number;
  fotografias: number;
  bytesFotos: number;
  elementos: ElementoDiagnostico[];
  inspeccionesIds: string[];
}

/** Lista cerrada: jamás se presenta el mensaje HTTP, payload, clave o cabecera original. */
export function errorSincronizacion(codigo?: string): string {
  const codigos: Record<string, string> = {
    concurrencia_conflicto: 'conflicto',
    version_formulario_distinta: 'version',
    fotografia_metadatos_invalidos: 'metadatos',
    categoria_fotografia_invalida: 'metadatos',
    estado_inspeccion_no_permite_archivos: 'estado',
    inspeccion_no_editable: 'estado',
    foto_local_incompleta: 'datos',
    operacion_en_proceso: 'proceso',
  };
  return (
    'offline.diagnostico.errores.' +
    (codigo && Object.hasOwn(codigos, codigo) ? codigos[codigo] : 'general')
  );
}

export function resumirSincronizacion(
  propietarioId: string | undefined,
  inspecciones: LocalInspection[],
  fotos: LocalPhoto[],
  cola: SyncQueueItem[],
): ResumenSincronizacion {
  const propias = propietarioId
    ? inspecciones.filter((i) => i.propietarioId === propietarioId)
    : [];
  const pendientes = propietarioId
    ? fotos.filter((f) => f.propietarioId === propietarioId && !f.sincronizado)
    : [];
  const operaciones = propietarioId ? cola.filter((i) => i.propietarioId === propietarioId) : [];
  const elementos: ElementoDiagnostico[] = operaciones.map((item) => ({
    id: item.id,
    entityId: item.entityId,
    tipo: item.tipo,
    inspeccionId:
      item.tipo === 'INSPECCION'
        ? item.entityId
        : pendientes.find((f) => f.id === item.entityId)?.inspeccionId,
    operacion: [
      'CREAR_INSPECCION',
      'GUARDAR_BORRADOR',
      'ENVIAR_INSPECCION',
      'PUBLICAR_INSPECCION',
    ].includes(item.operacion ?? '')
      ? item.operacion!
      : item.tipo === 'FOTO'
        ? 'FOTO'
        : 'DESCONOCIDA',
    estado: item.estado ?? 'PENDIENTE',
    intentos: item.intentos,
    iniciadoEn: item.iniciadoEn,
    motivo:
      item.estado === 'INTERVENCION'
        ? item.intentos >= 5
          ? 'offline.diagnostico.agotado'
          : errorSincronizacion(item.codigoError)
        : item.ultimoError
          ? 'offline.diagnostico.esperaReintento'
          : undefined,
    reintentable: item.estado !== 'ENVIANDO',
  }));
  for (const foto of pendientes) {
    if (!elementos.some((i) => i.tipo === 'FOTO' && i.entityId === foto.id))
      elementos.push({
        id: 'local-foto:' + foto.id,
        entityId: foto.id,
        inspeccionId: foto.inspeccionId,
        tipo: 'FOTO',
        operacion: 'FOTO',
        estado: 'INTERVENCION',
        intentos: 0,
        motivo: 'offline.diagnostico.sinCola',
        reintentable: !!foto.metadatos && !!foto.idempotencyKey && foto.blob.size > 0,
      });
  }
  for (const local of propias) {
    if (
      (!local.sincronizado ||
        local.actualizacionLocalPendiente ||
        ['PENDIENTE', 'RECHAZADA'].includes(local.intencionFinal?.estado ?? '')) &&
      !elementos.some((i) => i.tipo === 'INSPECCION' && i.entityId === local.id)
    )
      elementos.push({
        id: 'local-inspeccion:' + local.id,
        entityId: local.id,
        inspeccionId: local.id,
        tipo: 'INSPECCION',
        operacion: 'GUARDAR_BORRADOR',
        estado: 'INTERVENCION',
        intentos: 0,
        motivo:
          local.intencionFinal?.estado === 'RECHAZADA'
            ? 'offline.diagnostico.rechazada'
            : 'offline.diagnostico.sinCola',
        reintentable: false,
      });
  }
  return {
    propietarioId,
    listo: true,
    inspecciones: new Set(elementos.filter((i) => i.tipo === 'INSPECCION').map((i) => i.entityId))
      .size,
    fotografias: new Set(elementos.filter((i) => i.tipo === 'FOTO').map((i) => i.entityId)).size,
    bytesFotos: pendientes.reduce((n, foto) => n + foto.blob.size, 0),
    elementos,
    inspeccionesIds: propias.map((i) => i.id),
  };
}

/** Proyección técnica de solo lectura; excluye identificación, ubicación y texto libre. */
export function copiaTecnica(copia: unknown): unknown {
  if (!copia || typeof copia !== 'object' || Array.isArray(copia)) return {};
  const origen = copia as Record<string, unknown>;
  const limpiar = (valor: unknown, profundidad = 0): unknown => {
    if (profundidad > 8) return null;
    if (typeof valor === 'boolean' || typeof valor === 'number' || valor === null) return valor;
    if (typeof valor === 'string')
      return ['BUENO', 'REGULAR', 'MALO', 'SI', 'NO', 'NO_APLICA'].includes(valor) ? valor : '…';
    if (Array.isArray(valor)) return valor.slice(0, 100).map((v) => limpiar(v, profundidad + 1));
    if (valor && typeof valor === 'object')
      return Object.fromEntries(
        Object.entries(valor)
          .filter(
            ([k]) =>
              /^[a-zA-Z_][a-zA-Z_0-9]{0,60}$/.test(k) &&
              !/token|password|clave|secret|identifica|nombre|correo|email|telefono|dpi|cui|cedula|carnet|colegiado|direccion|contacto|apellido|acompan|ubicacion|latitud|longitud|coordenada|gps|autor|usuario|inspector/i.test(
                k,
              ),
          )
          .slice(0, 100)
          .map(([k, v]) => [k, limpiar(v, profundidad + 1)]),
      );
    return null;
  };
  return {
    version: typeof origen['version'] === 'number' ? origen['version'] : null,
    estado: ['BORRADOR', 'ENVIADA', 'EN_REVISION', 'PUBLICADA'].includes(String(origen['estado']))
      ? origen['estado']
      : null,
    datos: limpiar(origen['datos']),
  };
}
