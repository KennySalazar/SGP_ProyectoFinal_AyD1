package gt.usac.cunoc.sgp.revision.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.inspeccion.dto.IndiceCondicionPreliminarResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contenido histórico del envío y nombres de referencia para presentar la revisión. */
public record RevisionDetalleResponse(
    UUID revisionId,
    UUID inspeccionId,
    String estadoRevision,
    UUID revisorId,
    int numeroCiclo,
    Instant enviadaEn,
    UUID autorId,
    UUID puenteId,
    LocalDate fechaInspeccion,
    UUID versionFormularioId,
    String versionFormularioCodigo,
    JsonNode esquemaFormulario,
    JsonNode datos,
    JsonNode identificacionCapturada,
    IndiceCondicionPreliminarResponse icPreliminar,
    String fotografiasEndpoint,
    boolean fotografiasCongeladas,
    String observacionesEndpoint,
    String autorNombre,
    String puenteNombre,
    String puenteCodigo) {
  @com.fasterxml.jackson.annotation.JsonProperty(
      value = "calibracionEndpoint",
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  public String calibracionEndpoint() {
    return "/api/v1/formularios/" + versionFormularioId + "/estado-calibracion";
  }
}
