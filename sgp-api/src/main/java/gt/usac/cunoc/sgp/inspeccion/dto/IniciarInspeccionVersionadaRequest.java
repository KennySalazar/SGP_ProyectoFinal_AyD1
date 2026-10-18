package gt.usac.cunoc.sgp.inspeccion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record IniciarInspeccionVersionadaRequest(
    @NotNull(message = "El puente es obligatorio") UUID puenteId,
    @NotNull(message = "La versión del formulario es obligatoria") UUID versionFormularioId,
    LocalDate fechaInspeccion,
    String dispositivoId,
    String nombreAutorCapturado,
    JsonNode datos,
    UUID asignacionPuenteId) {

  public IniciarInspeccionVersionadaRequest(
      UUID puenteId,
      UUID versionFormularioId,
      LocalDate fechaInspeccion,
      String dispositivoId,
      String nombreAutorCapturado,
      JsonNode datos) {
    this(
        puenteId,
        versionFormularioId,
        fechaInspeccion,
        dispositivoId,
        nombreAutorCapturado,
        datos,
        null);
  }
}
