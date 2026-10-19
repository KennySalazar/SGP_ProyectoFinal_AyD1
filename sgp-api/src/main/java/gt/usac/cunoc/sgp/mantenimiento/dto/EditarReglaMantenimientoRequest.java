package gt.usac.cunoc.sgp.mantenimiento.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoMantenimiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EditarReglaMantenimientoRequest(
    @NotBlank(message = "El elemento_ref es obligatorio")
        @Size(max = 300, message = "El elemento_ref no debe exceder 300 caracteres")
        String elementoRef,
    @NotNull(message = "La condición es obligatoria") JsonNode condicion,
    @NotNull(message = "El tipo de mantenimiento es obligatorio")
        TipoMantenimiento tipoMantenimiento,
    @NotBlank(message = "La intervención sugerida es obligatoria") String intervencionSugerida,
    @NotNull(message = "La regla de prioridad es obligatoria") JsonNode reglaPrioridad,
    Boolean activa) {}
