package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoLocal;
import gt.usac.cunoc.sgp.archivo.service.InspectorImagenesImageIo;
import gt.usac.cunoc.sgp.archivo.service.ObjetosImagenService;
import gt.usac.cunoc.sgp.archivo.service.ProcesadorImagenes;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.mantenimiento.dto.CerrarComoEjecutadaRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.CrearOrdenMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.EvidenciaMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.IniciarEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.ProgramarOrdenRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.RegistrarEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.RetirarEvidenciaRequest;
import gt.usac.cunoc.sgp.mantenimiento.mapper.OrdenMantenimientoMapperImpl;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.service.EjecucionOrdenService;
import gt.usac.cunoc.sgp.mantenimiento.service.OrdenMantenimientoService;
import gt.usac.cunoc.sgp.mantenimiento.service.TransicionOrdenService;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Ejecución y cierre de una orden con evidencia contra PostgreSQL/PostGIS real (HU-048). */
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  OrdenMantenimientoService.class,
  TransicionOrdenService.class,
  EjecucionOrdenService.class,
  NotificacionService.class,
  AuditoriaWriter.class,
  OrdenMantenimientoMapperImpl.class,
  InspectorImagenesImageIo.class,
  ProcesadorImagenes.class,
  ObjetosImagenService.class,
  EjecucionOrdenMantenimientoIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class EjecucionOrdenMantenimientoIntegrationTest {

  /** 15:00 UTC del 20/10/2026, las 09:00 en Guatemala. */
  private static final Instant AHORA = Instant.parse("2026-10-20T15:00:00Z");

  private static final LocalDate HOY = LocalDate.of(2026, 10, 20);
  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000004831");
  private static final UUID CATEDRATICO_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000004832");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu048_test")
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

  /** Almacenamiento local que puede simular que el servicio de objetos no responde. */
  static class Almacenamiento implements AlmacenamientoArchivos {
    final Path directorio;
    final AlmacenamientoLocal real;
    volatile boolean caido;

    Almacenamiento() throws IOException {
      directorio = Files.createTempDirectory("sgp-hu048-");
      real = new AlmacenamientoLocal(directorio);
    }

    public void guardar(String clave, InputStream contenido, long tamano, String mime)
        throws IOException {
      exigirDisponible();
      real.guardar(clave, contenido, tamano, mime);
    }

    public InputStream abrir(String clave) throws IOException {
      exigirDisponible();
      return real.abrir(clave);
    }

    public boolean existe(String clave) throws IOException {
      exigirDisponible();
      return real.existe(clave);
    }

    public void eliminar(String clave) throws IOException {
      real.eliminar(clave);
    }

    long objetos() throws IOException {
      try (Stream<Path> rutas = Files.list(directorio)) {
        return rutas.count();
      }
    }

    private void exigirDisponible() throws IOException {
      if (caido) {
        throw new IOException("almacenamiento de prueba no disponible");
      }
    }
  }

  @Autowired private OrdenMantenimientoService ordenes;
  @Autowired private TransicionOrdenService transiciones;
  @Autowired private EjecucionOrdenService ejecucion;
  @Autowired private Almacenamiento almacenamiento;
  @Autowired private JdbcTemplate jdbc;

  private UUID puenteId;

  @BeforeEach
  void prepararDatos() {
    almacenamiento.caido = false;
    insertarUsuario(ADMINISTRADOR_ID, "admin.hu048@ejemplo.com", "ADMINISTRADOR");
    insertarUsuario(CATEDRATICO_ID, "catedratico.hu048@ejemplo.com", "CATEDRATICO");
    puenteId = insertarPuente();
  }

  @Test
  void unaOrdenSeCierraComoEjecutadaConFechaResponsableYEvidencia() throws Exception {
    UUID orden = enEjecucion();
    long objetosAntes = almacenamiento.objetos();

    EvidenciaMantenimientoResponse evidencia =
        ejecucion.adjuntarEvidencia(
            orden, "/superestructura/drenaje", imagen("jpeg", "foto.jpg"), CATEDRATICO_ID);
    var registro =
        ejecucion.registrarEjecucion(
            orden, new RegistrarEjecucionRequest(HOY, CATEDRATICO_ID, null), CATEDRATICO_ID);
    var cerrada =
        transiciones.cerrarComoEjecutada(
            orden, new CerrarComoEjecutadaRequest(2L), ADMINISTRADOR_ID);

    assertThat(cerrada.estado()).isEqualTo(EstadoOrden.EJECUTADA);
    assertThat(registro.responsableUsuarioEmail()).isEqualTo("catedratico.hu048@ejemplo.com");
    assertThat(almacenamiento.objetos()).isEqualTo(objetosAntes + 2);

    var archivo =
        jdbc.queryForMap(
            "SELECT tipo_uso, estado, nombre_original, nombre_almacenado, clave_objeto_miniatura,"
                + " mime_real FROM archivo WHERE id = ?",
            evidencia.id());
    assertThat(archivo.get("tipo_uso")).isEqualTo("EVIDENCIA_MANTENIMIENTO");
    assertThat(archivo.get("estado")).isEqualTo("DISPONIBLE");
    assertThat(archivo.get("nombre_original")).isNull();
    assertThat((String) archivo.get("nombre_almacenado")).isNotEqualTo("foto.jpg");
    assertThat(archivo.get("clave_objeto_miniatura")).isNotNull();
    assertThat(archivo.get("mime_real")).isEqualTo("image/jpeg");
    assertThat(
            jdbc.queryForObject(
                "SELECT elemento_ref FROM archivo_evidencia_mantenimiento WHERE archivo_id = ?",
                String.class,
                evidencia.id()))
        .isEqualTo("/superestructura/drenaje");

    var fila =
        jdbc.queryForMap(
            "SELECT fecha_ejecucion, responsable_usuario_id FROM orden_mantenimiento_ejecucion"
                + " WHERE orden_mantenimiento_id = ?",
            orden);
    assertThat(fila.get("fecha_ejecucion").toString()).isEqualTo("2026-10-20");
    assertThat(fila.get("responsable_usuario_id")).isEqualTo(CATEDRATICO_ID);

    var cierre =
        jdbc.queryForMap(
            "SELECT realizado_por_id FROM historial_estado_orden"
                + " WHERE orden_mantenimiento_id = ? AND estado_nuevo = 'EJECUTADA'",
            orden);
    assertThat(cierre.get("realizado_por_id")).isEqualTo(ADMINISTRADOR_ID);
    assertThat(ordenes.historial(orden))
        .extracting(h -> h.estadoAnterior() + ">" + h.estadoNuevo())
        .containsExactly(
            "null>PROPUESTA",
            "PROPUESTA>PROGRAMADA",
            "PROGRAMADA>EN_EJECUCION",
            "EN_EJECUCION>EJECUTADA");

    assertThat(contarAuditoria("archivo", evidencia.id(), "CREAR")).isEqualTo(1);
    assertThat(contarAuditoria("orden_mantenimiento_ejecucion", orden, "MODIFICAR")).isEqualTo(1);
    assertThat(contarAuditoria("orden_mantenimiento", orden, "CAMBIAR_ESTADO")).isEqualTo(3);
    try (InputStream miniatura = ejecucion.contenidoEvidencia(orden, evidencia.id(), true)) {
      assertThat(ImageIO.read(miniatura).getWidth()).isEqualTo(300);
    }
  }

  @Test
  void unArchivoQueNoEsJpegNiWebpSeRechazaSinGuardarNada() throws Exception {
    UUID orden = enEjecucion();
    long objetosAntes = almacenamiento.objetos();
    var texto =
        new MockMultipartFile(
            "archivo", "evidencia.jpg", "image/jpeg", "no soy una imagen".getBytes(UTF8));

    assertError(
        () -> ejecucion.adjuntarEvidencia(orden, null, texto, CATEDRATICO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "contenido_fotografico_invalido");
    assertError(
        () -> ejecucion.adjuntarEvidencia(orden, null, imagen("png", "foto.jpg"), CATEDRATICO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "contenido_fotografico_invalido");

    assertThat(contarEvidencias(orden)).isZero();
    assertThat(almacenamiento.objetos()).isEqualTo(objetosAntes);
  }

  @Test
  void unaEvidenciaRetiradaQuedaConSuMotivoYNoCuentaParaElCierre() throws Exception {
    UUID orden = enEjecucion();
    EvidenciaMantenimientoResponse evidencia =
        ejecucion.adjuntarEvidencia(orden, null, imagen("jpeg", "foto.jpg"), CATEDRATICO_ID);
    ejecucion.registrarEjecucion(
        orden, new RegistrarEjecucionRequest(HOY, null, "Constructora Los Altos"), CATEDRATICO_ID);

    ejecucion.retirarEvidencia(
        orden, evidencia.id(), new RetirarEvidenciaRequest("Foto de otro puente"), CATEDRATICO_ID);

    var archivo =
        jdbc.queryForMap(
            "SELECT estado, motivo_retiro, retirado_en FROM archivo WHERE id = ?", evidencia.id());
    assertThat(archivo.get("estado")).isEqualTo("RETIRADO");
    assertThat(archivo.get("motivo_retiro")).isEqualTo("Foto de otro puente");
    assertThat(archivo.get("retirado_en")).isNotNull();
    assertThat(ejecucion.listarEvidencias(orden)).isEmpty();
    assertThat(contarAuditoria("archivo", evidencia.id(), "CAMBIAR_ESTADO")).isEqualTo(1);

    assertThatThrownBy(
            () ->
                transiciones.cerrarComoEjecutada(
                    orden, new CerrarComoEjecutadaRequest(2L), CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo("cierre_incompleto");
              assertThat(e.getErrores())
                  .extracting(error -> error.get("campo"))
                  .containsExactly("evidencias");
            });
    assertThat(ordenes.obtener(orden).estado()).isEqualTo(EstadoOrden.EN_EJECUCION);
  }

  @Test
  void sinFechaDeEjecucionLaOrdenNoSeCierra() throws Exception {
    UUID orden = enEjecucion();
    ejecucion.adjuntarEvidencia(orden, null, imagen("jpeg", "foto.jpg"), CATEDRATICO_ID);

    assertThatThrownBy(
            () ->
                transiciones.cerrarComoEjecutada(
                    orden, new CerrarComoEjecutadaRequest(2L), CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.getErrores())
                    .extracting(error -> error.get("campo"))
                    .containsExactly("fechaEjecucion"));
    assertThat(ordenes.obtener(orden).estado()).isEqualTo(EstadoOrden.EN_EJECUCION);
    assertThat(ordenes.historial(orden)).hasSize(3);
  }

  @Test
  void laFechaDeEjecucionNoPuedeSerFuturaNiAnteriorAlInicio() {
    UUID orden = enEjecucion();

    assertError(
        () ->
            ejecucion.registrarEjecucion(
                orden,
                new RegistrarEjecucionRequest(HOY.plusDays(1), CATEDRATICO_ID, null),
                CATEDRATICO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
    assertError(
        () ->
            ejecucion.registrarEjecucion(
                orden,
                new RegistrarEjecucionRequest(HOY.minusDays(1), CATEDRATICO_ID, null),
                CATEDRATICO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
    assertError(
        () ->
            ejecucion.registrarEjecucion(
                orden, new RegistrarEjecucionRequest(HOY, null, "   "), CATEDRATICO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
    assertThat(ejecucion.obtenerEjecucion(orden).fechaEjecucion()).isNull();
  }

  @Test
  void loEjecutadoEsInmutable() throws Exception {
    UUID orden = enEjecucion();
    EvidenciaMantenimientoResponse evidencia =
        ejecucion.adjuntarEvidencia(orden, null, imagen("jpeg", "foto.jpg"), CATEDRATICO_ID);
    ejecucion.registrarEjecucion(
        orden, new RegistrarEjecucionRequest(HOY, CATEDRATICO_ID, null), CATEDRATICO_ID);
    transiciones.cerrarComoEjecutada(orden, new CerrarComoEjecutadaRequest(2L), CATEDRATICO_ID);

    assertError(
        () ->
            ejecucion.registrarEjecucion(
                orden, new RegistrarEjecucionRequest(HOY, null, "Otra empresa"), CATEDRATICO_ID),
        HttpStatus.CONFLICT,
        "ejecucion_inmutable");
    assertError(
        () ->
            ejecucion.retirarEvidencia(
                orden, evidencia.id(), new RetirarEvidenciaRequest("Cambio"), CATEDRATICO_ID),
        HttpStatus.CONFLICT,
        "ejecucion_inmutable");
    assertError(
        () -> ejecucion.adjuntarEvidencia(orden, null, imagen("jpeg", "otra.jpg"), CATEDRATICO_ID),
        HttpStatus.CONFLICT,
        "ejecucion_inmutable");
    assertThat(ejecucion.listarEvidencias(orden)).hasSize(1);
    assertThat(ejecucion.obtenerEjecucion(orden).responsableNombre()).isNull();
  }

  @Test
  void siElAlmacenamientoNoRespondeSeResponde503YLaOrdenNoCambia() throws Exception {
    UUID orden = enEjecucion();
    ejecucion.adjuntarEvidencia(orden, null, imagen("jpeg", "foto.jpg"), CATEDRATICO_ID);
    ejecucion.registrarEjecucion(
        orden, new RegistrarEjecucionRequest(HOY, CATEDRATICO_ID, null), CATEDRATICO_ID);
    almacenamiento.caido = true;

    assertError(
        () -> ejecucion.adjuntarEvidencia(orden, null, imagen("jpeg", "otra.jpg"), CATEDRATICO_ID),
        HttpStatus.SERVICE_UNAVAILABLE,
        "almacenamiento_no_disponible");
    assertError(
        () ->
            transiciones.cerrarComoEjecutada(
                orden, new CerrarComoEjecutadaRequest(2L), CATEDRATICO_ID),
        HttpStatus.SERVICE_UNAVAILABLE,
        "almacenamiento_no_disponible");

    assertThat(contarEvidencias(orden)).isEqualTo(1);
    assertThat(ordenes.obtener(orden).estado()).isEqualTo(EstadoOrden.EN_EJECUCION);
    assertThat(ordenes.historial(orden)).hasSize(3);
  }

  @Test
  void soloUnaOrdenEnEjecucionAdmiteEvidencia() throws Exception {
    UUID orden =
        ordenes
            .crear(
                new CrearOrdenMantenimientoRequest(
                    puenteId, "CORRECTIVO", "Reparar barandal", "ALTA", null, null, null),
                ADMINISTRADOR_ID)
            .id();

    assertError(
        () -> ejecucion.adjuntarEvidencia(orden, null, imagen("jpeg", "foto.jpg"), CATEDRATICO_ID),
        HttpStatus.CONFLICT,
        "orden_no_en_ejecucion");
  }

  private UUID enEjecucion() {
    UUID orden =
        ordenes
            .crear(
                new CrearOrdenMantenimientoRequest(
                    puenteId, "PREVENTIVO", "Limpiar drenajes", "MEDIA", null, null, null),
                ADMINISTRADOR_ID)
            .id();
    transiciones.aceptarYProgramar(orden, new ProgramarOrdenRequest(HOY, 0L), CATEDRATICO_ID);
    transiciones.iniciarEjecucion(orden, new IniciarEjecucionRequest(1L), CATEDRATICO_ID);
    return orden;
  }

  private static final java.nio.charset.Charset UTF8 = StandardCharsets.UTF_8;

  private static MockMultipartFile imagen(String formato, String nombre) throws IOException {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1800, 900, BufferedImage.TYPE_INT_RGB), formato, bytes);
    return new MockMultipartFile("archivo", nombre, "image/jpeg", bytes.toByteArray());
  }

  private static void assertError(ThrowingCallable accion, HttpStatus status, String codigo) {
    assertThatThrownBy(accion)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(status);
              assertThat(e.getCode()).isEqualTo(codigo);
            });
  }

  private int contarEvidencias(UUID orden) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM archivo_evidencia_mantenimiento WHERE orden_mantenimiento_id = ?",
            Integer.class,
            orden);
    return total == null ? 0 : total;
  }

  private int contarAuditoria(String entidad, UUID entidadId, String accion) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM auditoria WHERE entidad = ? AND entidad_id = ? AND accion = ?",
            Integer.class,
            entidad,
            entidadId,
            accion);
    return total == null ? 0 : total;
  }

  private void insertarUsuario(UUID id, String email, String rol) {
    jdbc.update(
        """
        INSERT INTO usuario (id, email, password_hash, rol_id, verificado, activado, activo)
        SELECT ?, ?, 'hash-de-prueba', id, true, true, true FROM rol WHERE nombre = ?
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
          m.id, 'Puente HU-048', 'CA-1',
          ST_SetSRID(ST_MakePoint(-90.615, 14.481), 4326)::geography, ?
        FROM municipio m WHERE m.codigo_ine = '0114'
        """,
        id,
        "T-" + id,
        ADMINISTRADOR_ID);
    return id;
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
    Almacenamiento almacenamiento() throws IOException {
      return new Almacenamiento();
    }
  }
}
