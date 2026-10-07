package gt.usac.cunoc.sgp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.aspect.Auditable;
import gt.usac.cunoc.sgp.common.audit.aspect.AuditoriaAspect;
import gt.usac.cunoc.sgp.common.audit.entity.Auditoria;
import gt.usac.cunoc.sgp.common.audit.mapper.AuditoriaMapperImpl;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.repository.AuditoriaRepository;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaConsultaService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.puente.dto.CrearPuenteRequest;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapperImpl;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.puente.service.CatalogoTerritorialService;
import gt.usac.cunoc.sgp.puente.service.PuenteAuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import gt.usac.cunoc.sgp.usuario.dto.RegisterRequest;
import gt.usac.cunoc.sgp.usuario.entity.OtpChallenge;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.mapper.UserMapper;
import gt.usac.cunoc.sgp.usuario.model.OtpPurpose;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.service.AdminProvisioningService;
import gt.usac.cunoc.sgp.usuario.service.AuthService;
import gt.usac.cunoc.sgp.usuario.service.OtpService;
import gt.usac.cunoc.sgp.usuario.service.RefreshTokenService;
import gt.usac.cunoc.sgp.usuario.service.UsuarioAuditoriaSnapshotProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
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
  PuenteService.class,
  PuenteMapperImpl.class,
  CatalogoTerritorialService.class,
  AdminProvisioningService.class,
  AuthService.class,
  AuditoriaAspect.class,
  AuditoriaWriter.class,
  AuditoriaSnapshotService.class,
  AuditoriaConsultaService.class,
  AuditoriaMapperImpl.class,
  PuenteAuditoriaSnapshotProvider.class,
  UsuarioAuditoriaSnapshotProvider.class,
  AuditoriaRegistroIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class AuditoriaRegistroIntegrationTest {

  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000006");

  private static final Instant AHORA = Instant.parse("2026-10-02T23:00:00Z");

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_hu006_test")
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

  @Autowired private PuenteService puenteService;
  @Autowired private PuenteRepository puenteRepository;
  @Autowired private AdminProvisioningService adminProvisioningService;
  @Autowired private AuthService authService;
  @MockBean private OtpService otpService;
  @MockBean private JwtService jwtService;
  @MockBean private RefreshTokenService refreshTokens;
  @MockBean private UserMapper userMapper;
  @Autowired private AuditoriaRepository auditoriaRepository;
  @Autowired private AuditoriaConsultaService auditoriaConsulta;
  @Autowired private EditarPuenteFixture editarPuenteFixture;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  private UUID departamentoId;
  private UUID municipioId;

  @BeforeEach
  void prepararDatos() {
    jdbc.update("DELETE FROM auditoria");
    jdbc.update("DELETE FROM puente");
    jdbc.update("UPDATE municipio SET ultimo_correlativo_puente = 0");

    jdbc.update(
        """
            INSERT INTO usuario (
              id, email, password_hash, rol_id,
              verificado, activado, activo, nombre_completo
            )
            SELECT ?, 'admin.hu006@ejemplo.com', 'hash-de-prueba', id,
                   true, true, true, 'Administrador de prueba HU006'
            FROM rol
            WHERE nombre = 'ADMINISTRADOR'
            ON CONFLICT (id) DO NOTHING
            """,
        ADMINISTRADOR_ID);

    departamentoId =
        jdbc.queryForObject("SELECT id FROM departamento WHERE codigo_ine = '01'", UUID.class);
    municipioId =
        jdbc.queryForObject("SELECT id FROM municipio WHERE codigo_ine = '0114'", UUID.class);

    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Escenario: registrar una acción exitosa crea el registro en auditoría")
  void registraAccionExitosaEnAuditoria() {
    // Dado un administrador autenticado con su JwtData
    autenticar(ADMINISTRADOR_ID);

    // Cuando registra un puente
    var response =
        puenteService.registrar(
            solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);

    // Entonces se crea un registro con usuario, fecha, acción, entidad y valores posteriores
    assertEquals(1, auditoriaRepository.count());
    var auditoria = auditoriaRepository.findAll().get(0);
    assertEquals(AccionAuditoria.CREAR, auditoria.getAccion());
    assertEquals("puente", auditoria.getEntidad());
    assertEquals(ADMINISTRADOR_ID, auditoria.getUsuarioId());
    assertEquals(response.id(), auditoria.getEntidadId());
    assertEquals(AHORA, auditoria.getCreadoEn());
    assertNull(auditoria.getProcesoAutomatico());
    assertNull(auditoria.getValoresAnteriores());
    assertNotNull(auditoria.getValoresPosteriores());
    assertEquals(response.codigo(), auditoria.getValoresPosteriores().get("codigo").asText());

    // Y la consulta paginada devuelve ese registro
    var pagina = auditoriaConsulta.consultar(null, "puente", null, null, null, 0, 10);
    assertEquals(1, pagina.getTotalElements());
    assertEquals(AccionAuditoria.CREAR, pagina.getContent().get(0).accion());
    assertEquals(ADMINISTRADOR_ID, pagina.getContent().get(0).usuarioId());
  }

  @Test
  @DisplayName("Escenario: modificar una entidad guarda valores anteriores y posteriores")
  void modificacionGuardaValoresAnterioresYPosteriores() {
    // Dado un puente registrado
    autenticar(ADMINISTRADOR_ID);
    var creado =
        puenteService.registrar(
            solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);

    // Cuando un servicio auditable modifica su nombre
    var editado = editarPuenteFixture.editarNombre(creado.id(), "Puente renombrado HU006");

    // Entonces la auditoría registra los valores antes y después
    var modificacion =
        auditoriaRepository.findAll().stream()
            .filter(auditoria -> auditoria.getAccion() == AccionAuditoria.MODIFICAR)
            .findFirst()
            .orElseThrow();

    assertEquals("puente", modificacion.getEntidad());
    assertEquals(creado.id(), modificacion.getEntidadId());
    assertEquals(ADMINISTRADOR_ID, modificacion.getUsuarioId());
    assertNotNull(modificacion.getValoresAnteriores());
    assertEquals(
        "Puente de prueba HU006", modificacion.getValoresAnteriores().get("nombre").asText());
    assertTrue(modificacion.getValoresAnteriores().has("latitud"));
    assertNotNull(modificacion.getValoresPosteriores());
    assertEquals(
        "Puente renombrado HU006", modificacion.getValoresPosteriores().get("nombre").asText());
    assertEquals(editado.id().toString(), modificacion.getValoresPosteriores().get("id").asText());
  }

  @Test
  @DisplayName("Escenario: acción ejecutada por un proceso automático usa usuario nulo y marca")
  void procesoAutomaticoUsaUsuarioNuloYMarca() {
    // Dado que no existe usuario autenticado
    SecurityContextHolder.clearContext();

    // Cuando el aprovisionamiento de administrador ejecuta su proceso
    adminProvisioningService.provisionAdmin("admin.hu006.auto@ejemplo.com", "Auditoria2026");

    // Entonces el registro queda con usuario nulo y la marca del proceso
    assertEquals(1, auditoriaRepository.count());
    var auditoria = auditoriaRepository.findAll().get(0);
    assertNull(auditoria.getUsuarioId());
    assertEquals("provision-admin", auditoria.getProcesoAutomatico());
    assertEquals(AccionAuditoria.CREAR, auditoria.getAccion());
    assertEquals("usuario", auditoria.getEntidad());
    assertNotNull(auditoria.getEntidadId());
    assertNull(auditoria.getValoresAnteriores());
    assertNotNull(auditoria.getValoresPosteriores());
    assertEquals(
        "admin.hu006.auto@ejemplo.com", auditoria.getValoresPosteriores().get("email").asText());
    assertNull(auditoria.getValoresPosteriores().get("passwordHash"));
    assertNotNull(auditoria.getCreadoEn());
  }

  @Test
  @DisplayName("Escenario: no se audita una operación revertida por su transacción externa")
  void noAuditaOperacionRevertida() {
    // Dado un administrador y una transacción externa
    autenticar(ADMINISTRADOR_ID);
    TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

    // Cuando se registra un puente y luego se revierte la transacción
    transaccion.execute(
        estado -> {
          puenteService.registrar(
              solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);
          estado.setRollbackOnly();
          return null;
        });

    // Entonces no persisten ni el puente ni un registro de auditoría fantasma
    assertEquals(0, puenteRepository.count());
    assertEquals(0, auditoriaRepository.count());
  }

  @Test
  @DisplayName("Escenario: la bitácora filtra por usuario y rango y muestra cambios legibles")
  void consultaPorUsuarioYFechasConDetalleLegible() {
    // Dado un puente registrado por un administrador y otro evento automático
    autenticar(ADMINISTRADOR_ID);
    var puente =
        puenteService.registrar(
            solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);
    SecurityContextHolder.clearContext();
    adminProvisioningService.provisionAdmin("admin.hu006.otro@ejemplo.com", "Auditoria2026");
    // Y otra acción del mismo administrador fuera del rango consultado
    auditoriaRepository.save(
        new Auditoria(
            UUID.randomUUID(),
            ADMINISTRADOR_ID,
            AccionAuditoria.MODIFICAR,
            "puente",
            puente.id(),
            null,
            null,
            null,
            AHORA.minusSeconds(60)));

    // Cuando se combinan el usuario y un rango que incluye la fecha de registro
    var pagina =
        auditoriaConsulta.consultar(
            null,
            " ADMIN.HU006@EJEMPLO.COM ",
            null,
            null,
            AHORA.minusSeconds(1),
            AHORA.plusSeconds(1),
            0,
            100);

    // Entonces aparece solo su registro y el detalle contiene campos, no JSON crudo
    assertEquals(1, pagina.getTotalElements());
    assertEquals(puente.id(), pagina.getContent().get(0).entidadId());
    assertEquals("admin.hu006@ejemplo.com", pagina.getContent().get(0).usuarioEmail());
    var detalle = auditoriaConsulta.detalle(pagina.getContent().get(0).id());
    assertTrue(
        detalle.cambios().stream()
            .anyMatch(c -> c.campo().equals("codigo") && c.posterior().equals(puente.codigo())));
    assertTrue(
        auditoriaConsulta
            .consultar(
                ADMINISTRADOR_ID, null, null, AHORA.plusSeconds(1), AHORA.plusSeconds(2), 0, 10)
            .isEmpty());
  }

  @Test
  @DisplayName("Escenario: la bitácora pagina registros por fecha descendente en PostgreSQL")
  void listadoPaginadoOrdenadoPorFecha() {
    // Dado tres acciones de un usuario en fechas distintas
    for (int segundo = 0; segundo < 3; segundo++) {
      auditoriaRepository.save(
          new Auditoria(
              UUID.randomUUID(),
              ADMINISTRADOR_ID,
              AccionAuditoria.MODIFICAR,
              "puente",
              UUID.randomUUID(),
              null,
              null,
              null,
              AHORA.plusSeconds(segundo)));
    }
    // Cuando se consulta la primera página con dos registros
    var primera = auditoriaConsulta.consultar(null, null, null, null, null, 0, 2);
    // Entonces se muestran primero los eventos más recientes y el total es tres
    assertEquals(3, primera.getTotalElements());
    assertEquals(2, primera.getContent().size());
    assertEquals(AHORA.plusSeconds(2), primera.getContent().get(0).creadoEn().toInstant());
    assertEquals(AHORA.plusSeconds(1), primera.getContent().get(1).creadoEn().toInstant());
    // Y la segunda página contiene el evento restante, sin duplicados
    var segunda = auditoriaConsulta.consultar(null, null, null, null, null, 1, 2);
    assertEquals(1, segunda.getContent().size());
    assertEquals(AHORA, segunda.getContent().getFirst().creadoEn().toInstant());
  }

  @Test
  @DisplayName("Escenario: el aprovisionamiento repetido no registra una creación inexistente")
  void aprovisionamientoRepetidoNoDuplicaCreacion() {
    // Dado un administrador creado por el proceso de arranque
    adminProvisioningService.provisionAdmin("admin.hu006.repetido@ejemplo.com", "Auditoria2026");
    assertEquals(1, auditoriaRepository.count());

    // Cuando el mismo proceso vuelve a ejecutarse sin cambiar datos
    adminProvisioningService.provisionAdmin("admin.hu006.repetido@ejemplo.com", "Auditoria2026");

    // Entonces solo queda la creación real en la bitácora
    assertEquals(1, auditoriaRepository.count());
  }

  @Test
  @DisplayName("Escenario: el auto-registro audita al usuario sin guardar sus credenciales")
  void autoRegistroAuditaSinCredenciales() {
    // Dado un visitante y un desafío OTP emitido por el servicio existente
    OtpChallenge desafio = mock(OtpChallenge.class);
    when(desafio.getId()).thenReturn(UUID.randomUUID());
    when(desafio.getExpiresAt()).thenReturn(AHORA.plusSeconds(600));
    when(otpService.issue(any(UserAccount.class), eq(OtpPurpose.REGISTRO))).thenReturn(desafio);

    // Cuando se registra con correo y contraseña
    authService.register(new RegisterRequest("nuevo.hu006@ejemplo.com", "Auditoria2026"));

    // Entonces el actor y la entidad son el usuario creado y no se guarda el hash
    var auditoria = auditoriaRepository.findAll().get(0);
    assertEquals(AccionAuditoria.CREAR, auditoria.getAccion());
    assertEquals("usuario", auditoria.getEntidad());
    assertEquals(auditoria.getEntidadId(), auditoria.getUsuarioId());
    assertEquals(
        "nuevo.hu006@ejemplo.com", auditoria.getValoresPosteriores().get("email").asText());
    assertNull(auditoria.getValoresPosteriores().get("passwordHash"));
  }

  @Test
  @DisplayName("Escenario: el fallo de auditoría no bloquea la operación de negocio")
  void falloDeAuditoriaNoBloqueaElNegocio() {
    // Dado un usuario autenticado que no existe (el INSERT de auditoría falla por llave foránea)
    autenticar(UUID.randomUUID());

    // Cuando registra un puente
    var response =
        puenteService.registrar(
            solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);

    // Entonces la operación de negocio se completa y la auditoría queda en el log técnico
    assertNotNull(response);
    assertEquals("GT-01-0114-0001", response.codigo());
    assertEquals(1, puenteRepository.count());
    assertEquals(0, auditoriaRepository.count());
  }

  @Test
  @DisplayName("Escenario: la auditoría se implementa con aspecto y sin triggers de base de datos")
  void auditoriaSeImplementaConAspectoSinTriggers() {
    // Dado un administrador autenticado
    autenticar(ADMINISTRADOR_ID);

    // Cuando ejecuta una acción auditable
    puenteService.registrar(
        solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);

    // Entonces existe el registro y la tabla auditoria no tiene triggers propios
    assertEquals(1, auditoriaRepository.count());
    Integer triggers =
        jdbc.queryForObject(
            """
                SELECT count(*)
                FROM pg_trigger
                WHERE tgrelid = 'auditoria'::regclass AND NOT tgisinternal
            """,
            Integer.class);
    assertEquals(0, triggers);
  }

  @Test
  @DisplayName("Escenario: la consulta rechaza paginación y acción inválidas")
  void rechazaConsultaInvalida() {
    // Dado el servicio de consulta
    // Cuando se pagina fuera de rango
    var excepcionPagina =
        assertThrows(
            ApiException.class,
            () -> auditoriaConsulta.consultar(null, null, null, null, null, -1, 10));

    // Entonces responde con el problema de paginación
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, excepcionPagina.getStatus());
    assertEquals("paginacion_invalida", excepcionPagina.getCode());

    // Cuando se indica una acción desconocida
    var excepcionAccion =
        assertThrows(
            ApiException.class,
            () -> auditoriaConsulta.consultar(null, null, "INVALIDA", null, null, 0, 10));

    // Entonces responde con el problema de acción
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, excepcionAccion.getStatus());
    assertEquals("accion_invalida", excepcionAccion.getCode());
  }

  private void autenticar(UUID usuarioId) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "admin.hu006@ejemplo.com",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));

    authentication.setDetails(
        new JwtData("admin.hu006@ejemplo.com", usuarioId, RoleName.ADMINISTRADOR, 0));

    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
  }

  private CrearPuenteRequest solicitud(
      UUID departamento, String latitud, String longitud, boolean confirmarCercania) {

    return new CrearPuenteRequest(
        "Puente de prueba HU006",
        departamento,
        municipioId,
        "CA-9",
        null,
        new BigDecimal(latitud),
        new BigDecimal(longitud),
        confirmarCercania);
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
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(4);
    }

    @Bean
    EditarPuenteFixture editarPuenteFixture(JdbcTemplate jdbc) {
      return new EditarPuenteFixture(jdbc);
    }
  }

  record RegistroEditado(UUID id, String nombre) {}

  static class EditarPuenteFixture {

    private final JdbcTemplate jdbc;

    EditarPuenteFixture(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Transactional
    @Auditable(
        accion = AccionAuditoria.MODIFICAR,
        entidad = "puente",
        tipo = Puente.class,
        idArg = "puenteId")
    public RegistroEditado editarNombre(UUID puenteId, String nuevoNombre) {
      int actualizados =
          jdbc.update("UPDATE puente SET nombre = ? WHERE id = ?", nuevoNombre, puenteId);
      if (actualizados != 1) {
        throw new IllegalStateException("puente inexistente");
      }
      return new RegistroEditado(puenteId, nuevoNombre);
    }
  }
}
