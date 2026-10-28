package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import gt.usac.cunoc.sgp.usuario.validation.ReglasPerfil;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Nombre completo y nombre de usuario de una cuenta. El nombre de usuario es lo único que el foro
 * muestra de una persona y lo que se escribe tras la arroba en una mención, así que se guarda en
 * minúsculas, es único y no puede hacerse pasar por un cargo.
 */
@Service
public class PerfilUsuarioService {

  /**
   * Nombres que sugieren un cargo o una cuenta del sistema. Como el foro muestra solo el nombre de
   * usuario, sin el rol, reservarlos evita que alguien se haga pasar por moderación.
   */
  private static final Set<String> RESERVADOS =
      Set.of(
          "admin",
          "administrador",
          "catedratico",
          "moderador",
          "sistema",
          "soporte",
          "sgp",
          "todos");

  private static final Pattern PATRON = Pattern.compile(ReglasPerfil.PATRON_USUARIO);
  private static final String INDICE_UNICO = "uq_usuario_nombre_usuario_lower";

  private final UserAccountRepository usuarios;

  public PerfilUsuarioService(UserAccountRepository usuarios) {
    this.usuarios = usuarios;
  }

  /**
   * Valida el perfil, comprueba que el nombre de usuario esté libre y lo aplica a la cuenta. No
   * guarda: eso lo hace {@link #guardar(UserAccount)}.
   */
  public void aplicar(UserAccount cuenta, String nombreCompleto, String nombreUsuario) {
    String nombre = nombreCompleto == null ? "" : nombreCompleto.strip().replaceAll("\\s+", " ");
    String usuario = nombreUsuario == null ? "" : nombreUsuario.strip().toLowerCase(Locale.ROOT);

    List<Map<String, String>> errores = new java.util.ArrayList<>();
    if (nombre.length() < ReglasPerfil.NOMBRE_COMPLETO_MINIMO
        || nombre.length() > ReglasPerfil.NOMBRE_COMPLETO_MAXIMO
        || nombre.codePoints().anyMatch(Character::isISOControl)) {
      errores.add(
          Map.of(
              "campo",
              "nombreCompleto",
              "mensaje",
              "El nombre completo debe tener entre 2 y 200 caracteres."));
    }
    if (!PATRON.matcher(usuario).matches()) {
      errores.add(Map.of("campo", "nombreUsuario", "mensaje", ReglasPerfil.MENSAJE_USUARIO + "."));
    } else if (RESERVADOS.contains(usuario)) {
      errores.add(
          Map.of(
              "campo",
              "nombreUsuario",
              "mensaje",
              "Ese nombre de usuario está reservado. Elige otro."));
    }
    if (!errores.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "perfil_invalido",
          "Perfil inválido",
          "El nombre completo o el nombre de usuario no son válidos.",
          errores);
    }

    usuarios
        .findByNombreUsuarioEnMinusculas(usuario)
        .filter(otra -> !otra.getId().equals(cuenta.getId()))
        .ifPresent(
            otra -> {
              throw nombreUsuarioEnUso();
            });
    cuenta.actualizarPerfil(nombre, usuario);
  }

  /**
   * Guarda la cuenta. Si otra petición tomó el mismo nombre de usuario entre la comprobación y el
   * guardado, el índice único lo detiene y se responde igual que si ya hubiera estado en uso.
   */
  public UserAccount guardar(UserAccount cuenta) {
    try {
      return usuarios.saveAndFlush(cuenta);
    } catch (DataIntegrityViolationException e) {
      Throwable causa = e.getMostSpecificCause();
      if (causa.getMessage() != null && causa.getMessage().contains(INDICE_UNICO)) {
        throw nombreUsuarioEnUso();
      }
      if (causa.getMessage() != null
          && (causa.getMessage().toLowerCase(Locale.ROOT).contains("email")
              || causa.getMessage().contains("usuario_email_key"))) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "email_already_registered",
            "Correo ya registrado",
            "El correo electrónico ya está registrado");
      }
      throw e;
    }
  }

  private ApiException nombreUsuarioEnUso() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "nombre_usuario_en_uso",
        "Nombre de usuario en uso",
        "Ese nombre de usuario ya está en uso. Elige otro.");
  }
}
