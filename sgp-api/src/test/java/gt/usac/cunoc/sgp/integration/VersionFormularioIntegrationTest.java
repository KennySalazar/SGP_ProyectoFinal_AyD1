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
  gt.usac.cunoc.sgp.notificacion.service.AvisosAcademicosService.class,
  gt.usac.cunoc.sgp.notificacion.service.NotificacionService.class,
  AuditoriaAspect.class,
  AuditoriaWriter.class,
  AuditoriaSnapshotService.class,
  AuditoriaConsultaService.class,
  AuditoriaMapperImpl.class,
  VersionFormularioAuditoriaSnapshotProvider.class,
  VersionFormularioService.class,
  gt.usac.cunoc.sgp.formulario.service.ActaCalibracionService.class,
  gt.usac.cunoc.sgp.formulario.validation.ValidadorActaPdf.class,
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
  @Autowired private gt.usac.cunoc.sgp.formulario.service.ActaCalibracionService actas;

  @Autowired
  private gt.usac.cunoc.sgp.archivo.repository.ArchivoActaCalibracionRepository actasRepository;

  @Autowired private VersionFormularioRepository versiones;
  @Autowired private MapeoCampoFormularioRepository mapeos;
  @Autowired private ConfiguracionIcElementoRepository configuracionesIc;
  @Autowired private AuditoriaRepository auditoriaRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void preparar() {
    autenticarAdministrador();
    jdbc.update("DELETE FROM archivo_acta_calibracion");
    jdbc.update("DELETE FROM archivo WHERE tipo_uso = 'ACTA_CALIBRACION'");
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

  private UUID autenticarCatedraticoHu053() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario(id,email,password_hash,rol_id,activo,activado,verificado) "
            + "SELECT ?,?,'hash',id,true,true,true FROM rol WHERE nombre='CATEDRATICO'",
        id,
        id + "@hu053.test");
    var auth =
        new UsernamePasswordAuthenticationToken(
            "catedratico", null, List.of(new SimpleGrantedAuthority("ROLE_CATEDRATICO")));
    auth.setDetails(new JwtData(id + "@hu053.test", id, RoleName.CATEDRATICO, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
    return id;
  }

  @Test
  void hu053CatedraticoCalibraConAuditoriaYElSegundoEditorNoPisaLosCambios() {
    var inicial = service.cargarPlantillaSiecaInicial("HU053-concurrencia", ADMINISTRADOR_ID);
    UUID catedratico = autenticarCatedraticoHu053();
    var pesos =
        inicial.pesos().stream()
            .map(
                peso ->
                    new gt.usac.cunoc.sgp.formulario.dto.ConfiguracionIcElementoRequest(
                        peso.elementoRef(), BigDecimal.valueOf(3), peso.descripcion()))
            .toList();
    service.calibrarPesos(inicial.id(), pesos, catedratico, inicial.version());
    var guardada = service.obtenerPorId(inicial.id());
    assertThat(guardada.calibradoPorId()).isEqualTo(catedratico);
    assertThat(guardada.calibradoEn()).isEqualTo(AHORA);
    assertThat(guardada.version()).isGreaterThan(inicial.version());
    assertThat(guardada.pesos()).allSatisfy(p -> assertThat(p.peso()).isEqualByComparingTo("3"));
    Integer auditorias =
        jdbc.queryForObject(
            "SELECT count(*) FROM auditoria WHERE entidad_id=? AND usuario_id=? "
                + "AND valores_anteriores IS NOT NULL AND valores_posteriores IS NOT NULL",
            Integer.class,
            inicial.id(),
            catedratico);
    assertThat(auditorias).isEqualTo(1);
    var valores =
        jdbc.queryForMap(
            "SELECT valores_anteriores::text antes, valores_posteriores::text despues "
                + "FROM auditoria WHERE entidad_id=? AND usuario_id=?",
            inicial.id(),
            catedratico);
    assertThat(valores.get("antes")).isNotEqualTo(valores.get("despues"));
    autenticarAdministrador();
    var error =
        assertThrows(
            ApiException.class,
            () -> service.calibrarPesos(inicial.id(), pesos, ADMINISTRADOR_ID, inicial.version()));
    assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(error.getCode()).isEqualTo("concurrencia_conflicto");
    assertThat(service.obtenerPorId(inicial.id()).version()).isEqualTo(guardada.version());
    assertThat(service.obtenerPorId(inicial.id()).pesos()).isEqualTo(guardada.pesos());
  }

  @Test
  void hu053RangosYUmbralesInvalidosDevuelvenCampoSinGuardarParcialmente() {
    var inicial = service.cargarPlantillaSiecaInicial("HU053-validacion", ADMINISTRADOR_ID);
    UUID catedratico = autenticarCatedraticoHu053();
    for (String caso : List.of("rango", "umbral")) {
      com.fasterxml.jackson.databind.node.ObjectNode catalogo =
          inicial.reglasEvaluacionIc().deepCopy();
      var reglas =
          (com.fasterxml.jackson.databind.node.ObjectNode) catalogo.path("reglas_evaluacion_ic");
      if ("rango".equals(caso)) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) reglas.path("rangos_estado"))
            .put("REGULAR", 101);
      } else {
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                reglas
                    .path("conversion_indicadores")
                    .path("numericos")
                    .path("golpeado_longitud_m")
                    .path("umbrales_m"))
            .put("MODERADO", 0);
      }
      var error =
          assertThrows(
              ApiException.class,
              () ->
                  service.actualizarBorrador(
                      inicial.id(),
                      inicial.esquema(),
                      null,
                      catalogo,
                      "No-debe-guardarse",
                      catedratico,
                      inicial.version()));
      assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
      assertThat(error.getErrores())
          .anySatisfy(
              e -> {
                assertThat(e.get("campo")).isNotBlank();
                assertThat(e.get("mensaje")).isNotBlank();
              });
      var despues = service.obtenerPorId(inicial.id());
      assertThat(despues.codigo()).isEqualTo(inicial.codigo());
      assertThat(despues.reglasEvaluacionIc()).isEqualTo(inicial.reglasEvaluacionIc());
      assertThat(despues.version()).isEqualTo(inicial.version());
    }
    var pesos =
        List.of(
            new gt.usac.cunoc.sgp.formulario.dto.ConfiguracionIcElementoRequest(
                "/viga", BigDecimal.ZERO, "Viga"));
    var error =
        assertThrows(
            ApiException.class,
            () -> service.calibrarPesos(inicial.id(), pesos, catedratico, inicial.version()));
    assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(error.getErrores()).isNotEmpty();
    assertThat(service.obtenerPorId(inicial.id()).pesos())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(inicial.pesos());
  }

  @Test
  void hu053ReglasDeCalculoGuardanResponsableYFechaEnElBorrador() {
    var inicial = service.cargarPlantillaSiecaInicial("HU053-reglas", ADMINISTRADOR_ID);
    UUID catedratico = autenticarCatedraticoHu053();
    com.fasterxml.jackson.databind.node.ObjectNode catalogo =
        inicial.reglasEvaluacionIc().deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            catalogo.path("reglas_evaluacion_ic").path("rangos_estado"))
        .put("REGULAR", 45);
    var respuesta =
        service.actualizarBorrador(
            inicial.id(),
            inicial.esquema(),
            null,
            catalogo,
            inicial.codigo(),
            catedratico,
            inicial.version());
    assertThat(respuesta.calibradoPorId()).isEqualTo(catedratico);
    assertThat(respuesta.calibradoEn()).isEqualTo(AHORA);
    assertThat(respuesta.version()).isGreaterThan(inicial.version());
    assertThat(
            respuesta
                .reglasEvaluacionIc()
                .at("/reglas_evaluacion_ic/rangos_estado/REGULAR")
                .asInt())
        .isEqualTo(45);
  }

  @Test
  void hu053DosGuardadosConcurrentesSoloConfirmanUnaCalibracion() throws Exception {
    var inicial = service.cargarPlantillaSiecaInicial("HU053-dos-editores", ADMINISTRADOR_ID);
    UUID primero = autenticarCatedraticoHu053();
    UUID segundo = autenticarCatedraticoHu053();
    var listos = new java.util.concurrent.CountDownLatch(2);
    var inicio = new java.util.concurrent.CountDownLatch(1);
    try (var ejecutor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var tareas = new java.util.ArrayList<java.util.concurrent.Future<String>>();
      for (UUID usuario : List.of(primero, segundo)) {
        tareas.add(
            ejecutor.submit(
                () -> {
                  var auth =
                      new UsernamePasswordAuthenticationToken(
                          "editor", null, List.of(new SimpleGrantedAuthority("ROLE_CATEDRATICO")));
                  auth.setDetails(
                      new JwtData(usuario + "@hu053.test", usuario, RoleName.CATEDRATICO, 0));
                  SecurityContextHolder.getContext().setAuthentication(auth);
                  listos.countDown();
                  try {
                    if (!inicio.await(10, java.util.concurrent.TimeUnit.SECONDS))
                      throw new IllegalStateException("Tiempo agotado");
                    service.configurarEscala(
                        inicial.id(),
                        escalaDePrueba(usuario.equals(primero) ? 3 : 2),
                        usuario,
                        inicial.version());
                    return "guardada";
                  } catch (ApiException e) {
                    return e.getCode();
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      }
      assertThat(listos.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      inicio.countDown();
      var resultados = new java.util.ArrayList<String>();
      for (var tarea : tareas) resultados.add(tarea.get(20, java.util.concurrent.TimeUnit.SECONDS));
      assertThat(resultados).containsExactlyInAnyOrder("guardada", "concurrencia_conflicto");
    }
    var finalizada = service.obtenerPorId(inicial.id());
    assertThat(finalizada.version()).isEqualTo(inicial.version() + 1);
    assertThat(finalizada.calibradoPorId()).isIn(primero, segundo);
  }

  @Test
  void hu053CatedraticoGuardaEscalaReglasYActaPdfConSuVersion() throws Exception {
    var inicial = service.cargarPlantillaSiecaInicial("HU053-acta-catedratico", ADMINISTRADOR_ID);
    UUID catedratico = autenticarCatedraticoHu053();
    var escala =
        service.configurarEscala(inicial.id(), escalaDePrueba(4), catedratico, inicial.version());
    var criticas =
        service.configurarReglasCriticas(
            inicial.id(),
            new gt.usac.cunoc.sgp.formulario.dto.ConfigurarReglasCriticasIcRequest(
                escala.reglasEvaluacionIc().path("reglas_evaluacion_ic").path("reglas_anulacion")),
            catedratico,
            escala.version());
    byte[] contenido;
    try (var documento = new org.apache.pdfbox.pdmodel.PDDocument();
        var salida = new java.io.ByteArrayOutputStream()) {
      documento.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      documento.save(salida);
      contenido = salida.toByteArray();
    }
    var fichero =
        new org.springframework.mock.web.MockMultipartFile(
            "archivo", "acta.pdf", "application/pdf", contenido);
    var acta = actas.adjuntar(inicial.id(), fichero, catedratico, criticas.version());
    assertThat(acta.acta().subidoPorId()).isEqualTo(catedratico);
    assertThat(actas.descargar(inicial.id())).isEqualTo(contenido);
    var actual = service.obtenerPorId(inicial.id());
    assertThat(actual.calibradoPorId()).isEqualTo(catedratico);
    assertThat(actual.calibradoEn()).isEqualTo(AHORA);
    assertThat(actual.version()).isGreaterThan(criticas.version());
    var error =
        assertThrows(
            ApiException.class,
            () -> actas.adjuntar(inicial.id(), fichero, catedratico, criticas.version()));
    assertThat(error.getCode()).isEqualTo("concurrencia_conflicto");
    assertThat(actasRepository.count()).isEqualTo(1);
  }

  @Test
  void hu053AmbosRolesRecibenConflictoAlModificarUnaVersionPublicada() {
    var inicial = service.cargarPlantillaSiecaInicial("HU053-inmutable", ADMINISTRADOR_ID);
    var publicada = service.publicar(inicial.id(), null, ADMINISTRADOR_ID);
    for (boolean catedratico : List.of(false, true)) {
      UUID actor;
      if (catedratico) actor = autenticarCatedraticoHu053();
      else {
        autenticarAdministrador();
        actor = ADMINISTRADOR_ID;
      }
      UUID usuario = actor;
      var error =
          assertThrows(
              ApiException.class,
              () ->
                  service.configurarEscala(
                      publicada.id(), escalaDePrueba(4), usuario, publicada.version()));
      assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(error.getCode()).isEqualTo("version_inmutable");
      assertThat(service.obtenerPorId(publicada.id()).hashContenido())
          .isEqualTo(publicada.hashContenido());
    }
  }

  @Test
  void cicloDeVidaCompletoVersionesYPublicacionExclusiva() throws Exception {
    VersionFormularioResponse v1 =
        service.cargarPlantillaSiecaInicial("SIECA-v1", ADMINISTRADOR_ID);

    assertThat(v1.codigo()).isEqualTo("SIECA-v1");
    assertThat(v1.estado()).isEqualTo(EstadoVersionFormulario.BORRADOR);
    assertThat(v1.activa()).isFalse();
    assertThat(v1.pesos()).hasSize(45);
    assertThat(v1.rotuloCalibracion()).contains("provisional");
    configurarPesosDePrueba(v1.id(), BigDecimal.ONE);
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
        new ArrayList<>(
            analisis.camposOrigen().stream()
                .map(
                    campo ->
                        new DeclaracionMapeoRequest(
                            campo, null, TipoMapeoCampo.ELIMINADO, "Eliminado en v2"))
                .toList());
    analisis
        .camposDestino()
        .forEach(
            campo ->
                declaraciones.add(
                    new DeclaracionMapeoRequest(null, campo, TipoMapeoCampo.NUEVO, "Nuevo en v2")));

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

  @Test
  void actaPdfSeAsociaAuditaConsultaYConservaAlPublicar() throws Exception {
    var version = service.cargarPlantillaSiecaInicial("SIECA-acta", ADMINISTRADOR_ID);
    assertThat(actas.consultar(version.id()).estado()).isEqualTo("SIN_ACTA");
    configurarPesosDePrueba(version.id(), BigDecimal.ONE);
    byte[] contenido;
    try (var documento = new org.apache.pdfbox.pdmodel.PDDocument();
        var salida = new java.io.ByteArrayOutputStream()) {
      documento.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      documento.save(salida);
      contenido = salida.toByteArray();
    }
    var fichero =
        new org.springframework.mock.web.MockMultipartFile(
            "archivo", "acta.pdf", "application/pdf", contenido);
    var primera = actas.adjuntar(version.id(), fichero, ADMINISTRADOR_ID);
    assertThat(primera.estado()).isEqualTo("CON_ACTA");
    assertThat(actas.descargar(version.id())).isEqualTo(contenido);
    var segunda = actas.adjuntar(version.id(), fichero, ADMINISTRADOR_ID);
    assertThat(segunda.acta().archivoId()).isNotEqualTo(primera.acta().archivoId());
    assertThat(actasRepository.count()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM archivo WHERE id = ?",
                String.class,
                primera.acta().archivoId()))
        .isEqualTo("RETIRADO");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad_id = ? AND proceso_automatico = 'HU-019: acta de calibracion'",
                Integer.class,
                version.id()))
        .isEqualTo(2);
    service.configurarEscala(version.id(), escalaDePrueba(4), ADMINISTRADOR_ID);
    configurarReglasDePrueba(version.id());
    service.publicar(version.id(), null, ADMINISTRADOR_ID);
    assertThat(actas.descargar(version.id())).isEqualTo(contenido);
    var errorActa =
        assertThrows(
            ApiException.class, () -> actas.adjuntar(version.id(), fichero, ADMINISTRADOR_ID));
    assertThat(errorActa.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errorActa.getCode()).isEqualTo("version_inmutable");
    assertThat(service.obtenerPorId(version.id()).estado())
        .isEqualTo(EstadoVersionFormulario.PUBLICADA);
    assertThat(actasRepository.count()).isEqualTo(2);
  }

  @Test
  void pdfFalsoNoCreaArchivosNiVinculos() {
    var version = service.cargarPlantillaSiecaInicial("SIECA-pdf-falso", ADMINISTRADOR_ID);
    configurarPesosDePrueba(version.id(), BigDecimal.ONE);
    var fichero =
        new org.springframework.mock.web.MockMultipartFile(
            "archivo",
            "acta.pdf",
            "application/pdf",
            "contenido de texto".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    ApiException error =
        assertThrows(
            ApiException.class, () -> actas.adjuntar(version.id(), fichero, ADMINISTRADOR_ID));
    assertThat(error.getCode()).isEqualTo("acta_pdf_invalida");
    assertThat(actasRepository.count()).isZero();
    assertThat(actas.consultar(version.id()).estado()).isEqualTo("SIN_ACTA");
  }

  @Test
  void plantillaSePublicaSinCalibracionManualYCalculaConSocavacionCritica() throws Exception {
    var borrador = service.cargarPlantillaSiecaInicial("HU050-primera", ADMINISTRADOR_ID);
    var publicada = service.publicar(borrador.id(), null, ADMINISTRADOR_ID);
    assertThat(publicada.activa()).isTrue();
    assertThat(publicada.pesos()).hasSize(45);
    assertThat(publicada.reglasEvaluacionIc().at("/reglas_evaluacion_ic/reglas_anulacion"))
        .hasSize(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad_id = ?",
                Integer.class,
                publicada.id()))
        .isEqualTo(2);
    var datos =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            objectMapper.readTree(
                getClass().getResourceAsStream("/formulario/sieca-v1/ejemplo-inspeccion.json"));
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            datos.at("/subestructura/estribo_entrada/socavacion"))
        .put("aguas_arriba", "CON_EXPOSICION");
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
    publicada.pesos().forEach(peso -> pesos.put(peso.elementoRef(), peso.peso()));
    var resultado =
        calculo.calcular(
            new gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc(
                publicada.id(),
                publicada.reglasEvaluacionIc().path("formulario").asText(),
                publicada.versionAlgoritmoIc(),
                datos,
                publicada.esquema(),
                publicada.reglasEvaluacionIc(),
                publicada.reglasEvaluacionIc().path("reglas_evaluacion_ic"),
                pesos));
    assertThat(resultado.indiceCalculado()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
    assertThat(resultado.estadoCalculado())
        .isEqualTo(gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion.MALO);
    assertThat(resultado.condicionesAnulacion())
        .anyMatch(regla -> "RN-IC-05a".equals(regla.reglaId()));
  }

  @Test
  void segundaPlantillaDeclaraMapeosYReemplazaSoloLaActiva() {
    var primera = service.cargarPlantillaSiecaInicial("HU050-base", ADMINISTRADOR_ID);
    service.publicar(primera.id(), null, ADMINISTRADOR_ID);
    var segunda = service.cargarPlantillaSiecaInicial("HU050-nueva", ADMINISTRADOR_ID);
    assertThat(segunda.versionAnteriorId()).isEqualTo(primera.id());
    assertThat(segunda.mapeos()).isNotEmpty().allMatch(m -> m.tipo() == TipoMapeoCampo.EQUIVALENTE);
    assertThat(service.analizarMapeo(segunda.id()).mapeoCompleto()).isTrue();
    service.publicar(segunda.id(), null, ADMINISTRADOR_ID);
    assertThat(service.obtenerPorId(primera.id()).activa()).isFalse();
    assertThat(service.obtenerActiva().id()).isEqualTo(segunda.id());
  }

  @Test
  void noPublicaUnaPlantillaCuandoSeRetiraUnaDeclaracionDeMapeo() {
    var primera = service.cargarPlantillaSiecaInicial("HU050-base", ADMINISTRADOR_ID);
    service.publicar(primera.id(), null, ADMINISTRADOR_ID);
    var segunda = service.cargarPlantillaSiecaInicial("HU050-nueva", ADMINISTRADOR_ID);
    mapeos.deleteById(segunda.mapeos().getFirst().id());
    var error =
        assertThrows(
            ApiException.class, () -> service.publicar(segunda.id(), null, ADMINISTRADOR_ID));
    assertThat(error.getCode()).isEqualTo("mapeo_incompleto");
    assertThat(service.obtenerActiva().id()).isEqualTo(primera.id());
  }

  private void configurarReglasDePrueba(UUID versionId) throws Exception {
    VersionFormularioResponse version = service.obtenerPorId(versionId);

    com.fasterxml.jackson.databind.node.ObjectNode configuracion =
        versiones.findById(versionId).orElseThrow().getReglasEvaluacionIc().deepCopy();

    com.fasterxml.jackson.databind.node.ObjectNode reglas =
        (com.fasterxml.jackson.databind.node.ObjectNode) configuracion.get("reglas_evaluacion_ic");

    for (JsonNode regla : reglas.path("reglas_anulacion")) {
      ((com.fasterxml.jackson.databind.node.ObjectNode) regla).putNull("pendiente");
    }

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
    gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos almacenamientoActas() {
      return new gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos() {
        private final java.util.Map<String, byte[]> objetos =
            new java.util.concurrent.ConcurrentHashMap<>();

        public void guardar(String clave, java.io.InputStream contenido, long tamano, String mime)
            throws java.io.IOException {
          if (objetos.putIfAbsent(clave, contenido.readAllBytes()) != null) {
            throw new java.io.IOException("El objeto ya existe");
          }
        }

        public java.io.InputStream abrir(String clave) throws java.io.IOException {
          byte[] contenido = objetos.get(clave);
          if (contenido == null) {
            throw new java.io.FileNotFoundException(clave);
          }
          return new java.io.ByteArrayInputStream(contenido);
        }

        public boolean existe(String clave) {
          return objetos.containsKey(clave);
        }

        public void eliminar(String clave) {
          objetos.remove(clave);
        }
      };
    }

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
