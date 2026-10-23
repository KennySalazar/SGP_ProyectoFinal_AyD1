package gt.usac.cunoc.sgp.formulario.dto;

import java.time.Instant;
import java.util.UUID;

public record EstadoCalibracionResponse(
    UUID versionFormularioId,
    String estado,
    String rotuloCalibracion,
    UUID aprobadoPorId,
    String aprobadoPorNombre,
    Instant aprobadoEn) {
  public static EstadoCalibracionResponse de(
      UUID versionId, AprobacionCalibracionResponse aprobacion) {
    return aprobacion == null
        ? new EstadoCalibracionResponse(
            versionId,
            "SIN_APROBAR",
            AprobacionCalibracionResponse.ROTULO_SIN_APROBAR,
            null,
            null,
            null)
        : new EstadoCalibracionResponse(
            versionId,
            "APROBADA",
            aprobacion.rotulo(),
            aprobacion.aprobadoPorId(),
            aprobacion.aprobadoPorNombre(),
            aprobacion.aprobadoEn());
  }
}
