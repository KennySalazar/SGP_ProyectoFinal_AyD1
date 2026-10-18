package gt.usac.cunoc.sgp.inspeccion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record GuardarRespuestasRequest(
    @NotNull(message = "Los datos de la inspección son obligatorios") JsonNode datos,
    Long version) {
  public GuardarRespuestasRequest(JsonNode datos) {
    this(datos, null);
  }
}
