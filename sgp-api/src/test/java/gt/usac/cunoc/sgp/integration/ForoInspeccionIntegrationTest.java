package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.validation.ValidadorAnclajeFormulario;
import gt.usac.cunoc.sgp.foro.dto.AnclaResponse;
import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.CrearComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.CrearHiloRequest;
import gt.usac.cunoc.sgp.foro.dto.HiloInspeccionResponse;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.mapper.ForoMapperImpl;
import gt.usac.cunoc.sgp.foro.model.CategoriaHilo;
import gt.usac.cunoc.sgp.foro.service.AnclajeComentarioForo;
import gt.usac.cunoc.sgp.foro.service.FiltroComentarios;
import gt.usac.cunoc.sgp.foro.service.ForoInspeccionService;
import gt.usac.cunoc.sgp.foro.service.ForoInspeccionService.Apertura;
import gt.usac.cunoc.sgp.foro.service.ForoService;
import gt.usac.cunoc.sgp.foro.service.HiloInspeccionAlPublicarListener;
import gt.usac.cunoc.sgp.inspeccion.model.InspeccionPublicadaEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Hilo de la inspección publicada y comentarios anclados, contra PostgreSQL/PostGIS real (HU-064;
 * RN-FOR-02, RN-FOR-03, RN-FOR-09, RN-INS-05, RN-USR-09).
 */
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  ForoService.class,
  ForoInspeccionService.class,
  HiloInspeccionAlPublicarListener.class,
  AnclajeComentarioForo.class,
  ValidadorAnclajeFormulario.class,
  AuditoriaWriter.class,
  ForoMapperImpl.class,
  ForoInspeccionIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ForoInspeccionIntegrationTest {

  private static final Instant AHORA = Instant.parse("2026-10-20T15:00:00Z");
  private static final String PROCESO = "abrir-hilo-inspeccion";

  private static final UUID ESTUDIANTE_ID = UUID.fromString("019a0000-0000-7000-8000-000000006401");
  private static final UUID CATEDRATICO_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000006402");
  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000006403");

  private static final String ESTRIBO = "/subestructura/estribo_entrada/viga_apoyo";
  private static final String CAMPO_ESTRIBO = ESTRIBO + "/exposicion_acero";
  private static final String PILAS = "/subestructura/pilas";
  private static final String SOCAVACION = "/subestructura/pilas/socavacion";
  private static final String GENERAL = "/observaciones_generales";

  private static final String ESQUEMA_V1 =
      """
      {"type":"object","properties":{
        "subestructura":{"type":"object","properties":{
          "estribo_entrada":{"type":"object","properties":{
            "viga_apoyo":{"type":"object","properties":{"exposicion_acero":{"type":"string"}}}}},
          "pilas":{"type":"array","items":{"type":"object","properties":{
            "instancia_id":{"type":"string"},
            "socavacion":{"type":"object","properties":{"aguas_arriba":{"type":"string"}}}}}}}},
        "observaciones_generales":{"type":"string"}}}
      """;

  /** La versión siguiente agrega un elemento que la primera no conoce. */
  private static final String ESQUEMA_V2 =
      ESQUEMA_V1.replace(
          "\"observaciones_generales\":{\"type\":\"string\"}",
          "\"observaciones_generales\":{\"type\":\"string\"},"
              + "\"refuerzos\":{\"type\":\"object\",\"properties\":{\"estado\":{\"type\":\"string\"}}}");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu064_test")
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

  @Autowired private ForoService foro;
  @Autowired private HiloInspeccionAlPublicarListener listener;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transacciones;
  @Autowired private ApplicationEventPublisher eventos;
  @SpyBean private ForoInspeccionService foroInspeccion;

  private UUID puente;
  private UUID versionV1;
  private UUID versionV2;
  private UUID pila1;
  private UUID pila2;

  @BeforeEach
  void preparar() {
    insertarUsuario(ESTUDIANTE_ID, "estudiante.hu064@ejemplo.com", "ESTUDIANTE", "ana.estudiante");
    insertarUsuario(CATEDRATICO_ID, "catedratico.hu064@ejemplo.com", "CATEDRATICO", "carlos.cat");
    insertarUsuario(ADMINISTRADOR_ID, "admin.hu064@ejemplo.com", "ADMINISTRADOR", "admin.sistema");
    puente = insertarPuente();
    versionV1 = insertarVersion(ESQUEMA_V1);
    versionV2 = insertarVersion(ESQUEMA_V2);
    pila1 = UUID.randomUUID();
    pila2 = UUID.randomUUID();
  }

  // ----------------------------------------------- el hilo se abre al publicar (RN-FOR-03)

  @Test
  void alPublicarseElHiloSeAbreDespuesDeConfirmarYNoAntes() {
    UUID inspeccion = insertarInspeccion(versionV1, "ENVIADA");

    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              publicar(inspeccion);
              eventos.publishEvent(new InspeccionPublicadaEvent(inspeccion, CATEDRATICO_ID));
              // Dentro de la transacción de publicación el foro todavía no hizo nada.
              assertThat(hilosDe(inspeccion)).isZero();
            });

    assertThat(hilosDe(inspeccion)).isEqualTo(1);
  }

  @Test
  void elHiloNaceVacioConLosDatosDeLaInspeccionYQuedaEnLaBitacora() {
    UUID inspeccion = insertarInspeccion(versionV1, "PUBLICADA");

    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado ->
                eventos.publishEvent(new InspeccionPublicadaEvent(inspeccion, CATEDRATICO_ID)));

    HiloInspeccionResponse respuesta = foroInspeccion.obtener(inspeccion);
    HiloResponse hilo = respuesta.hilo();
    assertThat(hilo.inspeccionId()).isEqualTo(inspeccion);
    assertThat(hilo.puenteId()).isEqualTo(puente);
    assertThat(hilo.categoria()).isEqualTo(CategoriaHilo.DISCUSION_TECNICA);
    assertThat(hilo.titulo()).isEqualTo("Inspección del 15/10/2026");
    assertThat(hilo.autorId()).isEqualTo(ESTUDIANTE_ID);
    assertThat(hilo.totalComentarios()).isZero();
    assertThat(hilo.cerrado()).isFalse();
    assertThat(respuesta.anclas()).isEmpty();
    assertThat(
            contar(
                "SELECT count(*) FROM auditoria WHERE entidad = 'foro_hilo' AND entidad_id = ?"
                    + " AND accion = 'CREAR' AND usuario_id IS NULL AND proceso_automatico = ?",
                hilo.id(),
                PROCESO))
        .isEqualTo(1);
  }

  @Test
  void siElEventoLlegaDosVecesElHiloNoSeDuplica() {
    UUID inspeccion = insertarInspeccion(versionV1, "PUBLICADA");
    InspeccionPublicadaEvent evento = new InspeccionPublicadaEvent(inspeccion, CATEDRATICO_ID);

    new TransactionTemplate(transacciones)
        .executeWithoutResult(estado -> eventos.publishEvent(evento));
    new TransactionTemplate(transacciones)
        .executeWithoutResult(estado -> eventos.publishEvent(evento));
    listener.alPublicarse(evento);

    assertThat(hilosDe(inspeccion)).isEqualTo(1);
    UUID hilo = foroInspeccion.obtener(inspeccion).hilo().id();
    assertThat(
            contar(
                "SELECT count(*) FROM auditoria WHERE entidad = 'foro_hilo' AND entidad_id = ?",
                hilo))
        .isEqualTo(1);
  }

  @Test
  void unaPublicacionRevertidaNoAbreHilo() {
    UUID inspeccion = insertarInspeccion(versionV1, "ENVIADA");

    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              publicar(inspeccion);
              eventos.publishEvent(new InspeccionPublicadaEvent(inspeccion, CATEDRATICO_ID));
              estado.setRollbackOnly();
            });

    assertThat(hilosDe(inspeccion)).isZero();
    assertThat(estadoDe(inspeccion)).isEqualTo("ENVIADA");
  }

  @Test
  void unFalloDelForoNuncaBloqueaNiRevierteLaPublicacion() {
    UUID inspeccion = insertarInspeccion(versionV1, "ENVIADA");
    doThrow(new IllegalStateException("fallo simulado del foro"))
        .when(foroInspeccion)
        .abrirTrasPublicacion(any());

    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              publicar(inspeccion);
              eventos.publishEvent(new InspeccionPublicadaEvent(inspeccion, CATEDRATICO_ID));
            });

    // La transacción de publicación terminó sin error y la inspección quedó publicada.
    assertThat(estadoDe(inspeccion)).isEqualTo("PUBLICADA");
    assertThat(hilosDe(inspeccion)).isZero();
  }

  @Test
  void elHiloQueNoSeAbrioAlPublicarseSeAbreCuandoAlguienAbreElForo() {
    UUID inspeccion = insertarInspeccion(versionV1, "ENVIADA");
    doThrow(new IllegalStateException("fallo simulado del foro"))
        .when(foroInspeccion)
        .abrirTrasPublicacion(any());
    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              publicar(inspeccion);
              eventos.publishEvent(new InspeccionPublicadaEvent(inspeccion, CATEDRATICO_ID));
            });
    assertThat(hilosDe(inspeccion)).isZero();

    Apertura apertura = foroInspeccion.abrir(inspeccion, ESTUDIANTE_ID);

    assertThat(apertura.creado()).isTrue();
    assertThat(hilosDe(inspeccion)).isEqualTo(1);
  }

  // ---------------------------------------- solo las inspecciones publicadas tienen hilo

  @Test
  void unaInspeccionQueNoEstaPublicadaNoTieneHiloNiPuedeAbrirlo() {
    UUID borrador = insertarInspeccion(versionV1, "BORRADOR");

    listener.alPublicarse(new InspeccionPublicadaEvent(borrador, CATEDRATICO_ID));

    assertThat(hilosDe(borrador)).isZero();
    assertNoEncontrado(
        () -> foroInspeccion.abrir(borrador, ESTUDIANTE_ID), "inspeccion_no_encontrada");
    assertNoEncontrado(() -> foroInspeccion.obtener(borrador), "inspeccion_no_encontrada");
    assertThat(hilosDe(borrador)).isZero();
  }

  @Test
  void unaInspeccionInexistenteResponde404() {
    UUID inexistente = UUID.randomUUID();

    assertNoEncontrado(() -> foroInspeccion.obtener(inexistente), "inspeccion_no_encontrada");
    assertNoEncontrado(
        () -> foroInspeccion.abrir(inexistente, ESTUDIANTE_ID), "inspeccion_no_encontrada");
    // Un fallo del oyente nunca llega a quien publica.
    listener.alPublicarse(new InspeccionPublicadaEvent(inexistente, CATEDRATICO_ID));
  }

  @Test
  void elHiloDeUnaInspeccionSeOcultaSiElPuenteSeDesactiva() {
    UUID inspeccion = insertarInspeccion(versionV1, "PUBLICADA");
    UUID hilo = foroInspeccion.abrir(inspeccion, ESTUDIANTE_ID).hilo().hilo().id();
    foro.crearComentario(hilo, new CrearComentarioRequest("Antes", null), ESTUDIANTE_ID);

    jdbc.update(
        "UPDATE puente SET activo = false, inactivado_en = now(), inactivado_por_id = ?,"
            + " motivo_inactivacion = 'Demolido' WHERE id = ?",
        ADMINISTRADOR_ID,
        puente);

    assertNoEncontrado(() -> foro.obtenerHilo(hilo), "puente_no_encontrado");
    assertNoEncontrado(() -> foro.listarComentarios(hilo, 0, 20), "puente_no_encontrado");
    assertNoEncontrado(
        () ->
            foro.crearComentario(hilo, new CrearComentarioRequest("Después", null), ESTUDIANTE_ID),
        "puente_no_encontrado");
    assertNoEncontrado(() -> foroInspeccion.obtener(inspeccion), "inspeccion_no_encontrada");
    assertNoEncontrado(
        () -> foroInspeccion.abrir(inspeccion, ESTUDIANTE_ID), "inspeccion_no_encontrada");
  }

  @Test
  void elHiloDeUnaInspeccionNoApareceEnLaListaDeHilosDelPuente() {
    UUID inspeccion = insertarInspeccion(versionV1, "PUBLICADA");
    foroInspeccion.abrir(inspeccion, ESTUDIANTE_ID);
    foro.crearHilo(
        puente, new CrearHiloRequest("Tema del puente", "CONSULTA", "Texto"), ESTUDIANTE_ID);

    List<String> titulos =
        foro.listarHilosDePuente(puente, 0, 20).getContent().stream()
            .map(HiloResponse::titulo)
            .toList();

    assertThat(titulos).containsExactly("Tema del puente");
  }

  // ----------------------------------------------- inspecciones publicadas antes de esta HU

  @Test
  void elHiloDeUnaInspeccionAnteriorSeCreaAlAbrirseSuForoYSoloUnaVez() {
    UUID inspeccion = insertarInspeccion(versionV1, "PUBLICADA");

    Apertura primera = foroInspeccion.abrir(inspeccion, CATEDRATICO_ID);
    Apertura segunda = foroInspeccion.abrir(inspeccion, ESTUDIANTE_ID);

    assertThat(primera.creado()).isTrue();
    assertThat(segunda.creado()).isFalse();
    assertThat(segunda.hilo().hilo().id()).isEqualTo(primera.hilo().hilo().id());
    assertThat(hilosDe(inspeccion)).isEqualTo(1);
    // La bitácora guarda a quien abrió el foro; el hilo es del autor de la inspección.
    assertThat(
            contar(
                "SELECT count(*) FROM auditoria WHERE entidad = 'foro_hilo' AND entidad_id = ?"
                    + " AND accion = 'CREAR' AND usuario_id = ? AND proceso_automatico IS NULL",
                primera.hilo().hilo().id(),
                CATEDRATICO_ID))
        .isEqualTo(1);
    assertThat(primera.hilo().hilo().autorId()).isEqualTo(ESTUDIANTE_ID);
  }

  @Test
  void consultarElHiloNuncaLoCrea() {
    UUID inspeccion = insertarInspeccion(versionV1, "PUBLICADA");

    assertNoEncontrado(() -> foroInspeccion.obtener(inspeccion), "hilo_no_encontrado");

    assertThat(hilosDe(inspeccion)).isZero();
  }

  @Test
  void abrirElForoAlMismoTiempoCreaUnSoloHilo() throws Exception {
    UUID inspeccion = insertarInspeccion(versionV1, "PUBLICADA");
    int participantes = 8;
    ExecutorService pool = Executors.newFixedThreadPool(participantes);
    CountDownLatch salida = new CountDownLatch(1);
    List<Future<Boolean>> resultados = new ArrayList<>();
    try {
      for (int i = 0; i < participantes; i++) {
        // Algunos entran por la creación perezosa y otros por el evento de publicación.
        boolean porEvento = i % 2 == 0;
        resultados.add(
            pool.submit(
                () -> {
                  salida.await();
                  if (porEvento) {
                    listener.alPublicarse(new InspeccionPublicadaEvent(inspeccion, CATEDRATICO_ID));
                    return false;
                  }
                  return foroInspeccion.abrir(inspeccion, ESTUDIANTE_ID).creado();
                }));
      }
      salida.countDown();
      for (Future<Boolean> resultado : resultados) resultado.get();
    } finally {
      pool.shutdownNow();
    }

    assertThat(hilosDe(inspeccion)).isEqualTo(1);
    UUID hilo = foroInspeccion.obtener(inspeccion).hilo().id();
    assertThat(
            contar(
                "SELECT count(*) FROM auditoria WHERE entidad = 'foro_hilo' AND entidad_id = ?",
                hilo))
        .isEqualTo(1);
  }

  @Test
  void laBaseDeDatosNoAdmiteDosHilosParaLaMismaInspeccion() {
    UUID inspeccion = insertarInspeccion(versionV1, "PUBLICADA");
    foroInspeccion.abrir(inspeccion, ESTUDIANTE_ID);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO foro_hilo (id, puente_id, inspeccion_id, autor_id, categoria,"
                        + " titulo) VALUES (?, ?, ?, ?, 'CONSULTA', 'Duplicado')",
                    UUID.randomUUID(),
                    puente,
                    inspeccion,
                    ESTUDIANTE_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
    // Los hilos del puente sí pueden repetirse: el índice solo cubre los de inspección.
    foro.crearHilo(puente, new CrearHiloRequest("Uno", "CONSULTA", "Texto"), ESTUDIANTE_ID);
    foro.crearHilo(puente, new CrearHiloRequest("Dos", "CONSULTA", "Texto"), ESTUDIANTE_ID);
  }

  // ------------------------------------------------------ comentarios anclados (RN-FOR-02)

  @Test
  void unComentarioSeAnclaAUnElementoYQuedaEnLaBitacoraConSuAncla() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    ComentarioResponse comentario =
        foro.crearComentario(
            hilo,
            new CrearComentarioRequest("La viga se ve expuesta", null, ESTRIBO, null),
            ESTUDIANTE_ID);

    assertThat(comentario.elementoRef()).isEqualTo(ESTRIBO);
    assertThat(comentario.instanciaElementoId()).isNull();
    assertThat(
            contar(
                "SELECT count(*) FROM foro_comentario WHERE id = ? AND elemento_ref = ?"
                    + " AND instancia_elemento_id IS NULL",
                comentario.id(),
                ESTRIBO))
        .isEqualTo(1);
    assertThat(
            contar(
                "SELECT count(*) FROM auditoria WHERE entidad = 'foro_comentario'"
                    + " AND entidad_id = ? AND accion = 'CREAR' AND usuario_id = ?"
                    + " AND valores_posteriores ->> 'elementoRef' = ?",
                comentario.id(),
                ESTUDIANTE_ID,
                ESTRIBO))
        .isEqualTo(1);
  }

  @Test
  void tambienSePuedeAnclarAUnCampoDentroDeUnElemento() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    ComentarioResponse comentario =
        foro.crearComentario(
            hilo,
            new CrearComentarioRequest("El acero está oxidado", null, CAMPO_ESTRIBO, null),
            CATEDRATICO_ID);

    assertThat(comentario.elementoRef()).isEqualTo(CAMPO_ESTRIBO);
  }

  @Test
  void unElementoRepetibleSeAnclaALaInstanciaIndicada() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    ComentarioResponse comentario =
        foro.crearComentario(
            hilo,
            new CrearComentarioRequest("Socavación en la pila 2", null, SOCAVACION, pila2),
            ESTUDIANTE_ID);

    assertThat(comentario.elementoRef()).isEqualTo(SOCAVACION);
    assertThat(comentario.instanciaElementoId()).isEqualTo(pila2);
  }

  @Test
  void unElementoQueNoExisteEnElEsquemaSeRechazaConUn422YNoSeGuardaNada() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    assertInvalido(
        () ->
            foro.crearComentario(
                hilo,
                new CrearComentarioRequest("Texto", null, "/subestructura/inexistente", null),
                ESTUDIANTE_ID),
        "elementoRef");

    assertThat(contar("SELECT count(*) FROM foro_comentario WHERE hilo_id = ?", hilo)).isZero();
  }

  @Test
  void unaReferenciaMalFormadaSeRechazaConUn422() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    for (String referencia : List.of("subestructura", "/", "/subestructura//pilas", "/a~2b")) {
      assertInvalido(
          () ->
              foro.crearComentario(
                  hilo, new CrearComentarioRequest("Texto", null, referencia, null), ESTUDIANTE_ID),
          "elementoRef");
    }
    assertThat(contar("SELECT count(*) FROM foro_comentario WHERE hilo_id = ?", hilo)).isZero();
  }

  @Test
  void elElementoDebeExistirEnLaVersionConQueSeLevantoLaInspeccion() {
    UUID conV1 = hiloDeInspeccionPublicada(versionV1);
    UUID conV2 = hiloDeInspeccionPublicada(versionV2);

    assertInvalido(
        () ->
            foro.crearComentario(
                conV1,
                new CrearComentarioRequest("Texto", null, "/refuerzos", null),
                ESTUDIANTE_ID),
        "elementoRef");
    ComentarioResponse enV2 =
        foro.crearComentario(
            conV2, new CrearComentarioRequest("Texto", null, "/refuerzos", null), ESTUDIANTE_ID);

    assertThat(enV2.elementoRef()).isEqualTo("/refuerzos");
  }

  @Test
  void unElementoRepetibleExigeUnaInstanciaQueExista() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    assertInvalido(
        () ->
            foro.crearComentario(
                hilo, new CrearComentarioRequest("Texto", null, SOCAVACION, null), ESTUDIANTE_ID),
        "instanciaElementoId");
    assertInvalido(
        () ->
            foro.crearComentario(
                hilo,
                new CrearComentarioRequest("Texto", null, SOCAVACION, UUID.randomUUID()),
                ESTUDIANTE_ID),
        "instanciaElementoId");
    assertThat(contar("SELECT count(*) FROM foro_comentario WHERE hilo_id = ?", hilo)).isZero();
  }

  @Test
  void unElementoQueNoEsRepetibleNoLlevaInstancia() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    assertInvalido(
        () ->
            foro.crearComentario(
                hilo, new CrearComentarioRequest("Texto", null, ESTRIBO, pila1), ESTUDIANTE_ID),
        "instanciaElementoId");
  }

  @Test
  void unaInstanciaSinElementoSeRechaza() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    assertInvalido(
        () ->
            foro.crearComentario(
                hilo, new CrearComentarioRequest("Texto", null, null, pila1), ESTUDIANTE_ID),
        "elementoRef");
  }

  @Test
  void elHiloDeUnPuenteNoAdmiteComentariosAnclados() {
    HiloResponse hiloPuente =
        foro.crearHilo(puente, new CrearHiloRequest("Tema", "CONSULTA", "Texto"), ESTUDIANTE_ID);

    assertInvalido(
        () ->
            foro.crearComentario(
                hiloPuente.id(),
                new CrearComentarioRequest("Texto", null, ESTRIBO, null),
                ESTUDIANTE_ID),
        "elementoRef");
  }

  @Test
  void unComentarioGeneralQuedaEnElHiloSinAncla() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    ComentarioResponse general =
        foro.crearComentario(
            hilo,
            new CrearComentarioRequest("Opinión de conjunto", null, "  ", null),
            ESTUDIANTE_ID);

    assertThat(general.elementoRef()).isNull();
    assertThat(general.instanciaElementoId()).isNull();
    assertThat(foro.listarComentarios(hilo, 0, 20).getContent()).containsExactly(general);
  }

  @Test
  void unaRespuestaTomaElAnclaDeSuComentarioYNoPuedeLlevarOtra() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);
    ComentarioResponse principal =
        foro.crearComentario(
            hilo, new CrearComentarioRequest("Principal", null, SOCAVACION, pila1), ESTUDIANTE_ID);

    ComentarioResponse respuesta =
        foro.crearComentario(
            hilo, new CrearComentarioRequest("Respuesta", principal.id()), CATEDRATICO_ID);

    assertThat(respuesta.padreId()).isEqualTo(principal.id());
    assertThat(respuesta.elementoRef()).isEqualTo(SOCAVACION);
    assertThat(respuesta.instanciaElementoId()).isEqualTo(pila1);
    assertInvalido(
        () ->
            foro.crearComentario(
                hilo,
                new CrearComentarioRequest("Otra", principal.id(), ESTRIBO, null),
                CATEDRATICO_ID),
        "elementoRef");
  }

  // ------------------------------------------ marcadores: conteo y comentarios agrupados

  @Test
  void elResumenDeAnclasCuentaLosComentariosDeCadaElementoConSusRespuestas() {
    UUID inspeccion = insertarInspeccion(versionV1, "PUBLICADA");
    UUID hilo = foroInspeccion.abrir(inspeccion, ESTUDIANTE_ID).hilo().hilo().id();
    ComentarioResponse enEstribo =
        foro.crearComentario(
            hilo, new CrearComentarioRequest("Uno", null, ESTRIBO, null), ESTUDIANTE_ID);
    foro.crearComentario(
        hilo, new CrearComentarioRequest("Dos", null, ESTRIBO, null), CATEDRATICO_ID);
    foro.crearComentario(
        hilo, new CrearComentarioRequest("Respuesta", enEstribo.id()), ADMINISTRADOR_ID);
    foro.crearComentario(
        hilo, new CrearComentarioRequest("Pila 1", null, SOCAVACION, pila1), ESTUDIANTE_ID);
    foro.crearComentario(
        hilo, new CrearComentarioRequest("Pila 2", null, SOCAVACION, pila2), ESTUDIANTE_ID);
    foro.crearComentario(
        hilo,
        new CrearComentarioRequest("Otra vez pila 2", null, SOCAVACION, pila2),
        ESTUDIANTE_ID);
    foro.crearComentario(
        hilo, new CrearComentarioRequest("General", null, null, null), ESTUDIANTE_ID);

    HiloInspeccionResponse respuesta = foroInspeccion.obtener(inspeccion);

    assertThat(respuesta.hilo().totalComentarios()).isEqualTo(7);
    assertThat(respuesta.anclas())
        .containsExactlyInAnyOrder(
            new AnclaResponse(ESTRIBO, null, 3),
            new AnclaResponse(SOCAVACION, pila1, 1),
            new AnclaResponse(SOCAVACION, pila2, 2));
  }

  @Test
  void losComentariosSePuedenFiltrarPorElementoYPorInstancia() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);
    ComentarioResponse enEstribo =
        foro.crearComentario(
            hilo, new CrearComentarioRequest("Estribo", null, ESTRIBO, null), ESTUDIANTE_ID);
    ComentarioResponse respuesta =
        foro.crearComentario(
            hilo, new CrearComentarioRequest("Respuesta", enEstribo.id()), CATEDRATICO_ID);
    foro.crearComentario(
        hilo, new CrearComentarioRequest("Pila 1", null, SOCAVACION, pila1), ESTUDIANTE_ID);
    ComentarioResponse enPila2 =
        foro.crearComentario(
            hilo, new CrearComentarioRequest("Pila 2", null, SOCAVACION, pila2), ESTUDIANTE_ID);
    foro.crearComentario(
        hilo, new CrearComentarioRequest("General", null, null, null), ESTUDIANTE_ID);

    assertThat(ids(foro.listarComentarios(hilo, 0, 20, ESTRIBO, null).getContent()))
        .containsExactly(enEstribo.id(), respuesta.id());
    assertThat(ids(foro.listarComentarios(hilo, 0, 20, SOCAVACION, pila2).getContent()))
        .containsExactly(enPila2.id());
    assertThat(foro.listarComentarios(hilo, 0, 20, SOCAVACION, null).getContent()).isEmpty();
    assertThat(foro.listarComentarios(hilo, 0, 20, null, null).getContent()).hasSize(5);
    assertThat(foro.listarComentarios(hilo, 0, 20, "  ", null).getContent()).hasSize(5);
    assertThat(foro.listarComentarios(hilo, 0, 20, "/otro/elemento", null).getContent()).isEmpty();
  }

  @Test
  void unaInstanciaSinElementoEnElFiltroSeRechaza() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    assertThatThrownBy(() -> foro.listarComentarios(hilo, 0, 20, null, pila1))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("filtro_invalido");
            });
  }

  // ----------------------------------------------- filtros del listado: sección y generales

  private ComentarioResponse comentar(UUID hilo, String ref, UUID instancia, String texto) {
    return foro.crearComentario(
        hilo, new CrearComentarioRequest(texto, null, ref, instancia), ESTUDIANTE_ID);
  }

  @Test
  void filtrarPorSeccionTraeLoAncladoALaSeccionYATodoLoQueContiene() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);
    ComentarioResponse enLaSeccion = comentar(hilo, "/subestructura", null, "Sección");
    ComentarioResponse enElElemento = comentar(hilo, ESTRIBO, null, "Elemento");
    ComentarioResponse enLaInstancia = comentar(hilo, PILAS + "/socavacion", pila1, "Pila");
    ComentarioResponse respuesta =
        foro.crearComentario(
            hilo, new CrearComentarioRequest("Respuesta", enElElemento.id()), CATEDRATICO_ID);
    comentar(hilo, GENERAL, null, "Otra sección");
    foro.crearComentario(hilo, new CrearComentarioRequest("General", null), ESTUDIANTE_ID);

    List<UUID> ids =
        ids(
            foro.listarComentarios(
                    hilo, 0, 20, new FiltroComentarios(null, null, "subestructura", false))
                .getContent());

    assertThat(ids)
        .containsExactly(enLaSeccion.id(), enElElemento.id(), enLaInstancia.id(), respuesta.id());
  }

  @Test
  void laSeccionNoSeConfundeConOtraDeNombreParecidoNiInterpretaComodines() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);
    UUID propio = insertarComentario(hilo, "/sub_estructura/viga");
    insertarComentario(hilo, "/subXestructura/viga"); // «_» no es un comodín
    insertarComentario(hilo, "/sub_estructura_extra/viga"); // el nombre debe ser exacto
    insertarComentario(hilo, "/subestructura/viga");

    assertThat(
            ids(
                foro.listarComentarios(
                        hilo, 0, 20, new FiltroComentarios(null, null, "sub_estructura", false))
                    .getContent()))
        .containsExactly(propio);
    // «%» tampoco es un comodín
    assertThat(
            foro.listarComentarios(hilo, 0, 20, new FiltroComentarios(null, null, "sub%", false))
                .getContent())
        .isEmpty();
  }

  @Test
  void filtrarPorGeneralesTraeLosNoAncladosYSusRespuestas() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);
    comentar(hilo, ESTRIBO, null, "Anclado");
    ComentarioResponse general =
        foro.crearComentario(hilo, new CrearComentarioRequest("General", null), ESTUDIANTE_ID);
    ComentarioResponse respuesta =
        foro.crearComentario(
            hilo, new CrearComentarioRequest("Respuesta", general.id()), CATEDRATICO_ID);

    List<UUID> ids =
        ids(
            foro.listarComentarios(hilo, 0, 20, new FiltroComentarios(null, null, null, true))
                .getContent());

    assertThat(ids).containsExactly(general.id(), respuesta.id());
  }

  @Test
  void losFiltrosTambienSePaginan() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);
    for (int i = 0; i < 5; i++) comentar(hilo, ESTRIBO, null, "Uno " + i);
    comentar(hilo, GENERAL, null, "Fuera");
    FiltroComentarios filtro = new FiltroComentarios(null, null, "subestructura", false);

    var primera = foro.listarComentarios(hilo, 0, 2, filtro);
    var ultima = foro.listarComentarios(hilo, 2, 2, filtro);

    assertThat(primera.getTotalElements()).isEqualTo(5);
    assertThat(primera.getContent()).hasSize(2);
    assertThat(ultima.getContent()).hasSize(1);
    assertThat(ultima.isLast()).isTrue();
  }

  @Test
  void dosFiltrosALaVezOUnaSeccionConBarrasSeRechazanConUn422() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);

    assertFiltroInvalido(
        () ->
            foro.listarComentarios(
                hilo, 0, 20, new FiltroComentarios(ESTRIBO, null, "subestructura", false)),
        "filtro");
    assertFiltroInvalido(
        () -> foro.listarComentarios(hilo, 0, 20, new FiltroComentarios(null, null, "a", true)),
        "filtro");
    assertFiltroInvalido(
        () -> foro.listarComentarios(hilo, 0, 20, new FiltroComentarios(ESTRIBO, null, null, true)),
        "filtro");
    assertFiltroInvalido(
        () ->
            foro.listarComentarios(
                hilo, 0, 20, new FiltroComentarios(null, null, "subestructura/pilas", false)),
        "seccion");
    assertFiltroInvalido(
        () -> foro.listarComentarios(hilo, 0, 20, new FiltroComentarios(null, pila1, null, false)),
        "elementoRef");
  }

  @Test
  void unaSeccionVaciaSeIgnoraYDevuelveTodo() {
    UUID hilo = hiloDeInspeccionPublicada(versionV1);
    comentar(hilo, ESTRIBO, null, "Uno");
    foro.crearComentario(hilo, new CrearComentarioRequest("General", null), ESTUDIANTE_ID);

    assertThat(
            foro.listarComentarios(hilo, 0, 20, new FiltroComentarios(null, null, "  ", false))
                .getContent())
        .hasSize(2);
  }

  // ------------------------------------------------------------------------------- ayudas

  /** Un comentario con un ancla cualquiera, sin pasar por la validación del esquema. */
  private UUID insertarComentario(UUID hilo, String ref) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO foro_comentario (id, hilo_id, autor_id, elemento_ref, contenido, creado_en)"
            + " VALUES (?, ?, ?, ?, 'Texto', now())",
        id,
        hilo,
        ESTUDIANTE_ID,
        ref);
    return id;
  }

  private static void assertFiltroInvalido(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String campo) {
    assertThatThrownBy(accion)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("filtro_invalido");
              assertThat(e.getErrores()).extracting(error -> error.get("campo")).contains(campo);
            });
  }

  private UUID hiloDeInspeccionPublicada(UUID version) {
    UUID inspeccion = insertarInspeccion(version, "PUBLICADA");
    return foroInspeccion.abrir(inspeccion, ESTUDIANTE_ID).hilo().hilo().id();
  }

  private void publicar(UUID inspeccion) {
    jdbc.update(
        "UPDATE inspeccion SET estado = 'PUBLICADA', publicado_en = ? WHERE id = ?",
        java.sql.Timestamp.from(AHORA),
        inspeccion);
  }

  private String estadoDe(UUID inspeccion) {
    return jdbc.queryForObject(
        "SELECT estado FROM inspeccion WHERE id = ?", String.class, inspeccion);
  }

  private int hilosDe(UUID inspeccion) {
    return contar("SELECT count(*) FROM foro_hilo WHERE inspeccion_id = ?", inspeccion);
  }

  private static List<UUID> ids(List<ComentarioResponse> comentarios) {
    return comentarios.stream().map(ComentarioResponse::id).toList();
  }

  private static void assertInvalido(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String campo) {
    assertThatThrownBy(accion)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("comentario_invalido");
              assertThat(e.getErrores()).extracting(error -> error.get("campo")).contains(campo);
            });
  }

  private static void assertNoEncontrado(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String codigo) {
    assertThatThrownBy(accion)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(e.getCode()).isEqualTo(codigo);
            });
  }

  private int contar(String sql, Object... argumentos) {
    Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
    return total == null ? 0 : total;
  }

  private void insertarUsuario(UUID id, String email, String rol, String usuario) {
    jdbc.update(
        """
        INSERT INTO usuario (
          id, email, password_hash, rol_id, verificado, activado, activo, nombre_usuario,
          nombre_completo
        )
        SELECT ?, ?, 'hash-de-prueba', id, true, true, true, ?, 'Nombre Completo Privado'
        FROM rol
        WHERE nombre = ?
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        email,
        usuario,
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
          m.id, 'Puente HU-064', 'CA-1',
          ST_SetSRID(ST_MakePoint(-90.615, 14.481), 4326)::geography, ?
        FROM municipio m WHERE m.codigo_ine = '0114'
        """,
        id,
        "T-" + id,
        ADMINISTRADOR_ID);
    return id;
  }

  private UUID insertarVersion(String esquema) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO version_formulario(id, codigo, esquema, creado_por_id)"
            + " VALUES (?, ?, ?::jsonb, ?)",
        id,
        "hu064-" + id,
        esquema,
        ADMINISTRADOR_ID);
    return id;
  }

  /** La inspección la levantó el Estudiante el 15/10/2026 con dos pilas. */
  private UUID insertarInspeccion(UUID version, String estado) {
    UUID id = UUID.randomUUID();
    String datos =
        """
        {"subestructura":{
           "estribo_entrada":{"viga_apoyo":{"exposicion_acero":"SEVERO"}},
           "pilas":[
             {"instancia_id":"%s","socavacion":{"aguas_arriba":"LEVE"}},
             {"instancia_id":"%s","socavacion":{"aguas_arriba":"SEVERO"}}]},
         "observaciones_generales":"Sin novedad"}
        """
            .formatted(pila1, pila2);
    jdbc.update(
        "INSERT INTO inspeccion(id, puente_id, version_formulario_id, autor_id,"
            + " rol_autor_creacion, nombre_autor_capturado, fecha_inspeccion, dispositivo_id,"
            + " estado, publicado_en, datos) VALUES (?, ?, ?, ?, 'ESTUDIANTE', 'Ana',"
            + " DATE '2026-10-15', 'test', ?, ?, ?::jsonb)",
        id,
        puente,
        version,
        ESTUDIANTE_ID,
        estado,
        "PUBLICADA".equals(estado) ? java.sql.Timestamp.from(AHORA) : null,
        datos);
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
      return new ObjectMapper().findAndRegisterModules();
    }
  }
}
