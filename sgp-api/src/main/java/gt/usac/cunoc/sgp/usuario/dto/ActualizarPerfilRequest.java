package gt.usac.cunoc.sgp.usuario.dto;

import gt.usac.cunoc.sgp.usuario.validation.ReglasPerfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Nombre completo y nombre de usuario de la cuenta que inició sesión. */
public record ActualizarPerfilRequest(
    @NotBlank(message = "El nombre completo es obligatorio")
        @Size(
            max = ReglasPerfil.NOMBRE_COMPLETO_MAXIMO,
            message = "El nombre completo no debe superar 200 caracteres")
        String nombreCompleto,
    @NotBlank(message = "El nombre de usuario es obligatorio")
        @Pattern(regexp = ReglasPerfil.PATRON_USUARIO, message = ReglasPerfil.MENSAJE_USUARIO)
        String nombreUsuario,
    String email) {

  public ActualizarPerfilRequest(String nombreCompleto, String nombreUsuario) {
    this(nombreCompleto, nombreUsuario, null);
  }
}
