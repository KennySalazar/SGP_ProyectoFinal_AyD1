package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.inspeccion.model.InspeccionPublicadaEvent;
import gt.usac.cunoc.sgp.inspeccion.service.ResolverElementosIc;
import gt.usac.cunoc.sgp.mantenimiento.dto.GeneracionPropuestasResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.entity.ReglaMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.mapper.OrdenMantenimientoMapperImpl;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.HistorialEstadoOrdenRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.ReglaMantenimientoRepository;
import gt.usac.cunoc.sgp.mantenimiento.service.EvaluadorCondicionRegla;
import gt.usac.cunoc.sgp.mantenimiento.service.GeneradorPropuestasMantenimientoService;
import gt.usac.cunoc.sgp.mantenimiento.service.OrdenesDeInspeccionService;
import gt.usac.cunoc.sgp.mantenimiento.service.PropuestasAlPublicarListener;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
 * Generación de propuestas de mantenimiento al publicar una inspección, contra PostgreSQL real
 * (HU-046, RN-MTO-01, RN-MTO-02, RN-MTO-03).
 */
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  GeneradorPropuestasMantenimientoService.class,
  EvaluadorCondicionRegla.class,
  ResolverElementosIc.class,
  AuditoriaWriter.class,
  NotificacionService.class,
  PropuestasAlPublicarListener.class,
  OrdenesDeInspeccionService.class,
  OrdenMantenimientoMapperImpl.class,
  GeneracionPropuestasMantenimientoIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class GeneracionPropuestasMantenimientoIntegrationTest {

  private static final Instant AHORA = Instant.parse("2026-10-19T12:00:00Z");
  private static final String PROCESO = "generar-propuestas-mantenimiento";

  /** Los datos persisten entre pruebas, y cada puente necesita un correlativo propio. */
  private static final AtomicInteger CORRELATIVO = new AtomicInteger(100);

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_propuestas_test")
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

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private EntityManager em;
  @Autowired private PlatformTransactionManager transacciones;
  @Autowired private ApplicationEventPublisher eventos;
  @Autowired private GeneradorPropuestasMantenimientoService generador;
  @Autowired private OrdenesDeInspeccionService ordenesDeInspeccion;
  @Autowired private PropuestasAlPublicarListener listener;
  @Autowired private ReglaMantenimientoRepository reglas;
  @Autowired private OrdenMantenimientoRepository ordenes;
  @Autowired private HistorialEstadoOrdenRepository historial;

  private UUID administrador;
  private UUID publicador;
  private UUID puente;
  private UUID version;
  private UUID instanciaPila;

  @BeforeEach
  void preparar() {
    administrador = UUID.randomUUID();
    publicador = UUID.randomUUID();
    puente = UUID.randomUUID();
    version = UUID.randomUUID();
    instanciaPila = UUID.randomUUID();
    crearUsuario(administrador, "ADMINISTRADOR");
    crearUsuario(publicador, "CATEDRATICO");
    jdbc.update(
        "INSERT INTO version_formulario(id,codigo,esquema,creado_por_id) VALUES (?,?,'{}',?)",
        version,
        "propuestas-" + version,
        administrador);
    jdbc.update(
        "INSERT INTO puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id) SELECT ?,?,?,id,'test','test',ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,? FROM municipio WHERE codigo_ine='0114'",
        puente,
        "T-" + puente,
        CORRELATIVO.getAndIncrement(),
        administrador);
  }

  // ---------------------------------------------------------------- generación

  @Test
  void generaUnaPropuestaPorCadaCondicionCumplidaYNingunaMas() throws Exception {
    crearReglasDeEjemplo();
    UUID inspeccion = crearInspeccionPublicada(datosConDanos());
    UUID resultadoIc = crearResultadoIc(inspeccion);

    GeneracionPropuestasResponse resultado =
        generador.generarTrasPublicacion(inspeccion, publicador);

    assertThat(resultado.reglasEvaluadas()).isEqualTo(4); // la inactiva no cuenta
    assertThat(resultado.creadas()).isEqualTo(3);
    assertThat(resultado.yaGeneradas()).isZero();
    assertThat(resultado.omitidasPorOrdenAbierta()).isZero();
    assertThat(resultado.reglasConError()).isZero();

    List<OrdenMantenimiento> propuestas =
        ordenes.findByInspeccionOrigenIdOrderByCreadoEnAscIdAsc(inspeccion);
    assertThat(propuestas)
        .extracting(OrdenMantenimiento::getEstado)
        .containsOnly(EstadoOrden.PROPUESTA);
    assertThat(propuestas)
        .extracting(OrdenMantenimiento::getElementoRef)
        .containsExactlyInAnyOrder(
            "/subestructura/estribo_entrada/viga_apoyo",
            "/subestructura/estribo_entrada/cortina_superior",
            "/subestructura/pilas/socavacion");

    OrdenMantenimiento correctiva = porElemento(propuestas, "viga_apoyo");
    assertThat(correctiva.getTipo()).isEqualTo(TipoOrden.CORRECTIVO);
    assertThat(correctiva.getPrioridadSugerida()).isEqualTo(PrioridadOrden.ALTA);
    assertThat(correctiva.getPrioridadConfirmada()).isEqualTo(PrioridadOrden.ALTA);
    assertThat(correctiva.getDescripcion()).isEqualTo("Reparar la viga de apoyo.");
    assertThat(correctiva.getPuenteId()).isEqualTo(puente);
    assertThat(correctiva.getInspeccionOrigenId()).isEqualTo(inspeccion);
    assertThat(correctiva.getResultadoIcOrigenId()).isEqualTo(resultadoIc);
    assertThat(correctiva.getReglaOrigenId()).isNotNull();
    assertThat(correctiva.getCreadoPorId()).isNull();
    assertThat(correctiva.getInstanciaElementoId()).isNull();
    assertThat(correctiva.getClaveGeneracion()).isEqualTo("REG-COR-001:-");
    assertThat(correctiva.getFundamentoGeneracion().path("reglaCodigo").asText())
        .isEqualTo("REG-COR-001");
    assertThat(correctiva.getFundamentoGeneracion().path("valorEncontrado").asText())
        .isEqualTo("SEVERO");
    assertThat(correctiva.getFundamentoGeneracion().path("umbral").asText()).isEqualTo("MODERADO");

    OrdenMantenimiento emergencia = porElemento(propuestas, "socavacion");
    assertThat(emergencia.getTipo()).isEqualTo(TipoOrden.EMERGENCIA);
    assertThat(emergencia.getPrioridadSugerida()).isEqualTo(PrioridadOrden.CRITICA);
    assertThat(emergencia.getInstanciaElementoId()).isEqualTo(instanciaPila);
    assertThat(emergencia.getClaveGeneracion()).isEqualTo("REG-EME-001:" + instanciaPila);
    assertThat(emergencia.getFundamentoGeneracion().path("instanciaElementoId").asText())
        .isEqualTo(instanciaPila.toString());
  }

  @Test
  void cadaPropuestaNaceConSuHistorialYSuAuditoriaDelProceso() throws Exception {
    crearReglasDeEjemplo();
    UUID inspeccion = crearInspeccionPublicada(datosConDanos());

    generador.generarTrasPublicacion(inspeccion, publicador);

    for (OrdenMantenimiento orden :
        ordenes.findByInspeccionOrigenIdOrderByCreadoEnAscIdAsc(inspeccion)) {
      List<HistorialEstadoOrden> filas =
          historial.findByOrdenMantenimientoIdOrderBySecuenciaAsc(orden.getId());
      assertThat(filas)
          .singleElement()
          .satisfies(
              fila -> {
                assertThat(fila.getSecuencia()).isEqualTo(1);
                assertThat(fila.getEstadoAnterior()).isNull();
                assertThat(fila.getEstadoNuevo()).isEqualTo("PROPUESTA");
                assertThat(fila.getRealizadoPorId()).isNull();
              });
      Integer auditorias =
          jdbc.queryForObject(
              "SELECT count(*) FROM auditoria WHERE entidad = 'orden_mantenimiento' AND entidad_id = ? AND usuario_id IS NULL AND proceso_automatico = ?",
              Integer.class,
              orden.getId(),
              PROCESO);
      assertThat(auditorias).isEqualTo(1);
    }
  }

  @Test
  void avisaUnaSolaVezAlosAdministradoresYAQuienPublico() throws Exception {
    crearReglasDeEjemplo();
    UUID inspeccion = crearInspeccionPublicada(datosConDanos());

    generador.generarTrasPublicacion(inspeccion, publicador);
    generador.regenerar(inspeccion, administrador);

    String clave = "mantenimiento-propuestas:" + inspeccion;
    assertThat(avisos(administrador, clave)).isEqualTo(1);
    assertThat(avisos(publicador, clave)).isEqualTo(1);
    String mensaje =
        jdbc.queryForObject(
            "SELECT mensaje FROM notificacion WHERE usuario_id = ? AND clave_evento = ?",
            String.class,
            publicador,
            clave);
    assertThat(mensaje)
        .isEqualTo(
            "Se generaron 3 propuestas de mantenimiento a partir de una inspección publicada.");
  }

  // --------------------------------------------------------------- idempotencia

  @Test
  void regenerarNoDuplicaLoYaGenerado() throws Exception {
    crearReglasDeEjemplo();
    UUID inspeccion = crearInspeccionPublicada(datosConDanos());
    generador.generarTrasPublicacion(inspeccion, publicador);

    GeneracionPropuestasResponse segunda = generador.regenerar(inspeccion, administrador);

    assertThat(segunda.creadas()).isZero();
    assertThat(segunda.yaGeneradas()).isEqualTo(3);
    assertThat(ordenes.findByInspeccionOrigenIdOrderByCreadoEnAscIdAsc(inspeccion)).hasSize(3);
  }

  @Test
  void regenerarCreaSoloLasQueFaltaban() throws Exception {
    crearReglasDeEjemplo();
    UUID inspeccion = crearInspeccionPublicada(datosConDanos());
    generador.generarTrasPublicacion(inspeccion, publicador);
    jdbc.update(
        "DELETE FROM historial_estado_orden WHERE orden_mantenimiento_id IN (SELECT id FROM orden_mantenimiento WHERE inspeccion_origen_id = ? AND clave_generacion = 'REG-PRV-001:-')",
        inspeccion);
    jdbc.update(
        "DELETE FROM orden_mantenimiento WHERE inspeccion_origen_id = ? AND clave_generacion = 'REG-PRV-001:-'",
        inspeccion);

    GeneracionPropuestasResponse resultado = generador.regenerar(inspeccion, administrador);

    assertThat(resultado.creadas()).isEqualTo(1);
    assertThat(resultado.yaGeneradas()).isEqualTo(2);
    assertThat(ordenes.findByInspeccionOrigenIdOrderByCreadoEnAscIdAsc(inspeccion)).hasSize(3);
  }

  @Test
  void noProponeLoQueYaTieneUnaOrdenEnCursoEnElMismoPuente() throws Exception {
    crearReglasDeEjemplo();
    UUID primera = crearInspeccionPublicada(datosConDanos());
    generador.generarTrasPublicacion(primera, publicador);
    UUID segunda = crearInspeccionPublicada(datosConDanos());

    GeneracionPropuestasResponse resultado = generador.generarTrasPublicacion(segunda, publicador);

    assertThat(resultado.creadas()).isZero();
    assertThat(resultado.omitidasPorOrdenAbierta()).isEqualTo(3);
    assertThat(ordenes.findByInspeccionOrigenIdOrderByCreadoEnAscIdAsc(segunda)).isEmpty();
  }

  // ------------------------------------------------------------ sin propuestas

  @Test
  void unaInspeccionSinDanosNoGeneraNadaNiFalla() throws Exception {
    crearReglasDeEjemplo();
    UUID inspeccion =
        crearInspeccionPublicada(
            json.readTree(
                """
                {"subestructura":{"estribo_entrada":{
                  "viga_apoyo":{"exposicion_acero":"NO_PRESENTA"},
                  "cortina_superior":{"grietas_una_direccion":"NO_PRESENTA"}}}}
                """));

    GeneracionPropuestasResponse resultado =
        generador.generarTrasPublicacion(inspeccion, publicador);

    assertThat(resultado.creadas()).isZero();
    assertThat(resultado.reglasConError()).isZero();
    assertThat(avisos(administrador, "mantenimiento-propuestas:" + inspeccion)).isZero();
  }

  @Test
  void unaVersionSinReglasActivasNoGeneraPropuestas() throws Exception {
    UUID inspeccion = crearInspeccionPublicada(datosConDanos());

    GeneracionPropuestasResponse resultado =
        generador.generarTrasPublicacion(inspeccion, publicador);

    assertThat(resultado.reglasEvaluadas()).isZero();
    assertThat(resultado.creadas()).isZero();
  }

  @Test
  void unaReglaDefectuosaNoImpideEvaluarLasDemas() throws Exception {
    crearReglasDeEjemplo();
    crearRegla(
        "REG-MAL-001",
        "sin-barra",
        "{\"indicador\":\"x\",\"operador\":\"IGUAL\",\"umbral\":1}",
        TipoOrden.RUTINARIO,
        "BAJA",
        true);
    UUID inspeccion = crearInspeccionPublicada(datosConDanos());

    GeneracionPropuestasResponse resultado =
        generador.generarTrasPublicacion(inspeccion, publicador);

    assertThat(resultado.reglasConError()).isEqualTo(1);
    assertThat(resultado.creadas()).isEqualTo(3);
  }

  // ----------------------------------------------------------------- estados

  @Test
  void soloUnaInspeccionPublicadaGeneraPropuestas() throws Exception {
    crearReglasDeEjemplo();
    UUID borrador = crearInspeccion(datosConDanos(), "BORRADOR");

    assertThatThrownBy(() -> generador.regenerar(borrador, administrador))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("inspeccion_no_publicada");
            });
    assertThat(ordenes.findByInspeccionOrigenIdOrderByCreadoEnAscIdAsc(borrador)).isEmpty();
  }

  @Test
  void unaInspeccionInexistenteResponde404() {
    assertThatThrownBy(() -> generador.regenerar(UUID.randomUUID(), administrador))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  // ------------------------------------------------------ después de publicar

  @Test
  void generaDespuesDeConfirmarLaPublicacionYNoAntes() throws Exception {
    crearReglasDeEjemplo();
    UUID inspeccion = crearInspeccionPublicada(datosConDanos());

    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              eventos.publishEvent(new InspeccionPublicadaEvent(inspeccion, publicador));
              // Dentro de la transacción de publicación todavía no se genera nada.
              assertThat(contarOrdenes(inspeccion)).isZero();
            });

    assertThat(contarOrdenes(inspeccion)).isEqualTo(3);
    assertThat(avisos(publicador, "mantenimiento-propuestas:" + inspeccion)).isEqualTo(1);
  }

  @Test
  void unaTransaccionRevertidaNoGeneraPropuestas() throws Exception {
    crearReglasDeEjemplo();
    UUID inspeccion = crearInspeccionPublicada(datosConDanos());

    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              eventos.publishEvent(new InspeccionPublicadaEvent(inspeccion, publicador));
              estado.setRollbackOnly();
            });

    assertThat(contarOrdenes(inspeccion)).isZero();
  }

  @Test
  void unFalloDeLaGeneracionNuncaSePropagaAlaPublicacion() {
    UUID inexistente = UUID.randomUUID();

    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> eventos.publishEvent(new InspeccionPublicadaEvent(inexistente, publicador)));

    assertThat(contarOrdenes(inexistente)).isZero();
    // y llamar al listener directamente tampoco lanza
    listener.alPublicarse(new InspeccionPublicadaEvent(inexistente, publicador));
  }

  // -------------------------------------------------------- ver desde la inspección

  @Test
  void laInspeccionPublicadaMuestraLasOrdenesQueOrigino() throws Exception {
    crearReglasDeEjemplo();
    UUID inspeccion = crearInspeccionPublicada(datosConDanos());
    generador.generarTrasPublicacion(inspeccion, publicador);

    List<OrdenMantenimientoResponse> listadas = ordenesDeInspeccion.listar(inspeccion);

    assertThat(listadas).hasSize(3);
    assertThat(listadas)
        .extracting(OrdenMantenimientoResponse::estado)
        .containsOnly(EstadoOrden.PROPUESTA);
    assertThat(listadas)
        .extracting(OrdenMantenimientoResponse::inspeccionOrigenId)
        .containsOnly(inspeccion);
  }

  @Test
  void unaInspeccionQueNoEstaPublicadaNoMuestraOrdenes() throws Exception {
    UUID borrador = crearInspeccion(datosConDanos(), "BORRADOR");

    assertThatThrownBy(() -> ordenesDeInspeccion.listar(borrador))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    assertThatThrownBy(() -> ordenesDeInspeccion.listar(UUID.randomUUID()))
        .isInstanceOf(ApiException.class);
  }

  // ----------------------------------------------------------------- auxiliares

  private void crearReglasDeEjemplo() throws Exception {
    crearRegla(
        "REG-COR-001",
        "/subestructura/estribo_entrada/viga_apoyo",
        "{\"indicador\":\"exposicion_acero\",\"operador\":\"MAYOR_O_IGUAL\",\"umbral\":\"MODERADO\"}",
        TipoOrden.CORRECTIVO,
        "ALTA",
        true);
    crearRegla(
        "REG-PRV-001",
        "/subestructura/estribo_entrada/cortina_superior",
        "{\"indicador\":\"grietas_una_direccion\",\"operador\":\"IGUAL\",\"umbral\":\"LEVE\"}",
        TipoOrden.PREVENTIVO,
        "MEDIA",
        true);
    crearRegla(
        "REG-EME-001",
        "/subestructura/pilas/socavacion",
        "{\"indicador\":\"aguas_abajo\",\"operador\":\"IGUAL\",\"umbral\":\"ASENTAMIENTO_ESTRUCTURA\"}",
        TipoOrden.EMERGENCIA,
        "CRITICA",
        true);
    crearRegla(
        "REG-RUT-001",
        "/elementos_no_estructurales/junta_entrada",
        "{\"indicador\":\"junta_limpia\",\"operador\":\"IGUAL\",\"umbral\":false}",
        TipoOrden.RUTINARIO,
        "BAJA",
        true);
    // Inactiva: cumpliría la condición, pero no se evalúa.
    crearRegla(
        "REG-OFF-001",
        "/subestructura/estribo_entrada/viga_apoyo",
        "{\"indicador\":\"exposicion_acero\",\"operador\":\"MAYOR_O_IGUAL\",\"umbral\":\"LEVE\"}",
        TipoOrden.PREVENTIVO,
        "BAJA",
        false);
  }

  private void crearRegla(
      String codigo,
      String elementoRef,
      String condicion,
      TipoOrden tipo,
      String prioridad,
      boolean activa)
      throws Exception {
    String intervencion =
        codigo.equals("REG-COR-001") ? "Reparar la viga de apoyo." : "Intervención de " + codigo;
    reglas.saveAndFlush(
        new ReglaMantenimiento(
            UUID.randomUUID(),
            codigo,
            1,
            em.getReference(VersionFormulario.class, version),
            elementoRef,
            json.readTree(condicion),
            tipo,
            intervencion,
            json.readTree("{\"prioridad_base\":\"" + prioridad + "\"}"),
            activa,
            administrador,
            null,
            null,
            AHORA));
  }

  /** Daños que cumplen tres reglas; la junta limpia no cumple la rutinaria. */
  private JsonNode datosConDanos() throws Exception {
    return json.readTree(
        """
        {
          "subestructura": {
            "estribo_entrada": {
              "viga_apoyo": {"exposicion_acero": "SEVERO"},
              "cortina_superior": {"grietas_una_direccion": "LEVE"}
            },
            "pilas": [
              {"instancia_id": "%s",
               "socavacion": {"aguas_arriba": "NO_HAY", "aguas_abajo": "ASENTAMIENTO_ESTRUCTURA"}}
            ]
          },
          "elementos_no_estructurales": {"junta_entrada": {"junta_limpia": true}}
        }
        """
            .formatted(instanciaPila));
  }

  private UUID crearInspeccionPublicada(JsonNode datos) {
    return crearInspeccion(datos, "PUBLICADA");
  }

  private UUID crearInspeccion(JsonNode datos, String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO inspeccion(id,puente_id,version_formulario_id,autor_id,rol_autor_creacion,nombre_autor_capturado,fecha_inspeccion,dispositivo_id,estado,publicado_en,datos) VALUES (?,?,?,?,'CATEDRATICO','test',CURRENT_DATE,'test',?,?,?::jsonb)",
        id,
        puente,
        version,
        publicador,
        estado,
        "PUBLICADA".equals(estado) ? java.sql.Timestamp.from(AHORA) : null,
        datos.toString());
    return id;
  }

  private UUID crearResultadoIc(UUID inspeccion) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO resultado_ic(id,inspeccion_id,version_configuracion_id,tipo,indice_calculado,estado_por_rango,estado_calculado,estado_confirmado,desglose,hash_entrada,version_algoritmo) VALUES (?,?,?,'PUBLICACION',40,'MALO','MALO','MALO','{}'::jsonb,'h','v')",
        id,
        inspeccion,
        version);
    return id;
  }

  private void crearUsuario(UUID id, String rol) {
    jdbc.update(
        "INSERT INTO usuario(id,email,password_hash,rol_id) SELECT ?,?,'test',id FROM rol WHERE nombre=?",
        id,
        id + "@test.com",
        rol);
  }

  private int avisos(UUID usuario, String clave) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM notificacion WHERE usuario_id = ? AND clave_evento = ?",
            Integer.class,
            usuario,
            clave);
    return total == null ? 0 : total;
  }

  private int contarOrdenes(UUID inspeccion) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM orden_mantenimiento WHERE inspeccion_origen_id = ?",
            Integer.class,
            inspeccion);
    return total == null ? 0 : total;
  }

  private static OrdenMantenimiento porElemento(List<OrdenMantenimiento> lista, String fragmento) {
    return lista.stream()
        .filter(o -> o.getElementoRef().contains(fragmento))
        .findFirst()
        .orElseThrow();
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
