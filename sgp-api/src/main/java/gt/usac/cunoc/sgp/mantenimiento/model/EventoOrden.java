package gt.usac.cunoc.sgp.mantenimiento.model;

/**
 * Acciones que cambian el estado de una orden de mantenimiento, cada una con su único origen y
 * destino válidos (RN-MTO-04).
 */
public enum EventoOrden {
  ACEPTAR_Y_PROGRAMAR(EstadoOrden.PROPUESTA, EstadoOrden.PROGRAMADA, false),
  DESCARTAR(EstadoOrden.PROPUESTA, EstadoOrden.DESCARTADA, true),
  INICIAR_EJECUCION(EstadoOrden.PROGRAMADA, EstadoOrden.EN_EJECUCION, false),
  CANCELAR(EstadoOrden.PROGRAMADA, EstadoOrden.CANCELADA, true),
  /** Exige fecha, responsable y evidencia; su operación se implementa en HU-048 (RN-MTO-05). */
  CERRAR_COMO_EJECUTADA(EstadoOrden.EN_EJECUCION, EstadoOrden.EJECUTADA, false);

  private final EstadoOrden origen;
  private final EstadoOrden destino;
  private final boolean requiereMotivo;

  EventoOrden(EstadoOrden origen, EstadoOrden destino, boolean requiereMotivo) {
    this.origen = origen;
    this.destino = destino;
    this.requiereMotivo = requiereMotivo;
  }

  public EstadoOrden origen() {
    return origen;
  }

  public EstadoOrden destino() {
    return destino;
  }

  /** Descartar y cancelar exigen un motivo documentado (RN-MTO-09). */
  public boolean requiereMotivo() {
    return requiereMotivo;
  }
}
