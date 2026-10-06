package gt.usac.cunoc.sgp.puente.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Solicitud para dar de baja lógica a un puente")
public record DarBajaPuenteRequest(
    @NotBlank(message = "El motivo de baja es obligatorio.")
        @Size(max = 1000, message = "El motivo no puede exceder 1000 caracteres.")
        @Schema(description = "Motivo documentado de la baja lógica", example = "demolido")
        String motivo) {}
