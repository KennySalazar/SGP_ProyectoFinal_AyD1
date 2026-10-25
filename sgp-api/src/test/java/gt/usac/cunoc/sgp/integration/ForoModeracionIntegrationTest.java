package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.validation.ValidadorAnclajeFormulario;
import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.CrearComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.CrearHiloRequest;
import gt.usac.cunoc.sgp.foro.dto.EditarComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.dto.InspeccionSinRevisionResponse;
import gt.usac.cunoc.sgp.foro.dto.MotivoModeracionRequest;
import gt.usac.cunoc.sgp.foro.mapper.ForoMapperImpl;
import gt.usac.cunoc.sgp.foro.service.AnclajeComentarioForo;
import gt.usac.cunoc.sgp.foro.service.AvisosForo;
import gt.usac.cunoc.sgp.foro.service.FiltroComentarios;
import gt.usac.cunoc.sgp.foro.service.ForoModeracionService;
import gt.usac.cunoc.sgp.foro.service.ForoService;
import gt.usac.cunoc.sgp.foro.service.LimiteTasaForo;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
 * Edición, eliminación y moderación auditada del foro contra PostgreSQL/PostGIS real (HU-065;
 * RN-FOR-05, RN-FOR-08, RN-REV-02, RN-USR-09). El reloj del servidor se controla para probar el
 * plazo de edición.
 */
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  ForoService.class,
  ForoModeracionService.class,
  AnclajeComentarioForo.class,
  AvisosForo.class,
  NotificacionService.class,
  ValidadorAnclajeFormulario.class,
  AuditoriaWriter.class,
  ForoMapperImpl.class,
  ForoModeracionIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ForoModeracionIntegrationTest {

  private static final Instant INICIO = Instant.parse("2026-10-20T15:00:00Z");
  private static final UUID ESTUDIANTE_ID = UUID.fromString("019a0000-0000-7000-8000-000000006501");
  private static final UUID OTRO_ESTUDIANTE_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000006502");
  private static final UUID CATEDRATICO_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000006503");
  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000006504");
  private static final UUID PROFESIONAL_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000006505");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu065_test")
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

  /** El límite de tasa (HU-066) tiene su propia prueba; aquí se publica sin esperar. */
  @MockBean private LimiteTasaForo limites;

  @Autowired private ForoModeracionService moderacion;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RelojMutable reloj;

  private UUID puenteId;
  private HiloResponse hilo;

  @BeforeEach
  void preparar() {
    reloj.fijar(INICIO);
    insertarUsuario(ESTUDIANTE_ID, "estudiante.hu065@ejemplo.com", "ESTUDIANTE", "ana.estudiante");
    insertarUsuario(OTRO_ESTUDIANTE_ID, "otro.hu065@ejemplo.com", "ESTUDIANTE", "beto.estudiante");
    insertarUsuario(CATEDRATICO_ID, "catedratico.hu065@ejemplo.com", "CATEDRATICO", "carlos.cat");
    insertarUsuario(ADMINISTRADOR_ID, "admin.hu065@ejemplo.com", "ADMINISTRADOR", "admin.sistema");
    insertarUsuario(PROFESIONAL_ID, "pro.hu065@ejemplo.com", "PROFESIONAL_EXTERNO", "pro.externo");
    puenteId = insertarPuente();
    hilo =
        foro.crearHilo(
            puenteId,
            new CrearHiloRequest("Grietas en la losa", "DISCUSION_TECNICA", "Primer comentario"),
            ESTUDIANTE_ID);
  }

  // ------------------------------------------------- edición dentro de 15 minutos (RN-FOR-05)

  @Test
  void elAutorEditaDentroDelPlazoYQuedaEnLaBitacoraConAntesYDespues() {
    ComentarioResponse comentario = comentar("Texto con erorr", ESTUDIANTE_ID);
    reloj.avanzar(Duration.ofMinutes(10));

    ComentarioResponse editado =
        moderacion.editar(
            comentario.id(), new EditarComentarioRequest("  Texto corregido  "), ESTUDIANTE_ID);

    assertThat(editado.contenido()).isEqualTo("Texto corregido");
    assertThat(editado.editadoEn().toInstant()).isEqualTo(INICIO.plus(Duration.ofMinutes(10)));
    assertThat(editado.editableHasta().toInstant()).isEqualTo(INICIO.plus(Duration.ofMinutes(15)));
    assertThat(contenidoGuardado(comentario.id())).isEqualTo("Texto corregido");

    Map<String, Object> registro =
        jdbc.queryForMap(
            "SELECT usuario_id, accion, valores_anteriores->>'contenido' AS antes,"
                + " valores_posteriores->>'contenido' AS despues FROM auditoria"
                + " WHERE entidad = 'foro_comentario' AND entidad_id = ? AND accion = 'MODIFICAR'",
            comentario.id());
    assertThat(registro.get("usuario_id")).isEqualTo(ESTUDIANTE_ID);
    assertThat(registro.get("antes")).isEqualTo("Texto con erorr");
    assertThat(registro.get("despues")).isEqualTo("Texto corregido");
  }

  @Test
  void elPlazoDeQuinceMinutosLoDecideLaHoraDelServidor() {
    ComentarioResponse comentario = comentar("Original", ESTUDIANTE_ID);

    reloj.avanzar(Duration.ofMinutes(15));
    assertThat(
            moderacion
                .editar(
                    comentario.id(), new EditarComentarioRequest("Justo a tiempo"), ESTUDIANTE_ID)
                .contenido())
        .isEqualTo("Justo a tiempo");

    // El cliente no envía su hora: aunque su reloj vaya atrasado, cuenta la del servidor.
    reloj.avanzar(Duration.ofSeconds(1));
    assertProblema(
        () ->
            moderacion.editar(
                comentario.id(), new EditarComentarioRequest("Demasiado tarde"), ESTUDIANTE_ID),
        HttpStatus.CONFLICT,
        "plazo_edicion_vencido");
    assertThat(contenidoGuardado(comentario.id())).isEqualTo("Justo a tiempo");
  }

  @Test
  void unaEdicionVaciaOLargaDa422YNoCambiaNada() {
    ComentarioResponse comentario = comentar("Original", ESTUDIANTE_ID);

    assertProblema(
        () ->
            moderacion.editar(comentario.id(), new EditarComentarioRequest(" \n "), ESTUDIANTE_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "comentario_invalido");
    assertProblema(
        () ->
            moderacion.editar(
                comentario.id(), new EditarComentarioRequest("x".repeat(2001)), ESTUDIANTE_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "comentario_invalido");
    assertThat(contenidoGuardado(comentario.id())).isEqualTo("Original");
  }

  // ---------------------------------------------------------- solo el autor (RN-FOR-05)

  @ParameterizedTest
  @ValueSource(strings = {"OTRO_ESTUDIANTE", "CATEDRATICO", "ADMINISTRADOR", "PROFESIONAL"})
  void nadieMasQueElAutorEditaOEliminaUnComentario(String quien) {
    UUID otro =
        switch (quien) {
          case "OTRO_ESTUDIANTE" -> OTRO_ESTUDIANTE_ID;
          case "CATEDRATICO" -> CATEDRATICO_ID;
          case "ADMINISTRADOR" -> ADMINISTRADOR_ID;
          default -> PROFESIONAL_ID;
        };
    ComentarioResponse comentario = comentar("Mío", ESTUDIANTE_ID);

    assertProblema(
        () -> moderacion.editar(comentario.id(), new EditarComentarioRequest("Ajeno"), otro),
        HttpStatus.FORBIDDEN,
        "comentario_ajeno");
    assertProblema(
        () -> moderacion.eliminar(comentario.id(), otro), HttpStatus.FORBIDDEN, "comentario_ajeno");
    assertThat(contenidoGuardado(comentario.id())).isEqualTo("Mío");
    assertThat(eliminadoEn(comentario.id())).isNull();
  }

  @Test
  void unComentarioInexistenteDa404() {
    assertProblema(
        () -> moderacion.eliminar(UUID.randomUUID(), ESTUDIANTE_ID),
        HttpStatus.NOT_FOUND,
        "comentario_no_encontrado");
  }

  // ------------------------------------------------------ eliminación con marca (RN-FOR-05)

  @Test
  void eliminarDejaLaMarcaYConservaLasRespuestas() {
    ComentarioResponse principal = comentar("Pregunta", ESTUDIANTE_ID);
    ComentarioResponse respuesta =
        foro.crearComentario(
            hilo.id(), new CrearComentarioRequest("Respuesta", principal.id()), OTRO_ESTUDIANTE_ID);

    ComentarioResponse eliminado = moderacion.eliminar(principal.id(), ESTUDIANTE_ID);

    assertThat(eliminado.eliminado()).isTrue();
    assertThat(eliminado.contenido()).isNull();
    for (RoleName lector : new RoleName[] {null, RoleName.ESTUDIANTE, RoleName.ADMINISTRADOR}) {
      List<ComentarioResponse> lista = comentarios(lector);
      ComentarioResponse marca = buscar(lista, principal.id());
      assertThat(marca.eliminado()).isTrue();
      assertThat(marca.contenido()).isNull();
      ComentarioResponse conservada = buscar(lista, respuesta.id());
      assertThat(conservada.padreId()).isEqualTo(principal.id());
      assertThat(conservada.contenido()).isEqualTo("Respuesta");
    }
    assertThat(
            contar(
                "SELECT count(*) FROM auditoria WHERE entidad = 'foro_comentario'"
                    + " AND entidad_id = ? AND accion = 'MODIFICAR' AND usuario_id = ?"
                    + " AND valores_anteriores->>'contenido' = 'Pregunta'"
                    + " AND (valores_posteriores->>'eliminado')::boolean",
                principal.id(),
                ESTUDIANTE_ID))
        .isEqualTo(1);

    // Eliminarlo otra vez no cambia nada ni deja otro registro; editarlo ya no es posible.
    moderacion.eliminar(principal.id(), ESTUDIANTE_ID);
    assertThat(
            contar(
                "SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = 'MODIFICAR'",
                principal.id()))
        .isEqualTo(1);
    assertProblema(
        () -> moderacion.editar(principal.id(), new EditarComentarioRequest("Otra"), ESTUDIANTE_ID),
        HttpStatus.CONFLICT,
        "comentario_eliminado");
  }

  @Test
  void eliminarNoTienePlazo() {
    ComentarioResponse comentario = comentar("Viejo", ESTUDIANTE_ID);
    reloj.avanzar(Duration.ofDays(30));

    assertThat(moderacion.eliminar(comentario.id(), ESTUDIANTE_ID).eliminado()).isTrue();
  }

  // ------------------------------------------------- ocultar con motivo (RN-FOR-08, RN-USR-09)

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\n\t"})
  void ocultarSinMotivoDa422YNoOcultaNada(String motivo) {
    ComentarioResponse comentario = comentar("Fuera de tono", ESTUDIANTE_ID);

    assertProblema(
        () ->
            moderacion.ocultar(
                comentario.id(), new MotivoModeracionRequest(motivo), CATEDRATICO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "motivo_requerido");
    assertProblema(
        () -> moderacion.ocultar(comentario.id(), null, CATEDRATICO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "motivo_requerido");
    assertThat(
            contar(
                "SELECT count(*) FROM foro_comentario WHERE id = ? AND ocultado_en IS NOT NULL",
                comentario.id()))
        .isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "ADMINISTRADOR"})
  void unModeradorOcultaYSoloLosModeradoresVenElTextoYElMotivo(RoleName rolModerador) {
    UUID moderador = rolModerador == RoleName.CATEDRATICO ? CATEDRATICO_ID : ADMINISTRADOR_ID;
    ComentarioResponse comentario = comentar("Fuera de tono", ESTUDIANTE_ID);

    ComentarioResponse oculto =
        moderacion.ocultar(
            comentario.id(), new MotivoModeracionRequest("  Lenguaje ofensivo "), moderador);

    assertThat(oculto.oculto()).isTrue();
    assertThat(oculto.contenido()).isEqualTo("Fuera de tono");
    assertThat(oculto.motivoOcultamiento()).isEqualTo("Lenguaje ofensivo");

    for (RoleName comun :
        new RoleName[] {null, RoleName.ESTUDIANTE, RoleName.PROFESIONAL_EXTERNO}) {
      ComentarioResponse visto = buscar(comentarios(comun), comentario.id());
      assertThat(visto.oculto()).isTrue();
      assertThat(visto.contenido()).isNull();
      assertThat(visto.motivoOcultamiento()).isNull();
    }
    for (RoleName mod : new RoleName[] {RoleName.CATEDRATICO, RoleName.ADMINISTRADOR}) {
      ComentarioResponse visto = buscar(comentarios(mod), comentario.id());
      assertThat(visto.contenido()).isEqualTo("Fuera de tono");
      assertThat(visto.motivoOcultamiento()).isEqualTo("Lenguaje ofensivo");
    }

    Map<String, Object> registro =
        jdbc.queryForMap(
            "SELECT usuario_id, (valores_anteriores->>'oculto')::boolean AS antes,"
                + " (valores_posteriores->>'oculto')::boolean AS despues,"
                + " valores_posteriores->>'motivo' AS motivo FROM auditoria"
                + " WHERE entidad = 'foro_comentario' AND entidad_id = ? AND accion = 'MODERAR'",
            comentario.id());
    assertThat(registro.get("usuario_id")).isEqualTo(moderador);
    assertThat(registro.get("antes")).isEqualTo(false);
    assertThat(registro.get("despues")).isEqualTo(true);
    assertThat(registro.get("motivo")).isEqualTo("Lenguaje ofensivo");
  }

  @Test
  void unComentarioOcultoOEliminadoNoSeVuelveAModerarNiLoCambiaSuAutor() {
    ComentarioResponse oculto = comentar("Fuera de tono", ESTUDIANTE_ID);
    MotivoModeracionRequest motivo = new MotivoModeracionRequest("Spam");
    moderacion.ocultar(oculto.id(), motivo, CATEDRATICO_ID);

    assertProblema(
        () -> moderacion.ocultar(oculto.id(), motivo, ADMINISTRADOR_ID),
        HttpStatus.CONFLICT,
        "comentario_oculto");
    assertProblema(
        () -> moderacion.editar(oculto.id(), new EditarComentarioRequest("Otro"), ESTUDIANTE_ID),
        HttpStatus.CONFLICT,
        "comentario_oculto");
    assertProblema(
        () -> moderacion.eliminar(oculto.id(), ESTUDIANTE_ID),
        HttpStatus.CONFLICT,
        "comentario_oculto");

    ComentarioResponse eliminado = comentar("Lo borro", ESTUDIANTE_ID);
    moderacion.eliminar(eliminado.id(), ESTUDIANTE_ID);
    assertProblema(
        () -> moderacion.ocultar(eliminado.id(), motivo, CATEDRATICO_ID),
        HttpStatus.CONFLICT,
        "comentario_eliminado");
  }

  // ------------------------------------------------------------- hilo cerrado (RN-FOR-08)

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void cerrarSinMotivoDa422YElHiloSigueAbierto(String motivo) {
    assertProblema(
        () -> moderacion.cerrar(hilo.id(), new MotivoModeracionRequest(motivo), ADMINISTRADOR_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "motivo_requerido");
    assertThat(foro.obtenerHilo(hilo.id()).cerrado()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "ADMINISTRADOR"})
  void unHiloCerradoSigueVisibleYComentarDaConflicto(RoleName rolModerador) {
    UUID moderador = rolModerador == RoleName.CATEDRATICO ? CATEDRATICO_ID : ADMINISTRADOR_ID;
    ComentarioResponse previo = comentar("Antes del cierre", ESTUDIANTE_ID);
    reloj.avanzar(Duration.ofMinutes(1));

    HiloResponse cerrado =
        moderacion.cerrar(hilo.id(), new MotivoModeracionRequest("Tema resuelto"), moderador);

    assertThat(cerrado.cerrado()).isTrue();
    assertThat(cerrado.motivoCierre()).isEqualTo("Tema resuelto");
    // Cerrar no cuenta como actividad del hilo.
    assertThat(cerrado.actualizadoEn().toInstant()).isEqualTo(INICIO);
    assertThat(foro.obtenerHilo(hilo.id()).cerrado()).isTrue();
    assertThat(comentarios(null)).hasSize(2);

    assertProblema(
        () ->
            foro.crearComentario(
                hilo.id(), new CrearComentarioRequest("Después", null), OTRO_ESTUDIANTE_ID),
        HttpStatus.CONFLICT,
        "hilo_cerrado");
    assertProblema(
        () ->
            foro.crearComentario(
                hilo.id(), new CrearComentarioRequest("Respuesta", previo.id()), moderador),
        HttpStatus.CONFLICT,
        "hilo_cerrado");
    assertProblema(
        () -> moderacion.editar(previo.id(), new EditarComentarioRequest("Otro"), ESTUDIANTE_ID),
        HttpStatus.CONFLICT,
        "hilo_cerrado");
    assertProblema(
        () -> moderacion.cerrar(hilo.id(), new MotivoModeracionRequest("Otra vez"), moderador),
        HttpStatus.CONFLICT,
        "hilo_cerrado");
    assertThat(comentarios(null)).hasSize(2);

    // Un comentario de un hilo cerrado todavía se puede ocultar.
    assertThat(
            moderacion
                .ocultar(previo.id(), new MotivoModeracionRequest("Dato personal"), moderador)
                .oculto())
        .isTrue();

    Map<String, Object> registro =
        jdbc.queryForMap(
            "SELECT usuario_id, (valores_anteriores->>'cerrado')::boolean AS antes,"
                + " (valores_posteriores->>'cerrado')::boolean AS despues,"
                + " valores_posteriores->>'motivo' AS motivo FROM auditoria"
                + " WHERE entidad = 'foro_hilo' AND entidad_id = ? AND accion = 'MODERAR'",
            hilo.id());
    assertThat(registro.get("usuario_id")).isEqualTo(moderador);
    assertThat(registro.get("antes")).isEqualTo(false);
    assertThat(registro.get("despues")).isEqualTo(true);
    assertThat(registro.get("motivo")).isEqualTo("Tema resuelto");
  }

  @Test
  void unComentarioLeidoAntesDelCierreNoEntraDespuesDeCerrarse() {
    // El cierre ocurre entre la lectura del hilo y el registro del comentario: la actualización
    // condicionada de la actividad lo detecta.
    jdbc.update(
        "UPDATE foro_hilo SET cerrado_en = now(), cerrado_por_id = ?, motivo_cierre = 'Cerrado'"
            + " WHERE id = ?",
        CATEDRATICO_ID,
        hilo.id());

    assertProblema(() -> comentar("Tarde", ESTUDIANTE_ID), HttpStatus.CONFLICT, "hilo_cerrado");
    assertThat(contar("SELECT count(*) FROM foro_comentario WHERE hilo_id = ?", hilo.id()))
        .isEqualTo(1);
  }

  // -------------------------------------------- moderación posterior (RN-REV-02)

  @Test
  void laListaTraeSoloLasPublicadasSinRevisionDeLaMasRecienteALaMasAntigua() {
    UUID version = insertarVersion();
    UUID antigua =
        insertarInspeccion(version, CATEDRATICO_ID, "CATEDRATICO", INICIO.minusSeconds(7200));
    historial(antigua, "PUBLICAR_SIN_REVISION");
    UUID reciente =
        insertarInspeccion(
            version, PROFESIONAL_ID, "PROFESIONAL_EXTERNO", INICIO.minusSeconds(600));
    historial(reciente, "PUBLICAR_SIN_REVISION");
    UUID hiloReciente = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO foro_hilo (id, puente_id, inspeccion_id, autor_id, categoria, titulo)"
            + " VALUES (?, ?, ?, ?, 'DISCUSION_TECNICA', 'Inspección')",
        hiloReciente,
        puenteId,
        reciente,
        PROFESIONAL_ID);
    jdbc.update(
        "INSERT INTO foro_comentario (id, hilo_id, autor_id, contenido) VALUES (?, ?, ?, 'Ojo')",
        UUID.randomUUID(),
        hiloReciente,
        ESTUDIANTE_ID);

    // Revisada por un Catedrático o de un puente inactivo: no se listan. (Una publicada no puede
    // eliminarse: lo impide la base de datos.)
    UUID revisada =
        insertarInspeccion(version, ESTUDIANTE_ID, "ESTUDIANTE", INICIO.minusSeconds(60));
    historial(revisada, "APROBAR");
    UUID otroPuente = insertarPuente();
    UUID deInactivo =
        insertarInspeccion(
            version, otroPuente, CATEDRATICO_ID, "CATEDRATICO", INICIO.minusSeconds(10));
    historial(deInactivo, "PUBLICAR_SIN_REVISION");
    jdbc.update(
        "UPDATE puente SET activo = false, inactivado_en = now(), inactivado_por_id = ?,"
            + " motivo_inactivacion = 'Demolido' WHERE id = ?",
        ADMINISTRADOR_ID,
        otroPuente);

    List<InspeccionSinRevisionResponse> propias =
        moderacion.listarInspeccionesSinRevision(0, 100).getContent().stream()
            .filter(i -> Set.of(antigua, reciente, revisada, deInactivo).contains(i.inspeccionId()))
            .toList();

    assertThat(propias)
        .extracting(InspeccionSinRevisionResponse::inspeccionId)
        .containsExactly(reciente, antigua);
    InspeccionSinRevisionResponse primera = propias.get(0);
    assertThat(primera.puenteId()).isEqualTo(puenteId);
    assertThat(primera.puenteNombre()).isEqualTo("Puente HU-065");
    assertThat(primera.autorUsuario()).isEqualTo("pro.externo");
    assertThat(primera.autorRol()).isEqualTo("PROFESIONAL_EXTERNO");
    assertThat(primera.publicadoEn().toInstant()).isEqualTo(INICIO.minusSeconds(600));
    assertThat(primera.hiloId()).isEqualTo(hiloReciente);
    assertThat(primera.totalComentarios()).isEqualTo(1);
    assertThat(propias.get(1).hiloId()).isNull();
    assertThat(propias.get(1).totalComentarios()).isZero();
  }

  @Test
  void laListaValidaLaPaginacion() {
    assertProblema(
        () -> moderacion.listarInspeccionesSinRevision(0, 101),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "paginacion_invalida");
  }

  // ----------------------------------------------------------------------------- ayudas

  private ComentarioResponse comentar(String texto, UUID autor) {
    return foro.crearComentario(hilo.id(), new CrearComentarioRequest(texto, null), autor);
  }

  private List<ComentarioResponse> comentarios(RoleName lector) {
    return foro.listarComentariosPara(hilo.id(), 0, 100, FiltroComentarios.NINGUNO, lector)
        .getContent();
  }

  private static ComentarioResponse buscar(List<ComentarioResponse> lista, UUID id) {
    return lista.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
  }

  private String contenidoGuardado(UUID comentario) {
    return jdbc.queryForObject(
        "SELECT contenido FROM foro_comentario WHERE id = ?", String.class, comentario);
  }

  private Timestamp eliminadoEn(UUID comentario) {
    return jdbc.queryForObject(
        "SELECT eliminado_en FROM foro_comentario WHERE id = ?", Timestamp.class, comentario);
  }

  private static void assertProblema(ThrowingCallable accion, HttpStatus estado, String codigo) {
    assertThatThrownBy(accion)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(estado);
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
          m.id, 'Puente HU-065', 'CA-1',
          ST_SetSRID(ST_MakePoint(-90.615, 14.481), 4326)::geography, ?
        FROM municipio m WHERE m.codigo_ine = '0114'
        """,
        id,
        "T-" + id,
        ADMINISTRADOR_ID);
    return id;
  }

  private UUID insertarVersion() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO version_formulario(id, codigo, esquema, creado_por_id)"
            + " VALUES (?, ?, '{}'::jsonb, ?)",
        id,
        "hu065-" + id,
        ADMINISTRADOR_ID);
    return id;
  }

  private UUID insertarInspeccion(UUID version, UUID autor, String rol, Instant publicadoEn) {
    return insertarInspeccion(version, puenteId, autor, rol, publicadoEn);
  }

  private UUID insertarInspeccion(
      UUID version, UUID puente, UUID autor, String rol, Instant publicadoEn) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO inspeccion(id, puente_id, version_formulario_id, autor_id,"
            + " rol_autor_creacion, nombre_autor_capturado, fecha_inspeccion, dispositivo_id,"
            + " estado, publicado_en) VALUES (?, ?, ?, ?, ?, 'Autor', DATE '2026-10-15',"
            + " 'test', 'PUBLICADA', ?)",
        id,
        puente,
        version,
        autor,
        rol,
        Timestamp.from(publicadoEn));
    return id;
  }

  private void historial(UUID inspeccion, String evento) {
    jdbc.update(
        "INSERT INTO historial_estado_inspeccion (id, inspeccion_id, secuencia, estado_anterior,"
            + " estado_nuevo, evento) VALUES (?, ?, 1, 'ENVIADA', 'PUBLICADA', ?)",
        UUID.randomUUID(),
        inspeccion,
        evento);
  }

  /** Reloj del servidor que se mueve a voluntad para probar el plazo de edición. */
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
