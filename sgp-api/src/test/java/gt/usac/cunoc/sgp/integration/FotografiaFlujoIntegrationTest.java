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
  gt.usac.cunoc.sgp.inspeccion.service.CalculadorProgresoBorrador.class,
  gt.usac.cunoc.sgp.inspeccion.service.PreparadorEntradaIcService.class,
  gt.usac.cunoc.sgp.inspeccion.service.CalculoIndiceCondicionService.class,
  gt.usac.cunoc.sgp.inspeccion.service.ResolverElementosIc.class,
  gt.usac.cunoc.sgp.inspeccion.service.ConversorIndicadoresIc.class,
  gt.usac.cunoc.sgp.inspeccion.service.ConversorMedicionesIc.class,
  gt.usac.cunoc.sgp.inspeccion.service.AgregadorCalificacionesIc.class,
  gt.usac.cunoc.sgp.inspeccion.service.EvaluadorCondicionesCriticasIc.class,
  gt.usac.cunoc.sgp.inspeccion.service.HuellaEntradaIc.class,
  gt.usac.cunoc.sgp.inspeccion.service.MotorPonderacionIc.class,
  gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService.class,
  gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapperImpl.class,
  AuditoriaWriter.class,
  gt.usac.cunoc.sgp.inspeccion.service.FotografiaService.class,
  gt.usac.cunoc.sgp.inspeccion.service.ReglasEvidenciaFotografica.class,
  gt.usac.cunoc.sgp.inspeccion.validation.ValidadorAnclajeFotografico.class,
  gt.usac.cunoc.sgp.archivo.mapper.FotografiaMapperImpl.class,
  InspectorImagenesImageIo.class,
  ProcesadorImagenes.class,
  CargasImagenService.class,
  ObjetosImagenService.class,
  RegistroFotografiasInspeccionJpa.class,
  FotografiaFlujoIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class FotografiaFlujoIntegrationTest {
  static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

  static class Storage implements AlmacenamientoArchivos {
    final Path directorio;
    final AlmacenamientoLocal real;
    boolean fallarMiniatura;
    Runnable despuesOriginal;
    volatile Runnable despuesMiniatura;

    Storage() throws java.io.IOException {
      directorio = Files.createTempDirectory("sgp-api-fotos-test-");
      real = new AlmacenamientoLocal(directorio);
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
      if (k.endsWith("-mini.jpg") && despuesMiniatura != null) despuesMiniatura.run();
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

    Set<String> claves() throws java.io.IOException {
      try (var rutas = Files.list(directorio)) {
        return rutas
            .map(p -> p.getFileName().toString())
            .collect(java.util.stream.Collectors.toSet());
      }
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
  @Autowired gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository inspeccionRepository;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager tm;
  @TempDir Path temporal;
  UUID usuario, version, puente;

  @BeforeEach
  void preparar() {
    storage.fallarMiniatura = false;
    storage.despuesOriginal = null;
    storage.despuesMiniatura = null;
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
  void autorRetiraFotografiaSinBorrarObjetosYNoPuedeListarlaNiDescargarla() throws Exception {
    var id = iniciar();
    var r = metadatos();
    fotos.adjuntar(id, usuario, r, imagen("jpeg"), "retirada");
    var clavesAntes = storage.claves();

    fotos.retirar(id, r.id(), usuario);

    assertThat(jdbc.queryForObject("select estado from archivo where id=?", String.class, r.id()))
        .isEqualTo("RETIRADO");
    Instant retiradoEn =
        jdbc.queryForObject(
            "select retirado_en from archivo where id=?",
            (rs, fila) -> rs.getTimestamp(1).toInstant(),
            r.id());
    assertThat(retiradoEn).isEqualTo(AHORA);
    assertThat(fotos.listar(id, usuario)).isEmpty();
    for (boolean miniatura : new boolean[] {false, true}) {
      exigirNoEncontrada(() -> fotos.contenido(id, r.id(), usuario, miniatura));
    }
    assertThat(inspeccionRepository.findCategoriasFotoDisponibles(id)).isEmpty();
    assertThat(storage.claves()).isEqualTo(clavesAntes);
    // Repetir DELETE conserva la retirada y no toca los objetos.
    fotos.retirar(id, r.id(), usuario);
    assertThat(storage.claves()).isEqualTo(clavesAntes);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from archivo_inspeccion where archivo_id=?", Long.class, r.id()))
        .isEqualTo(1);
  }

  @Test
  void retiradaRechazaUsuarioAjenoAntesDeRevelarElEstadoDeLaInspeccion() throws Exception {
    var id = iniciar();
    var r = metadatos();
    fotos.adjuntar(id, usuario, r, imagen("jpeg"), "privada");

    for (String estado : List.of("BORRADOR", "PUBLICADA")) {
      jdbc.update(
          "update inspeccion set estado=?, publicado_en=CURRENT_TIMESTAMP where id=?", estado, id);
      exigirNoEncontrada(() -> fotos.retirar(id, r.id(), UUID.randomUUID()));
    }

    assertThat(jdbc.queryForObject("select estado from archivo where id=?", String.class, r.id()))
        .isEqualTo("DISPONIBLE");
  }

  @Test
  void retiradaDelAutorRechazaTodosLosEstadosDistintosDeBorrador() throws Exception {
    var id = iniciar();
    var r = metadatos();
    fotos.adjuntar(id, usuario, r, imagen("jpeg"), "no-editable");
    var clavesAntes = storage.claves();

    for (String estado :
        List.of("ENVIADA", "EN_REVISION", "CAMBIOS_SOLICITADOS", "PUBLICADA", "RECHAZADA")) {
      jdbc.update(
          "update inspeccion set estado=?, publicado_en=CURRENT_TIMESTAMP where id=?", estado, id);
      conflicto(() -> fotos.retirar(id, r.id(), usuario));
    }

    assertThat(jdbc.queryForObject("select estado from archivo where id=?", String.class, r.id()))
        .isEqualTo("DISPONIBLE");
    assertThat(storage.claves()).isEqualTo(clavesAntes);
  }

  @Test
  void retiradaNoEncuentraFotoInexistenteAjenaDocumentoOPurgada() throws Exception {
    var id = iniciar();
    exigirNoEncontrada(() -> fotos.retirar(id, UUID.randomUUID(), usuario));

    var otra = iniciar();
    var r = metadatos();
    fotos.adjuntar(otra, usuario, r, imagen("jpeg"), "otra-inspeccion");
    exigirNoEncontrada(() -> fotos.retirar(id, r.id(), usuario));

    agregarArchivos(id, 1, "DOCUMENTO_INSPECCION", null, "DISPONIBLE");
    agregarArchivos(id, 1, "FOTO_INSPECCION", "OTRA", "PURGADO");
    var ids =
        jdbc.queryForList(
            "select archivo_id from archivo_inspeccion where inspeccion_id=?", UUID.class, id);
    for (var foto : ids) exigirNoEncontrada(() -> fotos.retirar(id, foto, usuario));
    assertThat(fotos.listar(otra, usuario)).hasSize(1);
  }

  @Test
  void retirarFotografiaLiberaUnaPlazaDelLimiteDe60() throws Exception {
    var id = iniciar();
    agregarArchivos(id, 59, "FOTO_INSPECCION", "ACCESO", "DISPONIBLE");
    var r = metadatos();
    var fichero = imagen("jpeg");
    fotos.adjuntar(id, usuario, r, fichero, "plaza-original");
    exigirLimite(() -> fotos.adjuntar(id, usuario, metadatos(), fichero, "sin-plaza"));

    fotos.retirar(id, r.id(), usuario);
    fotos.adjuntar(id, usuario, metadatos(), fichero, "plaza-liberada");

    assertThat(fotos.listar(id, usuario)).hasSize(60);
  }

  @Test
  void retiradaSeRevierteSiFallaLaTransaccion() throws Exception {
    var id = iniciar();
    var r = metadatos();
    fotos.adjuntar(id, usuario, r, imagen("jpeg"), "rollback-retirada");
    var clavesAntes = storage.claves();
    var tx = new org.springframework.transaction.support.TransactionTemplate(tm);

    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    t -> {
                      fotos.retirar(id, r.id(), usuario);
                      throw new IllegalStateException("fallo de prueba");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(fotos.listar(id, usuario)).hasSize(1);
    assertThat(
            jdbc.queryForObject("select retirado_en from archivo where id=?", Object.class, r.id()))
        .isNull();
    assertThat(storage.claves()).isEqualTo(clavesAntes);
  }

  void exigirNoEncontrada(Runnable accion) {
    assertThatThrownBy(accion::run)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
  }

  void agregarArchivos(UUID id, int cantidad, String tipo, String categoria, String estado) {
    jdbc.update(
        """
        WITH nuevos AS (
          SELECT uuid_generate_v4() AS id FROM generate_series(1, ?)
        ), archivos AS (
          INSERT INTO archivo (
            id, tipo_uso, categoria_foto, nombre_almacenado, clave_objeto_original,
            clave_objeto_miniatura, mime_real, tamano_bytes, checksum_sha256,
            ancho_px, alto_px, ubicacion_captura, capturado_en, subido_por_id,
            confirmado_en, estado
          )
          SELECT id, ?, ?, id::text, id::text || '.jpg', id::text || '-mini.jpg',
            ?, 100, repeat('a', 64), 100, 50,
            ST_SetSRID(ST_MakePoint(-90, 14), 4326)::geography,
            CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?
          FROM nuevos
          RETURNING id, tipo_uso
        )
        INSERT INTO archivo_inspeccion (archivo_id, tipo_uso, inspeccion_id)
        SELECT id, tipo_uso, ? FROM archivos
        """,
        cantidad,
        tipo,
        categoria,
        "DOCUMENTO_INSPECCION".equals(tipo) ? "application/pdf" : "image/jpeg",
        usuario,
        estado,
        id);
  }

  void exigirLimite(Runnable accion) {
    assertThatThrownBy(accion::run)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus().value()).isEqualTo(422);
              assertThat(e.getCode()).isEqualTo("limite_fotografias_excedido");
            });
  }

  @Test
  void aceptaLaFotografiaNumero60() throws Exception {
    var id = iniciar();
    agregarArchivos(id, 59, "FOTO_INSPECCION", "ACCESO", "DISPONIBLE");

    var r = metadatos();
    assertThat(fotos.adjuntar(id, usuario, r, imagen("jpeg"), "numero-60").id()).isEqualTo(r.id());
    assertThat(fotos.listar(id, usuario)).hasSize(60);
  }

  @Test
  void rechazaLaFotografia61AntesDeProcesarSinGuardarRegistrosNiObjetos() throws Exception {
    var id = iniciar();
    agregarArchivos(id, 60, "FOTO_INSPECCION", "ACCESO", "DISPONIBLE");
    var r = metadatos();
    var clavesAntes = storage.claves();
    // Si se alcanzara el procesador, este contenido produciría otro error de validación.
    var fichero = new org.springframework.mock.web.MockMultipartFile("archivo", new byte[] {1});

    exigirLimite(() -> fotos.adjuntar(id, usuario, r, fichero, "numero-61"));

    assertThat(fotos.listar(id, usuario)).hasSize(60);
    assertThat(jdbc.queryForObject("select count(*) from archivo where id=?", Long.class, r.id()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from archivo_inspeccion where archivo_id=?", Long.class, r.id()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from operacion_idempotente where usuario_id=? and clave=?",
                Long.class,
                usuario,
                "numero-61"))
        .isZero();
    assertThat(storage.claves()).isEqualTo(clavesAntes);
  }

  @Test
  void fotografiasRetiradasPurgadasDocumentosYOtrasInspeccionesNoConsumenCapacidad()
      throws Exception {
    var id = iniciar();
    agregarArchivos(id, 59, "FOTO_INSPECCION", "ACCESO", "DISPONIBLE");
    agregarArchivos(id, 1, "FOTO_INSPECCION", "ACCESO", "RETIRADO");
    agregarArchivos(id, 1, "FOTO_INSPECCION", "ACCESO", "PURGADO");
    agregarArchivos(id, 1, "DOCUMENTO_INSPECCION", null, "DISPONIBLE");
    agregarArchivos(iniciar(), 60, "FOTO_INSPECCION", "ACCESO", "DISPONIBLE");

    fotos.adjuntar(id, usuario, metadatos(), imagen("jpeg"), "solo-disponibles");

    assertThat(fotos.listar(id, usuario)).hasSize(60);
  }

  @Test
  void laCategoriaOtraCuentaParaElLimite() throws Exception {
    var id = iniciar();
    agregarArchivos(id, 59, "FOTO_INSPECCION", "ACCESO", "DISPONIBLE");
    agregarArchivos(id, 1, "FOTO_INSPECCION", "OTRA", "DISPONIBLE");
    var fichero = imagen("jpeg");

    exigirLimite(() -> fotos.adjuntar(id, usuario, metadatos(), fichero, "otra-cuenta"));

    assertThat(fotos.listar(id, usuario)).hasSize(60);
  }

  @Test
  void reproduceReintentoIdempotenteCon60FotografiasSinCrearObjetos() throws Exception {
    var id = iniciar();
    agregarArchivos(id, 59, "FOTO_INSPECCION", "ACCESO", "DISPONIBLE");
    var r = metadatos();
    var fichero = imagen("jpeg");
    var primera = fotos.adjuntar(id, usuario, r, fichero, "reintento-60");
    var clavesAntes = storage.claves();

    assertThat(fotos.adjuntar(id, usuario, r, fichero, "reintento-60")).isEqualTo(primera);
    assertThat(fotos.listar(id, usuario)).hasSize(60);
    assertThat(storage.claves()).isEqualTo(clavesAntes);
  }

  @Test
  void dosCargasDesde59ConfirmanSoloUnaYCompensanLosObjetosRechazados() throws Exception {
    var id = iniciar();
    agregarArchivos(id, 59, "FOTO_INSPECCION", "ACCESO", "DISPONIBLE");
    var primera = metadatos();
    var segunda = metadatos();
    var fichero = imagen("jpeg");
    var clavesAntes = storage.claves();
    var preparadas = new CyclicBarrier(2);
    // Ambas cargas pasan el control previo y escriben sus objetos antes de competir por el bloqueo.
    storage.despuesMiniatura =
        () -> {
          try {
            preparadas.await(15, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          } catch (BrokenBarrierException | TimeoutException e) {
            throw new IllegalStateException(e);
          }
        };
    var respuestas = new ArrayList<Integer>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var cargas =
          List.of(
              pool.submit(() -> fotos.adjuntar(id, usuario, primera, fichero, "carrera-1")),
              pool.submit(() -> fotos.adjuntar(id, usuario, segunda, fichero, "carrera-2")));
      for (var carga : cargas) {
        try {
          carga.get(30, TimeUnit.SECONDS);
          respuestas.add(201);
        } catch (ExecutionException e) {
          assertThat(e.getCause())
              .isInstanceOfSatisfying(
                  ApiException.class,
                  error -> {
                    assertThat(error.getStatus().value()).isEqualTo(422);
                    assertThat(error.getCode()).isEqualTo("limite_fotografias_excedido");
                  });
          respuestas.add(422);
        }
      }
    } finally {
      storage.despuesMiniatura = null;
    }

    assertThat(respuestas).containsExactlyInAnyOrder(201, 422);
    assertThat(fotos.listar(id, usuario)).hasSize(60);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from archivo_inspeccion where inspeccion_id=?", Long.class, id))
        .isEqualTo(60);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from archivo where id in (?, ?)",
                Long.class,
                primera.id(),
                segunda.id()))
        .isEqualTo(1);
    var nuevasClaves = new HashSet<>(storage.claves());
    assertThat(nuevasClaves).containsAll(clavesAntes);
    nuevasClaves.removeAll(clavesAntes);
    var confirmadas =
        jdbc.queryForObject(
            "select clave_objeto_original, clave_objeto_miniatura from archivo where id in (?, ?)",
            (rs, fila) -> Set.of(rs.getString(1), rs.getString(2)),
            primera.id(),
            segunda.id());
    assertThat(nuevasClaves).isEqualTo(confirmadas);
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
    assertThat(inspeccionRepository.findCategoriasFotoDisponibles(id)).containsExactly("ACCESO");
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
