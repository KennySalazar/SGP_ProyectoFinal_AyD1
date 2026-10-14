package gt.usac.cunoc.sgp.formulario.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CrearVersionRequest(
    @NotBlank(message = "El codigo es obligatorio")
        @Size(max = 50, message = "El codigo no debe superar 50 caracteres")
        String codigo,
    UUID versionBaseId,
    Boolean cargarPlantillaInicial,
    JsonNode esquema,
    JsonNode vinculosAlmacenamiento,
    JsonNode reglasEvaluacionIc) {}
