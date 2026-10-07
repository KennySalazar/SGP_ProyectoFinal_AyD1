package gt.usac.cunoc.sgp.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ActivarEstudianteRequest(
    @NotNull UUID estudianteId, @NotNull UUID cursoId, @NotBlank @Size(max = 30) String seccion) {}
