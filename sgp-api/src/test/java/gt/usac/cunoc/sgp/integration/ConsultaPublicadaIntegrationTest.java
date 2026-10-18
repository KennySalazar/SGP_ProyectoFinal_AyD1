package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapper;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.repository.ResultadoIcRepository;
import gt.usac.cunoc.sgp.inspeccion.service.CalculoIndiceCondicionService;
import gt.usac.cunoc.sgp.inspeccion.service.InspeccionService;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.generate_statistics=true"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ConsultaPublicadaIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu030_test")
          .withUsername("sgp_test")
          .withPassword("sgp_test");

  @DynamicPropertySource
  static void configurarBase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  private static final Instant FECHA = Instant.parse("2026-10-18T12:00:00Z");
  @Autowired InspeccionRepository inspecciones;
  @Autowired ResultadoIcRepository resultados;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager em;
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final CalculoIndiceCondicionService calculador =
      mock(CalculoIndiceCondicionService.class);
  private InspeccionService servicio;
  private UUID autor;
  private UUID puente;
  private UUID version;

  @BeforeEach
  void preparar() {
    autor = UUID.randomUUID();
    puente = UUID.randomUUID();
    version = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO usuario (id, email, password_hash, rol_id, nombre_completo)
        SELECT ?, ?, 'hash-prueba', id, 'Autor original' FROM rol WHERE nombre = 'CATEDRATICO'
        """,
        autor,
        autor + "@hu030.ejemplo.com");
    jdbc.update(
        """
        INSERT INTO puente (id, codigo, correlativo_municipal, municipio_id,
          nombre, ruta, ubicacion, creado_por_id)
        SELECT ?, ?, 1, id, 'Puente HU030', 'CA-1',
          ST_SetSRID(ST_MakePoint(-90.615, 14.481), 4326)::geography, ?
        FROM municipio WHERE codigo_ine = '0114'
        """,
        puente,
        "HU030-" + puente.toString().substring(0, 18),
        autor);
    crearVersion(version, false, "original");
    crearVersion(UUID.randomUUID(), true, "actual");
    servicio =
        new InspeccionService(
            inspecciones,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            json,
            Clock.systemUTC(),
            null,
            null,
            calculador,
            null,
            Mappers.getMapper(InspeccionMapper.class),
            null,
            resultados);
    em.clear();
  }

  @Test
  void historialFiltraPaginaYOrdenaPorPublicacionLuegoIdSinNMasUno() {
    UUID antigua = id(1), recienteMenor = id(2), recienteMayor = id(3);
    crearInspeccion(antigua, "PUBLICADA", FECHA.minusSeconds(60));
    crearInspeccion(recienteMenor, "PUBLICADA", FECHA);
    crearInspeccion(recienteMayor, "PUBLICADA", FECHA);
    for (String estado :
        List.of("BORRADOR", "ENVIADA", "EN_REVISION", "CAMBIOS_SOLICITADOS", "RECHAZADA")) {
      crearInspeccion(UUID.randomUUID(), estado, null);
    }
    UUID eliminada = UUID.randomUUID();
    crearInspeccion(eliminada, "BORRADOR", null);
    jdbc.update(
        "UPDATE inspeccion SET eliminado_en = now(), eliminado_por_id = ? WHERE id = ?",
        autor,
        eliminada);
    // Otra publicación pertenece a otro puente y tampoco debe aparecer.
    UUID otroPuente = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO puente (id, codigo, correlativo_municipal, municipio_id, nombre, ruta, ubicacion, creado_por_id)
        SELECT ?, ?, 2, municipio_id, nombre, ruta, ubicacion, creado_por_id FROM puente WHERE id = ?
        """,
        otroPuente,
        "OTRO-" + otroPuente.toString().substring(0, 18),
        puente);
    UUID ajena = UUID.randomUUID();
    crearInspeccion(ajena, "PUBLICADA", FECHA.plusSeconds(60));
    jdbc.update("UPDATE inspeccion SET puente_id = ? WHERE id = ?", otroPuente, ajena);
    Statistics stats = estadisticas();
    stats.clear();

    var primera = servicio.listarPublicadasPorPuente(puente, 0, 1);
    assertThat(primera.getTotalElements()).isEqualTo(3);
    assertThat(primera.getTotalPages()).isEqualTo(3);
    assertThat(primera.getContent()).extracting("id").containsExactly(recienteMayor);
    assertThat(primera.getContent().getFirst().nombreAutor()).isEqualTo("Autor capturado");
    assertThat(primera.getContent().getFirst().estado()).isEqualTo("PUBLICADA");
    assertThat(primera.getContent().getFirst().publicadoEn()).isEqualTo(FECHA);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
    assertThat(servicio.listarPublicadasPorPuente(puente, 1, 1).getContent())
        .extracting("id")
        .containsExactly(recienteMenor);
    assertThat(servicio.listarPublicadasPorPuente(puente, 2, 1).getContent())
        .extracting("id")
        .containsExactly(antigua);
    stats.clear();
    assertThat(servicio.listarPublicadasPorPuente(puente, 0, 2).getContent()).hasSize(2);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  void detalleLeeVersionOriginalMetadatosAcompanantesEIndicePersistidoSinRecalcular()
      throws Exception {
    UUID id = UUID.randomUUID();
    crearInspeccion(id, "PUBLICADA", FECHA);
    jdbc.update(
        """
        INSERT INTO inspeccion_acompanante (id, inspeccion_id, nombre, numero_colegiado)
        VALUES (?, ?, 'Acompañante', '1234')
        """,
        UUID.randomUUID(),
        id);
    jdbc.update(
        "INSERT INTO inspeccion_dato_tecnico (inspeccion_id, longitud_m) VALUES (?, 42)", id);
    guardarIc(id, "PUBLICACION", 12, "BUENO", "REGULAR", "Evidencia observada");
    guardarIc(id, "RECALCULO", 95, "MALO", "MALO", null);
    em.clear();
    var stats = estadisticas();
    stats.clear();

    var detalle = servicio.consultarPublicada(id);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
    em.clear();
    assertThat(detalle.versionFormularioId()).isEqualTo(version);
    assertThat(detalle.esquemaFormulario().path("title").asText()).isEqualTo("original");
    assertThat(detalle.nombreAutor()).isEqualTo("Autor capturado");
    assertThat(detalle.autorId()).isEqualTo(autor);
    assertThat(detalle.rolAutor()).isEqualTo("CATEDRATICO");
    assertThat(detalle.acompanantes()).extracting("nombre").containsExactly("Acompañante");
    assertThat(detalle.creadoEn()).isEqualTo(FECHA.minusSeconds(120));
    assertThat(detalle.enviadoEn()).isEqualTo(FECHA.minusSeconds(60));
    assertThat(detalle.publicadoEn()).isEqualTo(FECHA);
    assertThat(detalle.dispositivoId()).isEqualTo("equipo-hu030");
    assertThat(detalle.identificacionCapturada().path("nombre").asText())
        .isEqualTo("Identificación histórica");
    assertThat(detalle.datos().path("observacion").asText()).isEqualTo("Datos originales");
    assertThat(detalle.datosPermanentes().longitudM()).isEqualByComparingTo("42");
    assertThat(detalle.ic().indiceCalculado()).isEqualByComparingTo("12");
    assertThat(detalle.ic().estadoCalculado()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(detalle.ic().estadoConfirmado()).isEqualTo(EstadoCondicion.REGULAR);
    assertThat(detalle.ic().justificacionAjuste()).isEqualTo("Evidencia observada");
    assertThat(detalle.vigencia()).isEqualTo("VIGENTE");
    assertThat(detalle.superadaPorId()).isNull();
    assertThat(json.writeValueAsString(detalle)).contains("Identificación histórica");
    verifyNoInteractions(calculador);
  }

  @Test
  void soloUnaCorreccionPublicadaMarcaSuperadaYEnlazaSuDetalle() {
    UUID original = UUID.randomUUID(), correccion = UUID.randomUUID();
    crearInspeccion(original, "PUBLICADA", FECHA);
    crearInspeccion(correccion, "BORRADOR", null);
    jdbc.update(
        "UPDATE inspeccion SET tipo = 'CORRECCION', supersede_a_id = ?, motivo_correccion = 'Ajuste' WHERE id = ?",
        original,
        correccion);
    assertThat(servicio.consultarPublicada(original).superadaPorId()).isNull();
    jdbc.update("UPDATE inspeccion SET estado = 'ENVIADA' WHERE id = ?", correccion);
    assertThat(servicio.consultarPublicada(original).vigencia()).isEqualTo("VIGENTE");
    jdbc.update(
        "UPDATE inspeccion SET estado = 'PUBLICADA', publicado_en = now() WHERE id = ?",
        correccion);
    em.clear();
    var detalle = servicio.consultarPublicada(original);
    assertThat(detalle.vigencia()).isEqualTo("SUPERADA");
    assertThat(detalle.superadaPorId()).isEqualTo(correccion);
    assertThat(servicio.consultarPublicada(correccion).supersedeAId()).isEqualTo(original);
    assertThat(servicio.consultarPorId(original, UUID.randomUUID()).superadaPorId())
        .isEqualTo(correccion);
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORRADOR", "ENVIADA", "EN_REVISION", "CAMBIOS_SOLICITADOS", "RECHAZADA"})
  void detallePublicadoNoRevelaEstadosPrivados(String estado) {
    UUID id = UUID.randomUUID();
    crearInspeccion(id, estado, null);
    assert404(() -> servicio.consultarPublicada(id));
  }

  @Test
  void regresionConsultaPrivadaAutorConservaAccesoYAjenoRecibe404() {
    UUID id = UUID.randomUUID();
    crearInspeccion(id, "BORRADOR", null);
    assertThat(servicio.consultarPorId(id, autor).estado()).isEqualTo("BORRADOR");
    assertThat(servicio.obtenerBorrador(id, autor).autorId()).isEqualTo(autor);
    assert404(() -> servicio.consultarPorId(id, UUID.randomUUID()));
    assert404(() -> servicio.obtenerBorrador(id, UUID.randomUUID()));
    assert404(() -> servicio.consultarPublicada(UUID.randomUUID()));
  }

  @Test
  void publicacionSinResultadoHistoricoNoInventaIcNiRecalcula() {
    UUID id = UUID.randomUUID();
    crearInspeccion(id, "PUBLICADA", FECHA);
    assertThat(servicio.consultarPublicada(id).ic()).isNull();
    verifyNoInteractions(calculador);
    assertThat(servicio.listarPublicadasPorPuente(UUID.randomUUID(), 0, 20)).isEmpty();
  }

  @Test
  void rechazaPaginacionInvalida() {
    for (int[] valores : List.of(new int[] {-1, 20}, new int[] {0, 0}, new int[] {0, 101})) {
      assertThatThrownBy(() -> servicio.listarPublicadasPorPuente(puente, valores[0], valores[1]))
          .isInstanceOfSatisfying(
              ApiException.class,
              error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }
  }

  private void crearVersion(UUID id, boolean activa, String titulo) {
    jdbc.update(
        """
        INSERT INTO version_formulario (id, codigo, estado, activa, esquema, hash_contenido,
          version_algoritmo_ic, creado_por_id, publicado_por_id, publicado_en)
        VALUES (?, ?, 'PUBLICADA', ?, CAST(? AS jsonb), ?, 'hu030', ?, ?, now())
        """,
        id,
        "HU030-" + id,
        activa,
        "{\"title\":\"" + titulo + "\",\"type\":\"object\"}",
        "a".repeat(64),
        autor,
        autor);
  }

  private void crearInspeccion(UUID id, String estado, Instant publicada) {
    jdbc.update(
        """
        INSERT INTO inspeccion (id, puente_id, version_formulario_id, autor_id,
          rol_autor_creacion, nombre_autor_capturado, fecha_inspeccion, dispositivo_id,
          estado, datos, identificacion_capturada, creado_en, enviado_dispositivo_en, publicado_en)
        VALUES (?, ?, ?, ?, 'CATEDRATICO', 'Autor capturado', '2026-10-17', 'equipo-hu030', ?,
          '{"observacion":"Datos originales"}'::jsonb, '{"nombre":"Identificación histórica"}'::jsonb, ?, ?, ?)
        """,
        id,
        puente,
        version,
        autor,
        estado,
        Timestamp.from(FECHA.minusSeconds(120)),
        Timestamp.from(FECHA.minusSeconds(60)),
        publicada == null ? null : Timestamp.from(publicada));
  }

  private void guardarIc(
      UUID inspeccion,
      String tipo,
      int indice,
      String calculado,
      String confirmado,
      String justificacion) {
    boolean ajuste = !calculado.equals(confirmado);
    jdbc.update(
        """
        INSERT INTO resultado_ic (id, inspeccion_id, version_configuracion_id, tipo,
          indice_calculado, estado_por_rango, estado_calculado, estado_confirmado,
          desglose, hash_entrada, version_algoritmo, justificacion_ajuste, confirmado_por_id, confirmado_en)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, 'historico', ?, ?, ?)
        """,
        UUID.randomUUID(),
        inspeccion,
        version,
        tipo,
        indice,
        calculado,
        calculado,
        confirmado,
        "b".repeat(64),
        justificacion,
        ajuste ? autor : null,
        ajuste ? Timestamp.from(FECHA) : null);
  }

  private Statistics estadisticas() {
    return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
  }

  private static UUID id(int numero) {
    return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", numero));
  }

  private void assert404(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion) {
    assertThatThrownBy(accion)
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }
}
