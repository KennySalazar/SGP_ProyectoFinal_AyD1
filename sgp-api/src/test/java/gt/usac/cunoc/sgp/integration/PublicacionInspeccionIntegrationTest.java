package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarBorradorRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarRespuestasRequest;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapperImpl;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.service.AgregadorCalificacionesIc;
import gt.usac.cunoc.sgp.inspeccion.service.CalculadorProgresoBorrador;
import gt.usac.cunoc.sgp.inspeccion.service.CalculoIndiceCondicionService;
import gt.usac.cunoc.sgp.inspeccion.service.ConfirmacionEstadoIcService;
import gt.usac.cunoc.sgp.inspeccion.service.ConversorIndicadoresIc;
import gt.usac.cunoc.sgp.inspeccion.service.ConversorMedicionesIc;
import gt.usac.cunoc.sgp.inspeccion.service.CorreccionInspeccionService;
import gt.usac.cunoc.sgp.inspeccion.service.EvaluadorCondicionesCriticasIc;
import gt.usac.cunoc.sgp.inspeccion.service.HuellaEntradaIc;
import gt.usac.cunoc.sgp.inspeccion.service.InspeccionService;
import gt.usac.cunoc.sgp.inspeccion.service.MotorPonderacionIc;
import gt.usac.cunoc.sgp.inspeccion.service.PreparadorEntradaIcService;
import gt.usac.cunoc.sgp.inspeccion.service.PublicacionIndiceCondicionService;
import gt.usac.cunoc.sgp.inspeccion.service.ResolverElementosIc;
import gt.usac.cunoc.sgp.inspeccion.service.TransicionInspeccionService;
import gt.usac.cunoc.sgp.integration.InvitacionCatedraticoIntegrationTest.RelojAjustable;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Flujo real de HU-026 con el calculador real del IC: verifica la integración pendiente de HU-020
 * (publicación atómica, orden de bloqueos y concurrencia) y de HU-032 (confirmación del estado).
 */
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  TransicionInspeccionService.class,
  CorreccionInspeccionService.class,
  InspeccionService.class,
  gt.usac.cunoc.sgp.sync.service.OperacionIdempotenteService.class,
  PublicacionIndiceCondicionService.class,
  ConfirmacionEstadoIcService.class,
  PreparadorEntradaIcService.class,
  CalculoIndiceCondicionService.class,
  ResolverElementosIc.class,
  ConversorIndicadoresIc.class,
  ConversorMedicionesIc.class,
  AgregadorCalificacionesIc.class,
  EvaluadorCondicionesCriticasIc.class,
  HuellaEntradaIc.class,
  MotorPonderacionIc.class,
  CalculadorProgresoBorrador.class,
  JsonSchemaValidatorService.class,
  InspeccionMapperImpl.class,
  AuditoriaWriter.class,
  PublicacionInspeccionIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PublicacionInspeccionIntegrationTest {

  private static final Instant AHORA = Instant.parse("2026-10-16T18:00:00Z");
  private static final String ALGORITMO = "ic-sieca-prueba-v1";

  private static final UUID CATEDRATICO_ID = uuid("b1");
  private static final UUID ESTUDIANTE_ID = uuid("b2");
  private static final UUID PROFESIONAL_VERIFICADO_ID = uuid("b3");
  private static final UUID PROFESIONAL_SIN_VERIFICAR_ID = uuid("b4");
  private static final UUID REVISOR_ID = uuid("b5");
  private static final UUID OTRO_CATEDRATICO_ID = uuid("b6");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu026_test")
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

  @Autowired private TransicionInspeccionService transiciones;
  @Autowired private InspeccionService inspeccionService;
  @Autowired private ConfirmacionEstadoIcService confirmaciones;
  @Autowired private CorreccionInspeccionService correcciones;
  @Autowired private RelojAjustable reloj;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private UUID versionId;
  private UUID puenteId;
  private JsonNode datosCompletos;

  @BeforeEach
  void preparar() throws IOException {
    reloj.ajustar(AHORA);
    insertarUsuario(CATEDRATICO_ID, "CATEDRATICO");
    insertarUsuario(ESTUDIANTE_ID, "ESTUDIANTE");
    insertarUsuario(PROFESIONAL_VERIFICADO_ID, "PROFESIONAL_EXTERNO");
    insertarUsuario(PROFESIONAL_SIN_VERIFICAR_ID, "PROFESIONAL_EXTERNO");
    insertarUsuario(REVISOR_ID, "CATEDRATICO");
    insertarUsuario(OTRO_CATEDRATICO_ID, "CATEDRATICO");
    jdbc.update(
        """
        INSERT INTO usuario_profesional (usuario_id, numero_colegiado,
          colegiado_verificado_en, colegiado_verificado_por_id)
        VALUES (?, 'HU026-V', now(), ?), (?, 'HU026-N', NULL, NULL)
        ON CONFLICT (usuario_id) DO NOTHING
        """,
        PROFESIONAL_VERIFICADO_ID,
        CATEDRATICO_ID,
        PROFESIONAL_SIN_VERIFICAR_ID);

    datosCompletos = leer("/formulario/sieca-v1/ejemplo-inspeccion.json");
    versionId = crearVersionSieca();
    puenteId = crearPuente();
  }

  @AfterEach
  void limpiarSesion() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Escenario: publicación de Catedrático, atómica y con IC real")
  void catedraticoPublicaPasandoPorEnviadaConIcYEstadoDelPuente() {
    UUID inspeccion = crearInspeccionCompleta(CATEDRATICO_ID, "CATEDRATICO");

    var response = transiciones.publicar(inspeccion, CATEDRATICO_ID);

    assertThat(response.estado()).isEqualTo("PUBLICADA");
    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT estado, enviado_dispositivo_en, publicado_en FROM inspeccion WHERE id = ?",
            inspeccion);
    assertThat(fila.get("estado")).isEqualTo("PUBLICADA");
    assertThat(fila.get("enviado_dispositivo_en")).isNotNull();
    assertThat(fila.get("publicado_en")).isNotNull();

    // Pasó por ENVIADA y terminó PUBLICADA en una sola operación, con usuario, fecha y estados.
    assertThat(historial(inspeccion))
        .containsExactly(
            "1:BORRADOR>ENVIADA:ENVIAR:" + CATEDRATICO_ID,
            "2:ENVIADA>PUBLICADA:PUBLICAR_SIN_REVISION:" + CATEDRATICO_ID);
    assertThat(
            numero(
                "SELECT count(*) FROM historial_estado_inspeccion"
                    + " WHERE inspeccion_id = ? AND creado_en IS NOT NULL",
                inspeccion))
        .isEqualTo(2);

    // IC definitivo calculado con el motor real: la socavación con exposición fuerza MALO.
    Map<String, Object> resultado =
        jdbc.queryForMap(
            """
            SELECT indice_calculado, estado_calculado, estado_confirmado
            FROM resultado_ic WHERE inspeccion_id = ? AND tipo = 'PUBLICACION'
            """,
            inspeccion);
    assertThat((BigDecimal) resultado.get("indice_calculado")).isEqualByComparingTo("63.15789");
    assertThat(resultado.get("estado_calculado")).isEqualTo("MALO");
    assertThat(resultado.get("estado_confirmado")).isEqualTo("MALO");

    Map<String, Object> puente =
        jdbc.queryForMap(
            "SELECT estado_actual, indice_condicion_actual, inspeccion_vigente_id"
                + " FROM puente WHERE id = ?",
            puenteId);
    assertThat(puente.get("estado_actual")).isEqualTo("Malo");
    assertThat((BigDecimal) puente.get("indice_condicion_actual")).isEqualByComparingTo("63.15789");
    assertThat(puente.get("inspeccion_vigente_id")).isEqualTo(inspeccion);

    assertThat(
            numero(
                "SELECT count(*) FROM auditoria WHERE entidad = 'inspeccion' AND entidad_id = ?",
                inspeccion))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("Escenario: Profesional externo verificado publica; sin verificar recibe 403")
  void profesionalExternoSoloPublicaConColegiadoVerificado() {
    UUID sinVerificar =
        crearInspeccionCompleta(PROFESIONAL_SIN_VERIFICAR_ID, "PROFESIONAL_EXTERNO");
    assertThatThrownBy(() -> transiciones.publicar(sinVerificar, PROFESIONAL_SIN_VERIFICAR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(e.getCode()).isEqualTo("colegiado_no_verificado");
            });
    assertThat(estado(sinVerificar)).isEqualTo("BORRADOR");

    UUID verificado = crearInspeccionCompleta(PROFESIONAL_VERIFICADO_ID, "PROFESIONAL_EXTERNO");
    assertThat(transiciones.publicar(verificado, PROFESIONAL_VERIFICADO_ID).estado())
        .isEqualTo("PUBLICADA");
  }

  @Test
  @DisplayName("Escenario: campos requeridos para publicar")
  void camposObligatoriosFaltantesResponden422YLaInspeccionSigueEnBorrador() {
    UUID inspeccion = crearInspeccion(CATEDRATICO_ID, "CATEDRATICO", json.createObjectNode());
    agregarFotosObligatorias(inspeccion);

    assertThatThrownBy(() -> transiciones.publicar(inspeccion, CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("inspeccion_incompleta");
              assertThat(e.getErrores()).isNotEmpty();
              assertThat(e.getErrores())
                  .noneMatch(error -> error.get("campo").startsWith("fotografias."));
            });

    assertThat(estado(inspeccion)).isEqualTo("BORRADOR");
    assertThat(historial(inspeccion)).isEmpty();
  }

  @Test
  @DisplayName("Escenario: fotografías requeridas para publicar")
  void sinEvidenciaFotograficaDetallaLasCategoriasFaltantes() {
    UUID inspeccion = crearInspeccion(CATEDRATICO_ID, "CATEDRATICO", datosCompletos);
    agregarFoto(inspeccion, "ACCESO");
    agregarFoto(inspeccion, "CAUCE");
    agregarFoto(inspeccion, "OTRA");

    assertThatThrownBy(() -> transiciones.publicar(inspeccion, CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getErrores())
                  .extracting(error -> error.get("campo"))
                  .containsExactly("fotografias.SUPERESTRUCTURA", "fotografias.SUBESTRUCTURA");
            });
    assertThat(estado(inspeccion)).isEqualTo("BORRADOR");
  }

  @Test
  @DisplayName("Escenario: solo el autor puede enviar o publicar")
  void unUsuarioDistintoDelAutorRecibe403() {
    UUID deCatedratico = crearInspeccionCompleta(CATEDRATICO_ID, "CATEDRATICO");
    UUID deEstudiante = crearInspeccionCompleta(ESTUDIANTE_ID, "ESTUDIANTE");

    assertThatThrownBy(() -> transiciones.publicar(deCatedratico, OTRO_CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> transiciones.enviar(deEstudiante, OTRO_CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("solo_autor"));

    assertThat(estado(deCatedratico)).isEqualTo("BORRADOR");
    assertThat(estado(deEstudiante)).isEqualTo("BORRADOR");
  }

  @Test
  @DisplayName("Escenarios: enviar, revisar y aprobar con IC (RN-REV-01)")
  void estudianteEnviaYElRevisorApruebaPublicandoConIc() {
    UUID inspeccion = crearInspeccionCompleta(ESTUDIANTE_ID, "ESTUDIANTE");

    // El Estudiante no publica directamente: debe pasar por revisión.
    assertThatThrownBy(() -> transiciones.publicar(inspeccion, ESTUDIANTE_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo("publicacion_requiere_revision"));

    assertThat(transiciones.enviar(inspeccion, ESTUDIANTE_ID).estado()).isEqualTo("ENVIADA");
    autenticar(REVISOR_ID, RoleName.CATEDRATICO);
    assertThat(transiciones.iniciarRevision(inspeccion, REVISOR_ID).estado())
        .isEqualTo("EN_REVISION");
    assertThat(transiciones.aprobar(inspeccion, REVISOR_ID).estado()).isEqualTo("PUBLICADA");

    assertThat(historial(inspeccion))
        .containsExactly(
            "1:BORRADOR>ENVIADA:ENVIAR:" + ESTUDIANTE_ID,
            "2:ENVIADA>EN_REVISION:INICIAR_REVISION:" + REVISOR_ID,
            "3:EN_REVISION>PUBLICADA:APROBAR:" + REVISOR_ID);
    assertThat(
            numero(
                "SELECT count(*) FROM resultado_ic WHERE inspeccion_id = ? AND tipo = 'PUBLICACION'",
                inspeccion))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT estado_actual FROM puente WHERE id = ?", String.class, puenteId))
        .isEqualTo("Malo");
  }

  @Test
  @DisplayName("Escenarios: solicitar cambios, devolver para corrección y reenviar")
  void cambiosSolicitadosVuelvenABorradorYSePuedeReenviar() {
    UUID inspeccion = enRevision();

    assertThatThrownBy(() -> transiciones.solicitarCambios(inspeccion, REVISOR_ID, "  "))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("motivo_requerido"));

    assertThat(
            transiciones
                .solicitarCambios(inspeccion, REVISOR_ID, " Falta foto del estribo ")
                .estado())
        .isEqualTo("CAMBIOS_SOLICITADOS");
    assertThat(
            jdbc.queryForObject(
                "SELECT motivo FROM historial_estado_inspeccion"
                    + " WHERE inspeccion_id = ? AND evento = 'SOLICITAR_CAMBIOS'",
                String.class,
                inspeccion))
        .isEqualTo("Falta foto del estribo");

    assertThat(transiciones.devolverABorrador(inspeccion, ESTUDIANTE_ID).estado())
        .isEqualTo("BORRADOR");
    assertThat(transiciones.enviar(inspeccion, ESTUDIANTE_ID).estado()).isEqualTo("ENVIADA");
    assertThat(historial(inspeccion)).hasSize(5);
  }

  @Test
  @DisplayName("Escenarios: rechazar y estados terminales protegidos")
  void rechazadaYPublicadaNoAdmitenMasTransiciones() {
    UUID rechazada = enRevision();
    assertThat(transiciones.rechazar(rechazada, REVISOR_ID, "Puente equivocado").estado())
        .isEqualTo("RECHAZADA");
    assertThatThrownBy(() -> transiciones.iniciarRevision(rechazada, REVISOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("estado_terminal");
            });
    assertThat(estado(rechazada)).isEqualTo("RECHAZADA");

    UUID publicada = crearInspeccionCompleta(CATEDRATICO_ID, "CATEDRATICO");
    transiciones.publicar(publicada, CATEDRATICO_ID);
    assertThatThrownBy(() -> transiciones.publicar(publicada, CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    assertThat(historial(publicada)).hasSize(2);
  }

  @Test
  @DisplayName("Escenario: transición inválida responde 409 y conserva el estado")
  void transicionInvalidaConservaElEstado() {
    UUID inspeccion = crearInspeccionCompleta(ESTUDIANTE_ID, "ESTUDIANTE");
    transiciones.enviar(inspeccion, ESTUDIANTE_ID);

    assertThatThrownBy(() -> transiciones.enviar(inspeccion, ESTUDIANTE_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("transicion_invalida");
            });
    autenticar(REVISOR_ID, RoleName.CATEDRATICO);
    assertThatThrownBy(() -> transiciones.aprobar(inspeccion, REVISOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("transicion_invalida"));

    assertThat(estado(inspeccion)).isEqualTo("ENVIADA");
    assertThat(historial(inspeccion)).hasSize(1);
  }

  @Test
  void elRevisorNoPuedeSerElAutorYDebeSerCatedratico() {
    UUID inspeccion = crearInspeccionCompleta(ESTUDIANTE_ID, "ESTUDIANTE");
    transiciones.enviar(inspeccion, ESTUDIANTE_ID);

    autenticar(ESTUDIANTE_ID, RoleName.ESTUDIANTE);
    assertThatThrownBy(() -> transiciones.iniciarRevision(inspeccion, ESTUDIANTE_ID))
        .isInstanceOf(AccessDeniedException.class);

    UUID propia = crearInspeccionCompleta(REVISOR_ID, "ESTUDIANTE");
    transiciones.enviar(propia, REVISOR_ID);
    autenticar(REVISOR_ID, RoleName.CATEDRATICO);
    assertThatThrownBy(() -> transiciones.iniciarRevision(propia, REVISOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("revisor_es_autor"));
  }

  @Test
  @DisplayName("Escenario: publicación atómica ante un fallo del cálculo")
  void unFalloAlCalcularElIcRevierteEstadoHistorialAuditoriaYPuente() {
    UUID inspeccion = crearInspeccionCompleta(CATEDRATICO_ID, "CATEDRATICO");
    jdbc.update("DELETE FROM configuracion_ic_elemento WHERE version_formulario_id = ?", versionId);

    assertThatThrownBy(() -> transiciones.publicar(inspeccion, CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo("configuracion_ic_incompleta"));

    assertThat(estado(inspeccion)).isEqualTo("BORRADOR");
    assertThat(
            jdbc.queryForObject(
                "SELECT publicado_en IS NULL FROM inspeccion WHERE id = ?",
                Boolean.class,
                inspeccion))
        .isTrue();
    assertThat(historial(inspeccion)).isEmpty();
    assertThat(numero("SELECT count(*) FROM resultado_ic WHERE inspeccion_id = ?", inspeccion))
        .isZero();
    assertThat(
            numero(
                "SELECT count(*) FROM auditoria WHERE entidad = 'inspeccion' AND entidad_id = ?",
                inspeccion))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT estado_actual FROM puente WHERE id = ?", String.class, puenteId))
        .isEqualTo("Sin evaluar");
  }

  @Test
  @DisplayName(
      "HU-032: el ajuste justificado se incorpora y una confirmación desactualizada revierte")
  void confirmacionDelEvaluadorSeRespetaYSiQuedoDesactualizadaNoSePublica() {
    UUID conAjuste = crearInspeccionCompleta(CATEDRATICO_ID, "CATEDRATICO");
    autenticar(CATEDRATICO_ID, RoleName.CATEDRATICO);
    String hash = confirmaciones.consultar(conAjuste).calculo().hashEntrada();
    confirmaciones.confirmar(
        conAjuste,
        new ConfirmarEstadoIcRequest(
            EstadoCondicion.REGULAR, hash, "La socavación fue reparada después de la visita."));

    transiciones.publicar(conAjuste, CATEDRATICO_ID);

    assertThat(
            jdbc.queryForObject(
                "SELECT estado_confirmado FROM resultado_ic WHERE inspeccion_id = ?",
                String.class,
                conAjuste))
        .isEqualTo("REGULAR");
    assertThat(
            jdbc.queryForObject(
                "SELECT estado_actual FROM puente WHERE id = ?", String.class, puenteId))
        .isEqualTo("Regular");

    UUID desactualizada = crearInspeccionCompleta(CATEDRATICO_ID, "CATEDRATICO");
    String hashAnterior = confirmaciones.consultar(desactualizada).calculo().hashEntrada();
    confirmaciones.confirmar(
        desactualizada, new ConfirmarEstadoIcRequest(EstadoCondicion.MALO, hashAnterior, null));
    jdbc.update(
        """
        UPDATE inspeccion
        SET datos = jsonb_set(datos, '{otros,senalizacion,reglamentaria,estado}', '"BUENO"')
        WHERE id = ?
        """,
        desactualizada);

    assertThatThrownBy(() -> transiciones.publicar(desactualizada, CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo("confirmacion_ic_desactualizada"));
    assertThat(estado(desactualizada)).isEqualTo("BORRADOR");
    assertThat(historial(desactualizada)).isEmpty();
  }

  @Test
  @DisplayName("Escenario: publicaciones simultáneas")
  void dosPublicacionesSimultaneasProducenUnaSolaPublicacion() throws Exception {
    UUID inspeccion = crearInspeccionCompleta(CATEDRATICO_ID, "CATEDRATICO");
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    CountDownLatch salida = new CountDownLatch(1);
    Callable<String> publicar =
        () -> {
          salida.await();
          try {
            return transiciones.publicar(inspeccion, CATEDRATICO_ID).estado();
          } catch (ApiException e) {
            return e.getStatus().value() + ":" + e.getCode();
          }
        };
    try {
      Future<String> primera = hilos.submit(publicar);
      Future<String> segunda = hilos.submit(publicar);
      salida.countDown();
      List<String> resultados =
          List.of(primera.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS));

      assertThat(resultados).containsOnlyOnce("PUBLICADA");
      assertThat(resultados).anyMatch(resultado -> resultado.startsWith("409:"));
    } finally {
      hilos.shutdownNow();
    }
    assertThat(historial(inspeccion)).hasSize(2);
    assertThat(numero("SELECT count(*) FROM resultado_ic WHERE inspeccion_id = ?", inspeccion))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Escenario: inspección publicada inmutable")
  void unaInspeccionPublicadaNoSeModificaNiSeElimina() {
    UUID inspeccion = crearInspeccionCompleta(CATEDRATICO_ID, "CATEDRATICO");
    transiciones.publicar(inspeccion, CATEDRATICO_ID);

    assertThatThrownBy(
            () ->
                inspeccionService.guardarBorrador(
                    inspeccion,
                    new GuardarBorradorRequest(null, null, null, null, null),
                    CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    assertThatThrownBy(
            () ->
                inspeccionService.guardarRespuestas(
                    inspeccion, new GuardarRespuestasRequest(datosCompletos, null), CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    assertThatThrownBy(() -> inspeccionService.eliminarBorrador(inspeccion, CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

    assertThat(estado(inspeccion)).isEqualTo("PUBLICADA");
    assertThat(
            jdbc.queryForObject(
                "SELECT eliminado_en IS NULL FROM inspeccion WHERE id = ?",
                Boolean.class,
                inspeccion))
        .isTrue();
  }

  // ----- HU-027: corrección de una inspección publicada -----

  @Test
  @DisplayName("HU-027 Escenario: crear una corrección deja la original intacta y queda auditada")
  void crearUnaCorreccionRelacionadaYAuditadaSinTocarLaOriginal() {
    UUID original = publicada(CATEDRATICO_ID, "CATEDRATICO");
    Map<String, Object> antes = jdbc.queryForMap("SELECT * FROM inspeccion WHERE id = ?", original);

    var correccion =
        correcciones.crear(
            original, "  Error en la socavación  ", CATEDRATICO_ID, RoleName.CATEDRATICO);

    assertThat(correccion.tipo()).isEqualTo("CORRECCION");
    assertThat(correccion.estado()).isEqualTo("BORRADOR");
    assertThat(correccion.supersedeAId()).isEqualTo(original);
    assertThat(correccion.motivoCorreccion()).isEqualTo("Error en la socavación");
    assertThat(correccion.versionFormularioId()).isEqualTo(versionId);
    assertThat(correccion.fechaInspeccion()).isEqualTo(LocalDate.of(2026, 10, 10));
    assertThat(
            jdbc.queryForObject(
                "SELECT datos = (SELECT datos FROM inspeccion WHERE id = ?)"
                    + " FROM inspeccion WHERE id = ?",
                Boolean.class,
                original,
                correccion.id()))
        .isTrue();

    // La original sigue PUBLICADA y sin ningún cambio.
    assertThat(jdbc.queryForMap("SELECT * FROM inspeccion WHERE id = ?", original))
        .isEqualTo(antes);
    assertThat(inspeccionService.consultarPorId(original, CATEDRATICO_ID).superadaPorId()).isNull();

    // Auditoría con usuario, fecha, motivo e inspección original.
    Map<String, Object> auditoria =
        jdbc.queryForMap(
            "SELECT usuario_id, accion, creado_en,"
                + " valores_posteriores->>'motivoCorreccion' AS motivo,"
                + " valores_posteriores->>'supersedeAId' AS original"
                + " FROM auditoria WHERE entidad = 'inspeccion' AND entidad_id = ?",
            correccion.id());
    assertThat(auditoria.get("usuario_id")).isEqualTo(CATEDRATICO_ID);
    assertThat(auditoria.get("accion")).isEqualTo("CREAR");
    assertThat(auditoria.get("creado_en")).isNotNull();
    assertThat(auditoria.get("motivo")).isEqualTo("Error en la socavación");
    assertThat(auditoria.get("original")).isEqualTo(original.toString());
  }

  @Test
  @DisplayName("HU-027 Escenarios: motivo obligatorio, usuario no autorizado y borrador")
  void rechazaSinMotivoAUsuarioNoAutorizadoYABorradores() {
    UUID original = publicada(PROFESIONAL_VERIFICADO_ID, "PROFESIONAL_EXTERNO");

    assertThatThrownBy(
            () ->
                correcciones.crear(
                    original, "   ", PROFESIONAL_VERIFICADO_ID, RoleName.PROFESIONAL_EXTERNO))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("motivo_requerido");
            });
    assertThatThrownBy(
            () -> correcciones.crear(original, "Motivo", ESTUDIANTE_ID, RoleName.ESTUDIANTE))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(e.getCode()).isEqualTo("correccion_no_autorizada");
            });
    // Un Catedrático que no es el autor sí puede corregir.
    assertThat(
            correcciones
                .crear(original, "Revisión del curso", OTRO_CATEDRATICO_ID, RoleName.CATEDRATICO)
                .autorId())
        .isEqualTo(OTRO_CATEDRATICO_ID);

    UUID borrador = crearInspeccionCompleta(CATEDRATICO_ID, "CATEDRATICO");
    assertThatThrownBy(
            () -> correcciones.crear(borrador, "Motivo", CATEDRATICO_ID, RoleName.CATEDRATICO))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("inspeccion_no_publicada");
            });
    assertThat(numero("SELECT count(*) FROM inspeccion WHERE supersede_a_id = ?", borrador))
        .isZero();
  }

  @Test
  @DisplayName("HU-027 Escenarios: flujo normal, original superada y estado vigente del puente")
  void laCorreccionPublicadaSuperaALaOriginalYActualizaElPuente() {
    UUID original = publicada(CATEDRATICO_ID, "CATEDRATICO");
    UUID correccion =
        correcciones
            .crear(original, "Socavación mal registrada", CATEDRATICO_ID, RoleName.CATEDRATICO)
            .id();

    // Sigue las mismas validaciones: sin fotografías propias no se publica.
    assertThatThrownBy(() -> transiciones.publicar(correccion, CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getErrores())
                  .extracting(error -> error.get("campo"))
                  .contains("fotografias.ACCESO", "fotografias.CAUCE");
            });
    assertThat(estado(correccion)).isEqualTo("BORRADOR");

    jdbc.update(
        "UPDATE inspeccion SET datos = jsonb_set(datos,"
            + " '{otros,senalizacion,reglamentaria,estado}', '\"BUENO\"') WHERE id = ?",
        correccion);
    agregarFotosObligatorias(correccion);
    reloj.ajustar(AHORA.plusSeconds(3600));
    assertThat(transiciones.publicar(correccion, CATEDRATICO_ID).estado()).isEqualTo("PUBLICADA");

    // La original sigue publicada y enlaza a la corrección que la supera.
    var consultaOriginal = inspeccionService.consultarPorId(original, OTRO_CATEDRATICO_ID);
    assertThat(consultaOriginal.estado()).isEqualTo("PUBLICADA");
    assertThat(consultaOriginal.superadaPorId()).isEqualTo(correccion);
    assertThat(
            inspeccionService
                .listarMisInspecciones(CATEDRATICO_ID, "PUBLICADA", 0, 20)
                .getContent())
        .anySatisfy(
            resumen -> {
              assertThat(resumen.id()).isEqualTo(original);
              assertThat(resumen.superadaPorId()).isEqualTo(correccion);
            });

    // El puente toma su estado vigente de la publicación más reciente: la corrección.
    Map<String, Object> puente =
        jdbc.queryForMap(
            "SELECT inspeccion_vigente_id, resultado_ic_vigente_id FROM puente WHERE id = ?",
            puenteId);
    assertThat(puente.get("inspeccion_vigente_id")).isEqualTo(correccion);
    assertThat(puente.get("resultado_ic_vigente_id"))
        .isEqualTo(
            jdbc.queryForObject(
                "SELECT id FROM resultado_ic WHERE inspeccion_id = ? AND tipo = 'PUBLICACION'",
                UUID.class,
                correccion));
  }

  @Test
  @DisplayName("HU-027 Escenario: una sola corrección publicada")
  void unaInspeccionSoloAdmiteUnaCorreccionPublicada() {
    UUID original = publicada(CATEDRATICO_ID, "CATEDRATICO");
    UUID primera =
        correcciones.crear(original, "Primera", CATEDRATICO_ID, RoleName.CATEDRATICO).id();
    UUID segunda =
        correcciones.crear(original, "Segunda", OTRO_CATEDRATICO_ID, RoleName.CATEDRATICO).id();
    agregarFotosObligatorias(primera);
    agregarFotosObligatorias(segunda);

    reloj.ajustar(AHORA.plusSeconds(60));
    transiciones.publicar(primera, CATEDRATICO_ID);

    assertThatThrownBy(() -> transiciones.publicar(segunda, OTRO_CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("correccion_ya_publicada");
            });
    assertThat(estado(segunda)).isEqualTo("BORRADOR");
    assertThatThrownBy(
            () -> correcciones.crear(original, "Tercera", CATEDRATICO_ID, RoleName.CATEDRATICO))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("inspeccion_superada"));
  }

  private UUID publicada(UUID autorId, String rol) {
    UUID inspeccion = crearInspeccionCompleta(autorId, rol);
    transiciones.publicar(inspeccion, autorId);
    return inspeccion;
  }

  // ----- Datos de prueba -----

  private UUID enRevision() {
    UUID inspeccion = crearInspeccionCompleta(ESTUDIANTE_ID, "ESTUDIANTE");
    transiciones.enviar(inspeccion, ESTUDIANTE_ID);
    autenticar(REVISOR_ID, RoleName.CATEDRATICO);
    transiciones.iniciarRevision(inspeccion, REVISOR_ID);
    return inspeccion;
  }

  private UUID crearInspeccionCompleta(UUID autorId, String rol) {
    UUID inspeccion = crearInspeccion(autorId, rol, datosCompletos);
    agregarFotosObligatorias(inspeccion);
    return inspeccion;
  }

  private UUID crearInspeccion(UUID autorId, String rol, JsonNode datos) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO inspeccion (id, puente_id, version_formulario_id, autor_id,
          rol_autor_creacion, nombre_autor_capturado, fecha_inspeccion, dispositivo_id, datos)
        VALUES (?, ?, ?, ?, ?, 'Autor HU026', ?, 'equipo-hu026', CAST(? AS jsonb))
        """,
        id,
        puenteId,
        versionId,
        autorId,
        rol,
        LocalDate.of(2026, 10, 10),
        datos.toString());
    return id;
  }

  private void agregarFotosObligatorias(UUID inspeccion) {
    for (String categoria : List.of("ACCESO", "SUPERESTRUCTURA", "SUBESTRUCTURA", "CAUCE")) {
      agregarFoto(inspeccion, categoria);
    }
  }

  private void agregarFoto(UUID inspeccion, String categoria) {
    UUID archivo = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO archivo (id, tipo_uso, categoria_foto, nombre_almacenado,
          clave_objeto_original, clave_objeto_miniatura, mime_real, tamano_bytes,
          checksum_sha256, ancho_px, alto_px, ubicacion_captura, capturado_en,
          subido_por_id, confirmado_en)
        VALUES (?, 'FOTO_INSPECCION', ?, ?, ?, ?, 'image/jpeg', 1000, ?, 100, 100,
          ST_SetSRID(ST_MakePoint(-90.615, 14.481), 4326)::geography, now(), ?, now())
        """,
        archivo,
        categoria,
        archivo + ".jpg",
        "fotos/" + archivo + ".jpg",
        "miniaturas/" + archivo + ".jpg",
        "c".repeat(64),
        CATEDRATICO_ID);
    jdbc.update(
        "INSERT INTO archivo_inspeccion (archivo_id, tipo_uso, inspeccion_id)"
            + " VALUES (?, 'FOTO_INSPECCION', ?)",
        archivo,
        inspeccion);
  }

  /** Versión publicada con el esquema y el catálogo reales del SIECA y pesos de prueba. */
  private UUID crearVersionSieca() throws IOException {
    UUID id = UUID.randomUUID();
    JsonNode esquema = leer("/formulario/sieca-v1/esquema.schema.json");
    ObjectNode catalogo = (ObjectNode) leer("/formulario/sieca-v1/elementos-ic.json");
    ObjectNode reglas = (ObjectNode) leer("/formulario/sieca-v1/reglas-ic-calibradas-prueba.json");
    JsonNode anulaciones =
        catalogo.path("reglas_evaluacion_ic").path("reglas_anulacion").deepCopy();
    for (JsonNode regla : anulaciones) {
      ((ObjectNode) regla).putNull("pendiente");
    }
    reglas.set("reglas_anulacion", anulaciones);
    catalogo.set("reglas_evaluacion_ic", reglas);

    jdbc.update(
        """
        INSERT INTO version_formulario (id, codigo, estado, activa, esquema,
          reglas_evaluacion_ic, hash_contenido, version_algoritmo_ic,
          creado_por_id, publicado_por_id, publicado_en)
        VALUES (?, ?, 'PUBLICADA', false, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, now())
        """,
        id,
        "HU026-" + id,
        esquema.toString(),
        catalogo.toString(),
        "b".repeat(64),
        ALGORITMO,
        CATEDRATICO_ID,
        CATEDRATICO_ID);

    List<Object[]> pesos = new ArrayList<>();
    for (JsonNode elemento : catalogo.get("elementos")) {
      pesos.add(
          new Object[] {
            UUID.randomUUID(), id, elemento.get("elemento_ref").asText(), "Peso HU026"
          });
    }
    jdbc.batchUpdate(
        """
        INSERT INTO configuracion_ic_elemento
          (id, version_formulario_id, elemento_ref, peso, descripcion)
        VALUES (?, ?, ?, 1, ?)
        """,
        pesos);
    return id;
  }

  private UUID crearPuente() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO puente (id, codigo, correlativo_municipal, municipio_id,
          nombre, ruta, ubicacion, creado_por_id)
        SELECT ?, ?, (SELECT COALESCE(MAX(p.correlativo_municipal), 0) + 1
                      FROM puente p WHERE p.municipio_id = m.id),
          m.id, 'Puente HU026', 'CA-1',
          ST_SetSRID(ST_MakePoint(-90.615, 14.481), 4326)::geography, ?
        FROM municipio m WHERE m.codigo_ine = '0114'
        """,
        id,
        "HU026-" + id.toString().substring(0, 18),
        CATEDRATICO_ID);
    return id;
  }

  private void insertarUsuario(UUID id, String rol) {
    jdbc.update(
        """
        INSERT INTO usuario (id, email, password_hash, rol_id,
          verificado, activado, activo, nombre_completo)
        SELECT ?, ?, 'hash-prueba', id, true, true, true, 'Usuario HU026'
        FROM rol WHERE nombre = ?
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        id + "@hu026.ejemplo.com",
        rol);
  }

  private List<String> historial(UUID inspeccion) {
    return jdbc.query(
        """
        SELECT secuencia, estado_anterior, estado_nuevo, evento, realizado_por_id
        FROM historial_estado_inspeccion WHERE inspeccion_id = ? ORDER BY secuencia
        """,
        (rs, fila) ->
            rs.getInt("secuencia")
                + ":"
                + rs.getString("estado_anterior")
                + ">"
                + rs.getString("estado_nuevo")
                + ":"
                + rs.getString("evento")
                + ":"
                + rs.getObject("realizado_por_id", UUID.class),
        inspeccion);
  }

  private String estado(UUID inspeccion) {
    return jdbc.queryForObject(
        "SELECT estado FROM inspeccion WHERE id = ?", String.class, inspeccion);
  }

  private int numero(String consulta, UUID id) {
    return jdbc.queryForObject(consulta, Integer.class, id);
  }

  private JsonNode leer(String recurso) throws IOException {
    try (InputStream contenido = getClass().getResourceAsStream(recurso)) {
      if (contenido == null) {
        throw new IOException("No existe el recurso " + recurso);
      }
      return json.readTree(contenido);
    }
  }

  private void autenticar(UUID usuarioId, RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            usuarioId + "@hu026.ejemplo.com",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData(usuarioId + "@hu026.ejemplo.com", usuarioId, rol, 0));
    var contexto = SecurityContextHolder.createEmptyContext();
    contexto.setAuthentication(authentication);
    SecurityContextHolder.setContext(contexto);
  }

  private static UUID uuid(String sufijo) {
    return UUID.fromString("019a0000-0000-7000-8000-0000000000" + sufijo);
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class Config {

    @Bean
    RelojAjustable clock() {
      return new RelojAjustable();
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }
  }
}
