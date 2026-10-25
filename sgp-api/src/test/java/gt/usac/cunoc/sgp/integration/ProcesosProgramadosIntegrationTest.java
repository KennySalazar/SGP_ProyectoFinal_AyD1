package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoLocal;
import gt.usac.cunoc.sgp.archivo.service.PurgaArchivosBorradorService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.config.ProcesosProgramados;
import gt.usac.cunoc.sgp.inspeccion.service.BorradoresInactivosService;
import gt.usac.cunoc.sgp.mantenimiento.service.AlertasMantenimientoService;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.revision.service.AlertaRevisionesSinRevisarService;
import gt.usac.cunoc.sgp.sync.service.LimpiezaIdempotenciaService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Alertas y limpiezas programadas con reloj controlado y PostgreSQL/PostGIS real (HU-069). */
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  AlertaRevisionesSinRevisarService.class,
  AlertasMantenimientoService.class,
  BorradoresInactivosService.class,
  PurgaArchivosBorradorService.class,
  LimpiezaIdempotenciaService.class,
  NotificacionService.class,
  AuditoriaWriter.class,
  ProcesosProgramadosIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ProcesosProgramadosIntegrationTest {

  /** 15:00 UTC del 20/10/2026, las 09:00 en Guatemala. */
  private static final Instant AHORA = Instant.parse("2026-10-20T15:00:00Z");

  private static final LocalDate HOY = LocalDate.of(2026, 10, 20);

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu069_test")
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

  /** Reloj controlado que, a diferencia de un reloj fijo, sí respeta la zona solicitada. */
  static class Reloj extends Clock {
    final AtomicReference<Instant> instante;
    final ZoneId zona;

    Reloj(AtomicReference<Instant> instante, ZoneId zona) {
      this.instante = instante;
      this.zona = zona;
    }

    @Override
    public ZoneId getZone() {
      return zona;
    }

    @Override
    public Clock withZone(ZoneId otra) {
      return new Reloj(instante, otra);
    }

    @Override
    public Instant instant() {
      return instante.get();
    }
  }

  /** Almacenamiento local que puede fallar al eliminar claves concretas. */
  static class Almacenamiento implements AlmacenamientoArchivos {
    final AlmacenamientoLocal real;
    final Set<String> fallarAlEliminar = ConcurrentHashMap.newKeySet();

    Almacenamiento() throws IOException {
      real = new AlmacenamientoLocal(java.nio.file.Files.createTempDirectory("sgp-hu069-"));
    }

    public void guardar(String clave, InputStream contenido, long tamano, String mime)
        throws IOException {
      real.guardar(clave, contenido, tamano, mime);
    }

    public InputStream abrir(String clave) throws IOException {
      return real.abrir(clave);
    }

    public boolean existe(String clave) throws IOException {
      return real.existe(clave);
    }

    public void eliminar(String clave) throws IOException {
      if (fallarAlEliminar.contains(clave)) {
        throw new IOException("fallo de prueba al eliminar " + clave);
      }
      real.eliminar(clave);
    }
  }

  @TestConfiguration
  static class Config {
    @Bean
    Reloj clock() {
      return new Reloj(new AtomicReference<>(AHORA), ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    Almacenamiento almacenamiento() throws IOException {
      return new Almacenamiento();
    }
  }

  @Autowired private AlertaRevisionesSinRevisarService revisiones;
  @Autowired private AlertasMantenimientoService mantenimiento;
  @Autowired private BorradoresInactivosService borradores;
  @Autowired private LimpiezaIdempotenciaService idempotencia;
  @Autowired private Almacenamiento almacenamiento;
  @Autowired private Reloj reloj;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;

  private UUID administrador;
  private UUID catedratico;
  private UUID estudiante;

  @BeforeEach
  void preparar() {
    reloj.instante.set(AHORA);
    almacenamiento.fallarAlEliminar.clear();
    administrador = usuario("ADMINISTRADOR", true);
    catedratico = usuario("CATEDRATICO", true);
    estudiante = usuario("ESTUDIANTE", true);
  }

  @Test
  void unaInspeccionEnviadaHace15DiasAvisaUnaSolaVezAlCatedraticoAsignado() {
    UUID puente = puente(AHORA.minus(Duration.ofDays(30)));
    UUID vencida = inspeccion(puente, estudiante, "ENVIADA", AHORA.minus(Duration.ofDays(16)));
    UUID reciente = inspeccion(puente, estudiante, "ENVIADA", AHORA.minus(Duration.ofDays(3)));
    UUID inactivo = usuario("CATEDRATICO", false);
    UUID deInactivo = inspeccion(puente, estudiante, "ENVIADA", AHORA);
    UUID revisionVencida =
        revision(vencida, catedratico, AHORA.minus(Duration.ofDays(15)).minusSeconds(1));
    revision(reciente, catedratico, AHORA.minus(Duration.ofDays(3)));
    revision(deInactivo, inactivo, AHORA.minus(Duration.ofDays(40)));

    revisiones.ejecutar();
    revisiones.ejecutar();

    assertThat(avisos(catedratico, "REVISION_SIN_REVISAR_15_DIAS"))
        .containsExactly(revisionVencida.toString());
    assertThat(avisos(inactivo, "REVISION_SIN_REVISAR_15_DIAS")).isEmpty();
    assertThat(ejecuciones(ProcesosProgramados.ALERTA_REVISIONES_SIN_REVISAR)).isEqualTo(2);
  }

  @Test
  void unPuenteSinInspeccionPublicadaEn12MesesAvisaSinRepetirseCadaDia() {
    UUID olvidado = puente(AHORA.minus(Duration.ofDays(400)));
    asignar(olvidado, catedratico);
    UUID inspeccionado = puente(AHORA.minus(Duration.ofDays(400)));
    UUID publicada = inspeccion(inspeccionado, catedratico, "PUBLICADA", AHORA);
    jdbc.update(
        "UPDATE inspeccion SET publicado_en = ? WHERE id = ?",
        Timestamp.from(AHORA.minus(Duration.ofDays(60))),
        publicada);

    mantenimiento.alertarPuentesSinInspeccion();
    reloj.instante.set(AHORA.plus(Duration.ofDays(1)));
    mantenimiento.alertarPuentesSinInspeccion();

    assertThat(avisos(administrador, "PUENTE_SIN_INSPECCION_12_MESES"))
        .containsOnlyOnce(olvidado.toString())
        .doesNotContain(inspeccionado.toString());
    assertThat(avisos(catedratico, "PUENTE_SIN_INSPECCION_12_MESES"))
        .containsExactly(olvidado.toString());
    assertThat(avisos(estudiante, "PUENTE_SIN_INSPECCION_12_MESES")).isEmpty();
  }

  @Test
  void lasOrdenesProgramadasAvisanProximasYVencidasSinCambiarSuEstado() {
    UUID puente = puente(AHORA.minus(Duration.ofDays(30)));
    UUID proxima = ordenProgramada(puente, catedratico, HOY.plusDays(3));
    UUID lejana = ordenProgramada(puente, catedratico, HOY.plusDays(4));
    UUID vencida = ordenProgramada(puente, catedratico, HOY.minusDays(1));

    mantenimiento.alertarOrdenesProgramadas();
    mantenimiento.alertarOrdenesProgramadas();

    assertThat(avisos(catedratico, "ORDEN_PROGRAMADA_PROXIMA")).containsExactly(proxima.toString());
    assertThat(avisos(administrador, "ORDEN_PROGRAMADA_PROXIMA"))
        .containsOnlyOnce(proxima.toString())
        .doesNotContain(lejana.toString());
    assertThat(avisos(catedratico, "ORDEN_PROGRAMADA_VENCIDA")).containsExactly(vencida.toString());
    assertThat(avisos(administrador, "ORDEN_PROGRAMADA_VENCIDA"))
        .containsOnlyOnce(vencida.toString());
    assertThat(
            jdbc.queryForList(
                "SELECT estado FROM orden_mantenimiento WHERE id IN (?, ?, ?)",
                String.class,
                proxima,
                lejana,
                vencida))
        .containsOnly("PROGRAMADA");
  }

  @Test
  void dosEjecucionesSimultaneasNoDuplicanAvisos() throws Exception {
    UUID puente = puente(AHORA.minus(Duration.ofDays(30)));
    UUID orden = ordenProgramada(puente, catedratico, HOY.plusDays(1));
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    try {
      List<Future<?>> ejecuciones =
          List.of(
              hilos.submit(
                  () -> {
                    salida.await();
                    mantenimiento.alertarOrdenesProgramadas();
                    return null;
                  }),
              hilos.submit(
                  () -> {
                    salida.await();
                    mantenimiento.alertarOrdenesProgramadas();
                    return null;
                  }));
      salida.countDown();
      for (Future<?> ejecucion : ejecuciones) {
        ejecucion.get();
      }
    } finally {
      hilos.shutdownNow();
    }

    assertThat(avisos(catedratico, "ORDEN_PROGRAMADA_PROXIMA")).containsExactly(orden.toString());
    assertThat(avisos(administrador, "ORDEN_PROGRAMADA_PROXIMA"))
        .containsOnlyOnce(orden.toString());
  }

  @Test
  void elBloqueoDeShedLockImpideQueDosReplicasEjecutenElMismoProceso() {
    var proveedor =
        new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    var configuracion =
        new LockConfiguration(
            Instant.now(),
            ProcesosProgramados.ALERTA_ORDENES_PROGRAMADAS,
            Duration.parse(ProcesosProgramados.BLOQUEO_MAXIMO),
            Duration.ZERO);

    var primera = proveedor.lock(configuracion);
    var segunda = proveedor.lock(configuracion);

    assertThat(primera).isPresent();
    assertThat(segunda).isEmpty();
    primera.get().unlock();
    assertThat(proveedor.lock(configuracion)).isPresent();
  }

  @Test
  void unBorradorInactivoAvisaASuAutorA180DiasYPurgaSusArchivosA210() throws IOException {
    UUID puente = puente(AHORA.minus(Duration.ofDays(400)));
    UUID aviso = inspeccion(puente, estudiante, "BORRADOR", AHORA.minus(Duration.ofDays(181)));
    UUID purgable = inspeccion(puente, estudiante, "BORRADOR", AHORA.minus(Duration.ofDays(211)));
    UUID publicada = inspeccion(puente, estudiante, "PUBLICADA", AHORA.minus(Duration.ofDays(400)));
    UUID foto = archivo(purgable, "FOTO_INSPECCION", "DISPONIBLE");
    UUID retirado = archivo(purgable, "DOCUMENTO_INSPECCION", "RETIRADO");
    UUID fallido = archivo(purgable, "DOCUMENTO_INSPECCION", "DISPONIBLE");
    UUID dePublicada = archivo(publicada, "FOTO_INSPECCION", "DISPONIBLE");
    UUID deAviso = archivo(aviso, "FOTO_INSPECCION", "DISPONIBLE");
    almacenamiento.fallarAlEliminar.add(clave(fallido));

    borradores.avisarInactivos();
    borradores.purgarArchivos();

    assertThat(avisos(estudiante, "BORRADOR_180_DIAS"))
        .containsExactlyInAnyOrder(aviso.toString(), purgable.toString());
    assertThat(estadoArchivo(foto)).isEqualTo("PURGADO");
    assertThat(estadoArchivo(retirado)).isEqualTo("PURGADO");
    assertThat(almacenamiento.existe(clave(foto))).isFalse();
    assertThat(almacenamiento.existe(clave(foto) + "-mini")).isFalse();
    // El fallo de un archivo no detiene a los demás y se reintenta en la siguiente ejecución.
    assertThat(estadoArchivo(fallido)).isEqualTo("DISPONIBLE");
    assertThat(almacenamiento.existe(clave(fallido))).isTrue();
    // Nunca se tocan los archivos de una inspección publicada ni de un borrador de menos de 210.
    assertThat(estadoArchivo(dePublicada)).isEqualTo("DISPONIBLE");
    assertThat(almacenamiento.existe(clave(dePublicada))).isTrue();
    assertThat(estadoArchivo(deAviso)).isEqualTo("DISPONIBLE");
    // Se conserva el registro de datos del borrador.
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM inspeccion WHERE id = ?", String.class, purgable))
        .isEqualTo("BORRADOR");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'archivo' AND entidad_id = ?"
                    + " AND accion = 'CAMBIAR_ESTADO' AND proceso_automatico = ?"
                    + " AND usuario_id IS NULL",
                Integer.class,
                foto,
                ProcesosProgramados.PURGA_ARCHIVOS_BORRADORES))
        .isEqualTo(1);

    almacenamiento.fallarAlEliminar.clear();
    borradores.purgarArchivos();
    assertThat(estadoArchivo(fallido)).isEqualTo("PURGADO");
    assertThat(ejecuciones(ProcesosProgramados.PURGA_ARCHIVOS_BORRADORES)).isEqualTo(2);
  }

  @Test
  void laLimpiezaEliminaSoloLasClavesDeIdempotenciaVencidasQueYaNoProtegenNada() {
    UUID completadaVencida = operacion("COMPLETADA", "archivo", AHORA.minusSeconds(60));
    UUID completadaVigente = operacion("COMPLETADA", "archivo", AHORA.plusSeconds(60));
    UUID httpAbandonada = operacion("EN_PROCESO", "http_hu043", AHORA.minusSeconds(60));
    UUID cargaPendiente = operacion("EN_PROCESO", "archivo", AHORA.minusSeconds(60));

    idempotencia.limpiar();

    assertThat(
            jdbc.queryForList(
                "SELECT id FROM operacion_idempotente WHERE id IN (?, ?, ?, ?)",
                UUID.class,
                completadaVencida,
                completadaVigente,
                httpAbandonada,
                cargaPendiente))
        .containsExactlyInAnyOrder(completadaVigente, cargaPendiente);
    assertThat(ejecuciones(ProcesosProgramados.LIMPIEZA_IDEMPOTENCIA)).isEqualTo(1);
  }

  // --- datos de prueba ---

  private UUID usuario(String rol, boolean activo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario (id, email, password_hash, rol_id, verificado, activado, activo)"
            + " SELECT ?, ?, 'hash', id, true, true, ? FROM rol WHERE nombre = ?",
        id,
        id + "@hu069.test",
        activo,
        rol);
    return id;
  }

  private UUID puente(Instant creadoEn) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO puente (id, codigo, correlativo_municipal, municipio_id, nombre, ruta,
          ubicacion, creado_por_id, creado_en)
        SELECT ?, ?, (SELECT coalesce(max(correlativo_municipal), 0) + 1 FROM puente p
             WHERE p.municipio_id = m.id),
          m.id, 'Puente HU-069', 'CA-1',
          ST_SetSRID(ST_MakePoint(-90.6, 14.5), 4326)::geography, ?, ?
        FROM municipio m WHERE m.codigo_ine = '0114'
        """,
        id,
        "T-" + id,
        administrador,
        Timestamp.from(creadoEn));
    return id;
  }

  private UUID inspeccion(UUID puente, UUID autor, String estado, Instant ultimaActividad) {
    UUID version = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO version_formulario (id, codigo, estado, activa, esquema, hash_contenido,
          version_algoritmo_ic, creado_por_id, publicado_por_id, publicado_en)
        VALUES (?, ?, 'PUBLICADA', false, '{}'::jsonb, ?, 'ic', ?, ?, CURRENT_TIMESTAMP)
        """,
        version,
        "HU069-" + version,
        "d".repeat(64),
        administrador,
        administrador);
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO inspeccion (id, puente_id, version_formulario_id, autor_id,
          rol_autor_creacion, nombre_autor_capturado, fecha_inspeccion, dispositivo_id, datos,
          estado, ultima_actividad_en, publicado_en)
        VALUES (?, ?, ?, ?, 'ESTUDIANTE', 'Autor', ?, 'equipo', '{}'::jsonb, ?, ?, ?)
        """,
        id,
        puente,
        version,
        autor,
        HOY.minusDays(1),
        estado,
        Timestamp.from(ultimaActividad),
        "PUBLICADA".equals(estado) ? Timestamp.from(ultimaActividad) : null);
    return id;
  }

  private UUID revision(UUID inspeccion, UUID revisor, Instant enviadaEn) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO revision (id, inspeccion_id, numero_ciclo, revisor_id, enviada_en,
          version_inspeccion_enviada, contenido_enviado, hash_contenido)
        VALUES (?, ?, 1, ?, ?, 0, '{}'::jsonb, ?)
        """,
        id,
        inspeccion,
        revisor,
        Timestamp.from(enviadaEn),
        "e".repeat(64));
    return id;
  }

  private void asignar(UUID puente, UUID catedraticoId) {
    UUID asignatura = UUID.randomUUID();
    UUID curso = UUID.randomUUID();
    UUID inscripcion = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO asignatura (id, codigo, nombre) VALUES (?, ?, 'Asignatura HU-069')",
        asignatura,
        asignatura.toString());
    jdbc.update(
        "INSERT INTO curso (id, asignatura_id, periodo, catedratico_id, fecha_inicio, fecha_fin)"
            + " VALUES (?, ?, '2026', ?, '2026-01-01', '2026-12-31')",
        curso,
        asignatura,
        catedraticoId);
    jdbc.update(
        "INSERT INTO curso_estudiante (id, curso_id, estudiante_id, seccion, inscrito_por_id)"
            + " VALUES (?, ?, ?, 'A', ?)",
        inscripcion,
        curso,
        estudiante,
        catedraticoId);
    jdbc.update(
        "INSERT INTO asignacion_puente (id, curso_estudiante_id, puente_id, asignado_por_id)"
            + " VALUES (?, ?, ?, ?)",
        UUID.randomUUID(),
        inscripcion,
        puente,
        catedraticoId);
  }

  private UUID ordenProgramada(UUID puente, UUID aceptadoPor, LocalDate fecha) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO orden_mantenimiento (id, puente_id, tipo, estado, descripcion,
          prioridad_sugerida, prioridad_confirmada, aceptado_por_id, aceptado_en, fecha_programada)
        VALUES (?, ?, 'PREVENTIVO', 'PROGRAMADA', 'Limpiar drenajes', 'MEDIA', 'MEDIA', ?, ?, ?)
        """,
        id,
        puente,
        aceptadoPor,
        Timestamp.from(AHORA.minus(Duration.ofDays(10))),
        fecha);
    return id;
  }

  private UUID archivo(UUID inspeccion, String tipo, String estado) throws IOException {
    UUID id = UUID.randomUUID();
    boolean foto = "FOTO_INSPECCION".equals(tipo);
    String clave = id.toString();
    almacenamiento.guardar(clave, new ByteArrayInputStream(new byte[] {1}), 1, "image/jpeg");
    if (foto) {
      almacenamiento.guardar(
          clave + "-mini", new ByteArrayInputStream(new byte[] {1}), 1, "image/jpeg");
    }
    jdbc.update(
        """
        INSERT INTO archivo (id, tipo_uso, categoria_foto, nombre_almacenado,
          clave_objeto_original, clave_objeto_miniatura, mime_real, tamano_bytes, checksum_sha256,
          ancho_px, alto_px, ubicacion_captura, capturado_en, subido_por_id, confirmado_en,
          estado, retirado_en)
        VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?,
          CASE WHEN ? THEN ST_SetSRID(ST_MakePoint(-90.6, 14.5), 4326)::geography END,
          ?, ?, ?, ?, ?)
        """,
        id,
        tipo,
        foto ? "ACCESO" : null,
        clave,
        clave,
        foto ? clave + "-mini" : null,
        foto ? "image/jpeg" : "application/pdf",
        "f".repeat(64),
        foto ? 10 : null,
        foto ? 10 : null,
        foto,
        foto ? Timestamp.from(AHORA.minus(Duration.ofDays(300))) : null,
        estudiante,
        Timestamp.from(AHORA.minus(Duration.ofDays(300))),
        estado,
        "RETIRADO".equals(estado) ? Timestamp.from(AHORA.minus(Duration.ofDays(250))) : null);
    jdbc.update(
        "INSERT INTO archivo_inspeccion (archivo_id, tipo_uso, inspeccion_id) VALUES (?, ?, ?)",
        id,
        tipo,
        inspeccion);
    return id;
  }

  private UUID operacion(String estado, String entidad, Instant expiraEn) {
    UUID id = UUID.randomUUID();
    boolean completada = "COMPLETADA".equals(estado);
    jdbc.update(
        """
        INSERT INTO operacion_idempotente (id, usuario_id, clave, metodo, ruta, hash_solicitud,
          estado, codigo_respuesta, respuesta, entidad, completado_en, expira_en)
        VALUES (?, ?, ?, 'POST', '/api/v1/prueba', ?, ?, ?, '{}'::jsonb, ?, ?, ?)
        """,
        id,
        estudiante,
        id.toString(),
        "a".repeat(64),
        estado,
        completada ? 201 : null,
        entidad,
        completada ? Timestamp.from(AHORA.minus(Duration.ofDays(8))) : null,
        Timestamp.from(expiraEn));
    return id;
  }

  private static String clave(UUID archivo) {
    return archivo.toString();
  }

  private String estadoArchivo(UUID archivo) {
    return jdbc.queryForObject("SELECT estado FROM archivo WHERE id = ?", String.class, archivo);
  }

  private List<String> avisos(UUID usuario, String tipo) {
    return jdbc.queryForList(
        "SELECT entidad_id::text FROM notificacion WHERE usuario_id = ? AND tipo = ?",
        String.class,
        usuario,
        tipo);
  }

  private int ejecuciones(String proceso) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM auditoria WHERE accion = 'EJECUTAR_PROCESO'"
                + " AND proceso_automatico = ? AND usuario_id IS NULL",
            Integer.class,
            proceso);
    return total == null ? 0 : total;
  }
}
