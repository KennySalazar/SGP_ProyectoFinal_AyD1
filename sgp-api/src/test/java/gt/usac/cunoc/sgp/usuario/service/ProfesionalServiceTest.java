package gt.usac.cunoc.sgp.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.entity.UsuarioProfesional;
import gt.usac.cunoc.sgp.usuario.mapper.InvitacionMapper;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import gt.usac.cunoc.sgp.usuario.repository.UsuarioProfesionalRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class ProfesionalServiceTest {

  private static final Instant AHORA = Instant.parse("2026-10-07T18:00:00Z");
  private static final UUID PROFESIONAL_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000060");
  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000061");

  private final UsuarioProfesionalRepository profesionales =
      mock(UsuarioProfesionalRepository.class);
  private final ProfesionalService service =
      new ProfesionalService(
          profesionales,
          mock(UserAccountRepository.class),
          mock(InvitacionMapper.class),
          Clock.fixed(AHORA, ZoneOffset.UTC));

  @Test
  void normalizaElColegiadoSinEspaciosNiMinusculas() {
    assertThat(ProfesionalService.normalizarColegiado("  ab-123 ")).isEqualTo("AB-123");
    assertThat(ProfesionalService.normalizarColegiado(null)).isNull();
  }

  @Test
  void profesionalSinColegiadoVerificadoNoEstaHabilitadoParaInspeccionar() {
    when(profesionales.findById(PROFESIONAL_ID)).thenReturn(Optional.of(profesional()));

    assertThat(service.colegiadoHabilitado(autenticacion(RoleName.PROFESIONAL_EXTERNO))).isFalse();
  }

  @Test
  void profesionalConColegiadoVerificadoQuedaHabilitado() {
    UsuarioProfesional profesional = profesional();
    profesional.verificarColegiado(ADMINISTRADOR_ID, AHORA);
    when(profesionales.findById(PROFESIONAL_ID)).thenReturn(Optional.of(profesional));

    assertThat(service.colegiadoHabilitado(autenticacion(RoleName.PROFESIONAL_EXTERNO))).isTrue();
  }

  @Test
  void profesionalSinRegistroDeColegiadoNoQuedaHabilitado() {
    when(profesionales.findById(PROFESIONAL_ID)).thenReturn(Optional.empty());

    assertThat(service.colegiadoHabilitado(autenticacion(RoleName.PROFESIONAL_EXTERNO))).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "PROFESIONAL_EXTERNO", mode = EnumSource.Mode.EXCLUDE)
  void laVerificacionDeColegiadoNoRestringeAOtrosRoles(RoleName rol) {
    assertThat(service.colegiadoHabilitado(autenticacion(rol))).isTrue();
    verifyNoInteractions(profesionales);
  }

  @Test
  void sinSesionValidaNoEstaHabilitado() {
    assertThat(service.colegiadoHabilitado(null)).isFalse();
    assertThat(
            service.colegiadoHabilitado(
                new UsernamePasswordAuthenticationToken("x", null, List.of())))
        .isFalse();
  }

  @Test
  void verificarUnProfesionalInexistenteResponde404() {
    when(profesionales.findByUsuarioIdForUpdate(PROFESIONAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verificarColegiado(PROFESIONAL_ID, ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(e.getCode()).isEqualTo("profesional_no_encontrado");
            });
  }

  @Test
  void unColegiadoYaVerificadoNoSeVerificaDosVeces() {
    UsuarioProfesional profesional = profesional();
    profesional.verificarColegiado(ADMINISTRADOR_ID, AHORA);
    when(profesionales.findByUsuarioIdForUpdate(PROFESIONAL_ID))
        .thenReturn(Optional.of(profesional));

    assertThatThrownBy(() -> service.verificarColegiado(PROFESIONAL_ID, ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("colegiado_ya_verificado");
            });
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,101"})
  void listarRechazaPaginacionFueraDeRango(int pagina, int tamanio) {
    assertThatThrownBy(() -> service.listar(null, pagina, tamanio))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("paginacion_invalida"));
    verifyNoInteractions(profesionales);
  }

  private UsuarioProfesional profesional() {
    return new UsuarioProfesional(mock(UserAccount.class), "12345", AHORA);
  }

  private UsernamePasswordAuthenticationToken autenticacion(RoleName rol) {
    var authentication = new UsernamePasswordAuthenticationToken("profesional", null, List.of());
    authentication.setDetails(new JwtData("profesional@ejemplo.com", PROFESIONAL_ID, rol, 0));
    return authentication;
  }
}
