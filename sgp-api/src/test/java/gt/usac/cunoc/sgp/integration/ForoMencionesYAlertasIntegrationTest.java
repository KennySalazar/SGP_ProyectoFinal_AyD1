package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.DemasiadasSolicitudesException;
import gt.usac.cunoc.sgp.formulario.validation.ValidadorAnclajeFormulario;
import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.CrearComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.CrearHiloRequest;
import gt.usac.cunoc.sgp.foro.dto.EditarComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.mapper.ForoMapperImpl;
import gt.usac.cunoc.sgp.foro.service.AnclajeComentarioForo;
import gt.usac.cunoc.sgp.foro.service.AvisosForo;
import gt.usac.cunoc.sgp.foro.service.ForoModeracionService;
import gt.usac.cunoc.sgp.foro.service.ForoService;
import gt.usac.cunoc.sgp.foro.service.LimiteTasaForo;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Menciones, alerta sobre un puente en estado Malo y límite de tasa del foro contra
 * PostgreSQL/PostGIS real (HU-066; RN-FOR-07, RN-FOR-10, DT-SEC-07, DT-SEC-12).
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
  ValidadorAnclajeFormulario.class,
  LimiteTasaForo.class,
  AvisosForo.class,
  NotificacionService.class,
  AuditoriaWriter.class,
  ForoMapperImpl.class,
  ForoMencionesYAlertasIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ForoMencionesYAlertasIntegrationTest {

  private static final Instant INICIO = Instant.parse("2026-10-20T15:00:00Z");
  private static final UUID ANA_ID = UUID.fromString("019a0000-0000-7000-8000-000000006601");
  private static final UUID BETO_ID = UUID.fromString("019a0000-0000-7000-8000-000000006602");
  private static final UUID CARLOS_ID = UUID.fromString("019a0000-0000-7000-8000-000000006603");
  private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000006604");
  private static final UUID DORA_ID = UUID.fromString("019a0000-0000-7000-8000-000000006605");
  private static final UUID INACTIVO_ID = UUID.fromString("019a0000-0000-7000-8000-000000006606");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu066_test")
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
  @Autowired private ForoModeracionService moderacion;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RelojMutable reloj;

  private UUID puenteId;
  private HiloResponse hilo;

  /**
   * Cada prueba empieza en su propia hora, separada de las demás por más de un minuto: lo publicado
   * en otra prueba nunca cuenta para el límite de tasa de esta.
   */
  private static Instant siguienteInicio = INICIO;

  @BeforeEach
  void preparar() {
    siguienteInicio = siguienteInicio.plus(Duration.ofHours(1));
    reloj.fijar(siguienteInicio);
    insertarUsuario(ANA_ID, "ana.hu066@ejemplo.com", "ESTUDIANTE", "ana.hu066", true);
    insertarUsuario(BETO_ID, "beto.hu066@ejemplo.com", "ESTUDIANTE", "beto.hu066", true);
    insertarUsuario(CARLOS_ID, "carlos.hu066@ejemplo.com", "CATEDRATICO", "carlos.hu066", true);
    insertarUsuario(ADMIN_ID, "admin.hu066@ejemplo.com", "ADMINISTRADOR", "admin.hu066", true);
    insertarUsuario(DORA_ID, "dora.hu066@ejemplo.com", "PROFESIONAL_EXTERNO", "dora.hu066", true);
    insertarUsuario(
        INACTIVO_ID, "inactivo.hu066@ejemplo.com", "ESTUDIANTE", "inactivo.hu066", false);
    puenteId = insertarPuente();
    hilo =
        foro.crearHilo(
            puenteId, new CrearHiloRequest("Juntas", "DISCUSION_TECNICA", "Inicio"), ANA_ID);
    reloj.avanzar(Duration.ofMinutes(2));
  }

  // ---------------------------------------------------------------- menciones (RN-FOR-10)

  @Test
  void elMencionadoRecibeUnSoloAvisoPorComentarioYLaMencionQuedaRegistrada() {
    ComentarioResponse comentario =
        comentar("@beto.hu066 revisa la junta. De nuevo, @BETO.HU066: es urgente.", ANA_ID);

    List<Map<String, Object>> avisos = avisosDe(BETO_ID, "FORO_MENCION");
    assertThat(avisos).hasSize(1);
    Map<String, Object> aviso = avisos.get(0);
    assertThat(aviso.get("entidad")).isEqualTo("foro_comentario");
    assertThat(aviso.get("entidad_id")).isEqualTo(comentario.id());
    assertThat(
            contar(
                "SELECT count(*) FROM comentario_mencion WHERE comentario_id = ?"
                    + " AND usuario_mencionado_id = ?",
                comentario.id(),
                BETO_ID))
        .isEqualTo(1);
  }

  @Test
  void elAvisoNoIncluyeElTextoDelComentarioNiDatosDeQuienEscribe() {
    comentar("@beto.hu066 la grieta mide 3 mm según mi nota personal", ANA_ID);

    Map<String, Object> aviso = avisosDe(BETO_ID, "FORO_MENCION").get(0);
    String texto = aviso.get("titulo") + " " + aviso.get("mensaje");
    assertThat(texto).doesNotContain("grieta").doesNotContain("3 mm").doesNotContain("ana");
    assertThat(texto).doesNotContain("@");
    assertThat(aviso.get("mensaje").toString()).contains("T-" + puenteId);
  }

  @Test
  void mencionarAUnUsuarioInexistenteODesactivadoPublicaSinAviso() {
    ComentarioResponse comentario =
        comentar("Hola @nadie.hu066 y @inactivo.hu066, ¿lo vieron?", ANA_ID);

    assertThat(comentario.contenido()).contains("@nadie.hu066");
    assertThat(avisosDe(INACTIVO_ID, "FORO_MENCION")).isEmpty();
    assertThat(
            contar(
                "SELECT count(*) FROM comentario_mencion WHERE comentario_id = ?", comentario.id()))
        .isZero();
  }

  @Test
  void mencionarseAUnoMismoNoAvisa() {
    ComentarioResponse comentario = comentar("Nota para mí, @ana.hu066", ANA_ID);

    assertThat(avisosDe(ANA_ID, "FORO_MENCION")).isEmpty();
    assertThat(
            contar(
                "SELECT count(*) FROM comentario_mencion WHERE comentario_id = ?", comentario.id()))
        .isZero();
  }

  @Test
  void unCorreoNoEsUnaMencion() {
    comentar("Escríbele a correo@beto.hu066 o a beto.hu066@ejemplo.com", ANA_ID);

    assertThat(avisosDe(BETO_ID, "FORO_MENCION")).isEmpty();
  }

  @Test
  void laMencionEnElPrimerComentarioDeUnHiloTambienAvisa() {
    HiloResponse nuevo =
        foro.crearHilo(
            puenteId,
            new CrearHiloRequest("Apoyos", "CONSULTA", "¿Qué opinas, @dora.hu066?"),
            ANA_ID);

    assertThat(avisosDe(DORA_ID, "FORO_MENCION")).hasSize(1);
    assertThat(
            contar(
                "SELECT count(*) FROM comentario_mencion m JOIN foro_comentario c"
                    + " ON c.id = m.comentario_id WHERE c.hilo_id = ?",
                nuevo.id()))
        .isEqualTo(1);
  }

  @Test
  void alEditarSoloSeAvisaALosNuevosMencionados() {
    ComentarioResponse comentario = comentar("@beto.hu066 mira esto", ANA_ID);
    reloj.avanzar(Duration.ofMinutes(5));

    moderacion.editar(
        comentario.id(),
        new EditarComentarioRequest("@beto.hu066 mira esto, y tú también @dora.hu066"),
        ANA_ID);
    moderacion.editar(
        comentario.id(),
        new EditarComentarioRequest("@beto.hu066 y @dora.hu066, ya lo corregí"),
        ANA_ID);

    assertThat(avisosDe(BETO_ID, "FORO_MENCION")).hasSize(1);
    assertThat(avisosDe(DORA_ID, "FORO_MENCION")).hasSize(1);
    assertThat(
            contar(
                "SELECT count(*) FROM comentario_mencion WHERE comentario_id = ?", comentario.id()))
        .isEqualTo(2);
  }

  // ------------------------------------------------- alerta en un puente Malo (RN-FOR-07)

  @Test
  void unaAlertaSobreUnPuenteMaloAvisaAlCatedraticoYAlAdministrador() {
    marcarMalo(puenteId);

    HiloResponse alerta =
        foro.crearHilo(
            puenteId, new CrearHiloRequest("Socavación", "ALERTA", "Se ve el acero"), BETO_ID);

    for (UUID destinatario : List.of(CARLOS_ID, ADMIN_ID)) {
      List<Map<String, Object>> avisos = avisosDe(destinatario, "FORO_ALERTA_PUENTE_MALO");
      assertThat(avisos).hasSize(1);
      assertThat(avisos.get(0).get("entidad")).isEqualTo("foro_hilo");
      assertThat(avisos.get(0).get("entidad_id")).isEqualTo(alerta.id());
      assertThat(avisos.get(0).get("mensaje").toString())
          .doesNotContain("Socavación")
          .doesNotContain("acero");
    }
    for (UUID otro : List.of(ANA_ID, BETO_ID, DORA_ID)) {
      assertThat(avisosDe(otro, "FORO_ALERTA_PUENTE_MALO")).isEmpty();
    }
  }

  @Test
  void quienAbreLaAlertaNoSeAvisaASiMismo() {
    marcarMalo(puenteId);

    foro.crearHilo(puenteId, new CrearHiloRequest("Socavación", "ALERTA", "Urgente"), CARLOS_ID);

    assertThat(avisosDe(CARLOS_ID, "FORO_ALERTA_PUENTE_MALO")).isEmpty();
    assertThat(avisosDe(ADMIN_ID, "FORO_ALERTA_PUENTE_MALO")).hasSize(1);
  }

  @Test
  void unaAlertaSobreUnPuenteQueNoEstaMaloSeCreaSinAviso() {
    HiloResponse alerta =
        foro.crearHilo(
            puenteId, new CrearHiloRequest("Basura", "ALERTA", "Hay basura en el cauce"), BETO_ID);

    assertThat(alerta.id()).isNotNull();
    assertThat(avisosDe(CARLOS_ID, "FORO_ALERTA_PUENTE_MALO")).isEmpty();
    assertThat(avisosDe(ADMIN_ID, "FORO_ALERTA_PUENTE_MALO")).isEmpty();
  }

  @Test
  void otraCategoriaSobreUnPuenteMaloNoEsUnaAlerta() {
    marcarMalo(puenteId);

    foro.crearHilo(
        puenteId, new CrearHiloRequest("Duda", "CONSULTA", "¿Quién lo revisa?"), BETO_ID);

    assertThat(avisosDe(CARLOS_ID, "FORO_ALERTA_PUENTE_MALO")).isEmpty();
  }

  // ------------------------------------------------------- límite de tasa (DT-SEC-07)

  @Test
  void superarElLimiteDeComentariosDa429ConElTiempoDeEspera() {
    for (int i = 0; i < 10; i++) {
      comentar("Comentario " + i, BETO_ID);
      reloj.avanzar(Duration.ofSeconds(2));
    }
    // El primero de la ventana se publicó hace 20 s: sale de ella en 40 s.
    assertThatThrownBy(() -> comentar("Uno más", BETO_ID))
        .isInstanceOfSatisfying(
            DemasiadasSolicitudesException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(e.getCode()).isEqualTo("limite_foro_excedido");
              assertThat(e.getReintentarEnSegundos()).isEqualTo(40);
              assertThat(e.getMessage()).contains("Espera un momento");
            });
    assertThat(
            contar(
                "SELECT count(*) FROM foro_comentario WHERE autor_id = ? AND contenido = 'Uno más'",
                BETO_ID))
        .isZero();

    reloj.avanzar(Duration.ofSeconds(40));
    assertThat(comentar("Ya puedo", BETO_ID).contenido()).isEqualTo("Ya puedo");
  }

  @Test
  void elLimiteEsPorUsuarioYNoAfectaALosDemas() {
    for (int i = 0; i < 10; i++) comentar("Ráfaga " + i, BETO_ID);
    assertThatThrownBy(() -> comentar("Otro", BETO_ID))
        .isInstanceOf(DemasiadasSolicitudesException.class);

    assertThat(comentar("Yo sí puedo", DORA_ID).contenido()).isEqualTo("Yo sí puedo");
    assertThat(
            foro.crearHilo(puenteId, new CrearHiloRequest("Tema", "CONSULTA", "Texto"), DORA_ID)
                .id())
        .isNotNull();
  }

  @Test
  void superarElLimiteDeHilosDa429() {
    for (int i = 0; i < 3; i++) {
      foro.crearHilo(puenteId, new CrearHiloRequest("Tema " + i, "CONSULTA", "Texto"), DORA_ID);
    }

    assertThatThrownBy(
            () ->
                foro.crearHilo(
                    puenteId, new CrearHiloRequest("Cuarto", "CONSULTA", "Texto"), DORA_ID))
        .isInstanceOfSatisfying(
            DemasiadasSolicitudesException.class,
            e -> assertThat(e.getReintentarEnSegundos()).isEqualTo(60));
    assertThat(
            contar(
                "SELECT count(*) FROM foro_hilo WHERE titulo = 'Cuarto' AND puente_id = ?",
                puenteId))
        .isZero();
  }

  @Test
  void abrirUnHiloTambienCuentaComoComentario() {
    for (int i = 0; i < 8; i++) comentar("Antes " + i, DORA_ID);
    foro.crearHilo(puenteId, new CrearHiloRequest("Noveno", "CONSULTA", "Texto"), DORA_ID);
    comentar("Décimo", DORA_ID);

    assertThatThrownBy(
            () ->
                foro.crearHilo(
                    puenteId, new CrearHiloRequest("Once", "CONSULTA", "Texto"), DORA_ID))
        .isInstanceOf(DemasiadasSolicitudesException.class);
  }

  @Test
  void editarNoCuentaParaElLimite() {
    ComentarioResponse comentario = comentar("Primero", BETO_ID);
    for (int i = 0; i < 9; i++) comentar("Relleno " + i, BETO_ID);

    assertThat(
            moderacion
                .editar(comentario.id(), new EditarComentarioRequest("Corregido"), BETO_ID)
                .contenido())
        .isEqualTo("Corregido");
  }

  // ----------------------------------------------------------------------------- ayudas

  private ComentarioResponse comentar(String texto, UUID autor) {
    return foro.crearComentario(hilo.id(), new CrearComentarioRequest(texto, null), autor);
  }

  private List<Map<String, Object>> avisosDe(UUID usuario, String tipo) {
    return jdbc.queryForList(
        "SELECT titulo, mensaje, entidad, entidad_id, clave_evento FROM notificacion"
            + " WHERE usuario_id = ? AND tipo = ? AND creado_en >= ?",
        usuario,
        tipo,
        Timestamp.from(siguienteInicio));
  }

  private int contar(String sql, Object... argumentos) {
    Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
    return total == null ? 0 : total;
  }

  private void insertarUsuario(UUID id, String email, String rol, String usuario, boolean activo) {
    jdbc.update(
        """
        INSERT INTO usuario (
          id, email, password_hash, rol_id, verificado, activado, activo, nombre_usuario,
          nombre_completo
        )
        SELECT ?, ?, 'hash-de-prueba', id, true, true, ?, ?, 'Nombre Completo Privado'
        FROM rol
        WHERE nombre = ?
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        email,
        activo,
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
          m.id, 'Puente HU-066', 'CA-1',
          ST_SetSRID(ST_MakePoint(-90.615, 14.481), 4326)::geography, ?
        FROM municipio m WHERE m.codigo_ine = '0114'
        """,
        id,
        "T-" + id,
        ADMIN_ID);
    return id;
  }

  /** Deja el puente en estado Malo con una inspección publicada y su IC, como lo exige la BD. */
  private void marcarMalo(UUID puente) {
    UUID version = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO version_formulario(id, codigo, esquema, creado_por_id)"
            + " VALUES (?, ?, '{}'::jsonb, ?)",
        version,
        "hu066-" + version,
        ADMIN_ID);
    UUID inspeccion = UUID.randomUUID();
    Timestamp publicada = Timestamp.from(reloj.instant());
    jdbc.update(
        "INSERT INTO inspeccion(id, puente_id, version_formulario_id, autor_id,"
            + " rol_autor_creacion, nombre_autor_capturado, fecha_inspeccion, dispositivo_id,"
            + " estado, publicado_en) VALUES (?, ?, ?, ?, 'CATEDRATICO', 'Carlos',"
            + " DATE '2026-10-15', 'test', 'PUBLICADA', ?)",
        inspeccion,
        puente,
        version,
        CARLOS_ID,
        publicada);
    UUID resultado = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO resultado_ic (id, inspeccion_id, version_configuracion_id, tipo,"
            + " indice_calculado, estado_por_rango, estado_calculado, estado_confirmado,"
            + " desglose, hash_entrada, version_algoritmo)"
            + " VALUES (?, ?, ?, 'PUBLICACION', 25, 'MALO', 'MALO', 'MALO', '{}'::jsonb, ?, 'v1')",
        resultado,
        inspeccion,
        version,
        "a".repeat(64));
    jdbc.update(
        "UPDATE puente SET estado_actual = 'Malo', indice_condicion_actual = 25,"
            + " fecha_ultima_inspeccion = DATE '2026-10-15', inspeccion_vigente_id = ?,"
            + " resultado_ic_vigente_id = ?, inspeccion_vigente_publicada_en = ? WHERE id = ?",
        inspeccion,
        resultado,
        publicada,
        puente);
  }

  /** Reloj del servidor que se mueve a voluntad para probar el límite de tasa. */
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
