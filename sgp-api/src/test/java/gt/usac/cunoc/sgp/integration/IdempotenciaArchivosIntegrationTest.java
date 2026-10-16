package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import gt.usac.cunoc.sgp.archivo.service.*;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.*;
import gt.usac.cunoc.sgp.inspeccion.service.InspeccionService;
import gt.usac.cunoc.sgp.sync.model.*;
import gt.usac.cunoc.sgp.sync.service.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  OperacionIdempotenteService.class,
  InspeccionService.class,
  AuditoriaWriter.class,
  IdempotenciaArchivosIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class IdempotenciaArchivosIntegrationTest {
  static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

  @TestConfiguration
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }
  }

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

  @Autowired OperacionIdempotenteService operaciones;
  @Autowired InspeccionService inspecciones;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager tm;
  @TempDir Path temporal;
  UUID usuario, version, puente;

  @BeforeEach
  void preparar() {
    usuario = UUID.randomUUID();
    version = UUID.randomUUID();
    puente = UUID.randomUUID();
    jdbc.update("UPDATE version_formulario SET activa=false WHERE activa");
    jdbc.update(
        "INSERT INTO usuario(id,email,password_hash,rol_id,verificado,activado,activo) SELECT ?,?,'test',id,true,true,true FROM rol WHERE nombre='CATEDRATICO'",
        usuario,
        usuario + "@test.com");
    jdbc.update(
        "INSERT INTO version_formulario(id,codigo,esquema,creado_por_id,estado,activa,hash_contenido,version_algoritmo_ic,publicado_por_id,publicado_en) VALUES (?,?,'{}',?,'PUBLICADA',true,?,'test',?,CURRENT_TIMESTAMP)",
        version,
        "test-" + version,
        usuario,
        "a".repeat(64),
        usuario);
    jdbc.update(
        "INSERT INTO puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id) SELECT ?,?,COALESCE((SELECT max(correlativo_municipal)+1 FROM puente),1),id,'test','test',ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,? FROM municipio WHERE codigo_ine='0114'",
        puente,
        "T-" + puente,
        usuario);
  }

  IniciarInspeccionRequest inicio(UUID id) {
    return new IniciarInspeccionRequest(
        id, puente, LocalDate.of(2026, 10, 15), "dispositivo", null, null, null, null, false, null);
  }

  SolicitudOperacion foto(String clave, UUID id) {
    return new SolicitudOperacion(
        usuario,
        clave,
        "POST",
        "/api/v1/inspecciones/" + UUID.randomUUID() + "/fotografias",
        "a".repeat(64),
        "archivo",
        id);
  }

  void conflicto(Runnable accion) {
    assertThatThrownBy(accion::run)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
  }

  @Test
  void inicioReproduceRespuestaYRechazaPayloadDiferente() {
    var request = inicio(UUID.randomUUID());
    var primera = inspecciones.iniciarInspeccion(request, usuario, "inicio");
    assertThat(inspecciones.iniciarInspeccion(request, usuario, "inicio")).isEqualTo(primera);
    var diferente =
        new IniciarInspeccionRequest(
            request.id(),
            puente,
            request.fechaInspeccion(),
            "otro-dispositivo",
            null,
            null,
            null,
            null,
            false,
            null);
    conflicto(() -> inspecciones.iniciarInspeccion(diferente, usuario, "inicio"));
  }

  @Test
  void fotografiaEInicioNoPuedenConfundirseEnNingunaDireccion() {
    var f = foto("compartida", UUID.randomUUID());
    var reserva = operaciones.reservar(f, json.createObjectNode());
    new TransactionTemplate(tm)
        .executeWithoutResult(
            t ->
                operaciones.completar(
                    reserva,
                    json.createObjectNode().put("id", f.entidadId().toString()),
                    201,
                    f.entidadId()));
    conflicto(
        () -> inspecciones.iniciarInspeccion(inicio(UUID.randomUUID()), usuario, "compartida"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inspeccion WHERE autor_id=?", Integer.class, usuario))
        .isZero();
    inspecciones.iniciarInspeccion(inicio(UUID.randomUUID()), usuario, "otra");
    conflicto(() -> operaciones.reservar(foto("otra", UUID.randomUUID()), json.createObjectNode()));
  }

  @Test
  void solicitudesConcurrentesCreanUnaInspeccionYUnaOperacion() throws Exception {
    var request = inicio(UUID.randomUUID());
    try (var pool = Executors.newFixedThreadPool(4)) {
      var tareas = new ArrayList<Callable<IniciarInspeccionResponse>>();
      for (int i = 0; i < 4; i++)
        tareas.add(() -> inspecciones.iniciarInspeccion(request, usuario, "concurrente"));
      for (var resultado : pool.invokeAll(tareas))
        assertThat(resultado.get(30, TimeUnit.SECONDS).id()).isEqualTo(request.id());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inspeccion WHERE autor_id=?", Integer.class, usuario))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM operacion_idempotente WHERE usuario_id=?",
                Integer.class,
                usuario))
        .isEqualTo(1);
  }

  @Test
  void reservasConcurrentesSoloPermitenUnIntento() throws Exception {
    var solicitud = foto("foto-concurrente", UUID.randomUUID());
    AtomicInteger ganadoras = new AtomicInteger(), rechazadas = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(4)) {
      var tareas = new ArrayList<Callable<Void>>();
      for (int i = 0; i < 4; i++)
        tareas.add(
            () -> {
              try {
                operaciones.reservar(solicitud, json.createObjectNode());
                ganadoras.incrementAndGet();
              } catch (ApiException e) {
                assertThat(e.getStatus().value()).isEqualTo(409);
                rechazadas.incrementAndGet();
              }
              return null;
            });
      for (var resultado : pool.invokeAll(tareas)) resultado.get(30, TimeUnit.SECONDS);
    }
    assertThat(ganadoras.get()).isEqualTo(1);
    assertThat(rechazadas.get()).isEqualTo(3);
  }

  @Test
  void reservaRechazaMetodoRutaHuellaEntidadEIdentificadorDiferentes() {
    var s = foto("identidad", UUID.randomUUID());
    operaciones.reservar(s, json.createObjectNode());
    var diferentes =
        List.of(
            new SolicitudOperacion(
                usuario, s.clave(), "PUT", s.ruta(), s.hashSolicitud(), s.entidad(), s.entidadId()),
            new SolicitudOperacion(
                usuario,
                s.clave(),
                "POST",
                s.ruta() + "/otro",
                s.hashSolicitud(),
                s.entidad(),
                s.entidadId()),
            new SolicitudOperacion(
                usuario, s.clave(), "POST", s.ruta(), "b".repeat(64), s.entidad(), s.entidadId()),
            new SolicitudOperacion(
                usuario,
                s.clave(),
                "POST",
                s.ruta(),
                s.hashSolicitud(),
                "inspeccion",
                s.entidadId()),
            new SolicitudOperacion(
                usuario,
                s.clave(),
                "POST",
                s.ruta(),
                s.hashSolicitud(),
                s.entidad(),
                UUID.randomUUID()));
    for (var d : diferentes) conflicto(() -> operaciones.reservar(d, json.createObjectNode()));
  }

  @Test
  void recuperacionDuraderaLimpiaHuerfanosYExcluyeIntentosActivosYConfirmados() throws Exception {
    var storage = new AlmacenamientoLocal(temporal);
    var objetos = new ObjetosImagenService(storage);
    var carga = new CargasImagenService(operaciones, objetos, json);
    var entrada = temporal.resolve("entrada.jpg");
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(50, 25, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "jpeg",
        entrada.toFile());
    var imagen = new ProcesadorImagenes(new InspectorImagenesImageIo()).procesar(entrada);
    var solicitud = foto("recuperar", UUID.randomUUID());
    var preparada = carga.preparar(solicitud, imagen);
    assertThat(storage.existe(preparada.claves().original())).isTrue();
    AtomicInteger limpiezas = new AtomicInteger();
    assertThat(
            operaciones.recuperar(
                preparada.reserva().operacionId(), r -> limpiezas.incrementAndGet()))
        .isFalse();
    assertThat(limpiezas.get()).isZero();
    jdbc.update(
        "UPDATE operacion_idempotente SET expira_en=? WHERE id=?",
        java.sql.Timestamp.from(AHORA.minusSeconds(1)),
        preparada.reserva().operacionId());
    assertThatThrownBy(
            () ->
                operaciones.recuperar(
                    preparada.reserva().operacionId(),
                    r -> {
                      throw new IllegalStateException("fallo de almacenamiento");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(operaciones.vencidas()).contains(preparada.reserva().operacionId());
    new RecuperacionCargasService(operaciones, objetos).recuperarVencidas();
    assertThat(storage.existe(preparada.claves().original())).isFalse();
    assertThat(storage.existe(preparada.claves().miniatura())).isFalse();
    var segunda = carga.preparar(solicitud, imagen);
    assertThat(segunda.reserva().intentoId()).isNotEqualTo(preparada.reserva().intentoId());
    conflicto(
        () ->
            new TransactionTemplate(tm)
                .executeWithoutResult(
                    t ->
                        operaciones.completar(
                            preparada.reserva(),
                            json.createObjectNode(),
                            201,
                            solicitud.entidadId())));
    assertThatThrownBy(
            () ->
                new TransactionTemplate(tm)
                    .executeWithoutResult(
                        t -> {
                          operaciones.completar(
                              segunda.reserva(),
                              json.createObjectNode().put("ok", true),
                              201,
                              solicitud.entidadId());
                          throw new IllegalStateException("rollback asociación");
                        }))
        .isInstanceOf(IllegalStateException.class);
    conflicto(() -> operaciones.reservar(solicitud, json.createObjectNode()));
    new TransactionTemplate(tm)
        .executeWithoutResult(
            t ->
                operaciones.completar(
                    segunda.reserva(),
                    json.createObjectNode().put("ok", true),
                    201,
                    solicitud.entidadId()));
    assertThat(carga.preparar(solicitud, imagen).reserva().respuesta().path("ok").asBoolean())
        .isTrue();
    assertThat(
            operaciones.recuperar(
                segunda.reserva().operacionId(),
                r -> {
                  throw new AssertionError("no limpiar confirmados");
                }))
        .isFalse();
    assertThat(
            operaciones.compensar(
                segunda.reserva(),
                () -> {
                  throw new AssertionError("Nunca eliminar una carga confirmada");
                }))
        .isFalse();
    assertThat(storage.existe(segunda.claves().original())).isTrue();
  }
}
