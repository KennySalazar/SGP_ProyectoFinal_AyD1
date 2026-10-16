package gt.usac.cunoc.sgp.inspeccion.bdd;

import gt.usac.cunoc.sgp.archivo.mapper.FotografiaMapperImpl;
import gt.usac.cunoc.sgp.archivo.service.*;
import gt.usac.cunoc.sgp.common.config.JwtProperties;
import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.*;
import gt.usac.cunoc.sgp.inspeccion.config.EvidenciaFotograficaProperties;
import gt.usac.cunoc.sgp.inspeccion.controller.*;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.service.*;
import gt.usac.cunoc.sgp.inspeccion.validation.ValidadorAnclajeFotografico;
import gt.usac.cunoc.sgp.sync.service.OperacionIdempotenteService;
import io.cucumber.spring.CucumberContextConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@CucumberContextConfiguration
@SpringBootTest(
    classes = Hu029CucumberContext.App.class,
    properties = {
      "security.jwt.secret-key=hu029-gherkin-clave-exclusiva-de-prueba-2026",
      "app.cors.allowed-origins=http://localhost:4200",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.show-sql=false",
      "logging.level.root=WARN"
    })
@AutoConfigureMockMvc
public class Hu029CucumberContext {
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
          DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

  @DynamicPropertySource
  static void baseAislada(DynamicPropertyRegistry propiedades) {
    POSTGRES.start();
    propiedades.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    propiedades.add("spring.datasource.username", POSTGRES::getUsername);
    propiedades.add("spring.datasource.password", POSTGRES::getPassword);
    propiedades.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    propiedades.add("spring.flyway.user", POSTGRES::getUsername);
    propiedades.add("spring.flyway.password", POSTGRES::getPassword);
  }

  @Configuration(proxyBeanMethods = false)
  @TestComponent
  @EnableAutoConfiguration(exclude = MailSenderAutoConfiguration.class)
  @EntityScan("gt.usac.cunoc.sgp")
  @EnableJpaRepositories("gt.usac.cunoc.sgp")
  @EnableConfigurationProperties(JwtProperties.class)
  @Import({
    FotografiaController.class,
    EvidenciaFotograficaController.class,
    RetiradaFotografiaController.class,
    ApiExceptionHandler.class,
    SecurityConfiguration.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    AuthRateLimitFilter.class,
    ProblemAuthenticationEntryPoint.class,
    ProblemAccessDeniedHandler.class,
    EvidenciaFotograficaProperties.class,
    EvidenciaFotograficaService.class,
    RetiradaFotografiaService.class,
    PoliticaArchivosInspeccion.class,
    AccesoFotografiasInspeccionService.class,
    ReglasEvidenciaFotografica.class,
    FotografiaService.class,
    RegistroFotografiasInspeccionJpa.class,
    FotografiaMapperImpl.class,
    ValidadorAnclajeFotografico.class,
    ProcesadorImagenes.class,
    InspectorImagenesImageIo.class,
    CargasImagenService.class,
    ObjetosImagenService.class,
    OperacionIdempotenteService.class
  })
  static class App {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    AlmacenamientoArchivos almacenamiento() throws IOException {
      return new AlmacenamientoLocal(Files.createTempDirectory("hu029-gherkin-"));
    }

    @Bean
    TransicionDePrueba transicionDePrueba(
        EvidenciaFotograficaService evidencia,
        InspeccionRepository inspecciones,
        JdbcTemplate jdbc) {
      return new TransicionDePrueba(evidencia, inspecciones, jdbc);
    }
  }

  /** Llamador exclusivamente de prueba: comprueba el contrato, no implementa HU-026. */
  @RestController
  @TestComponent
  @RequestMapping("/prueba/inspecciones")
  static class TransicionDePrueba {
    private final EvidenciaFotograficaService evidencia;
    private final InspeccionRepository inspecciones;
    private final JdbcTemplate jdbc;

    TransicionDePrueba(
        EvidenciaFotograficaService evidencia,
        InspeccionRepository inspecciones,
        JdbcTemplate jdbc) {
      this.evidencia = evidencia;
      this.inspecciones = inspecciones;
      this.jdbc = jdbc;
    }

    @PostMapping("/{id}/validar-transicion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void validar(@PathVariable UUID id) {
      inspecciones.findByIdAndEliminadoEnIsNullForUpdate(id).orElseThrow();
      jdbc.update(
          "update inspeccion set ultima_actividad_en=ultima_actividad_en + interval '1 second' where id=?",
          id);
      evidencia.exigirParaTransicion(id);
    }
  }
}
