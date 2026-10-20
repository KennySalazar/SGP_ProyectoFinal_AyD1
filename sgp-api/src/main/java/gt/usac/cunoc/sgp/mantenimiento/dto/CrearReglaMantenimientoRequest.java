package gt.usac.cunoc.sgp.mantenimiento.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CrearReglaMantenimientoRequest(
    @NotBlank(message = "El código de la regla es obligatorio")
        @Size(max = 80, message = "El código no debe exceder 80 caracteres")
        @Pattern(
            regexp = "^[A-Za-z0-9_-]+$",
            message =
                "El código solo debe contener caracteres alfanuméricos, guiones y guiones bajos")
        String codigo,
    @NotNull(message = "El identificador de la versión de formulario es obligatorio")
        UUID versionFormularioId,
    @NotBlank(message = "El elemento_ref es obligatorio")
        @Size(max = 300, message = "El elemento_ref no debe exceder 300 caracteres")
        String elementoRef,
    @NotNull(message = "La condición es obligatoria") JsonNode condicion,
    @NotNull(message = "El tipo de mantenimiento es obligatorio") TipoOrden tipoMantenimiento,
    @NotBlank(message = "La intervención sugerida es obligatoria") String intervencionSugerida,
    @NotNull(message = "La regla de prioridad es obligatoria") JsonNode reglaPrioridad,
    Boolean activa) {}
