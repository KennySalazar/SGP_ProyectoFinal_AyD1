import { EstadoOrden } from '../models/orden-mantenimiento.models';

/** Severidad del `p-tag` de cada estado; siempre se muestra junto a su etiqueta. */
export function severidadEstadoOrden(
  estado: EstadoOrden,
): 'success' | 'warn' | 'danger' | 'info' | 'secondary' {
  switch (estado) {
    case 'EJECUTADA':
      return 'success';
    case 'EN_EJECUCION':
      return 'warn';
    case 'PROGRAMADA':
      return 'info';
    case 'DESCARTADA':
    case 'CANCELADA':
      return 'danger';
    default:
      return 'secondary';
  }
}

/** Pestañas de la bandeja (HU-049); la última agrupa los dos cierres sin ejecución. */
export const PESTANAS_ORDENES: { clave: string; estados: EstadoOrden[] }[] = [
  { clave: 'propuestas', estados: ['PROPUESTA'] },
  { clave: 'programadas', estados: ['PROGRAMADA'] },
  { clave: 'enEjecucion', estados: ['EN_EJECUCION'] },
  { clave: 'ejecutadas', estados: ['EJECUTADA'] },
  { clave: 'cerradas', estados: ['DESCARTADA', 'CANCELADA'] },
];

/** Las fechas con hora se muestran en hora de Guatemala (UTC-6, sin horario de verano). */
export const ZONA_GUATEMALA = '-0600';

/** Convierte una fecha del calendario a yyyy-MM-dd sin desplazarla por la zona horaria. */
export function aFechaIso(fecha: Date | null | undefined): string | null {
  if (!fecha) return null;
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}
