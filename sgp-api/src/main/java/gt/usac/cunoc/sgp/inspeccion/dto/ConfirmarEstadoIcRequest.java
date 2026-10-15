package gt.usac.cunoc.sgp.inspeccion.dto;

import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmarEstadoIcRequest(
    @NotNull EstadoCondicion estadoConfirmado,
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String hashEntrada,
    @Size(max = 10000) String justificacion) {}
