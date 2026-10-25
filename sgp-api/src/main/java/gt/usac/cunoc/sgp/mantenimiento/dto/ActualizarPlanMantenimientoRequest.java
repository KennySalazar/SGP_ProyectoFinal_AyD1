package gt.usac.cunoc.sgp.mantenimiento.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ActualizarPlanMantenimientoRequest(
    Integer periodicidadMeses,
    @NotNull @Size(max = 100) List<@NotBlank @Size(max = 1000) String> actividades,
    @NotNull @Min(0) Long version) {}
