package gt.usac.cunoc.sgp.usuario.dto;

import gt.usac.cunoc.sgp.usuario.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyRecoveryRequest(
    @NotBlank(message = "El correo electronico es obligatorio")
        @Email(message = "El correo electronico no es valido")
        String email,
    @NotBlank(message = "El codigo OTP es obligatorio") String otp,
    @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 10, max = 72)
        @ValidPassword
        String newPassword) {}
