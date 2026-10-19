package gt.usac.cunoc.sgp.mantenimiento.model;

/**
 * Estados del ciclo de vida de una orden de mantenimiento (RN-MTO-04, V10 orden_mantenimiento).
 *
 * <p>Las transiciones válidas entre ellos las gobierna la máquina de estados de HU-047.
 */
public enum EstadoOrdenMantenimiento {
  PROPUESTA,
  PROGRAMADA,
  EN_EJECUCION,
  EJECUTADA,
  DESCARTADA,
  CANCELADA
}
