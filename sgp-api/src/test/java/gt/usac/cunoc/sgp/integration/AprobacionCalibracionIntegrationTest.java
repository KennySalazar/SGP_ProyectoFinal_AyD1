package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.dto.AprobacionCalibracionResponse;
import gt.usac.cunoc.sgp.formulario.mapper.VersionFormularioMapperImpl;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.service.AprobacionCalibracionService;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
  AprobacionCalibracionService.class,
  AuditoriaWriter.class,
  NotificacionService.class,
  VersionFormularioMapperImpl.class,
  AprobacionCalibracionIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class AprobacionCalibracionIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu054")
          .withUsername("sgp")
          .withPassword("sgp");

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", postgres::getJdbcUrl);
    registro.add("spring.datasource.username", postgres::getUsername);
    registro.add("spring.datasource.password", postgres::getPassword);
    registro.add("spring.flyway.url", postgres::getJdbcUrl);
    registro.add("spring.flyway.user", postgres::getUsername);
    registro.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private AprobacionCalibracionService servicio;
  @Autowired private VersionFormularioRepository versiones;

  @Autowired
  private gt.usac.cunoc.sgp.formulario.repository.AprobacionCalibracionRepository aprobaciones;

  @Autowired private VersionFormularioMapperImpl mapper;
  @Autowired private PlatformTransactionManager transacciones;
  private UUID version;
  private UUID catedratico;
  private UUID otro;
  private UUID admin;

  @BeforeEach
  void preparar() {
    jdbc.execute("TRUNCATE version_formulario, usuario CASCADE");
    catedratico = usuario("CATEDRATICO", "Kenny");
    otro = usuario("CATEDRATICO", "Mario");
    admin = usuario("ADMINISTRADOR", "Admin");
    version = version("PUBLICADA", true, null);
    autenticar("CATEDRATICO");
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void migracionAplicadaYUnaAprobacionSinPdfNoModificaLaVersion() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '22' AND success",
                Integer.class))
        .isEqualTo(1);
    var antes = jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", version);
    assertThat(servicio.estado(version).estado()).isEqualTo("SIN_APROBAR");
    var respuesta = servicio.aprobar(version, catedratico, "  Respaldo academico  ");
    assertThat(respuesta.aprobadoPorId()).isEqualTo(catedratico);
    assertThat(respuesta.observaciones()).isEqualTo("Respaldo academico");
    assertThat(servicio.estado(version).rotuloCalibracion())
        .isEqualTo("Aprobada por Kenny el 22/10/2026");
    assertThat(jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", version))
        .isEqualTo(antes);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM archivo_acta_calibracion", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE usuario_id = ? AND entidad = 'aprobacion_calibracion_ic' AND valores_posteriores->>'versionFormularioId' = ?",
                Integer.class,
                catedratico,
                version.toString()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notificacion WHERE usuario_id = ? AND entidad_id = ? AND tipo = 'CALIBRACION_APROBADA'",
                Integer.class,
                admin,
                version))
        .isEqualTo(1);
    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              var entidad = versiones.findById(version).orElseThrow();
              var detalle =
                  mapper.toResponse(
                      entidad,
                      List.of(),
                      List.of(),
                      AprobacionCalibracionResponse.de(
                          aprobaciones.findByVersionFormularioId(version).orElseThrow()));
              assertThat(detalle.aprobacionCalibracion().aprobadoPorId()).isEqualTo(catedratico);
              assertThat(detalle.rotuloCalibracion()).isEqualTo("Aprobada por Kenny el 22/10/2026");
            });
  }

  @Test
  void completaNombreVisibleDeAprobacionAnteriorSinAlterarRegistroNiVersion() {
    jdbc.update("UPDATE usuario SET nombre_completo = NULL WHERE id = ?", catedratico);
    var original = servicio.aprobar(version, catedratico, "Respaldo original");
    assertThat(original.aprobadoPorNombre()).contains("@");
    var registro =
        jdbc.queryForMap("SELECT * FROM aprobacion_calibracion_ic WHERE id = ?", original.id());
    var formulario = jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", version);
    var bitacora = jdbc.queryForList("SELECT * FROM auditoria WHERE entidad_id = ?", original.id());
    jdbc.update(
        "UPDATE usuario SET nombre_completo = '  Kenny Salazar  ' WHERE id = ?", catedratico);

    var consulta = servicio.consultar(version);
    assertThat(consulta.aprobadoPorNombre()).isEqualTo("Kenny Salazar");
    assertThat(consulta.rotulo()).isEqualTo("Aprobada por Kenny Salazar el 22/10/2026");
    assertThat(consulta.id()).isEqualTo(original.id());
    assertThat(consulta.aprobadoPorId()).isEqualTo(original.aprobadoPorId());
    assertThat(consulta.aprobadoEn()).isEqualTo(original.aprobadoEn());
    assertThat(consulta.observaciones()).isEqualTo(original.observaciones());
    assertThat(servicio.estado(version).aprobadoPorNombre()).isEqualTo("Kenny Salazar");
    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              var detalle =
                  mapper.toResponse(
                      versiones.findById(version).orElseThrow(),
                      List.of(),
                      List.of(),
                      AprobacionCalibracionResponse.de(
                          aprobaciones.findByVersionFormularioId(version).orElseThrow()));
              assertThat(detalle.aprobacionCalibracion().aprobadoPorNombre())
                  .isEqualTo("Kenny Salazar");
              assertThat(detalle.rotuloCalibracion()).isEqualTo(consulta.rotulo());
            });
    assertThat(
            jdbc.queryForMap("SELECT * FROM aprobacion_calibracion_ic WHERE id = ?", original.id()))
        .isEqualTo(registro);
    assertThat(jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", version))
        .isEqualTo(formulario);
    assertThat(jdbc.queryForList("SELECT * FROM auditoria WHERE entidad_id = ?", original.id()))
        .isEqualTo(bitacora);
  }

  @Test
  void conservaNombreCapturadoAunqueCambieElPerfil() {
    servicio.aprobar(version, catedratico, null);
    jdbc.update("UPDATE usuario SET nombre_completo = 'Nombre nuevo' WHERE id = ?", catedratico);
    assertThat(servicio.consultar(version).aprobadoPorNombre()).isEqualTo("Kenny");
  }

  @Test
  void conservaCorreoSiElPerfilHistoricoAunNoTieneNombre() {
    jdbc.update("UPDATE usuario SET nombre_completo = NULL WHERE id = ?", catedratico);
    var original = servicio.aprobar(version, catedratico, null);
    assertThat(servicio.consultar(version).aprobadoPorNombre())
        .isEqualTo(original.aprobadoPorNombre());
    jdbc.update("UPDATE usuario SET nombre_completo = '   ' WHERE id = ?", catedratico);
    assertThat(servicio.estado(version).aprobadoPorNombre())
        .isEqualTo(original.aprobadoPorNombre());
  }

  @Test
  void rotuloSinAprobarCoincideEnVersionYEstadoAunqueNoExistaMarcaProvisional() {
    for (String reglas :
        List.of(
            "{}",
            "{\"calibracion\":{\"provisional\":false}}",
            "{\"calibracion\":{\"provisional\":true}}")) {
      jdbc.update(
          "UPDATE version_formulario SET reglas_evaluacion_ic = ?::jsonb WHERE id = ?",
          reglas,
          version);
      new TransactionTemplate(transacciones)
          .executeWithoutResult(
              estado -> {
                var detalle =
                    mapper.toResponse(
                        versiones.findById(version).orElseThrow(), List.of(), List.of());
                assertThat(detalle.rotuloCalibracion())
                    .isEqualTo("Calibración provisional · sin aprobar");
                assertThat(detalle.rotuloCalibracion())
                    .isEqualTo(servicio.estado(version).rotuloCalibracion());
              });
    }
  }

  @Test
  void rechazaBorradorSinRegistrarEfectos() {
    UUID borrador = version("BORRADOR", false, null);
    assertThatThrownBy(() -> servicio.aprobar(borrador, catedratico, null))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("calibracion_version_no_publicada");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM aprobacion_calibracion_ic", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Integer.class)).isZero();
  }

  @Test
  void noSobrescribeAprobacionOriginal() {
    var primera = servicio.aprobar(version, catedratico, "Primera");
    assertThatThrownBy(() -> servicio.aprobar(version, otro, "Segunda"))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("calibracion_ya_aprobada");
    assertThat(servicio.consultar(version)).isEqualTo(primera);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'aprobacion_calibracion_ic'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void restriccionUnicaFuncionaEnPostgresqlReal() {
    servicio.aprobar(version, catedratico, null);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO aprobacion_calibracion_ic (id, version_formulario_id, aprobado_por_id, aprobado_por_nombre, aprobado_en) VALUES (?, ?, ?, 'Otro', CURRENT_TIMESTAMP)",
                    UUID.randomUUID(),
                    version,
                    otro))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM aprobacion_calibracion_ic", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void dosCatedraticosSimultaneosSoloRegistranUnaAprobacion() throws Exception {
    var ejecutor = Executors.newFixedThreadPool(2);
    var listos = new CountDownLatch(2);
    var inicio = new CountDownLatch(1);
    try {
      List<Future<String>> resultados = new ArrayList<>();
      for (UUID autor : List.of(catedratico, otro)) {
        resultados.add(
            ejecutor.submit(
                () -> {
                  autenticar("CATEDRATICO");
                  listos.countDown();
                  try {
                    if (!inicio.await(10, TimeUnit.SECONDS))
                      throw new IllegalStateException("Inicio no recibido");
                    servicio.aprobar(version, autor, null);
                    return "APROBADA";
                  } catch (ApiException error) {
                    return error.getCode();
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      }
      assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
      inicio.countDown();
      assertThat(
              List.of(
                  resultados.get(0).get(30, TimeUnit.SECONDS),
                  resultados.get(1).get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("APROBADA", "calibracion_ya_aprobada");
      assertThat(
              jdbc.queryForObject("SELECT count(*) FROM aprobacion_calibracion_ic", Integer.class))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM notificacion WHERE usuario_id = ?", Integer.class, admin))
          .isEqualTo(1);
    } finally {
      inicio.countDown();
      ejecutor.shutdownNow();
    }
  }

  @Test
  void nuevaVersionNoHeredaAprobacionYPublicadaInactivaSePuedeAprobar() {
    servicio.aprobar(version, catedratico, null);
    UUID nueva = version("BORRADOR", false, version);
    assertThat(servicio.consultar(nueva)).isNull();
    UUID publicada = version("PUBLICADA", false, version);
    assertThat(servicio.estado(publicada).estado()).isEqualTo("SIN_APROBAR");
    assertThat(servicio.aprobar(publicada, otro, null).aprobadoPorId()).isEqualTo(otro);
  }

  @Test
  void administradorNoPuedeAprobarInclusoInvocandoElServicio() {
    autenticar("ADMINISTRADOR");
    assertThatThrownBy(() -> servicio.aprobar(version, admin, null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void falloDelAvisoRevierteAprobacionYAuditoria() {
    jdbc.execute(
        "ALTER TABLE notificacion ADD CONSTRAINT fallo_hu054 CHECK (tipo <> 'CALIBRACION_APROBADA')");
    try {
      assertThatThrownBy(() -> servicio.aprobar(version, catedratico, null))
          .isInstanceOf(RuntimeException.class);
      assertThat(
              jdbc.queryForObject("SELECT count(*) FROM aprobacion_calibracion_ic", Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM auditoria WHERE entidad = 'aprobacion_calibracion_ic'",
                  Integer.class))
          .isZero();
    } finally {
      jdbc.execute("ALTER TABLE notificacion DROP CONSTRAINT fallo_hu054");
    }
  }

  private UUID usuario(String rol, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario (id,email,password_hash,rol_id,verificado,activado,nombre_completo) SELECT ?, ?, 'hash', id, true, true, ? FROM rol WHERE nombre = ?",
        id,
        id + "@ejemplo.com",
        nombre,
        rol);
    return id;
  }

  private UUID version(String estado, boolean activa, UUID anterior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO version_formulario (id,codigo,estado,activa,esquema,reglas_evaluacion_ic,hash_contenido,version_algoritmo_ic,creado_por_id,publicado_por_id,publicado_en,version_anterior_id) VALUES (?, ?, ?, ?, '{}'::jsonb, '{\"calibracion\":{\"provisional\":true}}'::jsonb, ?, 'ic-v1', ?, ?, CURRENT_TIMESTAMP, ?)",
        id,
        "hu054-" + id,
        estado,
        activa,
        "a".repeat(64),
        catedratico,
        catedratico,
        anterior);
    return id;
  }

  private void autenticar(String rol) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-23T02:30:00Z"), ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }
  }
}
