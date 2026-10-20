package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoLocal;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.mantenimiento.dto.CerrarSinEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.CrearOrdenMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.FiltroOrdenesRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.IniciarEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.ProgramarOrdenRequest;
import gt.usac.cunoc.sgp.mantenimiento.mapper.OrdenMantenimientoMapperImpl;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.service.OrdenMantenimientoService;
import gt.usac.cunoc.sgp.mantenimiento.service.TransicionOrdenService;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Ciclo de vida de las órdenes contra PostgreSQL/PostGIS real (HU-047, RN-MTO-04, RN-MTO-09). */
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  OrdenMantenimientoService.class,
  TransicionOrdenService.class,
  NotificacionService.class,
  AuditoriaWriter.class,
  OrdenMantenimientoMapperImpl.class,
  CicloVidaOrdenMantenimientoIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class CicloVidaOrdenMantenimientoIntegrationTest {

  /** 15:00 UTC del 20/10/2026, las 09:00 en Guatemala. */
  private static final Instant AHORA = Instant.parse("2026-10-20T15:00:00Z");

  private static final LocalDate HOY = LocalDate.of(2026, 10, 20);
  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000004731");
  private static final UUID CATEDRATICO_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000004732");

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_hu047_test")
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

  @Autowired private OrdenMantenimientoService ordenes;
  @Autowired private TransicionOrdenService transiciones;
  @Autowired private JdbcTemplate jdbc;

  private UUID puenteId;

  @BeforeEach
  void prepararDatos() {
    insertarUsuario(ADMINISTRADOR_ID, "admin.hu047@ejemplo.com", "ADMINISTRADOR");
    insertarUsuario(CATEDRATICO_ID, "catedratico.hu047@ejemplo.com", "CATEDRATICO");
    puenteId = insertarPuente();
  }

  @Test
  void programarEIniciarRespetanLasRestriccionesDeLaBase() {
    OrdenMantenimientoResponse creada = crear(ADMINISTRADOR_ID);
    assertThat(creada.estado()).isEqualTo(EstadoOrden.PROPUESTA);
    assertThat(creada.version()).isZero();

    OrdenMantenimientoResponse programada =
        transiciones.aceptarYProgramar(
            creada.id(), new ProgramarOrdenRequest(HOY.plusDays(7), 0L), CATEDRATICO_ID);
    assertThat(programada.estado()).isEqualTo(EstadoOrden.PROGRAMADA);
    assertThat(programada.version()).isEqualTo(1L);

    OrdenMantenimientoResponse enEjecucion =
        transiciones.iniciarEjecucion(creada.id(), new IniciarEjecucionRequest(1L), CATEDRATICO_ID);
    assertThat(enEjecucion.estado()).isEqualTo(EstadoOrden.EN_EJECUCION);

    var fila =
        jdbc.queryForMap(
            "SELECT estado, aceptado_por_id, aceptado_en, fecha_programada"
                + " FROM orden_mantenimiento WHERE id = ?",
            creada.id());
    assertThat(fila.get("estado")).isEqualTo("EN_EJECUCION");
    assertThat(fila.get("aceptado_por_id")).isEqualTo(CATEDRATICO_ID);
    assertThat(fila.get("aceptado_en")).isNotNull();
    assertThat(fila.get("fecha_programada").toString()).isEqualTo("2026-10-27");

    var ejecucion =
        jdbc.queryForMap(
            "SELECT iniciado_en, responsable_usuario_id FROM orden_mantenimiento_ejecucion"
                + " WHERE orden_mantenimiento_id = ?",
            creada.id());
    assertThat(ejecucion.get("responsable_usuario_id")).isEqualTo(CATEDRATICO_ID);
    assertThat(ejecucion.get("iniciado_en")).isNotNull();

    assertThat(ordenes.historial(creada.id()))
        .extracting(h -> h.secuencia() + ":" + h.estadoAnterior() + ">" + h.estadoNuevo())
        .containsExactly("1:null>PROPUESTA", "2:PROPUESTA>PROGRAMADA", "3:PROGRAMADA>EN_EJECUCION");

    assertThat(
            contar(
                "SELECT count(*) FROM auditoria WHERE entidad = 'orden_mantenimiento'"
                    + " AND entidad_id = ? AND accion = 'CREAR'",
                creada.id()))
        .isEqualTo(1);
    assertThat(
            contar(
                "SELECT count(*) FROM auditoria WHERE entidad = 'orden_mantenimiento'"
                    + " AND entidad_id = ? AND accion = 'CAMBIAR_ESTADO'",
                creada.id()))
        .isEqualTo(2);
    assertThat(
            contar(
                "SELECT count(*) FROM notificacion WHERE usuario_id = ? AND entidad_id = ?"
                    + " AND clave_evento = ?",
                ADMINISTRADOR_ID,
                creada.id(),
                "orden-mantenimiento:" + creada.id() + ":PROGRAMADA"))
        .isEqualTo(1);
  }

  @Test
  void unaOrdenDescartadaSeConservaConMotivoQuienYCuando() {
    OrdenMantenimientoResponse creada = crear(ADMINISTRADOR_ID);

    transiciones.descartar(
        creada.id(), new CerrarSinEjecucionRequest("Daño ya reparado", 0L), CATEDRATICO_ID);

    var fila =
        jdbc.queryForMap(
            "SELECT estado, motivo_cierre_sin_ejecucion FROM orden_mantenimiento WHERE id = ?",
            creada.id());
    assertThat(fila.get("estado")).isEqualTo("DESCARTADA");
    assertThat(fila.get("motivo_cierre_sin_ejecucion")).isEqualTo("Daño ya reparado");

    var cierre =
        jdbc.queryForMap(
            "SELECT realizado_por_id, motivo, creado_en FROM historial_estado_orden"
                + " WHERE orden_mantenimiento_id = ? AND estado_nuevo = 'DESCARTADA'",
            creada.id());
    assertThat(cierre.get("realizado_por_id")).isEqualTo(CATEDRATICO_ID);
    assertThat(cierre.get("motivo")).isEqualTo("Daño ya reparado");
    assertThat(cierre.get("creado_en")).isNotNull();
    assertThat(ordenes.obtener(creada.id()).estado()).isEqualTo(EstadoOrden.DESCARTADA);
  }

  @Test
  void unaOrdenCanceladaSeConservaConSuMotivo() {
    OrdenMantenimientoResponse creada = crear(CATEDRATICO_ID);
    transiciones.aceptarYProgramar(
        creada.id(), new ProgramarOrdenRequest(HOY, 0L), ADMINISTRADOR_ID);

    transiciones.cancelar(
        creada.id(), new CerrarSinEjecucionRequest("Sin presupuesto", 1L), ADMINISTRADOR_ID);

    assertThat(ordenes.obtener(creada.id()).motivoCierreSinEjecucion())
        .isEqualTo("Sin presupuesto");
    assertThat(ordenes.historial(creada.id())).hasSize(3);
  }

  @Test
  void unaFechaPasadaResponde422SinCambiarElEstado() {
    OrdenMantenimientoResponse creada = crear(ADMINISTRADOR_ID);

    assertThatThrownBy(
            () ->
                transiciones.aceptarYProgramar(
                    creada.id(), new ProgramarOrdenRequest(HOY.minusDays(1), 0L), CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

    assertThat(ordenes.obtener(creada.id()).estado()).isEqualTo(EstadoOrden.PROPUESTA);
    assertThat(ordenes.historial(creada.id())).hasSize(1);
  }

  @Test
  void dosUsuariosConLaMismaVersionSoloAplicanUnaTransicion() {
    OrdenMantenimientoResponse creada = crear(ADMINISTRADOR_ID);
    transiciones.aceptarYProgramar(
        creada.id(), new ProgramarOrdenRequest(HOY.plusDays(1), 0L), ADMINISTRADOR_ID);

    assertThatThrownBy(
            () ->
                transiciones.descartar(
                    creada.id(), new CerrarSinEjecucionRequest("Leído antes", 0L), CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("version_desactualizada");
            });
    assertThat(ordenes.obtener(creada.id()).estado()).isEqualTo(EstadoOrden.PROGRAMADA);
  }

  @Test
  void dosTransicionesSimultaneasSobreLaMismaOrdenDejanSoloUnaConEfecto() throws Exception {
    OrdenMantenimientoResponse creada = crear(ADMINISTRADOR_ID);
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    try {
      Future<OrdenMantenimientoResponse> programar =
          hilos.submit(
              enParalelo(
                  salida,
                  () ->
                      transiciones.aceptarYProgramar(
                          creada.id(),
                          new ProgramarOrdenRequest(HOY.plusDays(3), 0L),
                          CATEDRATICO_ID)));
      Future<OrdenMantenimientoResponse> descartar =
          hilos.submit(
              enParalelo(
                  salida,
                  () ->
                      transiciones.descartar(
                          creada.id(),
                          new CerrarSinEjecucionRequest("Duplicada", 0L),
                          ADMINISTRADOR_ID)));
      salida.countDown();

      List<Throwable> fallos = new ArrayList<>();
      int exitos = 0;
      for (Future<OrdenMantenimientoResponse> resultado : List.of(programar, descartar)) {
        try {
          resultado.get();
          exitos++;
        } catch (ExecutionException e) {
          fallos.add(e.getCause());
        }
      }

      assertThat(exitos).isEqualTo(1);
      assertThat(fallos).hasSize(1);
      assertThat(esConflicto(fallos.get(0))).as("%s", fallos.get(0)).isTrue();
    } finally {
      hilos.shutdownNow();
    }

    assertThat(ordenes.obtener(creada.id()).estado())
        .isIn(EstadoOrden.PROGRAMADA, EstadoOrden.DESCARTADA);
    assertThat(ordenes.historial(creada.id())).hasSize(2);
  }

  @Test
  void unPuenteDadoDeBajaNoAdmiteOrdenesPeroConservaLasExistentes() {
    OrdenMantenimientoResponse existente = crear(ADMINISTRADOR_ID);
    jdbc.update(
        """
        UPDATE puente SET activo = false, inactivado_en = ?, inactivado_por_id = ?,
          motivo_inactivacion = 'Baja de prueba HU-047'
        WHERE id = ?
        """,
        OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
        ADMINISTRADOR_ID,
        puenteId);

    assertThatThrownBy(() -> crear(ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("puente_inactivo"));
    assertThat(
            ordenes
                .listar(new FiltroOrdenesRequest(puenteId, null, null, null, null, null), 0, 20)
                .getContent())
        .extracting(OrdenMantenimientoResponse::id)
        .containsExactly(existente.id());
  }

  @Test
  void laInspeccionDeOtroPuenteResponde409() {
    UUID otroPuenteId = insertarPuente();
    UUID inspeccionDeOtroPuente = insertarInspeccion(otroPuenteId);
    UUID inspeccionPropia = insertarInspeccion(puenteId);

    assertThatThrownBy(
            () ->
                ordenes.crear(
                    new CrearOrdenMantenimientoRequest(
                        puenteId,
                        "CORRECTIVO",
                        "Reparar losa",
                        "ALTA",
                        null,
                        null,
                        inspeccionDeOtroPuente),
                    ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("inspeccion_de_otro_puente");
            });

    OrdenMantenimientoResponse conInspeccion =
        ordenes.crear(
            new CrearOrdenMantenimientoRequest(
                puenteId,
                "CORRECTIVO",
                "Reparar losa",
                "ALTA",
                "/superestructura/losa_rodamiento",
                null,
                inspeccionPropia),
            ADMINISTRADOR_ID);
    assertThat(conInspeccion.inspeccionOrigenId()).isEqualTo(inspeccionPropia);
  }

  @Test
  void laBandejaCombinaEstadosTipoPrioridadPuenteYFechaDeCreacion() {
    OrdenMantenimientoResponse preventiva = crear(ADMINISTRADOR_ID);
    OrdenMantenimientoResponse correctiva = crearCon("CORRECTIVO", "ALTA");
    OrdenMantenimientoResponse emergencia = crearCon("EMERGENCIA", "ALTA");
    transiciones.descartar(
        correctiva.id(), new CerrarSinEjecucionRequest("Duplicada", 0L), CATEDRATICO_ID);
    transiciones.aceptarYProgramar(
        emergencia.id(), new ProgramarOrdenRequest(HOY, 0L), CATEDRATICO_ID);
    transiciones.cancelar(
        emergencia.id(), new CerrarSinEjecucionRequest("Sin presupuesto", 1L), CATEDRATICO_ID);
    UUID otroPuente = insertarPuente();

    assertThat(
            ids(
                new FiltroOrdenesRequest(
                    puenteId, List.of("DESCARTADA", "CANCELADA"), null, "ALTA", HOY, HOY)))
        .containsExactlyInAnyOrder(correctiva.id(), emergencia.id());
    assertThat(
            ids(
                new FiltroOrdenesRequest(
                    puenteId, List.of("DESCARTADA", "CANCELADA"), "EMERGENCIA", null, null, null)))
        .containsExactly(emergencia.id());
    assertThat(
            ids(new FiltroOrdenesRequest(puenteId, List.of("PROPUESTA"), null, null, null, null)))
        .containsExactly(preventiva.id());
    assertThat(ids(new FiltroOrdenesRequest(puenteId, null, null, null, HOY.plusDays(1), null)))
        .isEmpty();
    assertThat(ids(new FiltroOrdenesRequest(otroPuente, null, null, null, null, null))).isEmpty();

    var totales = ordenes.totales(new FiltroOrdenesRequest(puenteId, null, null, null, HOY, HOY));
    assertThat(totales.total()).isEqualTo(3);
    assertThat(totales.porEstado())
        .containsEntry(EstadoOrden.PROPUESTA, 1L)
        .containsEntry(EstadoOrden.DESCARTADA, 1L)
        .containsEntry(EstadoOrden.CANCELADA, 1L)
        .containsEntry(EstadoOrden.EJECUTADA, 0L);
    assertThat(
            ordenes
                .totales(new FiltroOrdenesRequest(puenteId, null, null, "ALTA", null, null))
                .total())
        .isEqualTo(2);
  }

  @Test
  void unaOrdenDeUnaReglaSinValidarSeRotulaProvisional() {
    UUID provisional = insertarOrdenDeRegla(insertarRegla(false));
    UUID validada = insertarOrdenDeRegla(insertarRegla(true));

    assertThat(ordenes.obtener(provisional).reglaProvisional()).isTrue();
    assertThat(ordenes.obtener(validada).reglaProvisional()).isFalse();
    assertThat(crear(ADMINISTRADOR_ID).reglaProvisional()).isNull();
    assertThat(
            ordenes.listar(new FiltroOrdenesRequest(puenteId, null, null, null, null, null), 0, 20))
        .filteredOn(o -> o.id().equals(provisional))
        .extracting(OrdenMantenimientoResponse::reglaProvisional)
        .containsExactly(true);
  }

  private List<UUID> ids(FiltroOrdenesRequest filtro) {
    return ordenes.listar(filtro, 0, 100).map(OrdenMantenimientoResponse::id).getContent();
  }

  private OrdenMantenimientoResponse crearCon(String tipo, String prioridad) {
    return ordenes.crear(
        new CrearOrdenMantenimientoRequest(
            puenteId, tipo, "Orden " + tipo, prioridad, null, null, null),
        ADMINISTRADOR_ID);
  }

  private UUID insertarRegla(boolean validada) {
    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO version_formulario (id, codigo, estado, activa, esquema,
          hash_contenido, version_algoritmo_ic, creado_por_id, publicado_por_id, publicado_en)
        VALUES (?, ?, 'PUBLICADA', false, '{"type":"object"}'::jsonb, ?, 'ic-hu049', ?, ?, ?)
        """,
        versionId,
        "HU049-" + versionId,
        "c".repeat(64),
        ADMINISTRADOR_ID,
        ADMINISTRADOR_ID,
        OffsetDateTime.ofInstant(AHORA.minusSeconds(3600), ZoneOffset.UTC));
    UUID reglaId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO regla_mantenimiento (id, codigo, numero_revision, version_formulario_id,
          elemento_ref, condicion, tipo_mantenimiento, intervencion_sugerida, regla_prioridad,
          activa, creado_por_id, validado_por_id, validado_en)
        VALUES (?, 'R-HU049', 1, ?, '/superestructura/drenaje', '{}'::jsonb, 'CORRECTIVO',
          'Limpiar drenaje', '{}'::jsonb, true, ?, ?, ?)
        """,
        reglaId,
        versionId,
        ADMINISTRADOR_ID,
        validada ? ADMINISTRADOR_ID : null,
        validada ? OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC) : null);
    return reglaId;
  }

  private UUID insertarOrdenDeRegla(UUID reglaId) {
    UUID ordenId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO orden_mantenimiento (id, puente_id, regla_origen_id, elemento_ref, tipo,
          descripcion, prioridad_sugerida, prioridad_confirmada)
        VALUES (?, ?, ?, '/superestructura/drenaje', 'CORRECTIVO', 'Limpiar drenaje',
          'MEDIA', 'MEDIA')
        """,
        ordenId,
        puenteId,
        reglaId);
    return ordenId;
  }

  private OrdenMantenimientoResponse crear(UUID usuarioId) {
    return ordenes.crear(
        new CrearOrdenMantenimientoRequest(
            puenteId, "PREVENTIVO", "Limpiar drenajes", "MEDIA", null, null, null),
        usuarioId);
  }

  private static <T> Callable<T> enParalelo(CountDownLatch salida, Callable<T> accion) {
    return () -> {
      salida.await();
      return accion.call();
    };
  }

  private static boolean esConflicto(Throwable fallo) {
    if (fallo instanceof ApiException api) {
      return api.getStatus() == HttpStatus.CONFLICT;
    }
    // El manejador global responde 409 a ambos casos.
    return fallo instanceof OptimisticLockingFailureException
        || fallo instanceof DataIntegrityViolationException;
  }

  private void insertarUsuario(UUID id, String email, String rol) {
    jdbc.update(
        """
        INSERT INTO usuario (
          id, email, password_hash, rol_id,
          verificado, activado, activo, nombre_completo
        )
        SELECT ?, ?, 'hash-de-prueba', id, true, true, true, 'Usuario de prueba HU047'
        FROM rol
        WHERE nombre = ?
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        email,
        rol);
  }

  private UUID insertarPuente() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO puente (id, codigo, correlativo_municipal, municipio_id,
          nombre, ruta, ubicacion, creado_por_id)
        SELECT ?, ?,
          (SELECT coalesce(max(correlativo_municipal), 0) + 1 FROM puente p
             WHERE p.municipio_id = m.id),
          m.id, 'Puente HU-047', 'CA-1',
          ST_SetSRID(ST_MakePoint(-90.615, 14.481), 4326)::geography, ?
        FROM municipio m WHERE m.codigo_ine = '0114'
        """,
        id,
        "T-" + id,
        ADMINISTRADOR_ID);
    return id;
  }

  private UUID insertarInspeccion(UUID puente) {
    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO version_formulario (id, codigo, estado, activa, esquema,
          hash_contenido, version_algoritmo_ic, creado_por_id, publicado_por_id, publicado_en)
        VALUES (?, ?, 'PUBLICADA', false, '{"type":"object"}'::jsonb, ?, 'ic-hu047', ?, ?, ?)
        """,
        versionId,
        "HU047-" + versionId,
        "b".repeat(64),
        ADMINISTRADOR_ID,
        ADMINISTRADOR_ID,
        OffsetDateTime.ofInstant(AHORA.minusSeconds(3600), ZoneOffset.UTC));
    UUID inspeccionId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO inspeccion (id, puente_id, version_formulario_id, autor_id,
          rol_autor_creacion, nombre_autor_capturado, fecha_inspeccion, dispositivo_id, datos)
        VALUES (?, ?, ?, ?, 'ADMINISTRADOR', 'Autor de prueba', ?, 'equipo-prueba', '{}'::jsonb)
        """,
        inspeccionId,
        puente,
        versionId,
        ADMINISTRADOR_ID,
        HOY.minusDays(2));
    return inspeccionId;
  }

  private int contar(String sql, Object... argumentos) {
    Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
    return total == null ? 0 : total;
  }

  @TestConfiguration
  static class Config {

    @Bean
    Clock clock() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    AlmacenamientoArchivos almacenamientoArchivos() throws java.io.IOException {
      return new AlmacenamientoLocal(java.nio.file.Files.createTempDirectory("sgp-hu047-"));
    }
  }
}
