package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.controller.RecalculoIcController;
import gt.usac.cunoc.sgp.inspeccion.dto.ResultadoIcHistorialResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.ResultadoIc;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.repository.ResultadoIcRepository;
import gt.usac.cunoc.sgp.inspeccion.service.*;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
  RecalculoIcService.class,
  RecalculoIcController.class,
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
  RecalculoIcIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class RecalculoIcIntegrationTest {
  private static final Instant AHORA = Instant.parse("2026-10-24T20:00:00Z");
  private static final String ALGORITMO = "ic-hu071-prueba-v1";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu071")
          .withUsername("sgp")
          .withPassword("sgp");

  @DynamicPropertySource
  static void configurar(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.url", postgres::getJdbcUrl);
    r.add("spring.flyway.user", postgres::getUsername);
    r.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private RecalculoIcService servicio;
  @Autowired private RecalculoIcController controller;
  @Autowired private PreparadorEntradaIcService preparador;
  @Autowired private CalculoIndiceCondicionService calculador;
  @Autowired private ResultadoIcRepository resultados;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PlatformTransactionManager transactionManager;
  private UUID docente,
      administrador,
      puente,
      inspeccion,
      versionOriginal,
      versionNueva,
      originalId;
  private JsonNode catalogo;
  private String esquema;
  private MockMvc mvc;

  @BeforeEach
  void preparar() throws Exception {
    jdbc.execute("TRUNCATE usuario, version_formulario CASCADE");
    docente = usuario(RoleName.CATEDRATICO, "Kenny Salazar");
    administrador = usuario(RoleName.ADMINISTRADOR, "Administrador");
    autenticar(docente, RoleName.CATEDRATICO);
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    catalogo = cargar("catalogo");
    esquema = cargar("esquema").toString();
    versionOriginal = version("v1", false, 1);
    versionNueva = version("v2", true, 3);
    puente = UUID.randomUUID();
    jdbc.update(
        """
                INSERT INTO puente (id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id)
                SELECT ?,?,1,id,'Puente HU071','CA-1',ST_SetSRID(ST_MakePoint(-90.615,14.481),4326)::geography,?
                FROM municipio WHERE codigo_ine='0114'
                """,
        puente,
        "IT-" + puente,
        administrador);
    inspeccion = UUID.randomUUID();
    JsonNode datos = cargar("datos");
    jdbc.update(
        """
                INSERT INTO inspeccion (id,puente_id,version_formulario_id,autor_id,rol_autor_creacion,
                  nombre_autor_capturado,estado,fecha_inspeccion,dispositivo_id,datos,publicado_en)
                VALUES (?,?,?,?,'CATEDRATICO','Kenny Salazar','PUBLICADA','2026-10-20','equipo-hu071',?::jsonb,?)
                """,
        inspeccion,
        puente,
        versionOriginal,
        docente,
        datos.toString(),
        OffsetDateTime.ofInstant(AHORA.minusSeconds(3600), ZoneOffset.UTC));
    var calculo = calculador.calcular(preparador.preparar(versionOriginal, datos));
    originalId = UUID.randomUUID();
    var original =
        new ResultadoIc(
            originalId,
            inspeccion,
            versionOriginal,
            calculo.indiceCalculado(),
            calculo.estadoPorRango(),
            calculo.estadoCalculado(),
            json.valueToTree(calculo.condicionesAnulacion()),
            json.valueToTree(calculo),
            calculo.hashEntrada(),
            calculo.versionAlgoritmo(),
            docente,
            AHORA.minusSeconds(3600),
            EstadoCondicion.BUENO,
            "Criterio profesional original",
            docente,
            AHORA.minusSeconds(3700));
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(s -> resultados.saveAndFlush(original));
    jdbc.update(
        """
                UPDATE puente SET estado_actual='Bueno', indice_condicion_actual=?, fecha_ultima_inspeccion='2026-10-20',
                  inspeccion_vigente_id=?, resultado_ic_vigente_id=?, inspeccion_vigente_publicada_en=? WHERE id=?
                """,
        calculo.indiceCalculado(),
        inspeccion,
        originalId,
        OffsetDateTime.ofInstant(AHORA.minusSeconds(3600), ZoneOffset.UTC),
        puente);
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void recalculaConPesosNuevosYConservaOriginalInspeccionYPuente() throws Exception {
    String antesOriginal = snapshot("resultado_ic", originalId);
    String antesPuente = snapshot("puente", puente);
    String antesInspeccion = snapshot("inspeccion", inspeccion);
    var recalculo = servicio.recalcular(inspeccion, docente);
    assertThat(recalculo.indiceCalculado()).isEqualByComparingTo("25");
    assertThat(recalculo.tipo()).isEqualTo("RECALCULO");
    assertThat(recalculo.versionConfiguracionId()).isEqualTo(versionNueva);
    assertThat(recalculo.versionConfiguracionCodigo()).isEqualTo("v2");
    assertThat(recalculo.solicitadoPorId()).isEqualTo(docente);
    assertThat(recalculo.solicitadoPorNombre()).isEqualTo("Kenny Salazar");
    assertThat(recalculo.calculadoEn()).isEqualTo(AHORA);
    assertThat(snapshot("resultado_ic", originalId)).isEqualTo(antesOriginal);
    assertThat(snapshot("puente", puente)).isEqualTo(antesPuente);
    assertThat(snapshot("inspeccion", inspeccion)).isEqualTo(antesInspeccion);
    var historial = servicio.historial(inspeccion);
    assertThat(historial.original().indiceCalculado()).isEqualByComparingTo("50");
    assertThat(historial.original().estadoConfirmado()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(historial.original().justificacionAjuste())
        .isEqualTo("Criterio profesional original");
    assertThat(historial.recalculos()).hasSize(1);
    assertThat(historial.recalculos().get(0).id()).isEqualTo(recalculo.id());
    var bitacora =
        jdbc.queryForMap(
            "SELECT usuario_id, accion, valores_posteriores::text AS datos FROM auditoria WHERE entidad='resultado_ic'");
    assertThat(bitacora.get("usuario_id")).isEqualTo(docente);
    assertThat(bitacora.get("accion")).isEqualTo("CREAR");
    assertThat(json.readTree((String) bitacora.get("datos")).path("inspeccionId").asText())
        .isEqualTo(inspeccion.toString());
    assertThat(
            json.readTree((String) bitacora.get("datos")).path("versionConfiguracionId").asText())
        .isEqualTo(versionNueva.toString());
  }

  @Test
  void repetidoConOtroUsuarioDevuelveMismoIdFechaYSolicitante() {
    var primero = servicio.recalcular(inspeccion, docente);
    autenticar(administrador, RoleName.ADMINISTRADOR);
    var segundo = servicio.recalcular(inspeccion, administrador);
    assertThat(segundo.id()).isEqualTo(primero.id());
    assertThat(segundo.solicitadoPorId()).isEqualTo(docente);
    assertThat(segundo.calculadoEn()).isEqualTo(primero.calculadoEn());
    assertThat(recalculos()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad='resultado_ic'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void simultaneosDevuelvenUnSoloResultadoConUnaSolaAuditoria() throws Exception {
    var listos = new CountDownLatch(2);
    var inicio = new CountDownLatch(1);
    ExecutorService ejecutor = Executors.newFixedThreadPool(2);
    try {
      List<Future<ResultadoIcHistorialResponse>> tareas = new ArrayList<>();
      for (UUID usuario : List.of(docente, administrador))
        tareas.add(
            ejecutor.submit(
                () -> {
                  autenticar(
                      usuario,
                      usuario.equals(docente) ? RoleName.CATEDRATICO : RoleName.ADMINISTRADOR);
                  listos.countDown();
                  try {
                    if (!inicio.await(10, TimeUnit.SECONDS))
                      throw new IllegalStateException("Falta inicio concurrente");
                    return servicio.recalcular(inspeccion, usuario);
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
      inicio.countDown();
      var primero = tareas.get(0).get(20, TimeUnit.SECONDS);
      var segundo = tareas.get(1).get(20, TimeUnit.SECONDS);
      assertThat(segundo.id()).isEqualTo(primero.id());
      assertThat(segundo.solicitadoPorId()).isEqualTo(primero.solicitadoPorId());
      assertThat(recalculos()).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM auditoria WHERE entidad='resultado_ic'", Integer.class))
          .isEqualTo(1);
    } finally {
      inicio.countDown();
      ejecutor.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORRADOR", "ENVIADA", "EN_REVISION", "CAMBIOS_SOLICITADOS", "RECHAZADA"})
  void noPublicadaRespondeConflictoSinGuardar(String estado) throws Exception {
    jdbc.update("UPDATE inspeccion SET estado=?, publicado_en=NULL WHERE id=?", estado, inspeccion);
    mvc.perform(
            post(base() + "/recalculos-ic").principal(autenticar(docente, RoleName.CATEDRATICO)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("inspeccion_no_publicada"));
    assertThat(recalculos()).isZero();
  }

  @Test
  void configuracionSinPesosResponde422SinGuardar() throws Exception {
    jdbc.update(
        "DELETE FROM configuracion_ic_elemento WHERE version_formulario_id=?", versionNueva);
    mvc.perform(
            post(base() + "/recalculos-ic").principal(autenticar(docente, RoleName.CATEDRATICO)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("configuracion_ic_incompleta"));
    assertThat(recalculos()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad='resultado_ic'", Integer.class))
        .isZero();
  }

  @Test
  void sinVersionActivaNoGuardaNada() {
    jdbc.update("UPDATE version_formulario SET activa=false");
    assertThatThrownBy(() -> servicio.recalcular(inspeccion, docente))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    assertThat(recalculos()).isZero();
  }

  @Test
  void nuevaVersionProduceOtroRegistroAunqueElValorCoincida() {
    var primero = servicio.recalcular(inspeccion, docente);
    jdbc.update("UPDATE version_formulario SET activa=false");
    UUID tercera = version("v3", true, 3);
    var segundo = servicio.recalcular(inspeccion, docente);
    assertThat(segundo.id()).isNotEqualTo(primero.id());
    assertThat(segundo.versionConfiguracionId()).isEqualTo(tercera);
    assertThat(segundo.indiceCalculado()).isEqualByComparingTo(primero.indiceCalculado());
    var historial = servicio.historial(inspeccion);
    assertThat(historial.original().id()).isEqualTo(originalId);
    assertThat(historial.recalculos()).hasSize(2);
  }

  @Test
  void laMigracionImpideDuplicadosInclusoPorSqlDirecto() {
    var primero = servicio.recalcular(inspeccion, docente);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version='24' AND success",
                Integer.class))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO resultado_ic (id,inspeccion_id,version_configuracion_id,tipo,indice_calculado,
                      estado_por_rango,estado_calculado,estado_confirmado,desglose,hash_entrada,version_algoritmo,solicitado_por_id)
                    SELECT ?,inspeccion_id,version_configuracion_id,tipo,indice_calculado,estado_por_rango,
                      estado_calculado,estado_confirmado,desglose,hash_entrada,version_algoritmo,solicitado_por_id
                    FROM resultado_ic WHERE id=?
                    """,
                    UUID.randomUUID(),
                    primero.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(recalculos()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(RoleName.class)
  void autorizacionPorRolConPersistenciaReal(RoleName rol) throws Exception {
    UUID actor = usuario(rol, "Usuario de prueba");
    var peticion = mvc.perform(post(base() + "/recalculos-ic").principal(autenticar(actor, rol)));
    if (rol == RoleName.CATEDRATICO || rol == RoleName.ADMINISTRADOR) {
      peticion.andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("RECALCULO"));
      assertThat(recalculos()).isEqualTo(1);
    } else {
      peticion.andExpect(status().isForbidden());
      assertThat(recalculos()).isZero();
    }
  }

  @Test
  void historialHttpEstaFechadoRotuladoYNoRecalcula() throws Exception {
    servicio.recalcular(inspeccion, docente);
    jdbc.update(
        "DELETE FROM configuracion_ic_elemento WHERE version_formulario_id=?", versionNueva);
    mvc.perform(
            get(base() + "/resultados-ic")
                .principal(autenticar(administrador, RoleName.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.original.id").value(originalId.toString()))
        .andExpect(jsonPath("$.original.tipo").value("PUBLICACION"))
        .andExpect(jsonPath("$.recalculos[0].tipo").value("RECALCULO"))
        .andExpect(jsonPath("$.recalculos[0].rotulo").value("Recalculo del IC · 24/10/2026 14:00"))
        .andExpect(jsonPath("$.recalculos[0].versionConfiguracionCodigo").value("v2"));
    assertThat(recalculos()).isEqualTo(1);
  }

  @Test
  void falloDeAuditoriaRevierteElRecalculo() {
    jdbc.execute(
        "ALTER TABLE auditoria ADD CONSTRAINT hu071_auditoria_prueba CHECK (entidad <> 'resultado_ic')");
    try {
      assertThatThrownBy(() -> servicio.recalcular(inspeccion, docente))
          .isInstanceOf(RuntimeException.class);
      assertThat(recalculos()).isZero();
    } finally {
      jdbc.execute("ALTER TABLE auditoria DROP CONSTRAINT hu071_auditoria_prueba");
    }
  }

  private JsonNode cargar(String nombre) throws Exception {
    try (var entrada =
        new org.springframework.core.io.ClassPathResource("inspeccion/hu071/" + nombre + ".json")
            .getInputStream()) {
      return json.readTree(entrada);
    }
  }

  private String base() {
    return "/api/v1/inspecciones/" + inspeccion;
  }

  private int recalculos() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM resultado_ic WHERE inspeccion_id=? AND tipo='RECALCULO'",
        Integer.class,
        inspeccion);
  }

  private String snapshot(String tabla, UUID id) {
    return jdbc.queryForObject(
        "SELECT to_jsonb(t)::text FROM " + tabla + " t WHERE id=?", String.class, id);
  }

  private UUID usuario(RoleName rol, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario (id,email,password_hash,rol_id,verificado,activado,activo,nombre_completo) SELECT ?,?,'hash-prueba',id,true,true,true,? FROM rol WHERE nombre=?",
        id,
        id + "@ejemplo.com",
        nombre,
        rol.name());
    return id;
  }

  private UUID version(String codigo, boolean activa, int pesoViga) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
                INSERT INTO version_formulario (id,codigo,estado,activa,esquema,reglas_evaluacion_ic,hash_contenido,
                  version_algoritmo_ic,creado_por_id,publicado_por_id,publicado_en)
                VALUES (?,?,'PUBLICADA',?,?::jsonb,?::jsonb,?,?,?, ?,CURRENT_TIMESTAMP)
                """,
        id,
        codigo,
        activa,
        esquema,
        catalogo.toString(),
        "a".repeat(64),
        ALGORITMO,
        administrador,
        administrador);
    for (String ref : List.of("/vigas", "/losa"))
      jdbc.update(
          "INSERT INTO configuracion_ic_elemento (id,version_formulario_id,elemento_ref,peso,descripcion) VALUES (?,?,?,?,'Peso HU071')",
          UUID.randomUUID(),
          id,
          ref,
          ref.equals("/vigas") ? pesoViga : 1);
    return id;
  }

  private UsernamePasswordAuthenticationToken autenticar(UUID id, RoleName rol) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            id.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    auth.setDetails(new JwtData(id + "@ejemplo.com", id, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
    return auth;
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
  }
}
