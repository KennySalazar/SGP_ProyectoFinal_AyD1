package gt.usac.cunoc.sgp.usuario.dto;

import gt.usac.cunoc.sgp.usuario.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "La contraseña actual es obligatoria") String currentPassword,
    @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 10, max = 72)
        @ValidPassword
        String newPassword) {}
