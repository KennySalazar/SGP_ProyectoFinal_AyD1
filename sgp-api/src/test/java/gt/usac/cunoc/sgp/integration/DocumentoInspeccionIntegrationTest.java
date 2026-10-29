package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.mapper.FotografiaMapperImpl;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoLocal;
import gt.usac.cunoc.sgp.archivo.service.CargasImagenService;
import gt.usac.cunoc.sgp.archivo.service.InspectorImagenesImageIo;
import gt.usac.cunoc.sgp.archivo.service.ObjetosImagenService;
import gt.usac.cunoc.sgp.archivo.service.ProcesadorImagenes;
import gt.usac.cunoc.sgp.archivo.service.RegistroFotografiasInspeccionJpa;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.DocumentoInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.service.DocumentoInspeccionService;
import gt.usac.cunoc.sgp.inspeccion.service.FotografiaService;
import gt.usac.cunoc.sgp.inspeccion.service.ReglasEvidenciaFotografica;
import gt.usac.cunoc.sgp.inspeccion.validation.ValidadorAnclajeFotografico;
import gt.usac.cunoc.sgp.inspeccion.validation.ValidadorDocumentoPdf;
import gt.usac.cunoc.sgp.sync.service.OperacionIdempotenteService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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

/** Documentos PDF de una inspección contra PostgreSQL/PostGIS real (HU-070). */
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  gt.usac.cunoc.sgp.inspeccion.service.PermisosCorreccionService.class,
  DocumentoInspeccionService.class,
  ValidadorDocumentoPdf.class,
  FotografiaService.class,
  ReglasEvidenciaFotografica.class,
  ValidadorAnclajeFotografico.class,
  FotografiaMapperImpl.class,
  InspectorImagenesImageIo.class,
  ProcesadorImagenes.class,
  CargasImagenService.class,
  ObjetosImagenService.class,
  RegistroFotografiasInspeccionJpa.class,
  OperacionIdempotenteService.class,
  AuditoriaWriter.class,
  DocumentoInspeccionIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class DocumentoInspeccionIntegrationTest {

  private static final Instant AHORA = Instant.parse("2026-10-20T15:00:00Z");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu070_test")
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

  /** Almacenamiento local de prueba que puede simular que el servicio de objetos no responde. */
  static class Almacenamiento implements AlmacenamientoArchivos {
    final Path directorio;
    final AlmacenamientoLocal real;
    volatile boolean caido;

    Almacenamiento() throws IOException {
      directorio = Files.createTempDirectory("sgp-hu070-");
      real = new AlmacenamientoLocal(directorio);
    }

    public void guardar(String clave, InputStream contenido, long tamano, String mime)
        throws IOException {
      if (caido) {
        throw new IOException("almacenamiento de prueba no disponible");
      }
      real.guardar(clave, contenido, tamano, mime);
    }

    public InputStream abrir(String clave) throws IOException {
      return real.abrir(clave);
    }

    public boolean existe(String clave) throws IOException {
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
  }

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

    @Bean
    Almacenamiento almacenamiento() throws IOException {
      return new Almacenamiento();
    }
  }

  @Autowired private DocumentoInspeccionService documentos;
  @Autowired private Almacenamiento almacenamiento;
  @Autowired private JdbcTemplate jdbc;

  private UUID estudiante;
  private UUID catedratico;
  private UUID puente;

  @BeforeEach
  void preparar() {
    almacenamiento.caido = false;
    estudiante = usuario("ESTUDIANTE");
    catedratico = usuario("CATEDRATICO");
    puente = puente();
  }

  @Test
  void elAutorAdjuntaUnPdfQueSeGuardaConNombreDelServidorYQuedaAuditado() throws IOException {
    UUID inspeccion = inspeccion("BORRADOR", null);
    long objetosAntes = almacenamiento.objetos();

    DocumentoInspeccionResponse documento =
        documentos.adjuntar(inspeccion, estudiante, pdf("../../planos/Plano \"final\""));

    assertThat(documento.nombreOriginal()).isEqualTo("Plano final.pdf");
    assertThat(almacenamiento.objetos()).isEqualTo(objetosAntes + 1);
    var fila =
        jdbc.queryForMap(
            "SELECT tipo_uso, mime_real, nombre_original, nombre_almacenado, clave_objeto_original"
                + " FROM archivo WHERE id = ?",
            documento.id());
    assertThat(fila.get("tipo_uso")).isEqualTo("DOCUMENTO_INSPECCION");
    assertThat(fila.get("mime_real")).isEqualTo("application/pdf");
    assertThat(fila.get("nombre_almacenado")).isEqualTo(documento.id() + ".pdf");
    assertThat((String) fila.get("clave_objeto_original")).doesNotContain("Plano");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'archivo' AND entidad_id = ?"
                    + " AND accion = 'CREAR' AND usuario_id = ?"
                    + " AND valores_posteriores->>'inspeccionId' = ?",
                Integer.class,
                documento.id(),
                estudiante,
                inspeccion.toString()))
        .isEqualTo(1);

    var descarga =
        documentos.descargar(inspeccion, documento.id(), estudiante, RoleName.ESTUDIANTE);
    assertThat(descarga.nombre()).isEqualTo("Plano final.pdf");
    try (InputStream contenido = descarga.contenido()) {
      assertThat(new String(contenido.readNBytes(5), StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
  }

  @Test
  void admiteHasta10DocumentosYElUndecimoSeRechaza() throws IOException {
    UUID inspeccion = inspeccion("BORRADOR", null);
    UUID primero = null;
    for (int i = 0; i < 10; i++) {
      UUID id = documentos.adjuntar(inspeccion, estudiante, pdf("informe-" + i)).id();
      primero = primero == null ? id : primero;
    }

    MockMultipartFile undecimo = pdf("informe-11");
    assertError(
        () -> documentos.adjuntar(inspeccion, estudiante, undecimo),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "limite_documentos");

    final UUID retirar = primero;
    documentos.retirar(inspeccion, retirar, estudiante);
    assertThat(documentos.adjuntar(inspeccion, estudiante, undecimo).id()).isNotNull();
    assertThat(documentos.listar(inspeccion, estudiante, RoleName.ESTUDIANTE))
        .hasSize(10)
        .noneMatch(d -> d.id().equals(retirar));
  }

  @Test
  void unArchivoQueNoEsPdfOExcede20MbSeRechazaSinGuardarNada() throws IOException {
    UUID inspeccion = inspeccion("BORRADOR", null);
    long objetosAntes = almacenamiento.objetos();

    assertError(
        () ->
            documentos.adjuntar(
                inspeccion,
                estudiante,
                new MockMultipartFile(
                    "archivo", "informe.pdf", "application/pdf", "no soy un pdf".getBytes())),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "documento_pdf_invalido");
    assertError(
        () ->
            documentos.adjuntar(
                inspeccion,
                estudiante,
                new MockMultipartFile(
                    "archivo", "falso.pdf", "application/pdf", "%PDF-1.7 basura".getBytes())),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "documento_pdf_invalido");
    assertError(
        () ->
            documentos.adjuntar(
                inspeccion,
                estudiante,
                new MockMultipartFile(
                    "archivo",
                    "enorme.pdf",
                    "application/pdf",
                    new byte[ValidadorDocumentoPdf.MAXIMO_BYTES + 1])),
        HttpStatus.PAYLOAD_TOO_LARGE,
        "documento_demasiado_grande");

    assertThat(documentosDe(inspeccion)).isZero();
    assertThat(almacenamiento.objetos()).isEqualTo(objetosAntes);
  }

  @Test
  void soloElAutorAdjuntaYSoloMientrasEsBorrador() throws IOException {
    UUID borrador = inspeccion("BORRADOR", null);
    UUID enviada = inspeccion("ENVIADA", null);
    UUID otro = usuario("ESTUDIANTE");

    assertError(
        () -> documentos.adjuntar(borrador, otro, pdf("ajeno")),
        HttpStatus.FORBIDDEN,
        "documento_no_autorizado");
    assertError(
        () -> documentos.adjuntar(enviada, estudiante, pdf("tarde")),
        HttpStatus.CONFLICT,
        "estado_inspeccion_no_permite_archivos");
    assertThat(documentosDe(borrador)).isZero();
  }

  @Test
  void losDocumentosDeUnBorradorSoloLosVenSuAutorYElCatedraticoAsignado() throws IOException {
    UUID asignacion = asignar();
    UUID borrador = inspeccion("BORRADOR", asignacion);
    UUID documento = documentos.adjuntar(borrador, estudiante, pdf("informe")).id();
    UUID otroEstudiante = usuario("ESTUDIANTE");
    UUID otroCatedratico = usuario("CATEDRATICO");

    assertThat(documentos.listar(borrador, catedratico, RoleName.CATEDRATICO)).hasSize(1);
    assertThat(documentos.descargar(borrador, documento, catedratico, RoleName.CATEDRATICO))
        .isNotNull();
    assertError(
        () -> documentos.listar(borrador, otroEstudiante, RoleName.ESTUDIANTE),
        HttpStatus.NOT_FOUND,
        "inspeccion_no_encontrada");
    assertError(
        () -> documentos.descargar(borrador, documento, otroCatedratico, RoleName.CATEDRATICO),
        HttpStatus.NOT_FOUND,
        "inspeccion_no_encontrada");
  }

  @Test
  void losDocumentosDeUnaInspeccionPublicadaLosVeCualquierCuentaYQuedanFijos() throws IOException {
    UUID inspeccion = inspeccion("BORRADOR", null);
    UUID documento = documentos.adjuntar(inspeccion, estudiante, pdf("informe")).id();
    jdbc.update(
        "UPDATE inspeccion SET estado = 'PUBLICADA', publicado_en = ? WHERE id = ?",
        Timestamp.from(AHORA),
        inspeccion);
    UUID profesional = usuario("PROFESIONAL_EXTERNO");

    assertThat(documentos.listar(inspeccion, profesional, RoleName.PROFESIONAL_EXTERNO))
        .extracting(DocumentoInspeccionResponse::id)
        .containsExactly(documento);
    assertError(
        () -> documentos.retirar(inspeccion, documento, estudiante),
        HttpStatus.CONFLICT,
        "estado_inspeccion_no_permite_archivos");
  }

  @Test
  void unFalloDelAlmacenamientoNoDejaRegistrosYPermiteReintentar() throws IOException {
    UUID inspeccion = inspeccion("BORRADOR", null);
    almacenamiento.caido = true;

    assertError(
        () -> documentos.adjuntar(inspeccion, estudiante, pdf("informe")),
        HttpStatus.SERVICE_UNAVAILABLE,
        "almacenamiento_no_disponible");
    assertThat(documentosDe(inspeccion)).isZero();

    almacenamiento.caido = false;
    assertThat(documentos.adjuntar(inspeccion, estudiante, pdf("informe")).id()).isNotNull();
    assertThat(documentosDe(inspeccion)).isEqualTo(1);
  }

  @Test
  void retirarDejaElDocumentoRetiradoYAuditado() throws IOException {
    UUID inspeccion = inspeccion("BORRADOR", null);
    UUID documento = documentos.adjuntar(inspeccion, estudiante, pdf("informe")).id();

    documentos.retirar(inspeccion, documento, estudiante);

    assertThat(
            jdbc.queryForObject("SELECT estado FROM archivo WHERE id = ?", String.class, documento))
        .isEqualTo("RETIRADO");
    assertThat(documentos.listar(inspeccion, estudiante, RoleName.ESTUDIANTE)).isEmpty();
    assertError(
        () -> documentos.descargar(inspeccion, documento, estudiante, RoleName.ESTUDIANTE),
        HttpStatus.NOT_FOUND,
        "documento_no_encontrado");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'archivo' AND entidad_id = ?"
                    + " AND accion = 'CAMBIAR_ESTADO' AND usuario_id = ?",
                Integer.class,
                documento,
                estudiante))
        .isEqualTo(1);
  }

  // --- datos de prueba ---

  private static MockMultipartFile pdf(String nombre) throws IOException {
    try (var documento = new PDDocument();
        var bytes = new ByteArrayOutputStream()) {
      documento.addPage(new PDPage());
      documento.save(bytes);
      // El tipo declarado se ignora: solo cuenta la firma binaria.
      return new MockMultipartFile("archivo", nombre, "text/plain", bytes.toByteArray());
    }
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

  private int documentosDe(UUID inspeccion) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM archivo_inspeccion WHERE inspeccion_id = ?"
                + " AND tipo_uso = 'DOCUMENTO_INSPECCION'",
            Integer.class,
            inspeccion);
    return total == null ? 0 : total;
  }

  private UUID usuario(String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario (id, email, password_hash, rol_id, verificado, activado, activo)"
            + " SELECT ?, ?, 'hash', id, true, true, true FROM rol WHERE nombre = ?",
        id,
        id + "@hu070.test",
        rol);
    return id;
  }

  private UUID puente() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
            INSERT INTO puente (id, codigo, correlativo_municipal, municipio_id, nombre, ruta,
              ubicacion, creado_por_id)
            SELECT ?, ?, (SELECT coalesce(max(correlativo_municipal), 0) + 1 FROM puente p
                 WHERE p.municipio_id = m.id),
              m.id, 'Puente HU-070', 'CA-1',
              ST_SetSRID(ST_MakePoint(-90.6, 14.5), 4326)::geography, ?
            FROM municipio m WHERE m.codigo_ine = '0114'
            """,
        id,
        "T-" + id,
        catedratico);
    return id;
  }

  private UUID asignar() {
    UUID asignatura = UUID.randomUUID();
    UUID curso = UUID.randomUUID();
    UUID inscripcion = UUID.randomUUID();
    UUID asignacion = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO asignatura (id, codigo, nombre) VALUES (?, ?, 'Asignatura HU-070')",
        asignatura,
        asignatura.toString());
    jdbc.update(
        "INSERT INTO curso (id, asignatura_id, periodo, catedratico_id, fecha_inicio, fecha_fin)"
            + " VALUES (?, ?, '2026', ?, '2026-01-01', '2026-12-31')",
        curso,
        asignatura,
        catedratico);
    jdbc.update(
        "INSERT INTO curso_estudiante (id, curso_id, estudiante_id, seccion, inscrito_por_id)"
            + " VALUES (?, ?, ?, 'A', ?)",
        inscripcion,
        curso,
        estudiante,
        catedratico);
    jdbc.update(
        "INSERT INTO asignacion_puente (id, curso_estudiante_id, puente_id, asignado_por_id)"
            + " VALUES (?, ?, ?, ?)",
        asignacion,
        inscripcion,
        puente,
        catedratico);
    return asignacion;
  }

  private UUID inspeccion(String estado, UUID asignacion) {
    UUID version = UUID.randomUUID();
    jdbc.update(
        """
            INSERT INTO version_formulario (id, codigo, estado, activa, esquema, hash_contenido,
              version_algoritmo_ic, creado_por_id, publicado_por_id, publicado_en)
            VALUES (?, ?, 'PUBLICADA', false, '{}'::jsonb, ?, 'ic', ?, ?, CURRENT_TIMESTAMP)
            """,
        version,
        "HU070-" + version,
        "d".repeat(64),
        catedratico,
        catedratico);
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
            INSERT INTO inspeccion (id, puente_id, version_formulario_id, autor_id,
              rol_autor_creacion, nombre_autor_capturado, asignacion_puente_id, fecha_inspeccion,
              dispositivo_id, datos, estado)
            VALUES (?, ?, ?, ?, 'ESTUDIANTE', 'Autor', ?, '2026-10-19', 'equipo', '{}'::jsonb, ?)
            """,
        id,
        puente,
        version,
        estudiante,
        asignacion,
        estado);
    return id;
  }
}
