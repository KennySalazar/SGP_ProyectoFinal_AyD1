package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.SgpApplication;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.config.AdminBootstrap;
import gt.usac.cunoc.sgp.common.config.BootstrapProperties;
import gt.usac.cunoc.sgp.formulario.mapper.VersionFormularioMapperImpl;
import gt.usac.cunoc.sgp.formulario.service.SemillaFormularioInitializer;
import gt.usac.cunoc.sgp.formulario.service.SemillaFormularioService;
import gt.usac.cunoc.sgp.formulario.service.VersionFormularioService;
import gt.usac.cunoc.sgp.formulario.validation.CampoSchemaExtractor;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.mantenimiento.mapper.ReglaMantenimientoMapperImpl;
import gt.usac.cunoc.sgp.mantenimiento.service.ReglaMantenimientoService;
import gt.usac.cunoc.sgp.mantenimiento.service.SemillaReglasMantenimientoInitializer;
import gt.usac.cunoc.sgp.mantenimiento.service.ValidadorReglaMantenimiento;
import gt.usac.cunoc.sgp.usuario.service.AdminProvisioningService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
    properties = {"app.formulario.siembra.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  gt.usac.cunoc.sgp.notificacion.service.AvisosAcademicosService.class,
  gt.usac.cunoc.sgp.notificacion.service.NotificacionService.class,
  VersionFormularioService.class,
  VersionFormularioMapperImpl.class,
  JsonSchemaValidatorService.class,
  CampoSchemaExtractor.class,
  SemillaFormularioService.class,
  SemillaFormularioInitializer.class,
  ReglaMantenimientoService.class,
  ReglaMantenimientoMapperImpl.class,
  ValidadorReglaMantenimiento.class,
  SemillaReglasMantenimientoInitializer.class,
  AdminBootstrap.class,
  AdminProvisioningService.class,
  AuditoriaWriter.class,
  SemillaFormularioIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class SemillaFormularioIntegrationTest {
  private static final String EMAIL = "admin.hu051@ejemplo.com";
  private static final String PASSWORD = "Prueba-HU051-Segura9!";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu051_test")
          .withUsername("sgp_test")
          .withPassword("sgp_test");

  @DynamicPropertySource
  static void base(DynamicPropertyRegistry registry) {
    registry.add("app.bootstrap.admin.email", () -> EMAIL);
    registry.add("app.bootstrap.admin.initial-password", () -> PASSWORD);

    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired SemillaFormularioService semilla;
  @Autowired VersionFormularioService formularios;
  @Autowired AdminProvisioningService administradores;
  @Autowired ConfigurableApplicationContext contexto;
  @Autowired ObjectMapper json;

  @BeforeEach
  void preparar() {
    jdbc.execute("TRUNCATE version_formulario CASCADE");
    jdbc.update("DELETE FROM auditoria");
    administradores.provisionAdmin(EMAIL, PASSWORD);
  }

  void arrancar() {
    contexto.publishEvent(
        new ApplicationReadyEvent(
            new SpringApplication(SgpApplication.class), new String[0], contexto, Duration.ZERO));
  }

  UUID administradorId() {
    return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL);
  }

  int contar(String tabla) {
    return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
  }

  UUID activa() {
    return jdbc.queryForObject("SELECT id FROM version_formulario WHERE activa", UUID.class);
  }

  @Test
  void primerYSegundoArranqueConservanExactamenteLaMismaVersionYAuditoria() throws Exception {
    arrancar();
    var id = activa();
    var antes = jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", id);
    var publicada = formularios.obtenerPorId(id);
    assertThat(publicada.codigo()).isEqualTo("SIECA-v1");
    assertThat(publicada.activa()).isTrue();
    assertThat(publicada.pesos()).hasSize(45).allMatch(peso -> peso.peso().signum() > 0);
    assertThat(publicada.rotuloCalibracion()).isEqualTo("Calibracion provisional · sin aprobar");
    var plantilla =
        json.readTree(getClass().getResourceAsStream("/formulario/sieca-v1/elementos-ic.json"));
    assertThat(publicada.reglasEvaluacionIc()).isEqualTo(plantilla);
    assertThat(contar("regla_mantenimiento")).isEqualTo(18);
    var registro =
        jdbc.queryForMap(
            "SELECT * FROM auditoria WHERE proceso_automatico = 'HU-051-siembra-formulario'");
    assertThat(registro.get("usuario_id")).isNull();
    assertThat(registro.get("entidad_id")).isEqualTo(id);
    assertThat(publicada.creadoPorId()).isEqualTo(administradorId());
    arrancar();
    assertThat(activa()).isEqualTo(id);
    assertThat(contar("version_formulario")).isEqualTo(1);
    assertThat(contar("regla_mantenimiento")).isEqualTo(18);
    assertThat(contar("auditoria")).isEqualTo(1);
    assertThat(jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", id))
        .isEqualTo(antes);
  }

  @Test
  void noReemplazaUnaVersionActivaElegidaPorElAdministrador() {
    var borrador =
        formularios.cargarPlantillaSiecaInicial("CONFIGURACION-PROPIA", administradorId());
    formularios.publicar(borrador.id(), null, administradorId());
    var antes = jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", borrador.id());
    assertThat(semilla.sembrarSiNoExisteActiva()).isEmpty();
    assertThat(jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", borrador.id()))
        .isEqualTo(antes);
    assertThat(contar("version_formulario")).isEqualTo(1);
    assertThat(contar("auditoria")).isZero();
  }

  @Test
  void conservaBorradoresYEscogeElSiguienteCodigoLibre() {
    var primero = formularios.cargarPlantillaSiecaInicial("SIECA-v1", administradorId());
    formularios.cargarPlantillaSiecaInicial("SIECA-v2", administradorId());
    var antes = jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", primero.id());
    arrancar();
    assertThat(formularios.obtenerPorId(activa()).codigo()).isEqualTo("SIECA-v3");
    assertThat(jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", primero.id()))
        .isEqualTo(antes);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM version_formulario WHERE estado = 'BORRADOR'", Integer.class))
        .isEqualTo(2);
    assertThat(contar("regla_mantenimiento")).isEqualTo(18);
  }

  @Test
  void conservaUnaPublicadaInactivaConElCodigoInicial() {
    var primera = formularios.cargarPlantillaSiecaInicial("SIECA-v1", administradorId());
    formularios.publicar(primera.id(), null, administradorId());
    jdbc.update("UPDATE version_formulario SET activa = false WHERE id = ?", primera.id());
    var antes = jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", primera.id());
    arrancar();
    assertThat(formularios.obtenerPorId(activa()).codigo()).isEqualTo("SIECA-v2");
    assertThat(jdbc.queryForMap("SELECT * FROM version_formulario WHERE id = ?", primera.id()))
        .isEqualTo(antes);
  }

  @Test
  void dosReplicasArrancanSinDuplicarAdministradorFormularioNiReglas() throws Exception {
    jdbc.update("DELETE FROM usuario WHERE email = ?", EMAIL);
    var inicio = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var primera =
          ejecutor.submit(
              () -> {
                inicio.await();
                arrancar();
                return true;
              });
      var segunda =
          ejecutor.submit(
              () -> {
                inicio.await();
                arrancar();
                return true;
              });
      inicio.countDown();
      assertThat(primera.get(60, TimeUnit.SECONDS)).isTrue();
      assertThat(segunda.get(60, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(contar("version_formulario")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM version_formulario WHERE activa", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE email = ?", Integer.class, EMAIL))
        .isEqualTo(1);
    assertThat(contar("regla_mantenimiento")).isEqualTo(18);
    assertThat(contar("auditoria")).isEqualTo(1);
  }

  @Test
  void falloRevierteTodoYLaCargaManualSigueDisponible() {
    jdbc.execute(
        "ALTER TABLE auditoria ADD CONSTRAINT hu051_fallo CHECK (proceso_automatico IS DISTINCT FROM 'HU-051-siembra-formulario')");
    try {
      assertThatCode(this::arrancar).doesNotThrowAnyException();
      assertThat(contar("version_formulario")).isZero();
      assertThat(contar("configuracion_ic_elemento")).isZero();
      assertThat(contar("regla_mantenimiento")).isZero();
      assertThat(contar("auditoria")).isZero();
    } finally {
      jdbc.execute("ALTER TABLE auditoria DROP CONSTRAINT hu051_fallo");
    }
    var manual = formularios.cargarPlantillaSiecaInicial("SIECA-manual", administradorId());
    assertThat(formularios.publicar(manual.id(), null, administradorId()).activa()).isTrue();
  }

  @Test
  void versionSembradaCalculaIcConSusPesosYReglas() throws Exception {
    arrancar();
    var version = formularios.obtenerPorId(activa());
    var datos =
        json.readTree(
            getClass().getResourceAsStream("/formulario/sieca-v1/ejemplo-inspeccion.json"));
    var agregador = new gt.usac.cunoc.sgp.inspeccion.service.AgregadorCalificacionesIc();
    var resolver = new gt.usac.cunoc.sgp.inspeccion.service.ResolverElementosIc();
    var conversor = new gt.usac.cunoc.sgp.inspeccion.service.ConversorIndicadoresIc(agregador);
    var calculo =
        new gt.usac.cunoc.sgp.inspeccion.service.CalculoIndiceCondicionService(
            resolver,
            conversor,
            new gt.usac.cunoc.sgp.inspeccion.service.ConversorMedicionesIc(conversor),
            agregador,
            new gt.usac.cunoc.sgp.inspeccion.service.EvaluadorCondicionesCriticasIc(resolver),
            new gt.usac.cunoc.sgp.inspeccion.service.HuellaEntradaIc(),
            new gt.usac.cunoc.sgp.inspeccion.service.MotorPonderacionIc(agregador));
    var pesos = new java.util.LinkedHashMap<String, BigDecimal>();
    version.pesos().forEach(peso -> pesos.put(peso.elementoRef(), peso.peso()));
    var resultado =
        calculo.calcular(
            new gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc(
                version.id(),
                version.reglasEvaluacionIc().path("formulario").asText(),
                version.versionAlgoritmoIc(),
                datos,
                version.esquema(),
                version.reglasEvaluacionIc(),
                version.reglasEvaluacionIc().path("reglas_evaluacion_ic"),
                pesos));
    assertThat(resultado.indiceCalculado()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
    assertThat(resultado.estadoCalculado()).isNotNull();
  }

  @TestConfiguration
  static class Config {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
    }

    @Bean
    BootstrapProperties bootstrapProperties() {
      var propiedades = new BootstrapProperties();
      propiedades.setEmail(EMAIL);
      propiedades.setInitialPassword(PASSWORD);
      return propiedades;
    }
  }
}
