package gt.usac.cunoc.sgp.usuario.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorRequest(
    @NotBlank(message = "La contraseña actual es obligatoria") String currentPassword) {}
