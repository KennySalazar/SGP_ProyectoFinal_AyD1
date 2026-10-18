package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.aspect.AuditoriaAspect;
import gt.usac.cunoc.sgp.common.audit.mapper.AuditoriaMapperImpl;
import gt.usac.cunoc.sgp.common.audit.repository.AuditoriaRepository;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaConsultaService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.dto.CrearVersionRequest;
import gt.usac.cunoc.sgp.formulario.dto.DeclaracionMapeoRequest;
import gt.usac.cunoc.sgp.formulario.dto.PublicarVersionRequest;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResponse;
import gt.usac.cunoc.sgp.formulario.mapper.VersionFormularioMapperImpl;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.TipoMapeoCampo;
import gt.usac.cunoc.sgp.formulario.repository.ConfiguracionIcElementoRepository;
import gt.usac.cunoc.sgp.formulario.repository.MapeoCampoFormularioRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.service.VersionFormularioAuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.formulario.service.VersionFormularioService;
import gt.usac.cunoc.sgp.formulario.validation.CampoSchemaExtractor;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
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
  AuditoriaAspect.class,
  AuditoriaWriter.class,
  AuditoriaSnapshotService.class,
  AuditoriaConsultaService.class,
  AuditoriaMapperImpl.class,
  VersionFormularioAuditoriaSnapshotProvider.class,
  VersionFormularioService.class,
  VersionFormularioMapperImpl.class,
  JsonSchemaValidatorService.class,
  CampoSchemaExtractor.class,
  VersionFormularioIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@WithMockUser(roles = "ADMINISTRADOR")
@Testcontainers
class VersionFormularioIntegrationTest {

  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000001");
  private static final Instant AHORA = Instant.parse("2026-10-13T01:00:00Z");

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_formulario_test")
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

  @Autowired private VersionFormularioService service;
  @Autowired private VersionFormularioRepository versiones;
  @Autowired private MapeoCampoFormularioRepository mapeos;
  @Autowired private ConfiguracionIcElementoRepository configuracionesIc;
  @Autowired private AuditoriaRepository auditoriaRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void preparar() {
    autenticarAdministrador();
    jdbc.update("DELETE FROM mapeo_campo_formulario");
    jdbc.update("DELETE FROM configuracion_ic_elemento");
    jdbc.update("DELETE FROM version_formulario");
    jdbc.update("DELETE FROM auditoria");

    jdbc.update(
        """
            INSERT INTO usuario (
              id, email, password_hash, rol_id,
              verificado, activado, activo, nombre_completo
            )
            SELECT ?, 'admin@cunoc.edu.gt', 'hash-de-prueba', id,
                   true, true, true, 'Administrador de prueba'
            FROM rol
            WHERE nombre = 'ADMINISTRADOR'
            ON CONFLICT (id) DO NOTHING
            """,
        ADMINISTRADOR_ID);
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void cicloDeVidaCompletoVersionesYPublicacionExclusiva() throws Exception {
    VersionFormularioResponse v1 =
        service.cargarPlantillaSiecaInicial("SIECA-v1", ADMINISTRADOR_ID);

    assertThat(v1.codigo()).isEqualTo("SIECA-v1");
    assertThat(v1.estado()).isEqualTo(EstadoVersionFormulario.BORRADOR);
    assertThat(v1.activa()).isFalse();
    assertThat(v1.pesos()).isEmpty();

    ApiException sinPesos =
        assertThrows(ApiException.class, () -> service.publicar(v1.id(), null, ADMINISTRADOR_ID));
    assertThat(sinPesos.getCode()).isEqualTo("pesos_incompletos");

    configurarPesosDePrueba(v1.id(), BigDecimal.ONE);
    ApiException sinEscala =
        assertThrows(ApiException.class, () -> service.publicar(v1.id(), null, ADMINISTRADOR_ID));
    assertThat(sinEscala.getCode()).isEqualTo("escala_incompleta");
    service.configurarEscala(v1.id(), escalaDePrueba(4), ADMINISTRADOR_ID);
    configurarReglasDePrueba(v1.id());
    assertThat(service.obtenerPorId(v1.id()).pesos()).hasSize(45);

    VersionFormularioResponse v1Publicada = service.publicar(v1.id(), null, ADMINISTRADOR_ID);

    assertThat(v1Publicada.estado()).isEqualTo(EstadoVersionFormulario.PUBLICADA);
    assertThat(v1Publicada.activa()).isTrue();
    assertThat(v1Publicada.hashContenido()).isNotBlank();

    ApiException errorInmutable =
        assertThrows(
            ApiException.class,
            () ->
                service.actualizarBorrador(
                    v1.id(),
                    objectMapper.readTree("{\"type\":\"object\"}"),
                    null,
                    null,
                    "v1-mod",
                    ADMINISTRADOR_ID));
    assertThat(errorInmutable.getStatus()).isEqualTo(HttpStatus.CONFLICT);

    JsonNode esquemaV2 =
        objectMapper.readTree(
            """
                    {
                      "$schema": "https://json-schema.org/draft/2020-12/schema",
                      "type": "object",
                      "properties": {
                        "datos_generales": {
                          "type": "object",
                          "properties": {
                            "nombre": { "type": "string" }
                          }
                        }
                      }
                    }
                    """);

    CrearVersionRequest crearV2Req =
        new CrearVersionRequest("SIECA-v2", v1.id(), false, esquemaV2, null, null);
    VersionFormularioResponse v2 = service.crear(crearV2Req, ADMINISTRADOR_ID);

    assertThat(v2.codigo()).isEqualTo("SIECA-v2");
    assertThat(v2.estado()).isEqualTo(EstadoVersionFormulario.BORRADOR);
    assertThat(v2.versionAnteriorId()).isEqualTo(v1.id());
    assertThat(v2.pesos()).hasSize(45);

    configurarPesosDePrueba(v2.id(), BigDecimal.valueOf(2));
    service.configurarEscala(v2.id(), escalaDePrueba(3), ADMINISTRADOR_ID);

    assertThat(configuracionesIc.findAllByVersionFormularioId(v1.id()))
        .allSatisfy(peso -> assertThat(peso.getPeso()).isEqualByComparingTo("1"));
    assertThat(configuracionesIc.findAllByVersionFormularioId(v2.id()))
        .hasSize(45)
        .allSatisfy(peso -> assertThat(peso.getPeso()).isEqualByComparingTo("2"));
    assertThat(
            versiones
                .findById(v1.id())
                .orElseThrow()
                .getReglasEvaluacionIc()
                .at("/reglas_evaluacion_ic/escala_severidad/SEVERO")
                .asInt())
        .isEqualTo(4);

    ApiException errorMapeo =
        assertThrows(ApiException.class, () -> service.publicar(v2.id(), null, ADMINISTRADOR_ID));
    assertThat(errorMapeo.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(errorMapeo.getCode()).isEqualTo("mapeo_incompleto");

    var analisis = service.analizarMapeo(v2.id());
    List<DeclaracionMapeoRequest> declaraciones =
        analisis.camposOrigen().stream()
            .map(
                campo ->
                    new DeclaracionMapeoRequest(
                        campo, null, TipoMapeoCampo.ELIMINADO, "Eliminado en v2"))
            .toList();

    PublicarVersionRequest pubReq = new PublicarVersionRequest(declaraciones);
    VersionFormularioResponse v2Publicada = service.publicar(v2.id(), pubReq, ADMINISTRADOR_ID);

    assertThat(v2Publicada.estado()).isEqualTo(EstadoVersionFormulario.PUBLICADA);
    assertThat(v2Publicada.activa()).isTrue();

    var v1EnDb = versiones.findById(v1.id()).orElseThrow();
    assertThat(v1EnDb.isActiva()).isFalse();

    var activaDb = versiones.findByActivaTrue().orElseThrow();
    assertThat(activaDb.getId()).isEqualTo(v2.id());

    var activoResp = service.obtenerActiva();
    assertThat(activoResp.codigo()).isEqualTo("SIECA-v2");
  }

  private void configurarReglasDePrueba(UUID versionId) throws Exception {
    VersionFormularioResponse version = service.obtenerPorId(versionId);

    com.fasterxml.jackson.databind.node.ObjectNode configuracion =
        versiones.findById(versionId).orElseThrow().getReglasEvaluacionIc().deepCopy();

    com.fasterxml.jackson.databind.node.ObjectNode reglas =
        (com.fasterxml.jackson.databind.node.ObjectNode) configuracion.get("reglas_evaluacion_ic");

    reglas.set(
        "agregacion",
        objectMapper.readTree(
            """
                    {
                      "indicadores_de_un_elemento": "MAXIMO",
                      "instancias_repetibles": "MAXIMO",
                      "elementos_ausentes_portantes": 4,
                      "elementos_ausentes_no_portantes": 4
                    }
                    """));

    reglas.set(
        "rangos_estado",
        objectMapper.readTree(
            """
                    {
                      "BUENO": 75,
                      "REGULAR": 50,
                      "MALO": 0
                    }
                    """));

    reglas.set(
        "conversion_indicadores",
        objectMapper.readTree(
            """
                    {
                      "booleanos": {
                        "junta.falta_elemento=true": 4,
                        "junta.falta_elemento=false": 0,
                        "junta.junta_limpia=true": 0,
                        "junta.junta_limpia=false": 4,
                        "losa.buen_estado=true": 0,
                        "losa.buen_estado=false": 4,
                        "senal.existe=true": 0,
                        "senal.existe=false": 4,
                        "drenaje_aledano.existe=true": 0,
                        "drenaje_aledano.existe=false": 4
                      },
                      "enumerados": {
                        "drenaje.estado": {
                          "LIMPIOS": 0,
                          "OBSTRUIDOS": 4
                        },
                        "socavacion.nivel": {
                          "NO_HAY": 0,
                          "SIN_EXPOSICION": 1,
                          "CON_EXPOSICION": 2,
                          "ASENTAMIENTO_ESTRUCTURA": 4
                        },
                        "condicionesCauce": {
                          "LIMPIO": 0,
                          "ASOLVADO_SEDIMENTADO": 1,
                          "EROSIONADO": 2,
                          "OTRO": 4
                        },
                        "estado_bueno_malo": {
                          "BUENO": 0,
                          "MALO": 4
                        }
                      },
                      "numericos": {
                        "golpeado_longitud_m": {
                          "umbrales_m": {
                            "LEVE": 1,
                            "MODERADO": 2,
                            "SEVERO": 3
                          }
                        },
                        "golpeados_cantidad": {
                          "umbrales": {
                            "LEVE": 1,
                            "MODERADO": 2,
                            "SEVERO": 3
                          }
                        }
                      }
                    }
                    """));

    service.actualizarBorrador(
        versionId, version.esquema(), null, configuracion, null, ADMINISTRADOR_ID);
  }

  private void configurarPesosDePrueba(UUID versionId, BigDecimal peso) {
    JsonNode catalogo = versiones.findById(versionId).orElseThrow().getReglasEvaluacionIc();
    List<gt.usac.cunoc.sgp.formulario.dto.ConfiguracionIcElementoRequest> configuraciones =
        new ArrayList<>();
    for (JsonNode elemento : catalogo.get("elementos")) {
      configuraciones.add(
          new gt.usac.cunoc.sgp.formulario.dto.ConfiguracionIcElementoRequest(
              elemento.get("elemento_ref").asText(),
              peso,
              elemento.path("nombre").asText(elemento.get("elemento_ref").asText())));
    }
    service.calibrarPesos(versionId, configuraciones, ADMINISTRADOR_ID);
  }

  private gt.usac.cunoc.sgp.formulario.dto.ConfigurarEscalaIcRequest escalaDePrueba(int severo) {
    return new gt.usac.cunoc.sgp.formulario.dto.ConfigurarEscalaIcRequest(
        java.util.Map.of(
            "NO_PRESENTA",
            BigDecimal.ZERO,
            "LEVE",
            BigDecimal.ONE,
            "MODERADO",
            BigDecimal.valueOf(2),
            "SEVERO",
            BigDecimal.valueOf(severo)));
  }

  private void autenticarAdministrador() {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "admin@cunoc.edu.gt", null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));

    authentication.setDetails(
        new JwtData("admin@cunoc.edu.gt", ADMINISTRADOR_ID, RoleName.ADMINISTRADOR, 0));

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @TestConfiguration
  @EnableMethodSecurity
  @EnableAspectJAutoProxy
  static class Config {

    @Bean
    Clock clock() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
      return mapper;
    }
  }
}
