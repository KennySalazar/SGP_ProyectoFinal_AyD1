package gt.usac.cunoc.sgp.puente.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevocarAsignacionPuenteRequest(@NotBlank @Size(max = 1000) String motivo) {}
