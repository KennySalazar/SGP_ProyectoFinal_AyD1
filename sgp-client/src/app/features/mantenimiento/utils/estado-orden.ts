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
