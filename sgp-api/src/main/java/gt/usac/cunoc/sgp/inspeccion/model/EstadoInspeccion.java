package gt.usac.cunoc.sgp.inspeccion.model;

/** Estados del ciclo de vida de una inspección (RN-INS-04). */
public enum EstadoInspeccion {
  BORRADOR,
  ENVIADA,
  EN_REVISION,
  CAMBIOS_SOLICITADOS,
  PUBLICADA,
  RECHAZADA;

  /** PUBLICADA y RECHAZADA no admiten ninguna transición posterior. */
  public boolean esTerminal() {
    return this == PUBLICADA || this == RECHAZADA;
  }
}
