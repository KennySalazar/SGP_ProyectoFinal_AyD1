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
import gt.usac.cunoc.sgp.common.mail.EmailService;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.util.TokenHasher;
import gt.usac.cunoc.sgp.usuario.dto.AceptarInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.CrearInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.InvitacionResponse;
import gt.usac.cunoc.sgp.usuario.dto.LoginRequest;
import gt.usac.cunoc.sgp.usuario.dto.TokenInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.mapper.InvitacionMapperImpl;
import gt.usac.cunoc.sgp.usuario.mapper.UserMapper;
import gt.usac.cunoc.sgp.usuario.model.EstadoInvitacion;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.service.AuthService;
import gt.usac.cunoc.sgp.usuario.service.CuentaInvitadaService;
import gt.usac.cunoc.sgp.usuario.service.InvitacionEmailService;
import gt.usac.cunoc.sgp.usuario.service.InvitacionService;
import gt.usac.cunoc.sgp.usuario.service.OtpService;
import gt.usac.cunoc.sgp.usuario.service.RefreshTokenService;
import gt.usac.cunoc.sgp.usuario.service.UsuarioAuditoriaSnapshotProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
  InvitacionEmailService.class,
  InvitacionMapperImpl.class,
  AuthService.class,
  AuditoriaAspect.class,
  AuditoriaWriter.class,
  AuditoriaSnapshotService.class,
  UsuarioAuditoriaSnapshotProvider.class,
  InvitacionCatedraticoIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class InvitacionCatedraticoIntegrationTest {

  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000040");
  private static final UUID CATEDRATICO_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000041");

  private static final Instant AHORA = Instant.parse("2026-10-06T18:00:00Z");
  private static final String CORREO = "catedratico@usac.edu.gt";
  private static final String CONTRASENA = "Puentes2026";
  private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_hu001_test")
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
  @Autowired private AuthService authService;
  @Autowired private AuditoriaRepository auditoriaRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CorreoCapturado correos;
  @Autowired private RelojAjustable reloj;
  @Autowired private JdbcTemplate jdbc;

  @MockBean private OtpService otpService;
  @MockBean private JwtService jwtService;
  @MockBean private RefreshTokenService refreshTokens;
  @MockBean private UserMapper userMapper;

  @BeforeEach
  void prepararDatos() {
    jdbc.update("DELETE FROM auditoria");
    jdbc.update("DELETE FROM invitacion_usuario");
    jdbc.update(
        "DELETE FROM usuario WHERE id <> ? AND email LIKE '%usac.edu.gt'", ADMINISTRADOR_ID);
    insertarUsuario(ADMINISTRADOR_ID, "admin.hu001@usac.edu.gt", "ADMINISTRADOR");

    correos.reiniciar();
    reloj.ajustar(AHORA);
    autenticar(ADMINISTRADOR_ID, RoleName.ADMINISTRADOR);
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Escenario: invitación exitosa")
  void invitacionExitosaCreaCuentaPendienteYEnviaEnlaceDeUnSoloUso() {
    InvitacionResponse response =
        invitacionService.invitar(
            new CrearInvitacionRequest("  Catedratico@USAC.edu.gt ", RoleName.CATEDRATICO),
            ADMINISTRADOR_ID);

    // La cuenta existe en estado pendiente de invitación.
    assertThat(response.email()).isEqualTo(CORREO);
    assertThat(response.rol()).isEqualTo(RoleName.CATEDRATICO);
    assertThat(response.estado()).isEqualTo(EstadoInvitacion.PENDIENTE);
    assertThat(response.invitadoPorId()).isEqualTo(ADMINISTRADOR_ID);
    assertThat(response.expiraEn().toInstant()).isEqualTo(AHORA.plus(Duration.ofHours(72)));
    assertThat(response.creadoEn().getOffset()).isEqualTo(ZoneOffset.ofHours(-6));
    var cuenta =
        jdbc.queryForMap(
            """
            SELECT u.id, u.verificado, u.activado, u.activo, r.nombre AS rol
            FROM usuario u JOIN rol r ON r.id = u.rol_id
            WHERE u.email = ?
            """,
            CORREO);
    assertThat(cuenta.get("id")).isEqualTo(response.usuarioId());
    assertThat(cuenta.get("verificado")).isEqualTo(false);
    assertThat(cuenta.get("activado")).isEqualTo(false);
    assertThat(cuenta.get("activo")).isEqualTo(true);
    assertThat(cuenta.get("rol")).isEqualTo("CATEDRATICO");

    // El correo lleva un enlace cuyo token solo se guarda como hash.
    assertThat(correos.enviados).hasSize(1);
    assertThat(correos.enviados.get(0).destinatario()).isEqualTo(CORREO);
    assertThat(correos.enviados.get(0).cuerpo())
        .contains("http://localhost:4200/activar-cuenta?token=")
        .contains("09/10/2026 12:00");
    String token = ultimoToken();
    assertThat(correos.enviados.get(0).asunto()).contains("Invitación");
    assertThat(correos.enviados.get(0).html())
        .contains("href=\"http://localhost:4200/activar-cuenta?token=" + token + "\"")
        .contains("Catedrático")
        .contains("catedratico@usac.edu.gt")
        .contains("09/10/2026 12:00")
        .doesNotContain("{{");
    assertThat(
            jdbc.queryForObject(
                "SELECT token_hash FROM invitacion_usuario WHERE id = ?",
                String.class,
                response.id()))
        .isEqualTo(TokenHasher.sha256(token))
        .doesNotContain(token);

    // La cuenta no puede iniciar sesión hasta completar la activación.
    assertThatThrownBy(() -> authService.login(new LoginRequest(CORREO, CONTRASENA)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

    // Se audita la creación de la cuenta y de la invitación con el Administrador como actor.
    var auditoria = auditoriaRepository.findAll();
    assertThat(auditoria)
        .extracting(registro -> registro.getEntidad() + ":" + registro.getAccion())
        .containsExactlyInAnyOrder("usuario:CREAR", "invitacion_usuario:CREAR");
    assertThat(auditoria).allMatch(registro -> ADMINISTRADOR_ID.equals(registro.getUsuarioId()));
    assertThat(auditoria)
        .allSatisfy(
            registro ->
                assertThat(String.valueOf(registro.getValoresPosteriores())).doesNotContain(token));
  }

  @Test
  @DisplayName("Escenario: correo ya registrado")
  void correoConCuentaActivaSeRechazaCon409() {
    insertarUsuario(CATEDRATICO_ID, CORREO, "CATEDRATICO");

    assertThatThrownBy(
            () ->
                invitacionService.invitar(
                    new CrearInvitacionRequest(CORREO, RoleName.CATEDRATICO), ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("email_already_registered");
            });

    assertThat(contarInvitaciones()).isZero();
    assertThat(correos.enviados).isEmpty();
    assertThat(auditoriaRepository.count()).isZero();
  }

  @Test
  void correoConInvitacionSinAceptarSeRechazaYSugiereReenvio() {
    invitar();

    assertThatThrownBy(
            () ->
                invitacionService.invitar(
                    new CrearInvitacionRequest(CORREO, RoleName.CATEDRATICO), ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("invitacion_pendiente"));
  }

  @Test
  @DisplayName("Escenario: el invitado completa su activación")
  void invitadoActivaSuCuentaConRolCatedraticoYQuedaAuditado() {
    InvitacionResponse invitacion = invitar();
    String token = ultimoToken();
    SecurityContextHolder.clearContext();
    auditoriaRepository.deleteAll();
    reloj.ajustar(AHORA.plus(Duration.ofHours(1)));

    var publica = invitacionService.validar(new TokenInvitacionRequest(token));
    assertThat(publica.email()).isEqualTo(CORREO);
    assertThat(publica.rol()).isEqualTo(RoleName.CATEDRATICO);

    var mensaje = invitacionService.aceptar(new AceptarInvitacionRequest(token, CONTRASENA));
    assertThat(mensaje.message()).contains("activada");

    var cuenta =
        jdbc.queryForMap(
            """
            SELECT u.password_hash, u.verificado, u.activado, u.activo, r.nombre AS rol
            FROM usuario u JOIN rol r ON r.id = u.rol_id
            WHERE u.id = ?
            """,
            invitacion.usuarioId());
    assertThat(cuenta.get("verificado")).isEqualTo(true);
    assertThat(cuenta.get("activado")).isEqualTo(true);
    assertThat(cuenta.get("activo")).isEqualTo(true);
    assertThat(cuenta.get("rol")).isEqualTo("CATEDRATICO");
    assertThat(passwordEncoder.matches(CONTRASENA, (String) cuenta.get("password_hash"))).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT aceptado_en FROM invitacion_usuario WHERE id = ?",
                java.sql.Timestamp.class,
                invitacion.id()))
        .isNotNull();

    // El evento queda en la bitácora con los valores anterior y posterior (RN-USR-09).
    var auditoria = auditoriaRepository.findAll();
    assertThat(auditoria).hasSize(1);
    var registro = auditoria.get(0);
    assertThat(registro.getAccion()).isEqualTo(AccionAuditoria.CAMBIAR_ESTADO);
    assertThat(registro.getEntidad()).isEqualTo("usuario");
    assertThat(registro.getEntidadId()).isEqualTo(invitacion.usuarioId());
    assertThat(registro.getUsuarioId()).isEqualTo(invitacion.usuarioId());
    assertThat(registro.getValoresAnteriores().get("activado").asBoolean()).isFalse();
    assertThat(registro.getValoresPosteriores().get("activado").asBoolean()).isTrue();
    assertThat(registro.getValoresPosteriores().get("rol").asText()).isEqualTo("CATEDRATICO");
    assertThat(registro.getValoresPosteriores().toString()).doesNotContain(CONTRASENA);

    // Ya puede iniciar sesión.
    var login = authService.login(new LoginRequest(CORREO, CONTRASENA));
    assertThat(login).isNotNull();
  }

  @Test
  void elEnlaceEsDeUnSoloUso() {
    invitar();
    String token = ultimoToken();
    SecurityContextHolder.clearContext();

    invitacionService.aceptar(new AceptarInvitacionRequest(token, CONTRASENA));

    assertThatThrownBy(
            () -> invitacionService.aceptar(new AceptarInvitacionRequest(token, "OtraClave2026")))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(e.getCode()).isEqualTo("invitacion_invalida");
            });
    assertThatThrownBy(() -> invitacionService.validar(new TokenInvitacionRequest(token)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("invitacion_invalida"));
  }

  @Test
  void rechazaTokenDesconocido() {
    assertThatThrownBy(
            () -> invitacionService.aceptar(new AceptarInvitacionRequest("inventado", CONTRASENA)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("invitacion_invalida"));
  }

  @Test
  @DisplayName("Escenario: enlace de invitación vencido")
  void enlaceVencidoSeRechazaYSoloElAdministradorPuedeReenviar() {
    InvitacionResponse original = invitar();
    String tokenVencido = ultimoToken();
    reloj.ajustar(AHORA.plus(Duration.ofHours(72)));

    SecurityContextHolder.clearContext();
    assertThatThrownBy(
            () -> invitacionService.aceptar(new AceptarInvitacionRequest(tokenVencido, CONTRASENA)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE);
              assertThat(e.getCode()).isEqualTo("invitacion_vencida");
            });
    assertThatThrownBy(() -> invitacionService.validar(new TokenInvitacionRequest(tokenVencido)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("invitacion_vencida"));
    assertThat(
            jdbc.queryForObject(
                "SELECT activado FROM usuario WHERE id = ?", Boolean.class, original.usuarioId()))
        .isFalse();

    // Un usuario sin rol de Administrador no puede reenviar.
    autenticar(CATEDRATICO_ID, RoleName.CATEDRATICO);
    assertThatThrownBy(() -> invitacionService.reenviar(original.id(), CATEDRATICO_ID))
        .isInstanceOf(AccessDeniedException.class);

    // El Administrador la reenvía: el enlace anterior queda cancelado y el nuevo funciona.
    autenticar(ADMINISTRADOR_ID, RoleName.ADMINISTRADOR);
    var vencidas = invitacionService.listar(EstadoInvitacion.VENCIDA, 0, 20);
    assertThat(vencidas.getContent()).extracting(InvitacionResponse::id).contains(original.id());

    InvitacionResponse nueva = invitacionService.reenviar(original.id(), ADMINISTRADOR_ID);
    String tokenNuevo = ultimoToken();
    assertThat(nueva.id()).isNotEqualTo(original.id());
    assertThat(nueva.usuarioId()).isEqualTo(original.usuarioId());
    assertThat(nueva.expiraEn().toInstant()).isEqualTo(AHORA.plus(Duration.ofHours(144)));
    assertThat(tokenNuevo).isNotEqualTo(tokenVencido);
    assertThat(
            jdbc.queryForObject(
                "SELECT cancelado_en IS NOT NULL FROM invitacion_usuario WHERE id = ?",
                Boolean.class,
                original.id()))
        .isTrue();

    SecurityContextHolder.clearContext();
    assertThatThrownBy(
            () -> invitacionService.aceptar(new AceptarInvitacionRequest(tokenVencido, CONTRASENA)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("invitacion_invalida"));
    invitacionService.aceptar(new AceptarInvitacionRequest(tokenNuevo, CONTRASENA));
    assertThat(
            jdbc.queryForObject(
                "SELECT activado FROM usuario WHERE id = ?", Boolean.class, original.usuarioId()))
        .isTrue();

    // Una invitación aceptada ya no se puede reenviar.
    autenticar(ADMINISTRADOR_ID, RoleName.ADMINISTRADOR);
    assertThatThrownBy(() -> invitacionService.reenviar(nueva.id(), ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("invitacion_no_reenviable"));
  }

  @Test
  @DisplayName("Escenario: rol no autorizado intenta invitar")
  void rolDistintoDeAdministradorNoPuedeInvitar() {
    for (RoleName rol : List.of(RoleName.CATEDRATICO, RoleName.ESTUDIANTE)) {
      autenticar(CATEDRATICO_ID, rol);
      assertThatThrownBy(
              () ->
                  invitacionService.invitar(
                      new CrearInvitacionRequest(CORREO, RoleName.CATEDRATICO), CATEDRATICO_ID))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> invitacionService.listar(null, 0, 20))
          .isInstanceOf(AccessDeniedException.class);
    }

    assertThat(contarInvitaciones()).isZero();
    assertThat(correos.enviados).isEmpty();
  }

  @Test
  void siElCorreoFallaNoQuedaCuentaNiInvitacion() {
    correos.fallar = true;

    assertThatThrownBy(this::invitar)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE email = ?", Integer.class, CORREO))
        .isZero();
    assertThat(contarInvitaciones()).isZero();
    assertThat(auditoriaRepository.count()).isZero();
  }

  private InvitacionResponse invitar() {
    return invitacionService.invitar(
        new CrearInvitacionRequest(CORREO, RoleName.CATEDRATICO), ADMINISTRADOR_ID);
  }

  private String ultimoToken() {
    Matcher matcher = TOKEN.matcher(correos.enviados.get(correos.enviados.size() - 1).cuerpo());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private int contarInvitaciones() {
    return jdbc.queryForObject("SELECT count(*) FROM invitacion_usuario", Integer.class);
  }

  private void insertarUsuario(UUID id, String email, String rol) {
    jdbc.update(
        """
        INSERT INTO usuario (
          id, email, password_hash, rol_id,
          verificado, activado, activo, nombre_completo
        )
        SELECT ?, ?, 'hash-de-prueba', id, true, true, true, 'Usuario de prueba HU001'
        FROM rol
        WHERE nombre = ?
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        email,
        rol);
  }

  private void autenticar(UUID usuarioId, RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "usuario.hu001@usac.edu.gt",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData("usuario.hu001@usac.edu.gt", usuarioId, rol, 0));

    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
  }

  record CorreoEnviado(String destinatario, String asunto, String cuerpo, String html) {}

  /** Sustituye el SMTP real y conserva los correos para leer el enlace de activación. */
  static class CorreoCapturado implements EmailService {

    final List<CorreoEnviado> enviados = new ArrayList<>();
    boolean fallar;

    void reiniciar() {
      enviados.clear();
      fallar = false;
    }

    @Override
    public void send(String recipient, String subject, String body) {
      if (fallar) {
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "email_delivery_failed",
            "Servicio de correo no disponible",
            "No fue posible enviar el correo");
      }
      enviados.add(new CorreoEnviado(recipient, subject, body, null));
    }

    @Override
    public void sendHtml(String recipient, String subject, String text, String html) {
      send(recipient, subject, text);
      CorreoEnviado enviado = enviados.remove(enviados.size() - 1);
      enviados.add(new CorreoEnviado(recipient, subject, text, html));
    }
  }

  /** Reloj controlable para simular el vencimiento del enlace. */
  static class RelojAjustable extends Clock {

    private Instant instante = AHORA;

    void ajustar(Instant instante) {
      this.instante = instante;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instante;
    }
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
