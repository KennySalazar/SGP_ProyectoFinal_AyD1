package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.CrearComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.CrearHiloRequest;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.mapper.ForoMapperImpl;
import gt.usac.cunoc.sgp.foro.model.CategoriaHilo;
import gt.usac.cunoc.sgp.foro.service.ForoService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
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

/**
 * Foro por puente contra PostgreSQL/PostGIS real (HU-063; RN-FOR-01, RN-FOR-04, RN-FOR-06,
 * RN-FOR-09; DT-BE-08, DT-BE-09).
 */
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
      "spring.jpa.properties.hibernate.generate_statistics=true"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  ForoService.class,
  AuditoriaWriter.class,
  ForoMapperImpl.class,
  ForoPuenteIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ForoPuenteIntegrationTest {

  private static final Instant INICIO = Instant.parse("2026-10-20T15:00:00Z");
  private static final UUID ESTUDIANTE_ID = UUID.fromString("019a0000-0000-7000-8000-000000006301");
  private static final UUID CATEDRATICO_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000006302");
  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000006303");
  private static final UUID SIN_NOMBRE_ID = UUID.fromString("019a0000-0000-7000-8000-000000006304");

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_hu063_test")
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
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;
  @Autowired private RelojMutable reloj;
  @Autowired private ObjectMapper json;

  private UUID puenteId;

  @BeforeEach
  void prepararDatos() {
    reloj.fijar(INICIO);
    insertarUsuario(ESTUDIANTE_ID, "estudiante.hu063@ejemplo.com", "ESTUDIANTE", "ana.estudiante");
    insertarUsuario(CATEDRATICO_ID, "catedratico.hu063@ejemplo.com", "CATEDRATICO", "carlos.cat");
    insertarUsuario(ADMINISTRADOR_ID, "admin.hu063@ejemplo.com", "ADMINISTRADOR", "admin.sistema");
    insertarUsuario(SIN_NOMBRE_ID, "anonimo.hu063@ejemplo.com", "PROFESIONAL_EXTERNO", null);
    puenteId = insertarPuente();
  }

  @Test
  void crearUnHiloGuardaElHiloSuPrimerComentarioYLaAuditoria() {
    HiloResponse hilo =
        foro.crearHilo(
            puenteId,
            new CrearHiloRequest("Socavación en la pila 2", "ALERTA", "Se ve el acero expuesto."),
            ESTUDIANTE_ID);

    assertThat(hilo.puenteId()).isEqualTo(puenteId);
    assertThat(hilo.inspeccionId()).isNull();
    assertThat(hilo.categoria()).isEqualTo(CategoriaHilo.ALERTA);
    assertThat(hilo.titulo()).isEqualTo("Socavación en la pila 2");
    assertThat(hilo.autorId()).isEqualTo(ESTUDIANTE_ID);
    assertThat(hilo.autorUsuario()).isEqualTo("ana.estudiante");
    assertThat(hilo.autorRol()).isEqualTo("ESTUDIANTE");
    assertThat(hilo.cerrado()).isFalse();
    assertThat(hilo.totalComentarios()).isEqualTo(1);
    assertThat(hilo.creadoEn().toInstant()).isEqualTo(INICIO);

    var comentario =
        jdbc.queryForMap(
            "SELECT autor_id, padre_id, contenido, elemento_ref FROM foro_comentario"
                + " WHERE hilo_id = ?",
            hilo.id());
    assertThat(comentario.get("autor_id")).isEqualTo(ESTUDIANTE_ID);
    assertThat(comentario.get("padre_id")).isNull();
    assertThat(comentario.get("contenido")).isEqualTo("Se ve el acero expuesto.");
    assertThat(comentario.get("elemento_ref")).isNull();

    assertThat(
            contar(
                "SELECT count(*) FROM auditoria WHERE entidad = 'foro_hilo' AND entidad_id = ?"
                    + " AND accion = 'CREAR' AND usuario_id = ?",
                hilo.id(),
                ESTUDIANTE_ID))
        .isEqualTo(1);
    assertThat(
            contar(
                "SELECT count(*) FROM auditoria a JOIN foro_comentario c ON c.id = a.entidad_id"
                    + " WHERE a.entidad = 'foro_comentario' AND a.accion = 'CREAR'"
                    + " AND c.hilo_id = ?",
                hilo.id()))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(CategoriaHilo.class)
  void cadaUnaDeLasCincoCategoriasSeAcepta(CategoriaHilo categoria) {
    HiloResponse hilo =
        foro.crearHilo(
            puenteId,
            new CrearHiloRequest("Un tema", categoria.name(), "Primer comentario"),
            CATEDRATICO_ID);

    assertThat(hilo.categoria()).isEqualTo(categoria);
  }

  @Test
  void lasCategoriasSonLasCincoDeRnFor06() {
    assertThat(CategoriaHilo.values())
        .extracting(Enum::name)
        .containsExactly(
            "DISCUSION_TECNICA",
            "PROPUESTA_INTERVENCION",
            "CONSULTA",
            "REFERENCIA_DOCUMENTAL",
            "ALERTA");
  }

  @Test
  void losDatosInvalidosDan422ConElDetalleDeCadaCampoYNoCreanNada() {
    assertThatThrownBy(
            () ->
                foro.crearHilo(
                    puenteId, new CrearHiloRequest("   ", "NO_EXISTE", "  \n "), ESTUDIANTE_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("hilo_invalido");
              assertThat(e.getErrores())
                  .extracting(error -> error.get("campo"))
                  .containsExactlyInAnyOrder("titulo", "categoria", "comentario");
            });

    assertThat(contar("SELECT count(*) FROM foro_hilo WHERE puente_id = ?", puenteId)).isZero();
  }

  @Test
  void elTituloYElComentarioTienenUnLimiteDeLongitud() {
    String tituloLargo = "t".repeat(121);
    String comentarioLargo = "c".repeat(2001);

    assertThatThrownBy(
            () ->
                foro.crearHilo(
                    puenteId,
                    new CrearHiloRequest(tituloLargo, "CONSULTA", comentarioLargo),
                    ESTUDIANTE_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.getErrores())
                    .extracting(error -> error.get("campo"))
                    .containsExactlyInAnyOrder("titulo", "comentario"));

    HiloResponse enElLimite =
        foro.crearHilo(
            puenteId,
            new CrearHiloRequest("t".repeat(120), "CONSULTA", "c".repeat(2000)),
            ESTUDIANTE_ID);
    assertThat(enElLimite.titulo()).hasSize(120);
  }

  @Test
  void elTituloSeNormalizaAUnaSolaLinea() {
    HiloResponse hilo =
        foro.crearHilo(
            puenteId,
            new CrearHiloRequest("  Grieta \n  en   el\tcabezal  ", "CONSULTA", "Hola"),
            ESTUDIANTE_ID);

    assertThat(hilo.titulo()).isEqualTo("Grieta en el cabezal");
  }

  @Test
  void elHtmlSeGuardaComoTextoSinTocarloYLosCaracteresDeControlSeQuitan() {
    HiloResponse hilo =
        foro.crearHilo(
            puenteId,
            new CrearHiloRequest(
                "Prueba", "CONSULTA", "<script>alert(1)</script>\r\nlínea\u0000 2"),
            ESTUDIANTE_ID);

    String contenido =
        jdbc.queryForObject(
            "SELECT contenido FROM foro_comentario WHERE hilo_id = ?", String.class, hilo.id());
    assertThat(contenido).isEqualTo("<script>alert(1)</script>\nlínea 2");
  }

  @Test
  void listarOrdenaPorActividadRecienteYCuentaLosComentarios() {
    HiloResponse antiguo = crearHilo("Antiguo", ESTUDIANTE_ID);
    reloj.avanzar(Duration.ofMinutes(5));
    HiloResponse medio = crearHilo("Medio", CATEDRATICO_ID);
    reloj.avanzar(Duration.ofMinutes(5));
    HiloResponse nuevo = crearHilo("Nuevo", ADMINISTRADOR_ID);

    assertThat(titulos(foro.listarHilosDePuente(puenteId, 0, 20)))
        .containsExactly("Nuevo", "Medio", "Antiguo");

    reloj.avanzar(Duration.ofMinutes(5));
    foro.crearComentario(
        antiguo.id(), new CrearComentarioRequest("Vuelve a la conversación", null), ESTUDIANTE_ID);
    foro.crearComentario(
        antiguo.id(), new CrearComentarioRequest("Otro más", null), CATEDRATICO_ID);

    Page<HiloResponse> pagina = foro.listarHilosDePuente(puenteId, 0, 20);
    assertThat(titulos(pagina)).containsExactly("Antiguo", "Nuevo", "Medio");
    assertThat(pagina.getContent())
        .extracting(HiloResponse::totalComentarios)
        .containsExactly(3L, 1L, 1L);
    assertThat(medio.totalComentarios()).isEqualTo(1);
    assertThat(nuevo.autorRol()).isEqualTo("ADMINISTRADOR");
  }

  @Test
  void listarSoloMuestraLosHilosDeEsePuenteYPagina() {
    UUID otroPuente = insertarPuente();
    for (int i = 1; i <= 5; i++) {
      reloj.avanzar(Duration.ofMinutes(1));
      crearHilo("Hilo " + i, ESTUDIANTE_ID);
    }
    foro.crearHilo(otroPuente, new CrearHiloRequest("Ajeno", "CONSULTA", "Texto"), ESTUDIANTE_ID);

    Page<HiloResponse> primera = foro.listarHilosDePuente(puenteId, 0, 2);
    assertThat(primera.getTotalElements()).isEqualTo(5);
    assertThat(primera.getTotalPages()).isEqualTo(3);
    assertThat(titulos(primera)).containsExactly("Hilo 5", "Hilo 4");
    assertThat(titulos(foro.listarHilosDePuente(puenteId, 2, 2))).containsExactly("Hilo 1");
    assertThat(foro.listarHilosDePuente(otroPuente, 0, 20).getContent())
        .extracting(HiloResponse::titulo)
        .containsExactly("Ajeno");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 101})
  void elTamanioDePaginaDebeEstarEntre1Y100(int tamanio) {
    assertPaginacionInvalida(() -> foro.listarHilosDePuente(puenteId, 0, tamanio));
    HiloResponse hilo = crearHilo("Hilo", ESTUDIANTE_ID);
    assertPaginacionInvalida(() -> foro.listarComentarios(hilo.id(), 0, tamanio));
  }

  @Test
  void laPaginaNoPuedeSerNegativaYElMaximoDe100SeAcepta() {
    assertPaginacionInvalida(() -> foro.listarHilosDePuente(puenteId, -1, 20));
    assertThat(foro.listarHilosDePuente(puenteId, 0, 100).getContent()).isEmpty();
  }

  @Test
  void comentarYResponderSeGuardanAUnSoloNivel() {
    HiloResponse hilo = crearHilo("Conversación", ESTUDIANTE_ID);
    reloj.avanzar(Duration.ofMinutes(1));
    ComentarioResponse principal =
        foro.crearComentario(
            hilo.id(), new CrearComentarioRequest("Comentario principal", null), CATEDRATICO_ID);
    reloj.avanzar(Duration.ofMinutes(1));
    ComentarioResponse respuesta =
        foro.crearComentario(
            hilo.id(), new CrearComentarioRequest("Respuesta", principal.id()), ADMINISTRADOR_ID);

    assertThat(principal.padreId()).isNull();
    assertThat(respuesta.padreId()).isEqualTo(principal.id());
    assertThat(respuesta.autorRol()).isEqualTo("ADMINISTRADOR");

    List<ComentarioResponse> lista = foro.listarComentarios(hilo.id(), 0, 20).getContent();
    assertThat(lista)
        .extracting(ComentarioResponse::contenido)
        .containsExactly("Texto inicial", "Comentario principal", "Respuesta");
    assertThat(lista)
        .extracting(ComentarioResponse::padreId)
        .containsExactly(null, null, principal.id());
    assertThat(foro.obtenerHilo(hilo.id()).totalComentarios()).isEqualTo(3);
  }

  @Test
  void noSePuedeResponderAUnaRespuesta() {
    HiloResponse hilo = crearHilo("Conversación", ESTUDIANTE_ID);
    ComentarioResponse principal =
        foro.crearComentario(
            hilo.id(), new CrearComentarioRequest("Principal", null), CATEDRATICO_ID);
    ComentarioResponse respuesta =
        foro.crearComentario(
            hilo.id(), new CrearComentarioRequest("Respuesta", principal.id()), CATEDRATICO_ID);

    assertThatThrownBy(
            () ->
                foro.crearComentario(
                    hilo.id(),
                    new CrearComentarioRequest("Respuesta de respuesta", respuesta.id()),
                    ESTUDIANTE_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("comentario_invalido");
              assertThat(e.getErrores()).extracting(x -> x.get("campo")).containsExactly("padreId");
            });
    assertThat(foro.obtenerHilo(hilo.id()).totalComentarios()).isEqualTo(3);
  }

  @Test
  void noSePuedeResponderAUnComentarioDeOtroHiloNiAUnoInexistente() {
    HiloResponse uno = crearHilo("Uno", ESTUDIANTE_ID);
    HiloResponse otro = crearHilo("Otro", ESTUDIANTE_ID);
    ComentarioResponse delOtro =
        foro.crearComentario(otro.id(), new CrearComentarioRequest("Texto", null), ESTUDIANTE_ID);

    for (UUID padre : List.of(delOtro.id(), UUID.randomUUID())) {
      assertThatThrownBy(
              () ->
                  foro.crearComentario(
                      uno.id(), new CrearComentarioRequest("Respuesta", padre), ESTUDIANTE_ID))
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(e.getErrores())
                    .extracting(x -> x.get("campo"))
                    .containsExactly("padreId");
              });
    }
    assertThat(foro.obtenerHilo(uno.id()).totalComentarios()).isEqualTo(1);
  }

  @Test
  void unComentarioVacioOMuyLargoSeRechaza() {
    HiloResponse hilo = crearHilo("Hilo", ESTUDIANTE_ID);

    for (String contenido : List.of("   ", "x".repeat(2001))) {
      assertThatThrownBy(
              () ->
                  foro.crearComentario(
                      hilo.id(), new CrearComentarioRequest(contenido, null), ESTUDIANTE_ID))
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> {
                assertThat(e.getCode()).isEqualTo("comentario_invalido");
                assertThat(e.getErrores())
                    .extracting(x -> x.get("campo"))
                    .containsExactly("contenido");
              });
    }
    assertThat(foro.obtenerHilo(hilo.id()).totalComentarios()).isEqualTo(1);
  }

  @Test
  void unPuenteInexistenteOInactivoDa404() {
    UUID inactivo = insertarPuente();
    jdbc.update(
        "UPDATE puente SET activo = false, inactivado_en = now(), inactivado_por_id = ?,"
            + " motivo_inactivacion = 'Demolido' WHERE id = ?",
        ADMINISTRADOR_ID,
        inactivo);

    for (UUID puente : List.of(UUID.randomUUID(), inactivo)) {
      assertNoEncontrado(() -> foro.listarHilosDePuente(puente, 0, 20), "puente_no_encontrado");
      assertNoEncontrado(
          () ->
              foro.crearHilo(
                  puente, new CrearHiloRequest("Tema", "CONSULTA", "Texto"), ESTUDIANTE_ID),
          "puente_no_encontrado");
    }
  }

  @Test
  void unHiloInexistenteODeUnPuenteInactivoDa404() {
    HiloResponse hilo = crearHilo("Se queda sin puente", ESTUDIANTE_ID);
    jdbc.update(
        "UPDATE puente SET activo = false, inactivado_en = now(), inactivado_por_id = ?,"
            + " motivo_inactivacion = 'Demolido' WHERE id = ?",
        ADMINISTRADOR_ID,
        puenteId);

    assertNoEncontrado(() -> foro.obtenerHilo(UUID.randomUUID()), "hilo_no_encontrado");
    assertNoEncontrado(() -> foro.obtenerHilo(hilo.id()), "puente_no_encontrado");
    assertNoEncontrado(
        () -> foro.listarComentarios(UUID.randomUUID(), 0, 20), "hilo_no_encontrado");
    assertNoEncontrado(() -> foro.listarComentarios(hilo.id(), 0, 20), "puente_no_encontrado");
    for (UUID id : List.of(UUID.randomUUID(), hilo.id())) {
      assertThatThrownBy(
              () ->
                  foro.crearComentario(
                      id, new CrearComentarioRequest("Texto", null), ESTUDIANTE_ID))
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
  }

  @Test
  void unHiloCerradoNoAdmiteComentarios() {
    HiloResponse hilo = crearHilo("Se va a cerrar", ESTUDIANTE_ID);
    jdbc.update(
        "UPDATE foro_hilo SET cerrado_en = now(), cerrado_por_id = ?, motivo_cierre = 'Resuelto'"
            + " WHERE id = ?",
        CATEDRATICO_ID,
        hilo.id());

    assertThat(foro.obtenerHilo(hilo.id()).cerrado()).isTrue();
    assertThatThrownBy(
            () ->
                foro.crearComentario(
                    hilo.id(), new CrearComentarioRequest("Tarde", null), ESTUDIANTE_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("hilo_cerrado");
            });
    assertThat(foro.listarComentarios(hilo.id(), 0, 20).getContent()).hasSize(1);
  }

  @Test
  void laRespuestaNuncaIncluyeElCorreoNiElNombreCompletoYUnAutorSinUsuarioSoloMuestraSuRol()
      throws Exception {
    HiloResponse hilo =
        foro.crearHilo(
            puenteId, new CrearHiloRequest("Sin nombre", "CONSULTA", "Texto"), SIN_NOMBRE_ID);

    assertThat(hilo.autorUsuario()).isNull();
    assertThat(hilo.autorRol()).isEqualTo("PROFESIONAL_EXTERNO");
    String hiloJson = json.writeValueAsString(hilo);
    String comentariosJson =
        json.writeValueAsString(foro.listarComentarios(hilo.id(), 0, 20).getContent());
    assertThat(hiloJson + comentariosJson)
        .doesNotContain("@ejemplo.com")
        .doesNotContain("email")
        .doesNotContain("Privado");
  }

  @Test
  void dosComentariosSimultaneosNoChocanEntreSi() throws Exception {
    HiloResponse hilo = crearHilo("Concurrencia", ESTUDIANTE_ID);
    int hilos = 8;
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(hilos);
    try {
      List<Future<ComentarioResponse>> resultados = new ArrayList<>();
      for (int i = 0; i < hilos; i++) {
        int numero = i;
        Callable<ComentarioResponse> tarea =
            () -> {
              salida.await();
              return foro.crearComentario(
                  hilo.id(),
                  new CrearComentarioRequest("Comentario " + numero, null),
                  numero % 2 == 0 ? ESTUDIANTE_ID : CATEDRATICO_ID);
            };
        resultados.add(pool.submit(tarea));
      }
      salida.countDown();
      for (Future<ComentarioResponse> resultado : resultados) {
        assertThat(resultado.get().id()).isNotNull();
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(foro.obtenerHilo(hilo.id()).totalComentarios()).isEqualTo(1 + hilos);
    assertThat(foro.listarComentarios(hilo.id(), 0, 100).getContent()).hasSize(1 + hilos);
  }

  @Test
  void losListadosNoHacenUnaConsultaPorFila() {
    crearHilo("Primero", ESTUDIANTE_ID);
    em.clear();
    Statistics estadisticas = estadisticas();
    estadisticas.clear();
    foro.listarHilosDePuente(puenteId, 0, 20);
    long conUnHilo = estadisticas.getPrepareStatementCount();

    UUID otroPuente = insertarPuente();
    UUID[] autores = {ESTUDIANTE_ID, CATEDRATICO_ID, ADMINISTRADOR_ID, SIN_NOMBRE_ID};
    HiloResponse ultimo = null;
    for (int i = 0; i < 12; i++) {
      ultimo =
          foro.crearHilo(
              otroPuente,
              new CrearHiloRequest("Hilo " + i, "CONSULTA", "Texto " + i),
              autores[i % autores.length]);
      foro.crearComentario(
          ultimo.id(), new CrearComentarioRequest("Otro", null), autores[(i + 1) % autores.length]);
    }
    em.clear();
    estadisticas.clear();
    Page<HiloResponse> pagina = foro.listarHilosDePuente(otroPuente, 0, 20);
    long conDoce = estadisticas.getPrepareStatementCount();

    assertThat(pagina.getContent()).hasSize(12);
    assertThat(conDoce).isLessThanOrEqualTo(conUnHilo + 1).isLessThanOrEqualTo(6);

    em.clear();
    estadisticas.clear();
    for (int i = 0; i < 12; i++) {
      foro.crearComentario(
          ultimo.id(), new CrearComentarioRequest("Más " + i, null), autores[i % autores.length]);
    }
    em.clear();
    estadisticas.clear();
    foro.listarComentarios(ultimo.id(), 0, 100);
    assertThat(estadisticas.getPrepareStatementCount()).isLessThanOrEqualTo(6);
  }

  private HiloResponse crearHilo(String titulo, UUID autor) {
    return foro.crearHilo(
        puenteId, new CrearHiloRequest(titulo, "DISCUSION_TECNICA", "Texto inicial"), autor);
  }

  private static List<String> titulos(Page<HiloResponse> pagina) {
    return pagina.getContent().stream().map(HiloResponse::titulo).toList();
  }

  private static void assertPaginacionInvalida(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable accion) {
    assertThatThrownBy(accion)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("paginacion_invalida");
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

  private Statistics estadisticas() {
    return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
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
          m.id, 'Puente HU-063', 'CA-1',
          ST_SetSRID(ST_MakePoint(-90.615, 14.481), 4326)::geography, ?
        FROM municipio m WHERE m.codigo_ine = '0114'
        """,
        id,
        "T-" + id,
        ADMINISTRADOR_ID);
    return id;
  }

  /** Reloj que se mueve a voluntad para probar el orden por actividad. */
  static class RelojMutable extends Clock {
    private volatile Instant ahora = INICIO;

    void fijar(Instant instante) {
      ahora = instante;
    }

    void avanzar(Duration duracion) {
      ahora = ahora.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return ahora;
    }
  }

  @TestConfiguration
  static class Config {

    @Bean
    RelojMutable reloj() {
      return new RelojMutable();
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }
  }
}
