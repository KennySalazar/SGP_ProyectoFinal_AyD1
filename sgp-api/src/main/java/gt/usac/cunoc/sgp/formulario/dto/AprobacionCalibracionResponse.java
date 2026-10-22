package gt.usac.cunoc.sgp.formulario.dto;

import gt.usac.cunoc.sgp.formulario.entity.AprobacionCalibracion;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record AprobacionCalibracionResponse(
    UUID id,
    UUID versionFormularioId,
    UUID aprobadoPorId,
    String aprobadoPorNombre,
    Instant aprobadoEn,
    String observaciones) {
  public static AprobacionCalibracionResponse de(AprobacionCalibracion aprobacion) {
    if (aprobacion == null) return null;
    return new AprobacionCalibracionResponse(
        aprobacion.getId(),
        aprobacion.getVersionFormulario().getId(),
        aprobacion.getAprobadoPorId(),
        aprobacion.getAprobadoPorNombre(),
        aprobacion.getAprobadoEn(),
        aprobacion.getObservaciones());
  }

  @com.fasterxml.jackson.annotation.JsonProperty("rotuloCalibracion")
  public String rotulo() {
    return "Aprobada por "
        + aprobadoPorNombre
        + " el "
        + DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Guatemala"))
            .format(aprobadoEn);
  }
}
