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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  OperacionIdempotenteService.class,
  InspeccionService.class,
  AuditoriaWriter.class,
  gt.usac.cunoc.sgp.inspeccion.service.FotografiaService.class,
  gt.usac.cunoc.sgp.inspeccion.validation.ValidadorAnclajeFotografico.class,
  gt.usac.cunoc.sgp.archivo.mapper.FotografiaMapperImpl.class,
  InspectorImagenesImageIo.class,
  ProcesadorImagenes.class,
  CargasImagenService.class,
  ObjetosImagenService.class,
  FotografiaFlujoIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class FotografiaFlujoIntegrationTest {
  static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

  static class Storage implements AlmacenamientoArchivos {
    final AlmacenamientoLocal real;
    boolean fallarMiniatura;
    Runnable despuesOriginal;

    Storage() throws java.io.IOException {
      real =
          new AlmacenamientoLocal(java.nio.file.Files.createTempDirectory("sgp-api-fotos-test-"));
    }

    public void guardar(String k, java.io.InputStream in, long n, String mime)
        throws java.io.IOException {
      if (fallarMiniatura && k.endsWith("-mini.jpg"))
        throw new java.io.IOException("fallo de prueba");
      real.guardar(k, in, n, mime);
      if (!k.endsWith("-mini.jpg") && despuesOriginal != null) {
        var accion = despuesOriginal;
        despuesOriginal = null;
        accion.run();
      }
    }

    public java.io.InputStream abrir(String k) throws java.io.IOException {
      return real.abrir(k);
    }

    public boolean existe(String k) throws java.io.IOException {
      return real.existe(k);
    }

    public void eliminar(String k) throws java.io.IOException {
      real.eliminar(k);
    }
  }

  @TestConfiguration
  static class Config {
    @Bean
    Storage storage() throws java.io.IOException {
      return new Storage();
    }

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
  @Autowired gt.usac.cunoc.sgp.inspeccion.service.FotografiaService fotos;
  @Autowired Storage storage;
  UUID inspeccion;
  @Autowired InspeccionService inspecciones;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager tm;
  @TempDir Path temporal;
  UUID usuario, version, puente;

  @BeforeEach
  void preparar() {
    storage.fallarMiniatura = false;
    storage.despuesOriginal = null;
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

  org.springframework.mock.web.MockMultipartFile imagen(String formato) throws Exception {
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(1800, 900, java.awt.image.BufferedImage.TYPE_INT_RGB),
        formato,
        bytes);
    return new org.springframework.mock.web.MockMultipartFile(
        "archivo", "foto.jpg", "text/plain", bytes.toByteArray());
  }

  AdjuntarFotografiaRequest metadatos() {
    return new AdjuntarFotografiaRequest(
        gt.usac.cunoc.sgp.common.util.UuidV7Generator.generate(),
        "ACCESO",
        14.0,
        -90.0,
        AHORA.minusSeconds(10),
        null,
        null);
  }

  UUID iniciar() {
    return inspecciones
        .iniciarInspeccion(inicio(UUID.randomUUID()), usuario, UUID.randomUUID().toString())
        .id();
  }

  @Test
  void recorridoHu024CargaListadoOriginalMiniaturaYReintento() throws Exception {
    var id = iniciar();
    var r = metadatos();
    var fichero = imagen("jpeg");
    var dto = fotos.adjuntar(id, usuario, r, fichero, "foto");
    assertThat(dto.anchoPx()).isEqualTo(1600);
    assertThat(dto.altoPx()).isEqualTo(800);
    assertThat(fotos.listar(id, usuario)).containsExactly(dto);
    try (var in = fotos.contenido(id, r.id(), usuario, false)) {
      assertThat(javax.imageio.ImageIO.read(in).getWidth()).isEqualTo(1600);
    }
    try (var in = fotos.contenido(id, r.id(), usuario, true)) {
      var mini = javax.imageio.ImageIO.read(in);
      assertThat(mini.getWidth()).isEqualTo(300);
      assertThat(mini.getHeight()).isEqualTo(150);
    }
    assertThat(fotos.adjuntar(id, usuario, r, fichero, "foto")).isEqualTo(dto);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from archivo_inspeccion where inspeccion_id=?", Integer.class, id))
        .isEqualTo(1);
    jdbc.update("update inspeccion set estado='ENVIADA' where id=?", id);
    assertThat(fotos.adjuntar(id, usuario, r, fichero, "foto")).isEqualTo(dto);
  }

  @Test
  void rechazaAutorAjenoYTodosLosEstadosNoEditables() throws Exception {
    var id = iniciar();
    var fichero = imagen("jpeg");
    assertThatThrownBy(() -> fotos.adjuntar(id, UUID.randomUUID(), metadatos(), fichero, "otra"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
    assertThatThrownBy(() -> fotos.listar(id, UUID.randomUUID())).isInstanceOf(ApiException.class);
    for (String estado :
        List.of("ENVIADA", "EN_REVISION", "CAMBIOS_SOLICITADOS", "PUBLICADA", "RECHAZADA")) {
      jdbc.update(
          "update inspeccion set estado=?,publicado_en=CURRENT_TIMESTAMP where id=?", estado, id);
      assertThatThrownBy(
              () -> fotos.adjuntar(id, usuario, metadatos(), fichero, UUID.randomUUID().toString()))
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }
  }

  @Test
  void rechazaMetadatosAusentesCategoriasGpsInvalidosYPng() throws Exception {
    var id = iniciar();
    var f = imagen("jpeg");
    var r = metadatos();
    for (var invalido :
        List.of(
            new AdjuntarFotografiaRequest(r.id(), null, 14.0, -90.0, r.capturadoEn(), null, null),
            new AdjuntarFotografiaRequest(
                r.id(), "INVALIDA", 14.0, -90.0, r.capturadoEn(), null, null),
            new AdjuntarFotografiaRequest(
                r.id(), "ACCESO", null, -90.0, r.capturadoEn(), null, null),
            new AdjuntarFotografiaRequest(
                r.id(), "ACCESO", 91.0, -90.0, r.capturadoEn(), null, null),
            new AdjuntarFotografiaRequest(r.id(), "ACCESO", 14.0, -90.0, null, null, null))) {
      assertThatThrownBy(() -> fotos.adjuntar(id, usuario, invalido, f, "invalidos"))
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(422));
    }
    assertThatThrownBy(
            () ->
                fotos.adjuntar(
                    id,
                    usuario,
                    r,
                    new org.springframework.mock.web.MockMultipartFile("archivo", new byte[0]),
                    "vacia"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(422));
    assertThatThrownBy(() -> fotos.adjuntar(id, usuario, r, imagen("png"), "png"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void aceptaWebpYCincoCategorias() throws Exception {
    var id = iniciar();
    byte[] bytes;
    try (var in = getClass().getResourceAsStream("/archivo/imagen.webp")) {
      bytes = in.readAllBytes();
    }
    for (String categoria :
        List.of("ACCESO", "SUPERESTRUCTURA", "SUBESTRUCTURA", "CAUCE", "OTRA")) {
      var r =
          new AdjuntarFotografiaRequest(
              gt.usac.cunoc.sgp.common.util.UuidV7Generator.generate(),
              categoria,
              14.0,
              -90.0,
              AHORA,
              null,
              null);
      var dto =
          fotos.adjuntar(
              id,
              usuario,
              r,
              new org.springframework.mock.web.MockMultipartFile(
                  "archivo", "imagen.webp", "image/webp", bytes),
              categoria);
      assertThat(dto.categoria().name()).isEqualTo(categoria);
    }
    assertThat(fotos.listar(id, usuario)).hasSize(5);
  }

  @Test
  void referenciaUsaVersionFijadaYValidaInstanciaPersistida() throws Exception {
    jdbc.update(
        "update version_formulario set esquema=cast(? as jsonb) where id=?",
        "{\"properties\":{\"vigas\":{\"type\":\"object\"},\"pilas\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}}}}",
        version);
    var id = iniciar();
    var base = metadatos();
    var f = imagen("jpeg");
    var valido =
        new AdjuntarFotografiaRequest(base.id(), "ACCESO", 14.0, -90.0, AHORA, "/vigas", null);
    fotos.adjuntar(id, usuario, valido, f, "anclaje");
    var instancia = UUID.randomUUID();
    var repetible =
        new AdjuntarFotografiaRequest(
            gt.usac.cunoc.sgp.common.util.UuidV7Generator.generate(),
            "ACCESO",
            14.0,
            -90.0,
            AHORA,
            "/pilas",
            instancia);
    assertThatThrownBy(() -> fotos.adjuntar(id, usuario, repetible, f, "repetible"))
        .isInstanceOf(ApiException.class);
    jdbc.update(
        "update inspeccion set datos=cast(? as jsonb) where id=?",
        "{\"pilas\":[{\"instancia_id\":\"" + instancia + "\"}]}",
        id);
    fotos.adjuntar(id, usuario, repetible, f, "repetible");
    assertThat(fotos.listar(id, usuario)).hasSize(2);
  }

  @Test
  void compensaFalloParcialYRevalidaEstadoAntesDeConfirmar() throws Exception {
    var id = iniciar();
    var r = metadatos();
    var f = imagen("jpeg");
    storage.fallarMiniatura = true;
    assertThatThrownBy(() -> fotos.adjuntar(id, usuario, r, f, "fallo"))
        .isInstanceOf(ApiException.class);
    assertThat(fotos.listar(id, usuario)).isEmpty();
    storage.fallarMiniatura = false;
    fotos.adjuntar(id, usuario, r, f, "fallo");
    storage.despuesOriginal =
        () -> jdbc.update("update inspeccion set estado='ENVIADA' where id=?", id);
    assertThatThrownBy(() -> fotos.adjuntar(id, usuario, metadatos(), f, "cambio"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
    assertThat(fotos.listar(id, usuario)).hasSize(1);
  }

  @Test
  void cargaConcurrenteNoDuplicaNiConfundeElIntentoEnCurso() throws Exception {
    var id = iniciar();
    var r = metadatos();
    var f = imagen("jpeg");
    var escrito = new java.util.concurrent.CountDownLatch(1);
    var continuar = new java.util.concurrent.CountDownLatch(1);
    storage.despuesOriginal =
        () -> {
          escrito.countDown();
          try {
            if (!continuar.await(20, java.util.concurrent.TimeUnit.SECONDS))
              throw new IllegalStateException("timeout de prueba");
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
        };
    try (var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var primera = pool.submit(() -> fotos.adjuntar(id, usuario, r, f, "concurrente"));
      try {
        assertThat(escrito.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> fotos.adjuntar(id, usuario, r, f, "concurrente"))
            .isInstanceOfSatisfying(
                ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
      } finally {
        continuar.countDown();
      }
      var dto = primera.get(20, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(fotos.adjuntar(id, usuario, r, f, "concurrente")).isEqualTo(dto);
    }
    assertThat(fotos.listar(id, usuario)).hasSize(1);
  }
}
