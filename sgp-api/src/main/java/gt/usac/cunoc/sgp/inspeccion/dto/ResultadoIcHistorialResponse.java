package gt.usac.cunoc.sgp.inspeccion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record ResultadoIcHistorialResponse(
    UUID id,
    UUID inspeccionId,
    String tipo,
    UUID versionConfiguracionId,
    String versionConfiguracionCodigo,
    BigDecimal indiceCalculado,
    EstadoCondicion estadoPorRango,
    EstadoCondicion estadoCalculado,
    EstadoCondicion estadoConfirmado,
    String justificacionAjuste,
    JsonNode condicionesAnulacion,
    JsonNode desglose,
    String hashEntrada,
    String versionAlgoritmo,
    UUID solicitadoPorId,
    String solicitadoPorNombre,
    Instant calculadoEn) {
  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  public String rotulo() {
    String fecha =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Guatemala"))
            .format(calculadoEn);
    return ("PUBLICACION".equals(tipo) ? "IC original" : "Recálculo del IC") + " · " + fecha;
  }
}
