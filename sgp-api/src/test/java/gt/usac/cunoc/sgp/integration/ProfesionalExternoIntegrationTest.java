package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.aspect.AuditoriaAspect;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.repository.AuditoriaRepository;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.config.InvitacionProperties;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.integration.InvitacionCatedraticoIntegrationTest.CorreoCapturado;
import gt.usac.cunoc.sgp.integration.InvitacionCatedraticoIntegrationTest.RelojAjustable;
import gt.usac.cunoc.sgp.usuario.dto.AceptarInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.CrearInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.InvitacionResponse;
import gt.usac.cunoc.sgp.usuario.dto.LoginRequest;
import gt.usac.cunoc.sgp.usuario.dto.ProfesionalResponse;
import gt.usac.cunoc.sgp.usuario.mapper.InvitacionMapperImpl;
import gt.usac.cunoc.sgp.usuario.mapper.UserMapperImpl;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.service.AuthService;
import gt.usac.cunoc.sgp.usuario.service.CuentaInvitadaService;
import gt.usac.cunoc.sgp.usuario.service.InvitacionEmailService;
import gt.usac.cunoc.sgp.usuario.service.InvitacionService;
import gt.usac.cunoc.sgp.usuario.service.OtpService;
import gt.usac.cunoc.sgp.usuario.service.ProfesionalService;
import gt.usac.cunoc.sgp.usuario.service.RefreshTokenService;
import gt.usac.cunoc.sgp.usuario.service.UsuarioAuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.usuario.service.UsuarioProfesionalAuditoriaSnapshotProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  InvitacionService.class,
  CuentaInvitadaService.class,
  ProfesionalService.class,
  InvitacionEmailService.class,
  InvitacionMapperImpl.class,
  UserMapperImpl.class,
  AuthService.class,
  AuditoriaAspect.class,
  AuditoriaWriter.class,
  AuditoriaSnapshotService.class,
  UsuarioAuditoriaSnapshotProvider.class,
  UsuarioProfesionalAuditoriaSnapshotProvider.class,
  ProfesionalExternoIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ProfesionalExternoIntegrationTest {

  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000070");
  private static final UUID CATEDRATICO_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000071");

  private static final Instant AHORA = Instant.parse("2026-10-07T18:00:00Z");
  private static final String CORREO = "ingeniero.hu002@ejemplo.com";
  private static final String CONTRASENA = "Puentes2026";
  private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_hu002_test")
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

  @Autowired private InvitacionService invitacionService;
  @Autowired private ProfesionalService profesionalService;
  @Autowired private AuthService authService;
  @Autowired private AuditoriaRepository auditoriaRepository;
  @Autowired private CorreoCapturado correos;
  @Autowired private RelojAjustable reloj;
  @Autowired private JdbcTemplate jdbc;

  @MockBean private OtpService otpService;
  @MockBean private JwtService jwtService;
  @MockBean private RefreshTokenService refreshTokens;

  @BeforeEach
  void prepararDatos() {
    jdbc.update("DELETE FROM auditoria");
    jdbc.update("DELETE FROM invitacion_usuario");
    jdbc.update("DELETE FROM usuario_profesional");
    jdbc.update("DELETE FROM usuario WHERE id <> ? AND email LIKE '%hu002%'", ADMINISTRADOR_ID);
    insertarUsuario(ADMINISTRADOR_ID, "admin.hu002@ejemplo.com", "ADMINISTRADOR");

    correos.reiniciar();
    reloj.ajustar(AHORA);
    autenticar(ADMINISTRADOR_ID, "admin.hu002@ejemplo.com", RoleName.ADMINISTRADOR);
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Escenario: invitación con número de colegiado")
  void invitacionConColegiadoCreaCuentaPendienteDeVerificacion() {
    InvitacionResponse response = invitar(CORREO, " 12345 ");

    assertThat(response.rol()).isEqualTo(RoleName.PROFESIONAL_EXTERNO);
    assertThat(response.numeroColegiado()).isEqualTo("12345");
    Map<String, Object> profesional =
        jdbc.queryForMap(
            """
            SELECT p.numero_colegiado, p.colegiado_verificado_en, u.activado, r.nombre AS rol
            FROM usuario_profesional p
            JOIN usuario u ON u.id = p.usuario_id
            JOIN rol r ON r.id = u.rol_id
            WHERE p.usuario_id = ?
            """,
            response.usuarioId());
    assertThat(profesional.get("numero_colegiado")).isEqualTo("12345");
    assertThat(profesional.get("colegiado_verificado_en")).isNull();
    assertThat(profesional.get("activado")).isEqualTo(false);
    assertThat(profesional.get("rol")).isEqualTo("PROFESIONAL_EXTERNO");

    // Se envía el correo de invitación con el rol correspondiente.
    assertThat(correos.enviados).hasSize(1);
    assertThat(correos.enviados.get(0).destinatario()).isEqualTo(CORREO);
    assertThat(correos.enviados.get(0).html()).contains("Profesional externo");

    // El registro del colegiado también queda en la bitácora.
    assertThat(auditoriaRepository.findAll())
        .extracting(registro -> registro.getEntidad() + ":" + registro.getAccion())
        .contains("usuario_profesional:CREAR", "invitacion_usuario:CREAR");

    var pendientes = profesionalService.listar(false, 0, 20);
    assertThat(pendientes.getContent())
        .extracting(ProfesionalResponse::usuarioId)
        .containsExactly(response.usuarioId());
    assertThat(profesionalService.listar(true, 0, 20).getContent()).isEmpty();
  }

  @Test
  @DisplayName("Escenario: activación sin verificación de colegiado")
  void profesionalActivadoSinVerificarPuedeAutenticarsePeroNoInspeccionar() {
    InvitacionResponse invitacion = invitar(CORREO, "12345");
    SecurityContextHolder.clearContext();

    invitacionService.aceptar(new AceptarInvitacionRequest(ultimoToken(), CONTRASENA));

    // Puede autenticarse.
    assertThat(authService.login(new LoginRequest(CORREO, CONTRASENA))).isNotNull();
    // La sesión indica que su colegiado está pendiente.
    assertThat(authService.currentUser(CORREO).colegiadoVerificado()).isFalse();
    // Y la regla de autorización le restringe las acciones de inspección (RN-USR-04).
    assertThat(
            profesionalService.colegiadoHabilitado(
                autenticacion(invitacion.usuarioId(), CORREO, RoleName.PROFESIONAL_EXTERNO)))
        .isFalse();
  }

  @Test
  @DisplayName("Escenario: Administrador verifica el colegiado")
  void administradorVerificaElColegiadoYQuedaAuditado() {
    InvitacionResponse invitacion = invitar(CORREO, "12345");
    SecurityContextHolder.clearContext();
    invitacionService.aceptar(new AceptarInvitacionRequest(ultimoToken(), CONTRASENA));
    autenticar(ADMINISTRADOR_ID, "admin.hu002@ejemplo.com", RoleName.ADMINISTRADOR);
    auditoriaRepository.deleteAll();

    profesionalService.verificarColegiado(invitacion.usuarioId(), ADMINISTRADOR_ID);
    ProfesionalResponse verificado = profesionalService.obtener(invitacion.usuarioId());

    assertThat(verificado.colegiadoVerificado()).isTrue();
    assertThat(verificado.colegiadoVerificadoPorId()).isEqualTo(ADMINISTRADOR_ID);
    assertThat(verificado.colegiadoVerificadoEn().toInstant()).isEqualTo(AHORA);
    assertThat(verificado.cuentaActivada()).isTrue();

    // La cuenta queda completamente habilitada.
    assertThat(authService.currentUser(CORREO).colegiadoVerificado()).isTrue();
    assertThat(
            profesionalService.colegiadoHabilitado(
                autenticacion(invitacion.usuarioId(), CORREO, RoleName.PROFESIONAL_EXTERNO)))
        .isTrue();

    // Y el cambio se registra en auditoría con el Administrador como autor.
    var auditoria = auditoriaRepository.findAll();
    assertThat(auditoria).hasSize(1);
    var registro = auditoria.get(0);
    assertThat(registro.getAccion()).isEqualTo(AccionAuditoria.CAMBIAR_ESTADO);
    assertThat(registro.getEntidad()).isEqualTo("usuario_profesional");
    assertThat(registro.getEntidadId()).isEqualTo(invitacion.usuarioId());
    assertThat(registro.getUsuarioId()).isEqualTo(ADMINISTRADOR_ID);
    assertThat(registro.getValoresAnteriores().get("colegiadoVerificado").asBoolean()).isFalse();
    assertThat(registro.getValoresPosteriores().get("colegiadoVerificado").asBoolean()).isTrue();
    // Antes y después tienen el mismo formato: solo cambian los datos de la verificación.
    var antes = registro.getValoresAnteriores();
    var despues = registro.getValoresPosteriores();
    assertThat(despues.size()).isEqualTo(antes.size());
    assertThat(despues.get("numeroColegiado")).isEqualTo(antes.get("numeroColegiado"));

    // No se verifica dos veces.
    assertThatThrownBy(
            () -> profesionalService.verificarColegiado(invitacion.usuarioId(), ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("colegiado_ya_verificado"));
  }

  @Test
  void soloElAdministradorPuedeVerificarColegiados() {
    InvitacionResponse invitacion = invitar(CORREO, "12345");
    autenticar(CATEDRATICO_ID, "catedratico.hu002@ejemplo.com", RoleName.CATEDRATICO);

    assertThatThrownBy(
            () -> profesionalService.verificarColegiado(invitacion.usuarioId(), CATEDRATICO_ID))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> profesionalService.listar(null, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT colegiado_verificado_en IS NULL FROM usuario_profesional"
                    + " WHERE usuario_id = ?",
                Boolean.class,
                invitacion.usuarioId()))
        .isTrue();
  }

  @Test
  @DisplayName("Escenario: número de colegiado duplicado")
  void colegiadoDuplicadoSeRechazaCon409SinCrearCuentaNiEnviarCorreo() {
    invitar(CORREO, "12345");
    correos.reiniciar();

    assertThatThrownBy(() -> invitar("otro.ingeniero.hu002@ejemplo.com", " 12345"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("colegiado_duplicado");
            });

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE email = 'otro.ingeniero.hu002@ejemplo.com'",
                Integer.class))
        .isZero();
    assertThat(correos.enviados).isEmpty();
  }

  @Test
  void elColegiadoNoAplicaAlCatedratico() {
    assertThatThrownBy(
            () ->
                invitacionService.invitar(
                    new CrearInvitacionRequest(
                        "catedratico.hu002@ejemplo.com", RoleName.CATEDRATICO, "12345"),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("colegiado_no_aplica"));
  }

  @Test
  void laSesionDeOtrosRolesNoIncluyeColegiado() {
    insertarUsuario(CATEDRATICO_ID, "catedratico.hu002@ejemplo.com", "CATEDRATICO");

    assertThat(authService.currentUser("catedratico.hu002@ejemplo.com").colegiadoVerificado())
        .isNull();
  }

  private InvitacionResponse invitar(String correo, String colegiado) {
    return invitacionService.invitar(
        new CrearInvitacionRequest(correo, RoleName.PROFESIONAL_EXTERNO, colegiado),
        ADMINISTRADOR_ID);
  }

  private String ultimoToken() {
    Matcher matcher = TOKEN.matcher(correos.enviados.get(correos.enviados.size() - 1).cuerpo());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private void insertarUsuario(UUID id, String email, String rol) {
    jdbc.update(
        """
        INSERT INTO usuario (
          id, email, password_hash, rol_id,
          verificado, activado, activo, nombre_completo
        )
        SELECT ?, ?, 'hash-de-prueba', id, true, true, true, 'Usuario de prueba HU002'
        FROM rol
        WHERE nombre = ?
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        email,
        rol);
  }

  private UsernamePasswordAuthenticationToken autenticacion(
      UUID usuarioId, String email, RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            email, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData(email, usuarioId, rol, 0));
    return authentication;
  }

  private void autenticar(UUID usuarioId, String email, RoleName rol) {
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(autenticacion(usuarioId, email, rol));
    SecurityContextHolder.setContext(context);
  }

  @TestConfiguration
  @EnableMethodSecurity
  @EnableAspectJAutoProxy(proxyTargetClass = true)
  static class Config {

    @Bean
    RelojAjustable clock() {
      return new RelojAjustable();
    }

    @Bean
    CorreoCapturado emailService() {
      return new CorreoCapturado();
    }

    @Bean
    InvitacionProperties invitacionProperties() {
      return new InvitacionProperties();
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
