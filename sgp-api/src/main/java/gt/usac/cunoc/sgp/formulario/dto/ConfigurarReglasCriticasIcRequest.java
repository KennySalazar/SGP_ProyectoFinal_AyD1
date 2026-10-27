package gt.usac.cunoc.sgp.formulario.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record ConfigurarReglasCriticasIcRequest(
    @NotNull(message = "Las reglas criticas son obligatorias") JsonNode reglasAnulacion) {}
