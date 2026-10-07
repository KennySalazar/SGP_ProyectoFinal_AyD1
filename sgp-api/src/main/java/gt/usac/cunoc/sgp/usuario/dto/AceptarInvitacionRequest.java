package gt.usac.cunoc.sgp.usuario.dto;

import gt.usac.cunoc.sgp.usuario.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AceptarInvitacionRequest(
    @NotBlank(message = "El token de invitacion es obligatorio")
        @Size(max = 200, message = "El token de invitacion no es valido")
        String token,
    @NotBlank(message = "La contraseña es obligatoria") @Size(min = 10, max = 72) @ValidPassword
        String password) {}
