package gt.usac.cunoc.sgp.inspeccion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record ValidarRespuestasRequest(
    @NotNull(message = "Los datos son obligatorios") JsonNode datos,
    Boolean esBorrador) {}
