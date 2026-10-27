package gt.usac.cunoc.sgp.mantenimiento.dto;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoReglaRequest(
    @NotNull(message = "El estado activa es obligatorio") Boolean activa) {}
