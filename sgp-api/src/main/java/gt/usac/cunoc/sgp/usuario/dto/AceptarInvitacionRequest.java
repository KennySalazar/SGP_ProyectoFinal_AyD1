package gt.usac.cunoc.sgp.usuario.dto;

import gt.usac.cunoc.sgp.usuario.validation.ReglasPerfil;
import gt.usac.cunoc.sgp.usuario.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AceptarInvitacionRequest(
    @NotBlank(message = "El token de invitacion es obligatorio")
        @Size(max = 200, message = "El token de invitacion no es valido")
        String token,
    @NotBlank(message = "La contraseña es obligatoria") @Size(min = 10, max = 72) @ValidPassword
        String password,
    @NotBlank(message = "El nombre completo es obligatorio")
        @Size(
            max = ReglasPerfil.NOMBRE_COMPLETO_MAXIMO,
            message = "El nombre completo no debe superar 200 caracteres")
        String nombreCompleto,
    @NotBlank(message = "El nombre de usuario es obligatorio")
        @Pattern(regexp = ReglasPerfil.PATRON_USUARIO, message = ReglasPerfil.MENSAJE_USUARIO)
        String nombreUsuario) {}
