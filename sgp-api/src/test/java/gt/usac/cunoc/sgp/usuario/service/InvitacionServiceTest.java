package gt.usac.cunoc.sgp.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.config.InvitacionProperties;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.usuario.dto.AceptarInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.CrearInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.entity.InvitacionUsuario;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.mapper.InvitacionMapper;
import gt.usac.cunoc.sgp.usuario.model.EstadoInvitacion;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.InvitacionUsuarioRepository;
import gt.usac.cunoc.sgp.usuario.repository.RoleRepository;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class InvitacionServiceTest {

  private static final Instant AHORA = Instant.parse("2026-10-06T18:00:00Z");
  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000030");

  private final InvitacionUsuarioRepository invitaciones = mock(InvitacionUsuarioRepository.class);
  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final CuentaInvitadaService cuentas = mock(CuentaInvitadaService.class);
  private final InvitacionEmailService correos = mock(InvitacionEmailService.class);

  private final InvitacionService service =
      new InvitacionService(
          invitaciones,
          users,
          roles,
          cuentas,
          correos,
          mock(InvitacionMapper.class),
          new InvitacionProperties(),
          Clock.fixed(AHORA, ZoneOffset.UTC));

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "CATEDRATICO", mode = EnumSource.Mode.EXCLUDE)
  void rechazaRolesQueAunNoSonInvitables(RoleName rol) {
    assertThatThrownBy(
            () ->
                service.invitar(
                    new CrearInvitacionRequest("persona@usac.edu.gt", rol), ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("rol_no_invitable");
            });

    verifyNoInteractions(users, cuentas, correos, invitaciones);
  }

  @Test
  void rechazaCorreoDeCuentaExistenteCon409() {
    UserAccount existente = mock(UserAccount.class);
    when(existente.getId()).thenReturn(UUID.randomUUID());
    when(users.findByEmail("catedratico@usac.edu.gt")).thenReturn(Optional.of(existente));

    assertThatThrownBy(
            () ->
                service.invitar(
                    new CrearInvitacionRequest("  Catedratico@USAC.edu.gt ", RoleName.CATEDRATICO),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("email_already_registered");
            });

    verifyNoInteractions(cuentas, correos);
  }

  @Test
  void distingueCorreoConInvitacionSinAceptar() {
    UUID usuarioId = UUID.randomUUID();
    UserAccount existente = mock(UserAccount.class);
    when(existente.getId()).thenReturn(usuarioId);
    when(users.findByEmail("catedratico@usac.edu.gt")).thenReturn(Optional.of(existente));
    when(invitaciones.existsByUsuarioCreadoIdAndAceptadoEnIsNullAndCanceladoEnIsNull(usuarioId))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                service.invitar(
                    new CrearInvitacionRequest("catedratico@usac.edu.gt", RoleName.CATEDRATICO),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("invitacion_pendiente");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"corta1", "solamenteletras", "1234567890", "   "})
  void aceptarRechazaContrasenaFueraDePoliticaSinConsultarLaInvitacion(String password) {
    assertThatThrownBy(() -> service.aceptar(new AceptarInvitacionRequest("token", password)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("contrasena_invalida"));

    verifyNoInteractions(invitaciones, cuentas);
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,101"})
  void listarRechazaPaginacionFueraDeRango(int pagina, int tamanio) {
    assertThatThrownBy(() -> service.listar(null, pagina, tamanio))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("paginacion_invalida"));

    verifyNoInteractions(invitaciones);
  }

  @Test
  void estadoDeLaInvitacionSeDerivaDeSusFechas() {
    InvitacionUsuario invitacion =
        new InvitacionUsuario(
            UUID.randomUUID(),
            "catedratico@usac.edu.gt",
            mock(Role.class),
            "hash",
            ADMINISTRADOR_ID,
            UUID.randomUUID(),
            AHORA,
            AHORA.plusSeconds(3600));

    assertThat(invitacion.estado(AHORA)).isEqualTo(EstadoInvitacion.PENDIENTE);
    assertThat(invitacion.estado(AHORA.plusSeconds(3600))).isEqualTo(EstadoInvitacion.VENCIDA);

    invitacion.aceptar(AHORA.plusSeconds(60));
    assertThat(invitacion.estado(AHORA.plusSeconds(7200))).isEqualTo(EstadoInvitacion.ACEPTADA);
    assertThatThrownBy(() -> invitacion.cancelar(AHORA)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void unaInvitacionVencidaNoPuedeAceptarse() {
    InvitacionUsuario invitacion =
        new InvitacionUsuario(
            UUID.randomUUID(),
            "catedratico@usac.edu.gt",
            mock(Role.class),
            "hash",
            ADMINISTRADOR_ID,
            UUID.randomUUID(),
            AHORA,
            AHORA.plusSeconds(3600));

    assertThatThrownBy(() -> invitacion.aceptar(AHORA.plusSeconds(3600)))
        .isInstanceOf(IllegalStateException.class);

    invitacion.cancelar(AHORA);
    assertThat(invitacion.estado(AHORA)).isEqualTo(EstadoInvitacion.CANCELADA);
  }
}
