package gt.usac.cunoc.sgp.mantenimiento.model;

/** Estados del ciclo de vida de una orden de mantenimiento (RN-MTO-04). */
public enum EstadoOrden {
  PROPUESTA,
  PROGRAMADA,
  EN_EJECUCION,
  EJECUTADA,
  DESCARTADA,
  CANCELADA;

  /** EJECUTADA, DESCARTADA y CANCELADA no admiten ninguna transición posterior. */
  public boolean esTerminal() {
    return this == EJECUTADA || this == DESCARTADA || this == CANCELADA;
  }
}
