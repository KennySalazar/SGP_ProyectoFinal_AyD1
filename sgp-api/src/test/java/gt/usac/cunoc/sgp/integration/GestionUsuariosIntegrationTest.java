package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.aspect.AuditoriaAspect;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.repository.AuditoriaRepository;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.usuario.dto.CambiarRolRequest;
import gt.usac.cunoc.sgp.usuario.dto.DesactivarUsuarioRequest;
import gt.usac.cunoc.sgp.usuario.dto.LoginRequest;
import gt.usac.cunoc.sgp.usuario.dto.UsuarioResponse;
import gt.usac.cunoc.sgp.usuario.mapper.InvitacionMapperImpl;
import gt.usac.cunoc.sgp.usuario.mapper.UserMapperImpl;
import gt.usac.cunoc.sgp.usuario.mapper.UsuarioMapperImpl;
import gt.usac.cunoc.sgp.usuario.model.EstadoUsuario;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.service.AuthService;
import gt.usac.cunoc.sgp.usuario.service.OtpService;
import gt.usac.cunoc.sgp.usuario.service.ProfesionalService;
import gt.usac.cunoc.sgp.usuario.service.RefreshTokenService;
import gt.usac.cunoc.sgp.usuario.service.UsuarioAuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.usuario.service.UsuarioProfesionalAuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.usuario.service.UsuarioService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  UsuarioService.class,
  ProfesionalService.class,
  RefreshTokenService.class,
  AuthService.class,
  UsuarioMapperImpl.class,
  InvitacionMapperImpl.class,
  UserMapperImpl.class,
  AuditoriaAspect.class,
  AuditoriaWriter.class,
  AuditoriaSnapshotService.class,
  UsuarioAuditoriaSnapshotProvider.class,
  UsuarioProfesionalAuditoriaSnapshotProvider.class,
  GestionUsuariosIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class GestionUsuariosIntegrationTest {

  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-0000000000a0");
  private static final UUID CATEDRATICO_ID =
      UUID.fromString("019a0000-0000-7000-8000-0000000000a1");
  private static final UUID ESTUDIANTE_PENDIENTE_ID =
      UUID.fromString("019a0000-0000-7000-8000-0000000000a2");
  private static final UUID PROFESIONAL_ID =
      UUID.fromString("019a0000-0000-7000-8000-0000000000a3");

  private static final Instant AHORA = Instant.parse("2026-10-07T18:00:00Z");
  private static final String CORREO_CATEDRATICO = "catedratico.hu008@ejemplo.com";
  private static final String CONTRASENA = "Puentes2026";

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_hu008_test")
          .withUsername("sgp_test")
          .withPassword("sgp_test");

  @DynamicPropertySource
  static void configurarBaseDeDatos(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private UsuarioService usuarioService;
  @Autowired private ProfesionalService profesionalService;
  @Autowired private AuthService authService;
  @Autowired private AuditoriaRepository auditoriaRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbc;

  @MockBean private OtpService otpService;
  @MockBean private JwtService jwtService;

  @BeforeEach
  void prepararDatos() {
    jdbc.update("DELETE FROM auditoria");
    jdbc.update("DELETE FROM token_refresco");
    jdbc.update("DELETE FROM usuario_profesional");
    jdbc.update("DELETE FROM usuario WHERE email LIKE '%hu008%'");

    insertarUsuario(ADMINISTRADOR_ID, "admin.hu008@ejemplo.com", "ADMINISTRADOR", true, true);
    insertarUsuario(CATEDRATICO_ID, CORREO_CATEDRATICO, "CATEDRATICO", true, true);
    insertarUsuario(
        ESTUDIANTE_PENDIENTE_ID, "estudiante.hu008@ejemplo.com", "ESTUDIANTE", true, false);
    insertarUsuario(
        PROFESIONAL_ID, "ingeniero.hu008@ejemplo.com", "PROFESIONAL_EXTERNO", true, true);
    jdbc.update(
        "INSERT INTO usuario_profesional (usuario_id, numero_colegiado) VALUES (?, '777')",
        PROFESIONAL_ID);

    autenticar(ADMINISTRADOR_ID, RoleName.ADMINISTRADOR);
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Escenario: listar usuarios con filtros")
  void listaPaginadaFiltrablePorRolYEstado() {
    var todos = correos(usuarioService.listar(null, null, 0, 100).getContent());
    assertThat(todos)
        .contains(
            "admin.hu008@ejemplo.com",
            CORREO_CATEDRATICO,
            "estudiante.hu008@ejemplo.com",
            "ingeniero.hu008@ejemplo.com");

    assertThat(correos(usuarioService.listar(RoleName.CATEDRATICO, null, 0, 100).getContent()))
        .contains(CORREO_CATEDRATICO)
        .doesNotContain("admin.hu008@ejemplo.com", "estudiante.hu008@ejemplo.com");
    assertThat(correos(usuarioService.listar(null, EstadoUsuario.PENDIENTE, 0, 100).getContent()))
        .contains("estudiante.hu008@ejemplo.com")
        .doesNotContain(CORREO_CATEDRATICO);

    var pagina = usuarioService.listar(null, null, 0, 2);
    assertThat(pagina.getContent()).hasSize(2);
    assertThat(pagina.getTotalElements()).isGreaterThanOrEqualTo(4);

    UsuarioResponse profesional =
        usuarioService.listar(RoleName.PROFESIONAL_EXTERNO, null, 0, 100).getContent().stream()
            .filter(usuario -> usuario.id().equals(PROFESIONAL_ID))
            .findFirst()
            .orElseThrow();
    assertThat(profesional.numeroColegiado()).isEqualTo("777");
    assertThat(profesional.colegiadoVerificado()).isFalse();
  }

  @Test
  @DisplayName("Escenario: desactivar un usuario")
  void desactivarEsUnaBajaLogicaQueCierraSusSesiones() {
    jdbc.update(
        """
        INSERT INTO token_refresco (id, usuario_id, token_hash, expira_en)
        VALUES (gen_random_uuid(), ?, 'hash-refresco-hu008', now() + interval '7 days')
        """,
        CATEDRATICO_ID);

    usuarioService.desactivar(
        CATEDRATICO_ID, new DesactivarUsuarioRequest("  Dejó el curso  "), ADMINISTRADOR_ID);
    UsuarioResponse response = usuarioService.obtener(CATEDRATICO_ID);

    // La cuenta queda inactiva, nunca eliminada: conserva su correo y su rol.
    assertThat(response.estado()).isEqualTo(EstadoUsuario.INACTIVO);
    assertThat(response.rol()).isEqualTo(RoleName.CATEDRATICO);
    assertThat(response.motivoDesactivacion()).isEqualTo("Dejó el curso");
    assertThat(response.desactivadoEn().toInstant()).isEqualTo(AHORA);
    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT email, activo, desactivado_por_id, token_version FROM usuario WHERE id = ?",
            CATEDRATICO_ID);
    assertThat(fila.get("email")).isEqualTo(CORREO_CATEDRATICO);
    assertThat(fila.get("activo")).isEqualTo(false);
    assertThat(fila.get("desactivado_por_id")).isEqualTo(ADMINISTRADOR_ID);
    assertThat(fila.get("token_version")).isEqualTo(1);

    // Sus sesiones se cierran y ya no puede iniciar sesión.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM token_refresco WHERE usuario_id = ? AND revocado_en IS NULL",
                Integer.class,
                CATEDRATICO_ID))
        .isZero();
    assertThatThrownBy(() -> authService.login(new LoginRequest(CORREO_CATEDRATICO, CONTRASENA)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("account_disabled"));

    // La baja queda en la bitácora con el valor anterior y el posterior en el mismo formato.
    var registro = auditoriaRepository.findAll().get(0);
    assertThat(registro.getAccion()).isEqualTo(AccionAuditoria.CAMBIAR_ESTADO);
    assertThat(registro.getEntidad()).isEqualTo("usuario");
    assertThat(registro.getEntidadId()).isEqualTo(CATEDRATICO_ID);
    assertThat(registro.getUsuarioId()).isEqualTo(ADMINISTRADOR_ID);
    assertThat(registro.getValoresAnteriores().get("activo").asBoolean()).isTrue();
    assertThat(registro.getValoresPosteriores().get("activo").asBoolean()).isFalse();
    assertThat(registro.getValoresPosteriores().get("motivoDesactivacion").asText())
        .isEqualTo("Dejó el curso");
    assertThat(registro.getValoresPosteriores().size())
        .isEqualTo(registro.getValoresAnteriores().size());

    // Desactivar de nuevo se rechaza.
    assertThatThrownBy(() -> usuarioService.desactivar(CATEDRATICO_ID, null, ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("usuario_ya_inactivo"));
  }

  @Test
  @DisplayName("Escenario: reactivar un usuario")
  void reactivarRecuperaElAccesoConElMismoRol() {
    usuarioService.desactivar(CATEDRATICO_ID, null, ADMINISTRADOR_ID);

    usuarioService.reactivar(CATEDRATICO_ID);
    UsuarioResponse response = usuarioService.obtener(CATEDRATICO_ID);

    assertThat(response.estado()).isEqualTo(EstadoUsuario.ACTIVO);
    assertThat(response.rol()).isEqualTo(RoleName.CATEDRATICO);
    assertThat(response.desactivadoEn()).isNull();
    assertThat(response.motivoDesactivacion()).isNull();
    assertThat(authService.login(new LoginRequest(CORREO_CATEDRATICO, CONTRASENA))).isNotNull();

    assertThatThrownBy(() -> usuarioService.reactivar(CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("usuario_ya_activo"));
  }

  @Test
  @DisplayName("Escenario: cambiar el rol de un usuario a Profesional Externo")
  void cambiarAProfesionalExternoExigeElColegiadoYLoDejaSinVerificar() {
    assertThatThrownBy(
            () ->
                usuarioService.cambiarRol(
                    CATEDRATICO_ID,
                    new CambiarRolRequest(RoleName.PROFESIONAL_EXTERNO, null),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("colegiado_requerido"));
    assertThat(rolDe(CATEDRATICO_ID)).isEqualTo("CATEDRATICO");

    usuarioService.cambiarRol(
        CATEDRATICO_ID,
        new CambiarRolRequest(RoleName.PROFESIONAL_EXTERNO, " cig-123 "),
        ADMINISTRADOR_ID);
    UsuarioResponse response = usuarioService.obtener(CATEDRATICO_ID);

    assertThat(response.rol()).isEqualTo(RoleName.PROFESIONAL_EXTERNO);
    assertThat(response.numeroColegiado()).isEqualTo("CIG-123");
    assertThat(response.colegiadoVerificado()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT token_version FROM usuario WHERE id = ?", Integer.class, CATEDRATICO_ID))
        .isEqualTo(1);
    assertThat(
            profesionalService.colegiadoHabilitado(
                autenticacion(CATEDRATICO_ID, RoleName.PROFESIONAL_EXTERNO)))
        .isFalse();

    // Se audita el cambio de rol y el registro del colegiado.
    assertThat(auditoriaRepository.findAll())
        .extracting(registro -> registro.getEntidad() + ":" + registro.getAccion())
        .containsExactlyInAnyOrder("usuario:MODIFICAR", "usuario_profesional:CREAR");
    var cambio =
        auditoriaRepository.findAll().stream()
            .filter(registro -> registro.getEntidad().equals("usuario"))
            .findFirst()
            .orElseThrow();
    assertThat(cambio.getValoresAnteriores().get("rol").asText()).isEqualTo("CATEDRATICO");
    assertThat(cambio.getValoresPosteriores().get("rol").asText()).isEqualTo("PROFESIONAL_EXTERNO");
  }

  @Test
  void unColegiadoAsociadoAOtraCuentaRevierteElCambioDeRol() {
    assertThatThrownBy(
            () ->
                usuarioService.cambiarRol(
                    CATEDRATICO_ID,
                    new CambiarRolRequest(RoleName.PROFESIONAL_EXTERNO, "777"),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("colegiado_duplicado");
            });

    assertThat(rolDe(CATEDRATICO_ID)).isEqualTo("CATEDRATICO");
    assertThat(auditoriaRepository.count()).isZero();
  }

  @Test
  void volverASerProfesionalConOtroColegiadoExigeVerificarloDeNuevo() {
    profesionalService.verificarColegiado(PROFESIONAL_ID, ADMINISTRADOR_ID);
    usuarioService.cambiarRol(
        PROFESIONAL_ID, new CambiarRolRequest(RoleName.CATEDRATICO, null), ADMINISTRADOR_ID);

    usuarioService.cambiarRol(
        PROFESIONAL_ID,
        new CambiarRolRequest(RoleName.PROFESIONAL_EXTERNO, "888"),
        ADMINISTRADOR_ID);

    UsuarioResponse response = usuarioService.obtener(PROFESIONAL_ID);
    assertThat(response.numeroColegiado()).isEqualTo("888");
    assertThat(response.colegiadoVerificado()).isFalse();
  }

  @Test
  void cambiarAlMismoRolSeRechaza() {
    assertThatThrownBy(
            () ->
                usuarioService.cambiarRol(
                    CATEDRATICO_ID,
                    new CambiarRolRequest(RoleName.CATEDRATICO, null),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("rol_sin_cambios"));
  }

  @Test
  @DisplayName("Escenario: un usuario no puede desactivarse a sí mismo")
  void elAdministradorNoPuedeDesactivarseNiCambiarSuRol() {
    assertThatThrownBy(() -> usuarioService.desactivar(ADMINISTRADOR_ID, null, ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo("autodesactivacion_no_permitida"));
    assertThatThrownBy(
            () ->
                usuarioService.cambiarRol(
                    ADMINISTRADOR_ID,
                    new CambiarRolRequest(RoleName.CATEDRATICO, null),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo("cambio_rol_propio_no_permitido"));

    assertThat(
            jdbc.queryForObject(
                "SELECT activo FROM usuario WHERE id = ?", Boolean.class, ADMINISTRADOR_ID))
        .isTrue();
    assertThat(rolDe(ADMINISTRADOR_ID)).isEqualTo("ADMINISTRADOR");
  }

  @Test
  void soloElAdministradorGestionaUsuarios() {
    autenticar(CATEDRATICO_ID, RoleName.CATEDRATICO);

    assertThatThrownBy(() -> usuarioService.listar(null, null, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> usuarioService.desactivar(ESTUDIANTE_PENDIENTE_ID, null, CATEDRATICO_ID))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> usuarioService.reactivar(ESTUDIANTE_PENDIENTE_ID))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                usuarioService.cambiarRol(
                    ESTUDIANTE_PENDIENTE_ID,
                    new CambiarRolRequest(RoleName.ADMINISTRADOR, null),
                    CATEDRATICO_ID))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(rolDe(ESTUDIANTE_PENDIENTE_ID)).isEqualTo("ESTUDIANTE");
  }

  @Test
  void operarSobreUnUsuarioInexistenteResponde404() {
    UUID inexistente = UUID.fromString("019a0000-0000-7000-8000-0000000000ff");

    assertThatThrownBy(() -> usuarioService.reactivar(inexistente))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    assertThatThrownBy(() -> usuarioService.obtener(inexistente))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("usuario_no_encontrado"));
  }

  private List<String> correos(List<UsuarioResponse> usuarios) {
    return usuarios.stream().map(UsuarioResponse::email).toList();
  }

  private String rolDe(UUID usuarioId) {
    return jdbc.queryForObject(
        "SELECT r.nombre FROM usuario u JOIN rol r ON r.id = u.rol_id WHERE u.id = ?",
        String.class,
        usuarioId);
  }

  private void insertarUsuario(
      UUID id, String email, String rol, boolean verificado, boolean activado) {
    jdbc.update(
        """
        INSERT INTO usuario (
          id, email, password_hash, rol_id,
          verificado, activado, activo, nombre_completo
        )
        SELECT ?, ?, ?, id, ?, ?, true, 'Usuario de prueba HU008'
        FROM rol
        WHERE nombre = ?
        """,
        id,
        email,
        passwordEncoder.encode(CONTRASENA),
        verificado,
        activado,
        rol);
  }

  private UsernamePasswordAuthenticationToken autenticacion(UUID usuarioId, RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "usuario.hu008@ejemplo.com",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData("usuario.hu008@ejemplo.com", usuarioId, rol, 0));
    return authentication;
  }

  private void autenticar(UUID usuarioId, RoleName rol) {
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(autenticacion(usuarioId, rol));
    SecurityContextHolder.setContext(context);
  }

  @TestConfiguration
  @EnableMethodSecurity
  @EnableAspectJAutoProxy(proxyTargetClass = true)
  static class Config {

    @Bean
    Clock clock() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(4);
    }
  }
}
