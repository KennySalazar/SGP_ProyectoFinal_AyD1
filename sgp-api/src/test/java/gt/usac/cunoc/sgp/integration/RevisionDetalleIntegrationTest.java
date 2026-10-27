package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.service.*;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import gt.usac.cunoc.sgp.revision.service.RevisionDetalleService;
import gt.usac.cunoc.sgp.revision.service.RevisionIndicePreliminarService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  RevisionDetalleService.class,
  RevisionIndicePreliminarService.class,
  PreparadorEntradaIcService.class,
  CalculoIndiceCondicionService.class,
  ResolverElementosIc.class,
  ConversorIndicadoresIc.class,
  ConversorMedicionesIc.class,
  AgregadorCalificacionesIc.class,
  EvaluadorCondicionesCriticasIc.class,
  HuellaEntradaIc.class,
  MotorPonderacionIc.class,
  RevisionDetalleIntegrationTest.Config.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RevisionDetalleIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.url", postgres::getJdbcUrl);
    r.add("spring.flyway.user", postgres::getUsername);
    r.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired RevisionDetalleService servicio;
  @SpyBean CalculoIndiceCondicionService calculador;
  @Autowired RevisionRepository revisiones;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  private UUID revisor, autor, puente, inspeccion, ciclo, version;
  private JsonNode esquema;
  private ObjectNode snapshot;
  private static final Instant ENVIADA = Instant.parse("2026-10-17T12:00:00Z");
  private static final String REF = "/subestructura/estribo_entrada/cortina_superior";

  @BeforeEach
  void preparar() throws Exception {
    jdbc.execute("TRUNCATE version_formulario CASCADE");
    revisor = usuario("CATEDRATICO");
    autor = usuario("ESTUDIANTE");
    puente = UUID.randomUUID();
    inspeccion = UUID.randomUUID();
    ciclo = UUID.randomUUID();
    version = UUID.randomUUID();
    try (var in = getClass().getResourceAsStream("/formulario/sieca-v1/esquema.schema.json")) {
      esquema = json.readTree(in);
    }
    JsonNode datos;
    try (var in = getClass().getResourceAsStream("/formulario/sieca-v1/ejemplo-inspeccion.json")) {
      datos = json.readTree(in);
    }
    String catalogo =
        """
            {"formulario":"sieca-v1","elementos":[{"elemento_ref":"/subestructura/estribo_entrada/cortina_superior","repetible":false,"portante":false,"indicadores":["grietas_una_direccion"]}],
             "reglas_evaluacion_ic":{"version_algoritmo":"hu037-prueba","escala_severidad":{"NO_PRESENTA":0,"LEVE":1,"MODERADO":2,"SEVERO":4},
             "agregacion":{"indicadores_de_un_elemento":"MAXIMO","instancias_repetibles":"MAXIMO","elementos_ausentes_no_portantes":4,"elementos_ausentes_portantes":4},
             "rangos_estado":{"BUENO":75,"REGULAR":50,"MALO":0},"conversion_indicadores":{"booleanos":{},"enumerados":{},"numericos":{}},"reglas_anulacion":[]}}
            """;
    jdbc.update(
        "INSERT INTO version_formulario(id,codigo,estado,activa,esquema,reglas_evaluacion_ic,hash_contenido,version_algoritmo_ic,creado_por_id,publicado_por_id,publicado_en) VALUES (?,?,'PUBLICADA',false,?::jsonb,?::jsonb,?,'hu037-prueba',?,?,CURRENT_TIMESTAMP)",
        version,
        "Historica-" + version,
        esquema.toString(),
        catalogo,
        "a".repeat(64),
        revisor,
        revisor);
    UUID activa = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO version_formulario(id,codigo,estado,activa,esquema,hash_contenido,version_algoritmo_ic,creado_por_id,publicado_por_id,publicado_en) VALUES (?,?,'PUBLICADA',true,'{\"properties\":{\"nuevo\":{\"type\":\"string\"}}}',?,'otra',?,?,CURRENT_TIMESTAMP)",
        activa,
        "Activa-" + activa,
        "b".repeat(64),
        revisor,
        revisor);
    jdbc.update(
        "INSERT INTO configuracion_ic_elemento(id,version_formulario_id,elemento_ref,peso,descripcion) VALUES (?,?,?,1,'Peso de prueba')",
        UUID.randomUUID(),
        version,
        REF);
    jdbc.update(
        "INSERT INTO puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id) SELECT ?,?,1,id,'Nombre actual','test',ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,? FROM municipio WHERE codigo_ine='0114'",
        puente,
        "T-" + puente,
        revisor);
    ObjectNode vivos = datos.deepCopy();
    ((ObjectNode) vivos.at(REF)).put("grietas_una_direccion", "SEVERO");
    jdbc.update(
        "INSERT INTO inspeccion(id,puente_id,version_formulario_id,autor_id,rol_autor_creacion,nombre_autor_capturado,fecha_inspeccion,dispositivo_id,estado,datos,identificacion_capturada) VALUES (?,?,?,?,'ESTUDIANTE','test',CURRENT_DATE,'test','EN_REVISION',?::jsonb,'{\"nombre_puente\":\"Actual\"}')",
        inspeccion,
        puente,
        activa,
        autor,
        vivos.toString());
    snapshot =
        json.createObjectNode()
            .put("inspeccionId", inspeccion.toString())
            .put("versionFormularioId", version.toString())
            .put("autorId", autor.toString())
            .put("puenteId", puente.toString())
            .put("fechaInspeccion", "2026-10-16");
    snapshot.set("datos", datos);
    snapshot
        .putObject("identificacionCapturada")
        .put("nombre_puente", "Nombre capturado al enviar");
    jdbc.update(
        "INSERT INTO revision(id,inspeccion_id,numero_ciclo,revisor_id,estado,enviada_en,version_inspeccion_enviada,contenido_enviado,hash_contenido) VALUES (?,?,1,?,'EN_CURSO',?,0,?::jsonb,?)",
        ciclo,
        inspeccion,
        revisor,
        java.sql.Timestamp.from(ENVIADA),
        snapshot.toString(),
        "c".repeat(64));
    var auth =
        new UsernamePasswordAuthenticationToken(
            "test", null, List.of(new SimpleGrantedAuthority("ROLE_CATEDRATICO")));
    auth.setDetails(new JwtData("test", revisor, RoleName.CATEDRATICO, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private UUID usuario(String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario(id,email,password_hash,rol_id) SELECT ?,?,'test',id FROM rol WHERE nombre=?",
        id,
        id + "@hu037.test",
        rol);
    return id;
  }

  @AfterEach
  void cerrarSesion() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void devuelveSnapshotReconstruibleDeOchoSeccionesEIndiceHistorico() {
    var respuesta = servicio.obtener(ciclo, revisor);
    assertThat(respuesta.revisionId()).isEqualTo(ciclo);
    assertThat(respuesta.inspeccionId()).isEqualTo(inspeccion);
    assertThat(respuesta.revisorId()).isEqualTo(revisor);
    assertThat(respuesta.autorId()).isEqualTo(autor);
    assertThat(respuesta.puenteId()).isEqualTo(puente);
    assertThat(respuesta.enviadaEn()).isEqualTo(ENVIADA);
    assertThat(respuesta.fechaInspeccion()).isEqualTo(LocalDate.of(2026, 10, 16));
    assertThat(respuesta.numeroCiclo()).isEqualTo(1);
    assertThat(respuesta.estadoRevision()).isEqualTo("EN_CURSO");
    assertThat(respuesta.datos()).isEqualTo(snapshot.path("datos"));
    assertThat(respuesta.identificacionCapturada())
        .isEqualTo(snapshot.path("identificacionCapturada"));
    assertThat(respuesta.versionFormularioId()).isEqualTo(version);
    assertThat(respuesta.versionFormularioCodigo()).startsWith("Historica-");
    assertThat(respuesta.esquemaFormulario()).isEqualTo(esquema);
    assertThat(respuesta.esquemaFormulario().path("properties").size()).isEqualTo(8);
    assertThat(
            new JsonSchemaValidatorService()
                .obtenerErroresValidacion(respuesta.esquemaFormulario(), respuesta.datos(), false))
        .isEmpty();
    assertThat(respuesta.icPreliminar().indice()).isEqualByComparingTo(new BigDecimal("75"));
    assertThat(respuesta.icPreliminar().estado()).isEqualTo("BUENO");
    assertThat(respuesta.icPreliminar().etiqueta()).isEqualTo("PRELIMINAR");
    assertThat(respuesta.fotografiasCongeladas()).isFalse();
    assertThat(respuesta.fotografiasEndpoint())
        .isEqualTo("/api/v1/inspecciones/" + inspeccion + "/fotografias");
    assertThat(respuesta.observacionesEndpoint())
        .isEqualTo("/api/v1/revisiones/" + ciclo + "/observaciones");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM resultado_ic WHERE inspeccion_id=?",
                Integer.class,
                inspeccion))
        .isZero();
  }

  @Test
  void respuestaNoComparteJsonMutableConLaEntidadNiConLaVersion() {
    var cicloGestionado = revisiones.findById(ciclo).orElseThrow();
    JsonNode antes = cicloGestionado.getContenidoEnviado().deepCopy();
    var respuesta = servicio.obtener(ciclo, revisor);
    ((ObjectNode) respuesta.datos()).removeAll();
    ((ObjectNode) respuesta.identificacionCapturada()).removeAll();
    ((ObjectNode) respuesta.esquemaFormulario()).removeAll();
    assertThat(cicloGestionado.getContenidoEnviado()).isEqualTo(antes);
    assertThat(servicio.obtener(ciclo, revisor).esquemaFormulario()).isEqualTo(esquema);
  }

  @Test
  void ajenoYDesconocidoReciben404SinCalcularIndice() {
    for (UUID id : List.of(ciclo, UUID.randomUUID())) {
      assertThatThrownBy(() -> servicio.obtener(id, UUID.randomUUID()))
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDIENTE", "RESUELTA"})
  void consultarNoCambiaEstadosYAdmiteCiclosHistoricos(String estado) {
    jdbc.update(
        "UPDATE revision SET estado=?,veredicto='APROBADA',resuelta_en=CURRENT_TIMESTAMP WHERE id=?",
        estado,
        ciclo);
    var respuesta = servicio.obtener(ciclo, revisor);
    assertThat(respuesta.estadoRevision()).isEqualTo(estado);
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM inspeccion WHERE id=?", String.class, inspeccion))
        .isEqualTo("EN_REVISION");
  }

  @Test
  void autorRecibeSnapshotHistoricoYOtroEstudianteNoAccede() {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "test", null, List.of(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")));
    auth.setDetails(new JwtData("test", autor, RoleName.ESTUDIANTE, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
    var respuesta = servicio.obtener(ciclo, autor);
    assertThat(respuesta.datos()).isEqualTo(snapshot.path("datos"));
    assertThat(respuesta.esquemaFormulario()).isEqualTo(esquema);
    assertThat(respuesta.versionFormularioId()).isEqualTo(version);
    assertThatThrownBy(() -> servicio.obtener(ciclo, usuario("ESTUDIANTE")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void contenidoIncompletoNoSeReconstruyeDesdeLaInspeccionViva() {
    jdbc.update("UPDATE revision SET contenido_enviado='{}' WHERE id=?", ciclo);
    assertThatThrownBy(() -> servicio.obtener(ciclo, revisor))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
  }

  @ParameterizedTest
  @ValueSource(strings = {"pesos", "catalogo"})
  void configuracionIncompletaNoImpideAbrirElDetalleNiMarcaRollback(String falta) {
    var antes = servicio.obtener(ciclo, revisor);
    if ("pesos".equals(falta)) {
      jdbc.update("DELETE FROM configuracion_ic_elemento WHERE version_formulario_id=?", version);
    } else {
      jdbc.update(
          "UPDATE version_formulario SET reglas_evaluacion_ic='{}'::jsonb WHERE id=?", version);
    }
    var respuesta = servicio.obtener(ciclo, revisor);
    assertThat(respuesta.icPreliminar()).isNull();
    assertThat(respuesta.datos()).isEqualTo(snapshot.path("datos"));
    assertThat(respuesta.esquemaFormulario()).isEqualTo(esquema);
    assertThat(respuesta)
        .usingRecursiveComparison()
        .ignoringFields("icPreliminar")
        .isEqualTo(antes);
    assertThat(jdbc.queryForObject("SELECT estado FROM revision WHERE id=?", String.class, ciclo))
        .isEqualTo("EN_CURSO");
  }

  @Test
  void datosInsuficientesConservanElRestoDelDetalle() {
    var antes = servicio.obtener(ciclo, revisor);
    ((ObjectNode) snapshot.path("datos")).remove("subestructura");
    jdbc.update(
        "UPDATE revision SET contenido_enviado=?::jsonb WHERE id=?", snapshot.toString(), ciclo);
    var respuesta = servicio.obtener(ciclo, revisor);
    assertThat(respuesta.icPreliminar()).isNull();
    assertThat(respuesta.datos()).isEqualTo(snapshot.path("datos"));
    assertThat(respuesta)
        .usingRecursiveComparison()
        .ignoringFields("icPreliminar", "datos")
        .isEqualTo(antes);
    assertThat(json.valueToTree(respuesta).toString())
        .doesNotContain("configuracion_ic_incompleta", "Falta evaluar", "UNPROCESSABLE_ENTITY");
  }

  @Test
  void falloInesperadoNoExponeMensajeNiDatosEnElRegistro() {
    var appender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(RevisionDetalleService.class);
    appender.start();
    logger.addAppender(appender);
    try {
      doThrow(new IllegalStateException("dato-confidencial-de-inspeccion"))
          .when(calculador)
          .calcular(any());
      var respuesta = servicio.obtener(ciclo, revisor);
      assertThat(respuesta.icPreliminar()).isNull();
      assertThat(respuesta.datos()).isEqualTo(snapshot.path("datos"));
      assertThat(appender.list)
          .anySatisfy(
              evento -> {
                assertThat(evento.getFormattedMessage())
                    .contains("IllegalStateException", "fallo_inesperado_calculo_ic");
                assertThat(evento.getFormattedMessage())
                    .doesNotContain("dato-confidencial", snapshot.toString());
                assertThat(evento.getThrowableProxy()).isNull();
              });
      assertThat(json.valueToTree(respuesta).toString()).doesNotContain("dato-confidencial");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class Config {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }
  }
}
