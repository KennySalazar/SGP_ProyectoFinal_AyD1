package gt.usac.cunoc.sgp.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.usuario.dto.CambiarRolRequest;
import gt.usac.cunoc.sgp.usuario.dto.DesactivarUsuarioRequest;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.mapper.UsuarioMapper;
import gt.usac.cunoc.sgp.usuario.model.EstadoUsuario;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.RoleRepository;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class UsuarioServiceTest {

  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000090");
  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000091");

  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final ProfesionalService profesionales = mock(ProfesionalService.class);
  private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
  private final UsuarioService service =
      new UsuarioService(
          users,
          mock(RoleRepository.class),
          profesionales,
          refreshTokens,
          mock(UsuarioMapper.class),
          Clock.systemUTC());

  @Test
  void unAdministradorNoPuedeDesactivarseASiMismo() {
    assertThatThrownBy(
            () ->
                service.desactivar(
                    ADMINISTRADOR_ID, new DesactivarUsuarioRequest(null), ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("autodesactivacion_no_permitida");
            });
    verifyNoInteractions(users, refreshTokens);
  }

  @Test
  void unAdministradorNoPuedeCambiarSuPropioRol() {
    assertThatThrownBy(
            () ->
                service.cambiarRol(
                    ADMINISTRADOR_ID,
                    new CambiarRolRequest(RoleName.CATEDRATICO, null),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo("cambio_rol_propio_no_permitido"));
    verifyNoInteractions(users, refreshTokens, profesionales);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void pasarAProfesionalExternoExigeColegiadoAntesDeTocarLaCuenta(String colegiado) {
    assertThatThrownBy(
            () ->
                service.cambiarRol(
                    USUARIO_ID,
                    new CambiarRolRequest(RoleName.PROFESIONAL_EXTERNO, colegiado),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("colegiado_requerido");
            });
    verifyNoInteractions(users, refreshTokens, profesionales);
  }

  @Test
  void elColegiadoNoAplicaAOtrosRoles() {
    assertThatThrownBy(
            () ->
                service.cambiarRol(
                    USUARIO_ID,
                    new CambiarRolRequest(RoleName.ESTUDIANTE, "12345"),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("colegiado_no_aplica"));
    verifyNoInteractions(users);
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,101"})
  void listarRechazaPaginacionFueraDeRango(int pagina, int tamanio) {
    assertThatThrownBy(() -> service.listar(null, null, pagina, tamanio))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("paginacion_invalida"));
    verifyNoInteractions(users);
  }

  @ParameterizedTest
  @CsvSource({
    "true,true,true,ACTIVO",
    "true,true,false,PENDIENTE",
    "true,false,true,PENDIENTE",
    "false,true,true,INACTIVO",
    "false,false,false,INACTIVO"
  })
  void elEstadoSeDerivaDeLaCuenta(
      boolean activo, boolean activado, boolean verificado, EstadoUsuario esperado) {
    UserAccount usuario = mock(UserAccount.class);
    when(usuario.isActive()).thenReturn(activo);
    when(usuario.isActivated()).thenReturn(activado);
    when(usuario.isVerified()).thenReturn(verificado);

    assertThat(UsuarioService.estadoDe(usuario)).isEqualTo(esperado);
  }
}
