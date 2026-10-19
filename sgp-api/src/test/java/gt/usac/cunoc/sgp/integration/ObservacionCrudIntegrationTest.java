package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.formulario.validation.ValidadorAnclajeFormulario;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapperImpl;
import gt.usac.cunoc.sgp.inspeccion.service.PublicacionIndiceCondicionService;
import gt.usac.cunoc.sgp.inspeccion.service.ReglasEvidenciaFotografica;
import gt.usac.cunoc.sgp.inspeccion.service.TransicionInspeccionService;
import gt.usac.cunoc.sgp.inspeccion.validation.ValidadorCategoriaFotografica;
import gt.usac.cunoc.sgp.revision.dto.*;
import gt.usac.cunoc.sgp.revision.mapper.ObservacionMapperImpl;
import gt.usac.cunoc.sgp.revision.model.*;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import gt.usac.cunoc.sgp.revision.service.ObservacionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
  ObservacionService.class,
  ObservacionMapperImpl.class,
  ValidadorAnclajeFormulario.class,
  ValidadorCategoriaFotografica.class,
  AuditoriaWriter.class,
  TransicionInspeccionService.class,
  InspeccionMapperImpl.class,
  JsonSchemaValidatorService.class,
  ReglasEvidenciaFotografica.class,
  ObservacionCrudIntegrationTest.Config.class
})
@Testcontainers
class ObservacionCrudIntegrationTest {
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

  @Autowired ObservacionService servicio;
  @Autowired RevisionRepository revisiones;
  @Autowired TransicionInspeccionService transiciones;
  // Solicitar cambios y rechazar usan los servicios, repositorios y auditoría reales.
  // El cálculo de publicación no se ejecuta en estas carreras y queda fuera de este slice.
  @MockBean PublicacionIndiceCondicionService publicacionIc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager tm;
  @SpyBean AuditoriaWriter auditoria;
  @Autowired ObjectMapper json;
  private UUID revisor, autor, ajeno, ciclo, inspeccion;
  private static final UUID TRAMO = UUID.fromString("01900000-0000-7000-8000-000000000001");
  private static final UUID PILA = UUID.fromString("01900000-0000-7000-8000-000000000002");
  private static final Instant AHORA = Instant.parse("2026-10-19T12:00:00Z");

  @BeforeEach
  void preparar() throws Exception {
    AuditoriaWriter escritor = AopTestUtils.getUltimateTargetObject(auditoria);
    reset(escritor);
    revisor = usuario("CATEDRATICO");
    autor = usuario("ESTUDIANTE");
    ajeno = usuario("CATEDRATICO");
    UUID version = UUID.randomUUID(), actual = UUID.randomUUID(), puente = UUID.randomUUID();
    String esquema =
        """
        {"properties":{"campo":{"type":"string"},"tramos":{"type":"array","items":{"type":"object","properties":{"longitud_m":{"type":"number"}}}},"pilas":{"type":"array","items":{"type":"object","properties":{"altura":{"type":"number"}}}}}}
        """;
    for (UUID id : List.of(version, actual)) {
      jdbc.update(
          "INSERT INTO version_formulario(id,codigo,esquema,creado_por_id) VALUES (?,?,?::jsonb,?)",
          id,
          "HU037-" + id,
          id.equals(version) ? esquema : "{\"properties\":{\"soloActual\":{}}}",
          revisor);
    }
    int correlativo =
        jdbc.queryForObject(
            "select coalesce(max(correlativo_municipal), 0) + 1 from puente", Integer.class);
    jdbc.update(
        "INSERT INTO puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id) SELECT ?,?,?,id,'test','test',ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,? FROM municipio WHERE codigo_ine='0114'",
        puente,
        "T-" + puente,
        correlativo,
        revisor);
    inspeccion = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO inspeccion(id,puente_id,version_formulario_id,autor_id,rol_autor_creacion,nombre_autor_capturado,fecha_inspeccion,dispositivo_id,estado,datos) VALUES (?,?,?,?,'ESTUDIANTE','test',CURRENT_DATE,'test','EN_REVISION','{}')",
        inspeccion,
        puente,
        actual,
        autor);
    ciclo = UUID.randomUUID();
    var snapshot =
        json.createObjectNode()
            .put("versionFormularioId", version.toString())
            .put("autorId", autor.toString());
    var datos = snapshot.putObject("datos");
    datos.putArray("tramos").addObject().put("instancia_id", TRAMO.toString());
    datos.putArray("pilas").addObject().put("instancia_id", PILA.toString());
    jdbc.update(
        "INSERT INTO revision(id,inspeccion_id,numero_ciclo,revisor_id,estado,enviada_en,version_inspeccion_enviada,contenido_enviado,hash_contenido) VALUES (?,?,1,?,'EN_CURSO',CURRENT_TIMESTAMP,0,?::jsonb,?)",
        ciclo,
        inspeccion,
        revisor,
        snapshot.toString(),
        "a".repeat(64));
    sesion(revisor, RoleName.CATEDRATICO);
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

  private void sesion(UUID id, RoleName rol) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "test", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    auth.setDetails(new JwtData("test", id, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void limpiarSesion() {
    SecurityContextHolder.clearContext();
  }

  private CrearObservacionRequest elemento(String ref, UUID instancia, String texto) {
    return new CrearObservacionRequest(
        new DestinoObservacion(TipoDestinoObservacion.ELEMENTO, ref, instancia, null), texto);
  }

  private ObservacionResponse crear() {
    return servicio.crear(ciclo, revisor, elemento("/campo", null, "Original"));
  }

  private void error(HttpStatus status, Runnable accion) {
    assertThatThrownBy(accion::run)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
  }

  @Test
  void crudUsaSnapshotConservaUuidYAuditaValoresActorCicloYFecha() throws Exception {
    var inicial = servicio.crear(ciclo, revisor, elemento("/tramos/longitud_m", TRAMO, "Original"));
    assertThat(inicial.estado()).isEqualTo(EstadoObservacion.PENDIENTE);
    assertThat(inicial.creadoPorId()).isEqualTo(revisor);
    assertThat(inicial.creadoEn()).isEqualTo(AHORA);
    assertThat(inicial.id().version()).isEqualTo(7);
    var editada =
        servicio.editar(ciclo, inicial.id(), revisor, new EditarObservacionRequest("Editada"));
    assertThat(editada.destino()).isEqualTo(inicial.destino());
    assertThat(servicio.obtener(ciclo, inicial.id(), revisor)).isEqualTo(editada);
    servicio.eliminar(ciclo, inicial.id(), revisor);
    assertThat(servicio.contar(ciclo, revisor).total()).isZero();
    var registros =
        jdbc.queryForList(
            "SELECT * FROM auditoria WHERE entidad='observacion_revision' AND entidad_id=?",
            inicial.id());
    assertThat(registros).hasSize(3);
    for (var registro : registros) {
      assertThat(registro.get("usuario_id")).isEqualTo(revisor);
      assertThat(((java.sql.Timestamp) registro.get("creado_en")).toInstant()).isEqualTo(AHORA);
      Object posterior = registro.get("valores_posteriores"),
          anterior = registro.get("valores_anteriores");
      var valor = json.readTree((posterior != null ? posterior : anterior).toString());
      assertThat(valor.path("revisionId").asText()).isEqualTo(ciclo.toString());
      assertThat(valor.path("id").asText()).isEqualTo(inicial.id().toString());
    }
    assertThat(registros)
        .anySatisfy(
            r -> {
              assertThat(r.get("valores_anteriores")).isNull();
              assertThat(r.get("valores_posteriores").toString()).contains("Original");
            })
        .anySatisfy(
            r -> {
              assertThat(r.get("valores_anteriores")).isNotNull();
              assertThat(r.get("valores_posteriores")).isNotNull();
              assertThat(r.get("valores_anteriores").toString()).contains("Original");
              assertThat(r.get("valores_posteriores").toString()).contains("Editada");
            })
        .anySatisfy(
            r -> {
              assertThat(r.get("valores_anteriores")).isNotNull();
              assertThat(r.get("valores_anteriores").toString()).contains("Editada");
              assertThat(r.get("valores_posteriores")).isNull();
            });
  }

  @Test
  void categoriasGeneralesYContadoresNoMezclanDestinos() {
    var primera = crear();
    var foto =
        servicio.crear(
            ciclo,
            revisor,
            new CrearObservacionRequest(
                new DestinoObservacion(
                    TipoDestinoObservacion.CATEGORIA_FOTOGRAFICA, null, null, CategoriaFoto.CAUCE),
                "Categoría"));
    assertThat(foto.destino().elementoRef()).isNull();
    servicio.guardarGeneral(ciclo, revisor, new ObservacionGeneralRequest("General"));
    assertThat(servicio.obtenerGeneral(ciclo, revisor).texto()).isEqualTo("General");
    jdbc.update(
        "UPDATE observacion_revision SET estado='RESUELTA',resuelto_por_id=?,resuelto_en=CURRENT_TIMESTAMP WHERE id=?",
        autor,
        primera.id());
    var lista = servicio.listar(ciclo, revisor);
    assertThat(lista.total()).isEqualTo(2);
    assertThat(lista.pendientes()).isEqualTo(1);
    assertThat(lista.general().texto()).isEqualTo("General");
    assertThat(servicio.contar(ciclo, revisor))
        .isEqualTo(new ContadorObservacionesResponse(ciclo, 2, 1));
    servicio.guardarGeneral(ciclo, revisor, new ObservacionGeneralRequest(null));
    assertThat(servicio.obtenerGeneral(ciclo, revisor).texto()).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad='revision_observacion_general' AND entidad_id=?",
                Long.class,
                ciclo))
        .isEqualTo(2);
  }

  @Test
  void ajenoYAutorNoPuedenConsultarNiModificarNiCruzarObservacionesDeCiclo() {
    var observacion = crear();
    for (UUID actor : List.of(ajeno, autor)) {
      error(HttpStatus.NOT_FOUND, () -> servicio.listar(ciclo, actor));
      error(HttpStatus.NOT_FOUND, () -> servicio.contar(ciclo, actor));
      error(HttpStatus.NOT_FOUND, () -> servicio.obtenerGeneral(ciclo, actor));
      error(HttpStatus.NOT_FOUND, () -> servicio.obtener(ciclo, observacion.id(), actor));
      error(
          HttpStatus.NOT_FOUND,
          () -> servicio.crear(ciclo, actor, elemento("/campo", null, "Ajeno")));
      error(
          HttpStatus.NOT_FOUND,
          () ->
              servicio.editar(
                  ciclo, observacion.id(), actor, new EditarObservacionRequest("Ajeno")));
      error(HttpStatus.NOT_FOUND, () -> servicio.eliminar(ciclo, observacion.id(), actor));
      error(
          HttpStatus.NOT_FOUND,
          () -> servicio.guardarGeneral(ciclo, actor, new ObservacionGeneralRequest("Ajeno")));
    }
    error(HttpStatus.NOT_FOUND, () -> servicio.obtener(ciclo, UUID.randomUUID(), revisor));
    assertThat(servicio.contar(ciclo, revisor).total()).isEqualTo(1);
  }

  @Test
  void servicioTambienRechazaOtrosRoles() {
    for (RoleName rol :
        List.of(RoleName.ESTUDIANTE, RoleName.ADMINISTRADOR, RoleName.PROFESIONAL_EXTERNO)) {
      sesion(autor, rol);
      assertThatThrownBy(() -> servicio.listar(ciclo, revisor))
          .isInstanceOf(AccessDeniedException.class);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDIENTE", "RESUELTA"})
  void cicloNoEditableResponde409PeroPermiteLectura(String estado) {
    var observacion = crear();
    servicio.guardarGeneral(ciclo, revisor, new ObservacionGeneralRequest("General original"));
    var antes = estadoObservaciones();
    jdbc.update(
        "UPDATE revision SET estado=?,veredicto='APROBADA',resuelta_en=CURRENT_TIMESTAMP WHERE id=?",
        estado,
        ciclo);
    error(
        HttpStatus.CONFLICT,
        () -> servicio.crear(ciclo, revisor, elemento("/campo", null, "Nuevo")));
    error(
        HttpStatus.CONFLICT,
        () ->
            servicio.editar(
                ciclo, observacion.id(), revisor, new EditarObservacionRequest("Cambio")));
    error(HttpStatus.CONFLICT, () -> servicio.eliminar(ciclo, observacion.id(), revisor));
    error(
        HttpStatus.CONFLICT,
        () -> servicio.guardarGeneral(ciclo, revisor, new ObservacionGeneralRequest("Cambio")));
    assertThat(servicio.listar(ciclo, revisor).total()).isEqualTo(1);
    assertThat(servicio.obtenerGeneral(ciclo, revisor).texto()).isEqualTo("General original");
    assertThat(estadoObservaciones()).isEqualTo(antes);
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORRADOR", "ENVIADA", "CAMBIOS_SOLICITADOS", "PUBLICADA", "RECHAZADA"})
  void inspeccionFueraDeRevisionImpideTodaEscritura(String estado) {
    var observacion = crear();
    servicio.guardarGeneral(ciclo, revisor, new ObservacionGeneralRequest("General original"));
    var antes = estadoObservaciones();
    jdbc.update(
        "UPDATE inspeccion SET estado=?,publicado_en=CASE WHEN ?='PUBLICADA' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE id=?",
        estado,
        estado,
        inspeccion);
    error(
        HttpStatus.CONFLICT,
        () -> servicio.crear(ciclo, revisor, elemento("/campo", null, "Nuevo")));
    error(
        HttpStatus.CONFLICT,
        () ->
            servicio.editar(
                ciclo, observacion.id(), revisor, new EditarObservacionRequest("Cambio")));
    error(HttpStatus.CONFLICT, () -> servicio.eliminar(ciclo, observacion.id(), revisor));
    error(
        HttpStatus.CONFLICT,
        () -> servicio.guardarGeneral(ciclo, revisor, new ObservacionGeneralRequest("Cambio")));
    assertThat(estadoObservaciones()).isEqualTo(antes);
    assertThat(servicio.listar(ciclo, revisor).total()).isEqualTo(1);
  }

  @Test
  void textoAnclajesUUIDYCombinacionesInvalidasResponden422SinGuardar() {
    for (String texto : Arrays.asList(null, "", "  ", "x".repeat(2001))) {
      error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          () -> servicio.crear(ciclo, revisor, elemento("/campo", null, texto)));
    }
    for (var request :
        List.of(
            elemento("/inexistente", null, "Texto"),
            elemento("/soloActual", null, "Texto"),
            elemento("/tramos/longitud_m", PILA, "Texto"),
            elemento("/tramos", null, "Texto"),
            new CrearObservacionRequest(
                new DestinoObservacion(
                    TipoDestinoObservacion.ELEMENTO, "/campo", null, CategoriaFoto.CAUCE),
                "Texto"),
            new CrearObservacionRequest(
                new DestinoObservacion(
                    TipoDestinoObservacion.CATEGORIA_FOTOGRAFICA, null, null, null),
                "Texto"))) {
      error(HttpStatus.UNPROCESSABLE_ENTITY, () -> servicio.crear(ciclo, revisor, request));
    }
    assertThat(servicio.contar(ciclo, revisor).total()).isZero();
    var observacion = servicio.crear(ciclo, revisor, elemento("/campo", null, "x".repeat(2000)));
    error(
        HttpStatus.UNPROCESSABLE_ENTITY,
        () -> servicio.editar(ciclo, observacion.id(), revisor, new EditarObservacionRequest(" ")));
    error(
        HttpStatus.UNPROCESSABLE_ENTITY,
        () ->
            servicio.editar(
                ciclo, observacion.id(), revisor, new EditarObservacionRequest("x".repeat(2001))));
    error(
        HttpStatus.UNPROCESSABLE_ENTITY,
        () ->
            servicio.guardarGeneral(
                ciclo, revisor, new ObservacionGeneralRequest("x".repeat(2001))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"crear", "editar", "eliminar", "general"})
  void falloDeAuditoriaRevierteLaOperacion(String operacion) {
    var original = crear();
    AuditoriaWriter escritor = AopTestUtils.getUltimateTargetObject(auditoria);
    doThrow(new IllegalStateException("Auditoría no disponible"))
        .when(escritor)
        .registrarEnTransaccion(any(), any(), anyString(), any(), any(), any(), any());
    assertThatThrownBy(
            () -> {
              switch (operacion) {
                case "crear" -> servicio.crear(ciclo, revisor, elemento("/campo", null, "Nuevo"));
                case "editar" ->
                    servicio.editar(
                        ciclo, original.id(), revisor, new EditarObservacionRequest("Cambio"));
                case "eliminar" -> servicio.eliminar(ciclo, original.id(), revisor);
                case "general" ->
                    servicio.guardarGeneral(
                        ciclo, revisor, new ObservacionGeneralRequest("Cambio"));
              }
            })
        .isInstanceOf(IllegalStateException.class);
    assertThat(servicio.listar(ciclo, revisor).total()).isEqualTo(1);
    assertThat(servicio.obtener(ciclo, original.id(), revisor).texto()).isEqualTo("Original");
    assertThat(servicio.obtenerGeneral(ciclo, revisor).texto()).isNull();
  }

  @Test
  void escriturasConcurrentesNoPierdenAuditoriaYElAjenoNoVeBloqueos() throws Exception {
    var original = crear();
    conBloqueoConcurrente(
        () ->
            servicio.editar(ciclo, original.id(), revisor, new EditarObservacionRequest("Primera")),
        () -> {
          error(
              HttpStatus.CONFLICT,
              () ->
                  servicio.editar(
                      ciclo, original.id(), revisor, new EditarObservacionRequest("Segunda")));
          error(
              HttpStatus.NOT_FOUND,
              () ->
                  servicio.editar(
                      ciclo, original.id(), ajeno, new EditarObservacionRequest("Ajeno")));
        });
    servicio.editar(ciclo, original.id(), revisor, new EditarObservacionRequest("Segunda"));
    assertThat(servicio.obtener(ciclo, original.id(), revisor).texto()).isEqualTo("Segunda");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad_id=?", Long.class, original.id()))
        .isEqualTo(3);
  }

  @Test
  void cierreConcurrenteDeCicloOInspeccionImpideEscribirDespues() throws Exception {
    var original = crear();
    for (boolean soloInspeccion : new boolean[] {true, false}) {
      jdbc.update("UPDATE inspeccion SET estado='EN_REVISION' WHERE id=?", inspeccion);
      jdbc.update("UPDATE revision SET estado='EN_CURSO' WHERE id=?", ciclo);
      conBloqueoConcurrente(
          () -> {
            if (!soloInspeccion) {
              revisiones.findByIdForUpdate(ciclo).orElseThrow();
              jdbc.update(
                  "UPDATE revision SET estado='RESUELTA',veredicto='APROBADA',resuelta_en=CURRENT_TIMESTAMP WHERE id=?",
                  ciclo);
            }
            jdbc.update(
                "UPDATE inspeccion SET estado='CAMBIOS_SOLICITADOS' WHERE id=?", inspeccion);
          },
          () ->
              error(
                  HttpStatus.CONFLICT,
                  () ->
                      servicio.editar(
                          ciclo, original.id(), revisor, new EditarObservacionRequest("Tardía"))));
      error(
          HttpStatus.CONFLICT,
          () ->
              servicio.editar(
                  ciclo, original.id(), revisor, new EditarObservacionRequest("Tardía")));
    }
    assertThat(servicio.obtener(ciclo, original.id(), revisor).texto()).isEqualTo("Original");
  }

  @Test
  void consultaDeRevisionBloqueaRealmenteCicloEInspeccionHastaElCommit() throws Exception {
    conBloqueoConcurrente(
        this::crear,
        () -> {
          // La consulta join fetch de RevisionRepository ya bloquea ambas filas en PostgreSQL.
          // No se presupone un orden interno de filas elegido por el planificador.
          for (String tabla : List.of("revision", "inspeccion")) {
            UUID id = tabla.equals("revision") ? ciclo : inspeccion;
            assertThatThrownBy(
                    () ->
                        jdbc.queryForObject(
                            "SELECT id FROM " + tabla + " WHERE id=? FOR UPDATE NOWAIT",
                            UUID.class,
                            id))
                .hasRootCauseInstanceOf(java.sql.SQLException.class)
                .satisfies(
                    e ->
                        assertThat(((java.sql.SQLException) e.getCause()).getSQLState())
                            .isEqualTo("55P03"));
          }
          assertThat(estadoObservaciones().observaciones()).isEmpty();
          assertThat(estadoObservaciones().auditorias()).isZero();
          error(HttpStatus.CONFLICT, this::crear);
          assertThat(estadoObservaciones().observaciones()).isEmpty();
          assertThat(estadoObservaciones().auditorias()).isZero();
        });
    assertThat(estadoObservaciones().observaciones()).hasSize(1);
    assertThat(estadoObservaciones().auditorias()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"cambios", "rechazo"})
  void transicionRealPrimeroImpideTodasLasEscriturasConcurrentesYPosteriores(String evento)
      throws Exception {
    var observacion = crear();
    servicio.guardarGeneral(ciclo, revisor, new ObservacionGeneralRequest("General original"));
    var antes = estadoObservaciones();
    conBloqueoConcurrente(
        () -> transicionar(evento),
        () -> {
          rechazarTodasLasEscrituras(observacion.id());
          assertThat(estadoObservaciones()).isEqualTo(antes);
        });
    rechazarTodasLasEscrituras(observacion.id());
    assertThat(estadoObservaciones()).isEqualTo(antes);
    assertThat(servicio.listar(ciclo, revisor).observaciones()).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT estado FROM revision WHERE id=?", String.class, ciclo))
        .isEqualTo("EN_CURSO");
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM inspeccion WHERE id=?", String.class, inspeccion))
        .isEqualTo(evento.equals("cambios") ? "CAMBIOS_SOLICITADOS" : "RECHAZADA");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM historial_estado_inspeccion WHERE inspeccion_id=?",
                Long.class,
                inspeccion))
        .isEqualTo(1);
  }

  @Test
  void escrituraPrimeroObligaALaTransicionRealAEsperarSuCommit() throws Exception {
    var observacion = crear();
    AtomicInteger escritor = new AtomicInteger();
    AtomicInteger transicion = new AtomicInteger();
    AtomicReference<Future<?>> futura = new AtomicReference<>();
    var inicioTransicion = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      conBloqueoConcurrente(
          () -> {
            escritor.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
            servicio.editar(
                ciclo,
                observacion.id(),
                revisor,
                new EditarObservacionRequest("Antes de transición"));
          },
          () -> {
            futura.set(
                executor.submit(
                    () -> {
                      sesion(revisor, RoleName.CATEDRATICO);
                      try {
                        new TransactionTemplate(tm)
                            .executeWithoutResult(
                                t -> {
                                  transicion.set(
                                      jdbc.queryForObject(
                                          "SELECT pg_backend_pid()", Integer.class));
                                  inicioTransicion.countDown();
                                  transicionar("cambios");
                                });
                      } finally {
                        SecurityContextHolder.clearContext();
                      }
                    }));
            try {
              assertThat(inicioTransicion.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean esperando;
            do {
              esperando =
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' AND ? = ANY(pg_blocking_pids(pid)))",
                          Boolean.class,
                          transicion.get(),
                          escritor.get()));
              if (!esperando) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            } while (!esperando && System.nanoTime() < limite && !futura.get().isDone());
            assertThat(esperando)
                .as("PostgreSQL debe confirmar la espera del bloqueo de inspección")
                .isTrue();
            assertThat(futura.get()).isNotDone();
          });
      futura.get().get(15, TimeUnit.SECONDS);
    }
    assertThat(servicio.obtener(ciclo, observacion.id(), revisor).texto())
        .isEqualTo("Antes de transición");
    var antes = estadoObservaciones();
    rechazarTodasLasEscrituras(observacion.id());
    assertThat(estadoObservaciones()).isEqualTo(antes);
    assertThat(antes.auditorias()).isEqualTo(2);
  }

  private void transicionar(String evento) {
    if (evento.equals("cambios"))
      transiciones.solicitarCambios(inspeccion, revisor, "Completar evidencia");
    else transiciones.rechazar(inspeccion, revisor, "Inspección no aceptada");
  }

  private void rechazarTodasLasEscrituras(UUID observacion) {
    error(HttpStatus.CONFLICT, this::crear);
    error(
        HttpStatus.CONFLICT,
        () -> servicio.editar(ciclo, observacion, revisor, new EditarObservacionRequest("Tardía")));
    error(HttpStatus.CONFLICT, () -> servicio.eliminar(ciclo, observacion, revisor));
    error(
        HttpStatus.CONFLICT,
        () -> servicio.guardarGeneral(ciclo, revisor, new ObservacionGeneralRequest("Tardía")));
    error(
        HttpStatus.CONFLICT,
        () -> servicio.guardarGeneral(ciclo, revisor, new ObservacionGeneralRequest(null)));
  }

  private EstadoObservaciones estadoObservaciones() {
    return new EstadoObservaciones(
        jdbc.queryForList(
            "SELECT id,contenido,estado FROM observacion_revision WHERE revision_id=? ORDER BY id",
            ciclo),
        jdbc.queryForObject(
            "SELECT observacion_general FROM revision WHERE id=?", String.class, ciclo),
        jdbc.queryForObject(
            "SELECT count(*) FROM auditoria WHERE usuario_id=? AND entidad IN ('observacion_revision','revision_observacion_general')",
            Long.class,
            revisor));
  }

  private record EstadoObservaciones(
      List<Map<String, Object>> observaciones, String general, long auditorias) {}

  private void conBloqueoConcurrente(Runnable primera, Runnable concurrente) throws Exception {
    var bloqueada = new CountDownLatch(1);
    var terminar = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var futura =
          executor.submit(
              () -> {
                sesion(revisor, RoleName.CATEDRATICO);
                try {
                  new TransactionTemplate(tm)
                      .executeWithoutResult(
                          t -> {
                            primera.run();
                            bloqueada.countDown();
                            try {
                              if (!terminar.await(15, TimeUnit.SECONDS))
                                throw new IllegalStateException("Timeout de prueba");
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(e);
                            }
                          });
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertThat(bloqueada.await(15, TimeUnit.SECONDS)).isTrue();
        concurrente.run();
      } finally {
        terminar.countDown();
      }
      futura.get(15, TimeUnit.SECONDS);
    }
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class Config {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    Clock clock() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Bean
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }
  }
}
