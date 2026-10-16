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
  gt.usac.cunoc.sgp.inspeccion.service.PoliticaArchivosInspeccion.class,
  gt.usac.cunoc.sgp.inspeccion.service.AccesoFotografiasInspeccionService.class,
  gt.usac.cunoc.sgp.inspeccion.service.ReglasEvidenciaFotografica.class,
  RegistroFotografiasInspeccionJpa.class,
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
    volatile Runnable antesMiniatura;
    final Set<String> escritas = ConcurrentHashMap.newKeySet();

    Storage() throws java.io.IOException {
      real =
          new AlmacenamientoLocal(java.nio.file.Files.createTempDirectory("sgp-api-fotos-test-"));
    }

    public void guardar(String k, java.io.InputStream in, long n, String mime)
        throws java.io.IOException {
      if (fallarMiniatura && k.endsWith("-mini.jpg"))
        throw new java.io.IOException("fallo de prueba");
      if (k.endsWith("-mini.jpg") && antesMiniatura != null) antesMiniatura.run();
      real.guardar(k, in, n, mime);
      escritas.add(k);
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
      escritas.remove(k);
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
    storage.antesMiniatura = null;
    storage.escritas.clear();
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
  void rechazaFotoSesentaYUnoYCompensaLosObjetosDelIntento() throws Exception {
    var id = iniciar();
    sembrarFotografias(id, 60);

    assertThatThrownBy(() -> fotos.adjuntar(id, usuario, metadatos(), imagen("jpeg"), "limite"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus().value()).isEqualTo(422);
              assertThat(e.getCode()).isEqualTo("limite_fotografias_excedido");
            });

    assertThat(disponibles(id)).isEqualTo(60);
    assertThat(storage.escritas).isEmpty();
  }

  @Test
  void excluyeHistoricasYReproduceConfirmadaInclusoConSesentaYEstadoEnviada() throws Exception {
    var id = iniciar();
    sembrarFotografias(id, 61);
    var ids =
        jdbc.queryForList(
            "select archivo_id from archivo_inspeccion where inspeccion_id=? order by archivo_id limit 2",
            UUID.class,
            id);
    jdbc.update(
        "update archivo set estado='RETIRADO',retirado_en=CURRENT_TIMESTAMP where id=?",
        ids.get(0));
    jdbc.update(
        "update archivo set estado='PURGADO',purgado_en=CURRENT_TIMESTAMP where id=?", ids.get(1));
    var metadatos = metadatos();
    var fichero = imagen("jpeg");

    var dto = fotos.adjuntar(id, usuario, metadatos, fichero, "ultima");
    assertThat(disponibles(id)).isEqualTo(60);
    assertThat(fotos.listar(id, usuario)).hasSize(60);
    assertThat(fotos.adjuntar(id, usuario, metadatos, fichero, "ultima")).isEqualTo(dto);
    jdbc.update("update inspeccion set estado='ENVIADA' where id=?", id);
    assertThat(fotos.adjuntar(id, usuario, metadatos, fichero, "ultima")).isEqualTo(dto);
    assertThat(storage.escritas).hasSize(2);
    assertThat(disponibles(id)).isEqualTo(60);
  }

  @Test
  void dosCargasDistintasDesdeCincuentaYNueveSoloConfirmanUna() throws Exception {
    var id = iniciar();
    sembrarFotografias(id, 59);
    var listas = new CountDownLatch(2);
    storage.antesMiniatura =
        () -> {
          listas.countDown();
          try {
            if (!listas.await(15, TimeUnit.SECONDS))
              throw new IllegalStateException("timeout de prueba");
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
        };
    var primera = metadatos();
    var segunda = metadatos();
    var fichero = imagen("jpeg");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> resultadoCarga(id, primera, fichero, "plaza-a"));
      var b = pool.submit(() -> resultadoCarga(id, segunda, fichero, "plaza-b"));
      assertThat(List.of(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 422);
    } finally {
      storage.antesMiniatura = null;
    }
    assertThat(disponibles(id)).isEqualTo(60);
    assertThat(storage.escritas).hasSize(2);
  }

  @Test
  void asignadoLeeBorradorPeroNoCargaYRevocacionImpideLecturasPosteriores() throws Exception {
    var id = iniciar();
    var metadatos = metadatos();
    var fichero = imagen("jpeg");
    var dto = fotos.adjuntar(id, usuario, metadatos, fichero, "lectura");
    UUID lector = UUID.randomUUID(), estudiante = UUID.randomUUID(), asignatura = UUID.randomUUID();
    UUID curso = UUID.randomUUID(), inscripcion = UUID.randomUUID(), asignacion = UUID.randomUUID();
    crearUsuario(lector, "CATEDRATICO");
    crearUsuario(estudiante, "ESTUDIANTE");
    jdbc.update(
        "insert into asignatura(id,codigo,nombre) values (?,?,'prueba')",
        asignatura,
        asignatura.toString());
    jdbc.update(
        "insert into curso(id,asignatura_id,periodo,catedratico_id,fecha_inicio,fecha_fin) values (?,?,'2026',?,'2026-01-01','2026-12-31')",
        curso,
        asignatura,
        lector);
    jdbc.update(
        "insert into curso_estudiante(id,curso_id,estudiante_id,inscrito_por_id,seccion) values (?,?,?,?,'A')",
        inscripcion,
        curso,
        estudiante,
        lector);
    jdbc.update(
        "insert into asignacion_puente(id,curso_estudiante_id,puente_id,asignado_por_id) values (?,?,?,?)",
        asignacion,
        inscripcion,
        puente,
        lector);
    // Fixture de la relación que el futuro flujo académico debe persistir.
    jdbc.update("update inspeccion set asignacion_puente_id=? where id=?", asignacion, id);

    assertThat(fotos.listar(id, lector)).containsExactly(dto);
    for (boolean miniatura : List.of(false, true)) {
      try (var entrada = fotos.contenido(id, dto.id(), lector, miniatura)) {
        assertThat(entrada.read()).isNotEqualTo(-1);
      }
    }
    assertThatThrownBy(() -> fotos.adjuntar(id, lector, metadatos(), fichero, "ajena"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
    jdbc.update(
        "update asignacion_puente set revocado_en=CURRENT_TIMESTAMP,motivo_revocacion='prueba' where id=?",
        asignacion);
    assertThatThrownBy(() -> fotos.listar(id, lector))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
    for (boolean miniatura : List.of(false, true)) {
      assertThatThrownBy(() -> fotos.contenido(id, dto.id(), lector, miniatura))
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
    }
    assertThat(fotos.listar(id, usuario)).containsExactly(dto);
  }

  private void crearUsuario(UUID id, String rol) {
    jdbc.update(
        "insert into usuario(id,email,password_hash,rol_id,verificado,activado,activo) select ?,?,'test',id,true,true,true from rol where nombre=?",
        id,
        id + "@test.com",
        rol);
  }

  private long disponibles(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from archivo a join archivo_inspeccion v on v.archivo_id=a.id where v.inspeccion_id=? and a.tipo_uso='FOTO_INSPECCION' and a.estado='DISPONIBLE'",
        Long.class,
        id);
  }

  private int resultadoCarga(
      UUID id,
      AdjuntarFotografiaRequest metadatos,
      org.springframework.mock.web.MockMultipartFile fichero,
      String clave) {
    try {
      fotos.adjuntar(id, usuario, metadatos, fichero, clave);
      return 201;
    } catch (ApiException e) {
      assertThat(e.getCode()).isEqualTo("limite_fotografias_excedido");
      return e.getStatus().value();
    }
  }

  private void sembrarFotografias(UUID id, int cantidad) {
    // Metadatos de inventario para probar conteo; estas fotos de fixture no se descargan.
    jdbc.update(
        """
        with nuevas as (
          insert into archivo(id,tipo_uso,categoria_foto,nombre_almacenado,
              clave_objeto_original,clave_objeto_miniatura,mime_real,tamano_bytes,
              checksum_sha256,ancho_px,alto_px,ubicacion_captura,capturado_en,
              subido_por_id,confirmado_en)
          select uuid_generate_v4(),'FOTO_INSPECCION','ACCESO','fixture.jpg',
              'fixture/' || cast(? as text) || '/' || numero || '.jpg',
              'fixture/' || cast(? as text) || '/' || numero || '-mini.jpg',
              'image/jpeg',100,repeat('a',64),100,100,
              ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,CURRENT_TIMESTAMP,
              ?,CURRENT_TIMESTAMP from generate_series(1,?) as serie(numero)
          returning id
        )
        insert into archivo_inspeccion(archivo_id,tipo_uso,inspeccion_id)
          select id,'FOTO_INSPECCION',? from nuevas
        """,
        id,
        id,
        usuario,
        cantidad,
        id);
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
