package gt.usac.cunoc.sgp.usuario.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** El perfil es obligatorio y está acotado en el alta, en la invitación y al actualizarlo. */
class PerfilRequestsValidationTest {

  private static final Validator VALIDADOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @ParameterizedTest
  @ValueSource(strings = {"ana", "Ana.Reyes", "ana_reyes-1", "a1b", "ABC", "u.s.e.r"})
  void nombresDeUsuarioValidos(String usuario) {
    assertThat(campos(new ActualizarPerfilRequest("Ana Reyes", usuario))).isEmpty();
  }

  @Test
  void requestConEmailOpcionalEsValido() {
    assertThat(campos(new ActualizarPerfilRequest("Ana Reyes", "ana.reyes", "ana@ejemplo.com")))
        .isEmpty();
    assertThat(campos(new ActualizarPerfilRequest("Ana Reyes", "ana.reyes", null))).isEmpty();
    assertThat(campos(new ActualizarPerfilRequest("Ana Reyes", "ana.reyes"))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", " ", "ab", "ana reyes", ".ana", "ana.", "_ana", "ana-", "a@b.c", "áná"})
  void nombresDeUsuarioInvalidos(String usuario) {
    assertThat(campos(new ActualizarPerfilRequest("Ana Reyes", usuario)))
        .containsExactly("nombreUsuario");
  }

  @Test
  void elNombreDeUsuarioNoPuedeTenerMasDe30Caracteres() {
    assertThat(campos(new ActualizarPerfilRequest("Ana", "a".repeat(30)))).isEmpty();
    assertThat(campos(new ActualizarPerfilRequest("Ana", "a".repeat(31))))
        .containsExactly("nombreUsuario");
  }

  @Test
  void elNombreCompletoEsObligatorioYTieneUnTope() {
    assertThat(campos(new ActualizarPerfilRequest("   ", "ana"))).containsExactly("nombreCompleto");
    assertThat(campos(new ActualizarPerfilRequest(null, "ana"))).containsExactly("nombreCompleto");
    assertThat(campos(new ActualizarPerfilRequest("x".repeat(200), "ana"))).isEmpty();
    assertThat(campos(new ActualizarPerfilRequest("x".repeat(201), "ana")))
        .containsExactly("nombreCompleto");
  }

  @Test
  void elAltaYLaAceptacionDeInvitacionTambienExigenElPerfil() {
    assertThat(campos(new RegisterRequest("a@b.com", "Clave12345", null, null)))
        .containsExactlyInAnyOrder("nombreCompleto", "nombreUsuario");
    assertThat(campos(new AceptarInvitacionRequest("token", "Clave12345", "", "")))
        .containsExactlyInAnyOrder("nombreCompleto", "nombreUsuario");
    assertThat(campos(new RegisterRequest("a@b.com", "Clave12345", "Ana Reyes", "ana.reyes")))
        .isEmpty();
    assertThat(
            campos(new AceptarInvitacionRequest("token", "Clave12345", "Ana Reyes", "ana.reyes")))
        .isEmpty();
  }

  private static java.util.Set<String> campos(Object request) {
    return VALIDADOR.validate(request).stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}
