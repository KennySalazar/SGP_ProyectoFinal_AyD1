package gt.usac.cunoc.sgp.formulario.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActualizarBorradorRequest(
    @Size(max = 50, message = "El codigo no debe superar 50 caracteres") String codigo,
    @NotNull(message = "El esquema es obligatorio") JsonNode esquema,
    JsonNode vinculosAlmacenamiento,
    JsonNode reglasEvaluacionIc) {}
