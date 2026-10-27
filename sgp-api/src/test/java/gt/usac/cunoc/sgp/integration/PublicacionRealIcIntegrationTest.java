package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.controller.TransicionInspeccionController;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapperImpl;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.InspeccionPublicadaEvent;
import gt.usac.cunoc.sgp.inspeccion.service.*;
import gt.usac.cunoc.sgp.sync.http.IdempotenciaHttpFilter;
import gt.usac.cunoc.sgp.sync.repository.OperacionHttpRepository;
import gt.usac.cunoc.sgp.sync.service.IdempotenciaHttpService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  IdempotenciaHttpService.class,
  OperacionHttpRepository.class,
  TransicionInspeccionService.class,
  ReglasEvidenciaFotografica.class,
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
  AuditoriaWriter.class,
  InspeccionMapperImpl.class,
  JsonSchemaValidatorService.class,
  PublicacionRealIcIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PublicacionRealIcIntegrationTest {

  private static final Instant AHORA = Instant.parse("2026-10-17T04:00:00Z");
  private static final String ALGORITMO = "ic-integracion-prueba-v1";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_publicacion_real_test")
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

  @Autowired private IdempotenciaHttpService idempotencia;
  @Autowired private PlatformTransactionManager tm;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private TransicionInspeccionService transiciones;
  @Autowired private ConfirmacionEstadoIcService confirmaciones;
  @Autowired private EventosDePublicacion eventosDePublicacion;

  private UUID autorId;
  private UUID puenteId;
  private UUID versionId;
  private UUID inspeccionId;

  @BeforeEach
  void preparar() throws Exception {
    eventosDePublicacion.recibidos.clear();
    autorId = UUID.randomUUID();
    puenteId = UUID.randomUUID();
    versionId = UUID.randomUUID();
    inspeccionId = UUID.randomUUID();
    crearUsuario(autorId, "CATEDRATICO");
    autenticar(autorId, RoleName.CATEDRATICO);

    JsonNode catalogo =
        json.readTree(
            """
        {
          "formulario":"sieca-v1",
          "elementos":[{
            "elemento_ref":"/vigas", "repetible":false,
            "portante":true, "indicadores":["dano"]
          }],
          "reglas_evaluacion_ic":{
            "version_algoritmo":"ic-integracion-prueba-v1",
            "escala_severidad":{"NO_PRESENTA":0,"LEVE":1,"MODERADO":2,"SEVERO":4},
            "agregacion":{
              "indicadores_de_un_elemento":"MAXIMO",
              "instancias_repetibles":"MAXIMO",
              "elementos_ausentes_no_portantes":4,
              "elementos_ausentes_portantes":4
            },
            "rangos_estado":{"BUENO":75,"REGULAR":50,"MALO":0},
            "conversion_indicadores":{"booleanos":{},"enumerados":{},"numericos":{}},
            "reglas_anulacion":[{
              "id":"portante-cortado", "descripcion":"Elemento portante cortado",
              "estado_resultante":"MALO", "elementos":["/vigas"],
              "condicion":{"campo":"cortado","igual_a":"SI"}
            }]
          }
        }
        """);
    String esquema =
        """
        {"type":"object","required":["vigas"],"properties":{
          "vigas":{"type":"object","required":["existencia","dano"],"properties":{
            "existencia":{"enum":["PRESENTE","AUSENTE","NO_APLICA"]},
            "dano":{"enum":["NO_PRESENTA","LEVE","MODERADO","SEVERO"]}
          }}
        }}
        """;
    jdbc.update(
        """
        INSERT INTO version_formulario (id,codigo,estado,activa,esquema,
          reglas_evaluacion_ic,hash_contenido,version_algoritmo_ic,
          creado_por_id,publicado_por_id,publicado_en)
        VALUES (?,?,'PUBLICADA',false,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?)
        """,
        versionId,
        "IT-" + versionId,
        esquema,
        catalogo.toString(),
        "a".repeat(64),
        ALGORITMO,
        autorId,
        autorId,
        OffsetDateTime.ofInstant(AHORA.minusSeconds(3600), ZoneOffset.UTC));
    jdbc.update(
        """
        INSERT INTO configuracion_ic_elemento (id,version_formulario_id,elemento_ref,peso,descripcion)
        VALUES (?,?,'/vigas',1,'Peso exclusivo de prueba')
        """,
        UUID.randomUUID(),
        versionId);
    int correlativo =
        jdbc.queryForObject(
            """
        SELECT COALESCE(MAX(p.correlativo_municipal),0)+1 FROM puente p
        JOIN municipio m ON m.id=p.municipio_id WHERE m.codigo_ine='0114'
        """,
            Integer.class);
    jdbc.update(
        """
        INSERT INTO puente (id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id)
        SELECT ?,?,?,id,'Puente integracion real','CA-1',
          ST_SetSRID(ST_MakePoint(-90.615,14.481),4326)::geography,?
        FROM municipio WHERE codigo_ine='0114'
        """,
        puenteId,
        "IT-" + puenteId,
        correlativo,
        autorId);
    jdbc.update(
        """
        INSERT INTO inspeccion (id,puente_id,version_formulario_id,autor_id,
          rol_autor_creacion,nombre_autor_capturado,fecha_inspeccion,dispositivo_id,datos)
        VALUES (?,?,?,?,'CATEDRATICO','Autor de prueba',?,'equipo-prueba',
          '{"vigas":{"existencia":"PRESENTE","dano":"MODERADO","cortado":"NO"}}'::jsonb)
        """,
        inspeccionId,
        puenteId,
        versionId,
        autorId,
        LocalDate.of(2026, 10, 16));
    for (String categoria : List.of("ACCESO", "SUPERESTRUCTURA", "SUBESTRUCTURA", "CAUCE")) {
      UUID archivoId = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO archivo (id,tipo_uso,categoria_foto,nombre_almacenado,
            clave_objeto_original,clave_objeto_miniatura,mime_real,tamano_bytes,checksum_sha256,
            ancho_px,alto_px,ubicacion_captura,capturado_en,subido_por_id,confirmado_en)
          VALUES (?,'FOTO_INSPECCION',?,?,?,?,'image/jpeg',100,?,10,10,
            ST_SetSRID(ST_MakePoint(-90.615,14.481),4326)::geography,?,?,?)
          """,
          archivoId,
          categoria,
          archivoId + ".jpg",
          archivoId + ".jpg",
          archivoId + "-mini.jpg",
          "c".repeat(64),
          OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
          autorId,
          OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
      jdbc.update(
          "INSERT INTO archivo_inspeccion (archivo_id,tipo_uso,inspeccion_id) VALUES (?,'FOTO_INSPECCION',?)",
          archivoId,
          inspeccionId);
    }
  }

  @AfterEach
  void limpiarSesion() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @CsvSource({"NO_PRESENTA,100,Bueno", "MODERADO,50,Regular", "SEVERO,0,Malo"})
  void publicacionDirectaCalculaYGuardaTodo(String severidad, String indice, String estado) {
    cambiarDano(severidad);
    transiciones.publicar(inspeccionId, autorId);
    comprobarPublicacion(indice, estado, 2);
    assertThat(
            jdbc.queryForList(
                "SELECT estado_nuevo FROM historial_estado_inspeccion WHERE inspeccion_id=? ORDER BY secuencia",
                String.class,
                inspeccionId))
        .containsExactly("ENVIADA", "PUBLICADA");
    assertThat(
            numero(
                "SELECT count(*) FROM auditoria WHERE entidad='inspeccion' AND entidad_id=?",
                inspeccionId))
        .isEqualTo(2);
  }

  @Test
  void hu061RepetirPublicacionHttpConservaRespuestaIcHistorialYEvento() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new TransicionInspeccionController(transiciones))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new IdempotenciaHttpFilter(idempotencia, json, tm))
            .build();
    var ruta = "/api/v1/inspecciones/" + inspeccionId + "/publicar";
    var primera =
        mvc.perform(
                post(ruta)
                    .servletPath(ruta)
                    .principal(SecurityContextHolder.getContext().getAuthentication())
                    .header("Idempotency-Key", "publicacion-hu061"))
            .andReturn();
    var repetida =
        mvc.perform(
                post(ruta)
                    .servletPath(ruta)
                    .principal(SecurityContextHolder.getContext().getAuthentication())
                    .header("Idempotency-Key", "publicacion-hu061"))
            .andReturn();
    assertThat(primera.getResponse().getStatus()).isEqualTo(200);
    assertThat(repetida.getResponse().getStatus()).isEqualTo(200);
    assertThat(repetida.getResponse().getHeader("Idempotent-Replay")).isEqualTo("true");
    assertThat(repetida.getResponse().getContentAsByteArray())
        .isEqualTo(primera.getResponse().getContentAsByteArray());
    comprobarPublicacion("50", "Regular", 2);
    assertThat(eventosDePublicacion.recibidos).hasSize(1);
  }

  @Test
  void ajusteDelAutorLlegaAlResultadoYAlPuente() {
    var calculo = confirmaciones.consultar(inspeccionId).calculo();
    confirmaciones.confirmar(
        inspeccionId,
        new ConfirmarEstadoIcRequest(
            EstadoCondicion.BUENO, calculo.hashEntrada(), "Criterio profesional documentado"));
    transiciones.publicar(inspeccionId, autorId);
    comprobarPublicacion("50", "Bueno", 2);
    var resultado =
        jdbc.queryForMap(
            "SELECT estado_calculado,estado_confirmado,confirmado_por_id,justificacion_ajuste FROM resultado_ic WHERE inspeccion_id=?",
            inspeccionId);
    assertThat(resultado.get("estado_calculado")).isEqualTo("REGULAR");
    assertThat(resultado.get("estado_confirmado")).isEqualTo("BUENO");
    assertThat(resultado.get("confirmado_por_id")).isEqualTo(autorId);
    assertThat(resultado.get("justificacion_ajuste")).isEqualTo("Criterio profesional documentado");
  }

  @Test
  void condicionCriticaRealConservaIcYPermiteAjusteJustificado() {
    cambiarDano("LEVE");
    jdbc.update(
        "UPDATE inspeccion SET datos=jsonb_set(datos,'{vigas,cortado}','\"SI\"'::jsonb) WHERE id=?",
        inspeccionId);
    var calculo = confirmaciones.consultar(inspeccionId).calculo();
    assertThat(calculo.indiceCalculado()).isEqualByComparingTo("75");
    assertThat(calculo.estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
    assertThat(calculo.condicionesAnulacion()).hasSize(1);
    confirmaciones.confirmar(
        inspeccionId,
        new ConfirmarEstadoIcRequest(
            EstadoCondicion.REGULAR, calculo.hashEntrada(), "Justificacion del hallazgo critico"));
    transiciones.publicar(inspeccionId, autorId);
    comprobarPublicacion("75", "Regular", 2);
    assertThat(
            jdbc.queryForObject(
                "SELECT estado_calculado FROM resultado_ic WHERE inspeccion_id=?",
                String.class,
                inspeccionId))
        .isEqualTo("MALO");
    assertThat(
            jdbc.queryForObject(
                "SELECT jsonb_array_length(condiciones_anulacion) FROM resultado_ic WHERE inspeccion_id=?",
                Integer.class,
                inspeccionId))
        .isEqualTo(1);
  }

  @Test
  void publicarEmiteElEventoQueDisparaElMantenimientoSoloSiTodoSeConfirmo() {
    transiciones.publicar(inspeccionId, autorId);

    assertThat(eventosDePublicacion.recibidos)
        .containsExactly(new InspeccionPublicadaEvent(inspeccionId, autorId));
  }

  @Test
  void unaPublicacionRevertidaNoEmiteElEvento() {
    jdbc.update("DELETE FROM configuracion_ic_elemento WHERE version_formulario_id=?", versionId);

    assertThrows(ApiException.class, () -> transiciones.publicar(inspeccionId, autorId));

    assertThat(eventosDePublicacion.recibidos).isEmpty();
  }

  @Test
  void faltaDePesoRevierteEstadosHistorialResultadoYAuditoria() {
    jdbc.update("DELETE FROM configuracion_ic_elemento WHERE version_formulario_id=?", versionId);
    assertThrows(ApiException.class, () -> transiciones.publicar(inspeccionId, autorId));
    comprobarReversion();
  }

  @Test
  void confirmacionDesactualizadaRevierteLaPublicacionCompleta() {
    var calculo = confirmaciones.consultar(inspeccionId).calculo();
    confirmaciones.confirmar(
        inspeccionId,
        new ConfirmarEstadoIcRequest(
            EstadoCondicion.BUENO, calculo.hashEntrada(), "Criterio inicial"));
    cambiarDano("SEVERO");
    var excepcion =
        assertThrows(ApiException.class, () -> transiciones.publicar(inspeccionId, autorId));
    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    comprobarReversion();
    assertThat(
            numero(
                "SELECT count(*) FROM confirmacion_estado_inspeccion WHERE inspeccion_id=?",
                inspeccionId))
        .isEqualTo(1);
  }

  @Test
  void aprobacionDeEstudianteConservaDecisionDelAutorYAuditaAlRevisor() {
    UUID revisorId = UUID.randomUUID();
    crearUsuario(revisorId, "CATEDRATICO");
    jdbc.update(
        "UPDATE usuario SET rol_id=(SELECT id FROM rol WHERE nombre='ESTUDIANTE') WHERE id=?",
        autorId);
    jdbc.update("UPDATE inspeccion SET rol_autor_creacion='ESTUDIANTE' WHERE id=?", inspeccionId);
    autenticar(autorId, RoleName.ESTUDIANTE);
    var calculo = confirmaciones.consultar(inspeccionId).calculo();
    confirmaciones.confirmar(
        inspeccionId,
        new ConfirmarEstadoIcRequest(
            EstadoCondicion.BUENO, calculo.hashEntrada(), "Decision del autor"));
    transiciones.enviar(inspeccionId, autorId);
    autenticar(revisorId, RoleName.CATEDRATICO);
    transiciones.iniciarRevision(inspeccionId, revisorId);
    transiciones.aprobar(inspeccionId, revisorId);
    assertThat(eventosDePublicacion.recibidos)
        .containsExactly(new InspeccionPublicadaEvent(inspeccionId, revisorId));
    comprobarPublicacion("50", "Bueno", 3);
    var fila =
        jdbc.queryForMap(
            "SELECT confirmado_por_id,solicitado_por_id FROM resultado_ic WHERE inspeccion_id=?",
            inspeccionId);
    assertThat(fila.get("confirmado_por_id")).isEqualTo(autorId);
    assertThat(fila.get("solicitado_por_id")).isEqualTo(revisorId);
    assertThat(
            jdbc.queryForObject(
                "SELECT usuario_id FROM auditoria WHERE entidad='puente' AND entidad_id=?",
                UUID.class,
                puenteId))
        .isEqualTo(revisorId);
  }

  private void comprobarPublicacion(String indice, String estado, int transicionesEsperadas) {
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM inspeccion WHERE id=?", String.class, inspeccionId))
        .isEqualTo("PUBLICADA");
    assertThat(
            numero(
                "SELECT count(*) FROM historial_estado_inspeccion WHERE inspeccion_id=?",
                inspeccionId))
        .isEqualTo(transicionesEsperadas);
    assertThat(numero("SELECT count(*) FROM resultado_ic WHERE inspeccion_id=?", inspeccionId))
        .isEqualTo(1);
    var puente =
        jdbc.queryForMap(
            "SELECT estado_actual,indice_condicion_actual,fecha_ultima_inspeccion,inspeccion_vigente_id FROM puente WHERE id=?",
            puenteId);
    assertThat(puente.get("estado_actual")).isEqualTo(estado);
    assertThat((BigDecimal) puente.get("indice_condicion_actual")).isEqualByComparingTo(indice);
    assertThat(
            jdbc.queryForObject(
                "SELECT fecha_ultima_inspeccion FROM puente WHERE id=?", LocalDate.class, puenteId))
        .isEqualTo(LocalDate.of(2026, 10, 16));
    assertThat(puente.get("inspeccion_vigente_id")).isEqualTo(inspeccionId);
    assertThat(
            jdbc.queryForObject(
                "SELECT version_algoritmo FROM resultado_ic WHERE inspeccion_id=?",
                String.class,
                inspeccionId))
        .isEqualTo(ALGORITMO);
    assertThat(
            jdbc.queryForObject(
                "SELECT version_configuracion_id FROM resultado_ic WHERE inspeccion_id=?",
                UUID.class,
                inspeccionId))
        .isEqualTo(versionId);
    assertThat(
            jdbc.queryForObject(
                "SELECT jsonb_array_length(desglose->'desglose') FROM resultado_ic WHERE inspeccion_id=?",
                Integer.class,
                inspeccionId))
        .isEqualTo(1);
  }

  private void comprobarReversion() {
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM inspeccion WHERE id=?", String.class, inspeccionId))
        .isEqualTo("BORRADOR");
    assertThat(
            jdbc.queryForObject(
                "SELECT publicado_en FROM inspeccion WHERE id=?",
                OffsetDateTime.class,
                inspeccionId))
        .isNull();
    assertThat(
            numero(
                "SELECT count(*) FROM historial_estado_inspeccion WHERE inspeccion_id=?",
                inspeccionId))
        .isZero();
    assertThat(numero("SELECT count(*) FROM resultado_ic WHERE inspeccion_id=?", inspeccionId))
        .isZero();
    assertThat(
            numero(
                "SELECT count(*) FROM auditoria WHERE entidad='inspeccion' AND entidad_id=?",
                inspeccionId))
        .isZero();
    assertThat(
            numero(
                "SELECT count(*) FROM auditoria WHERE entidad='puente' AND entidad_id=?", puenteId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT estado_actual FROM puente WHERE id=?", String.class, puenteId))
        .isEqualTo("Sin evaluar");
    assertThat(
            jdbc.queryForObject(
                "SELECT indice_condicion_actual FROM puente WHERE id=?",
                BigDecimal.class,
                puenteId))
        .isNull();
  }

  private void cambiarDano(String severidad) {
    jdbc.update(
        "UPDATE inspeccion SET datos=jsonb_set(datos,'{vigas,dano}',to_jsonb(CAST(? AS text))) WHERE id=?",
        severidad,
        inspeccionId);
  }

  private int numero(String sql, UUID id) {
    return jdbc.queryForObject(sql, Integer.class, id);
  }

  private void crearUsuario(UUID id, String rol) {
    jdbc.update(
        """
        INSERT INTO usuario (id,email,password_hash,rol_id,verificado,activado,activo,nombre_completo)
        SELECT ?,?,'hash-prueba',id,true,true,true,'Evaluador de prueba' FROM rol WHERE nombre=?
        """,
        id,
        id + "@ejemplo.com",
        rol);
  }

  private void autenticar(UUID id, RoleName rol) {
    var sesion =
        new UsernamePasswordAuthenticationToken(
            id.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    sesion.setDetails(new JwtData(id + "@ejemplo.com", id, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(sesion);
  }

  @TestConfiguration
  @EnableMethodSecurity
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
    EventosDePublicacion eventosDePublicacion() {
      return new EventosDePublicacion();
    }

    @Bean
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }
  }

  /** Guarda los eventos de publicación para comprobar quién los recibiría. */
  static class EventosDePublicacion {
    final List<InspeccionPublicadaEvent> recibidos = new CopyOnWriteArrayList<>();

    @EventListener
    public void alPublicarse(InspeccionPublicadaEvent evento) {
      recibidos.add(evento);
    }
  }
}
