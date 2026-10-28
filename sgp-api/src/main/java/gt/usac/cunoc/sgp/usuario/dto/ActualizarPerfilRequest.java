package gt.usac.cunoc.sgp.usuario.dto;

import gt.usac.cunoc.sgp.usuario.validation.ReglasPerfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Nombre completo, nombre de usuario y correo electrónico de la cuenta que inició sesión. */
public record ActualizarPerfilRequest(
    @NotBlank(message = "El nombre completo es obligatorio")
        @Size(
            max = ReglasPerfil.NOMBRE_COMPLETO_MAXIMO,
            message = "El nombre completo no debe superar 200 caracteres")
        String nombreCompleto,
    @NotBlank(message = "El nombre de usuario es obligatorio")
        @Pattern(regexp = ReglasPerfil.PATRON_USUARIO, message = ReglasPerfil.MENSAJE_USUARIO)
        String nombreUsuario,
    @Email(message = "El correo electrónico no es válido")
        @Size(max = 320, message = "El correo no puede tener más de 320 caracteres")
        String email) {

  public ActualizarPerfilRequest(String nombreCompleto, String nombreUsuario) {
    this(nombreCompleto, nombreUsuario, null);
  }
}
