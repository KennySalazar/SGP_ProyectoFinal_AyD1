package gt.usac.cunoc.sgp.inspeccion.model;

/**
 * Acciones que cambian el estado de una inspección, cada una con su único origen y destino válidos
 * (RN-INS-04, RN-REV-02, RN-REV-06).
 */
public enum EventoInspeccion {
  ENVIAR(EstadoInspeccion.BORRADOR, EstadoInspeccion.ENVIADA),
  INICIAR_REVISION(EstadoInspeccion.ENVIADA, EstadoInspeccion.EN_REVISION),
  APROBAR(EstadoInspeccion.EN_REVISION, EstadoInspeccion.PUBLICADA),
  SOLICITAR_CAMBIOS(EstadoInspeccion.EN_REVISION, EstadoInspeccion.CAMBIOS_SOLICITADOS),
  RECHAZAR(EstadoInspeccion.EN_REVISION, EstadoInspeccion.RECHAZADA),
  DEVOLVER_A_BORRADOR(EstadoInspeccion.CAMBIOS_SOLICITADOS, EstadoInspeccion.BORRADOR),
  /** Catedrático o Profesional externo verificado: se publica sin revisión previa (RN-REV-02). */
  PUBLICAR_SIN_REVISION(EstadoInspeccion.ENVIADA, EstadoInspeccion.PUBLICADA);

  private final EstadoInspeccion origen;
  private final EstadoInspeccion destino;

  EventoInspeccion(EstadoInspeccion origen, EstadoInspeccion destino) {
    this.origen = origen;
    this.destino = destino;
  }

  public EstadoInspeccion origen() {
    return origen;
  }

  public EstadoInspeccion destino() {
    return destino;
  }
}
