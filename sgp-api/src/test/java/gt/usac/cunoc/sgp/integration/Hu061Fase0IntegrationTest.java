package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.controller.InspeccionController;
import gt.usac.cunoc.sgp.inspeccion.controller.TransicionInspeccionController;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionRequest;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapperImpl;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionDatoTecnicoRepository;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.service.InspeccionService;
import gt.usac.cunoc.sgp.inspeccion.service.PublicacionIndiceCondicionService;
import gt.usac.cunoc.sgp.inspeccion.service.ReglasEvidenciaFotografica;
import gt.usac.cunoc.sgp.inspeccion.service.TransicionInspeccionService;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapperImpl;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.sync.dto.PrepararSalidaRequest;
import gt.usac.cunoc.sgp.sync.http.IdempotenciaHttpFilter;
import gt.usac.cunoc.sgp.sync.repository.OperacionHttpRepository;
import gt.usac.cunoc.sgp.sync.repository.PreparacionSalidaRepository;
import gt.usac.cunoc.sgp.sync.service.IdempotenciaHttpService;
import gt.usac.cunoc.sgp.sync.service.OperacionIdempotenteService;
import gt.usac.cunoc.sgp.sync.service.PreparacionSalidaService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import gt.usac.cunoc.sgp.usuario.repository.UsuarioProfesionalRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Caracterización de fase 0: filtro, controladores y servicios de idempotencia reales sobre PostGIS
 * efímero. No ejecuta el filtro JWT: la identidad se establece explícitamente en la prueba.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  IdempotenciaHttpService.class,
  OperacionHttpRepository.class,
  OperacionIdempotenteService.class,
  TransicionInspeccionService.class,
  ReglasEvidenciaFotografica.class,
  JsonSchemaValidatorService.class,
  InspeccionMapperImpl.class,
  AuditoriaWriter.class,
  PreparacionSalidaService.class,
  PreparacionSalidaRepository.class,
  PuenteMapperImpl.class,
  Hu061Fase0IntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class Hu061Fase0IntegrationTest {
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("hu061_fase0_test")
          .withUsername("hu061_test")
          .withPassword("hu061_test");

  @DynamicPropertySource
  static void baseAislada(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.url", postgres::getJdbcUrl);
    r.add("spring.flyway.user", postgres::getUsername);
    r.add("spring.flyway.password", postgres::getPassword);
  }

  @TestConfiguration
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-24T18:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper json() {
      return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    InspeccionService inspeccionService(
        InspeccionRepository inspecciones,
        InspeccionDatoTecnicoRepository datos,
        PuenteRepository puentes,
        VersionFormularioRepository formularios,
        UserAccountRepository usuarios,
        UsuarioProfesionalRepository profesionales,
        OperacionIdempotenteService operaciones,
        AuditoriaWriter auditoria,
        ObjectMapper json,
        Clock clock) {
      return new InspeccionService(
          inspecciones,
          datos,
          puentes,
          formularios,
          usuarios,
          profesionales,
          operaciones,
          auditoria,
          json,
          clock);
    }
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired InspeccionService inspecciones;
  @Autowired TransicionInspeccionService transiciones;
  @Autowired IdempotenciaHttpService idempotenciaHttp;
  @Autowired PreparacionSalidaService salidas;
  @Autowired PlatformTransactionManager tm;

  // Publicar/IC no se invocan en estos escenarios; el circuito auditado E10 permanece real.
  @MockBean PublicacionIndiceCondicionService publicacionIc;

  MockMvc conFiltro;
  MockMvc sinFiltro;
  UUID autor;
  UUID puente;
  UUID formulario;

  @BeforeEach
  void preparar() {
    autor = usuario("CATEDRATICO");
    UUID municipio =
        jdbc.queryForObject("select id from municipio order by codigo_ine limit 1", UUID.class);
    puente = UUID.randomUUID();
    int correlativo =
        jdbc.queryForObject(
            "select coalesce(max(correlativo_municipal),0)+1 from puente where municipio_id=?",
            Integer.class,
            municipio);
    jdbc.update(
        "insert into puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id) "
            + "values (?,?,?,?,'Puente fase 0','RN-1',ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,?)",
        puente,
        "F0-" + puente.toString().substring(0, 8),
        correlativo,
        municipio,
        autor);
    formulario = versionActiva();
    var controlador = new InspeccionController(inspecciones);
    var transicion = new TransicionInspeccionController(transiciones);
    sinFiltro =
        MockMvcBuilders.standaloneSetup(controlador, transicion)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    conFiltro =
        MockMvcBuilders.standaloneSetup(controlador, transicion)
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new IdempotenciaHttpFilter(idempotenciaHttp, json, tm))
            .build();
  }

  @AfterEach
  void limpiarIdentidad() {
    SecurityContextHolder.clearContext();
    verifyNoInteractions(publicacionIc);
  }

  UUID usuario(String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into usuario(id,email,password_hash,rol_id,verificado,activado,activo) "
            + "select ?,?,'test',id,true,true,true from rol where nombre=?",
        id,
        id + "@test.example",
        rol);
    return id;
  }

  UUID versionActiva() {
    UUID id = UUID.randomUUID();
    jdbc.update("update version_formulario set activa=false where activa");
    jdbc.update(
        "insert into version_formulario(id,codigo,estado,activa,esquema,hash_contenido,"
            + "version_algoritmo_ic,creado_por_id,publicado_por_id,publicado_en) "
            + "values (?,?,'PUBLICADA',true,'{\"type\":\"object\"}',?,'fase0',?,?,CURRENT_TIMESTAMP)",
        id,
        "fase0-" + id,
        "a".repeat(64),
        autor,
        autor);
    return id;
  }

  IniciarInspeccionRequest solicitud(UUID id) {
    return new IniciarInspeccionRequest(
        id,
        puente,
        LocalDate.of(2026, 10, 20),
        "fase0-equipo",
        "Autor fase 0",
        null,
        null,
        null,
        false,
        null);
  }

  MvcResult pedir(MockMvc mvc, String ruta, Object cuerpo, String clave, UUID actor, RoleName rol)
      throws Exception {
    var auth =
        new UsernamePasswordAuthenticationToken(
            actor + "@test.example", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    auth.setDetails(new JwtData(actor + "@test.example", actor, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
    var request = post(ruta).servletPath(ruta).principal(auth);
    if (cuerpo != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo));
    if (clave != null) request.header("Idempotency-Key", clave);
    return mvc.perform(request).andReturn();
  }

  @Test
  void controlCrearSinFiltroConservaUuidYPermiteReplayDeServicio() throws Exception {
    UUID id = UUID.randomUUID();
    var request = solicitud(id);
    var primera =
        pedir(
            sinFiltro,
            "/api/v1/inspecciones",
            request,
            "solo-servicio",
            autor,
            RoleName.CATEDRATICO);
    var repetida =
        pedir(
            sinFiltro,
            "/api/v1/inspecciones",
            request,
            "solo-servicio",
            autor,
            RoleName.CATEDRATICO);
    assertThat(primera.getResponse().getStatus()).isEqualTo(201);
    assertThat(repetida.getResponse().getStatus()).isEqualTo(201);
    assertThat(json.readTree(primera.getResponse().getContentAsString()).path("id").asText())
        .isEqualTo(id.toString());
    assertThat(jdbc.queryForObject("select count(*) from inspeccion where id=?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void e10NoSeReproduceCrearConFiltroYReplayUsaUnaSolaReserva() throws Exception {
    UUID id = UUID.randomUUID();
    var request = solicitud(id);
    var primera =
        pedir(
            conFiltro,
            "/api/v1/inspecciones",
            request,
            "creacion-doble",
            autor,
            RoleName.CATEDRATICO);
    assertThat(primera.getResponse().getStatus()).isEqualTo(201);
    assertThat(primera.getRequest().getAttribute("operacionIdempotenteId")).isNotNull();
    assertThat(jdbc.queryForObject("select count(*) from inspeccion where id=?", Integer.class, id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForMap(
                "select entidad,estado,codigo_respuesta from operacion_idempotente where usuario_id=? and clave=?",
                autor,
                "creacion-doble"))
        .containsEntry("entidad", "http_hu043")
        .containsEntry("estado", "COMPLETADA")
        .containsEntry("codigo_respuesta", 201);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from operacion_idempotente where usuario_id=? and clave=?",
                Integer.class,
                autor,
                "creacion-doble"))
        .isEqualTo(1);
    var repetida =
        pedir(
            conFiltro,
            "/api/v1/inspecciones",
            request,
            "creacion-doble",
            autor,
            RoleName.CATEDRATICO);
    assertThat(repetida.getResponse().getHeader("Idempotent-Replay")).isEqualTo("true");
    assertThat(repetida.getResponse().getContentAsByteArray())
        .isEqualTo(primera.getResponse().getContentAsByteArray());
  }

  @ParameterizedTest
  @ValueSource(strings = {"enviar", "reenviar"})
  void e10NoSeReproduceEnvioIncompletoAlcanzaValidacion(String accion) throws Exception {
    UUID estudiante = usuario("ESTUDIANTE");
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into inspeccion(id,puente_id,version_formulario_id,autor_id,rol_autor_creacion,nombre_autor_capturado,estado,fecha_inspeccion,dispositivo_id,datos) "
            + "values (?,?,?,?,'ESTUDIANTE','Autor fase 0','BORRADOR','2026-10-20','fase0','{}')",
        id,
        puente,
        formulario,
        estudiante);
    String ruta = "/api/v1/inspecciones/" + id + "/" + accion;
    var control = pedir(sinFiltro, ruta, null, null, estudiante, RoleName.ESTUDIANTE);
    assertThat(control.getResponse().getStatus()).isEqualTo(422);
    assertThat(json.readTree(control.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("inspeccion_incompleta");
    var respuesta = pedir(conFiltro, ruta, null, "envio-doble", estudiante, RoleName.ESTUDIANTE);
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(422);
    assertThat(json.readTree(respuesta.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("inspeccion_incompleta");
    assertThat(jdbc.queryForObject("select estado from inspeccion where id=?", String.class, id))
        .isEqualTo("BORRADOR");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from historial_estado_inspeccion where inspeccion_id=?",
                Integer.class,
                id))
        .isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"enviar", "reenviar"})
  void e10NoSeReproduceEnvioCompletoYReplaySoloTransicionanUnaVez(String accion) throws Exception {
    UUID estudiante = usuario("ESTUDIANTE");
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into inspeccion(id,puente_id,version_formulario_id,autor_id,rol_autor_creacion,nombre_autor_capturado,estado,fecha_inspeccion,dispositivo_id,datos) "
            + "values (?,?,?,?,'ESTUDIANTE','Autor fase 0','BORRADOR','2026-10-20','fase0','{}')",
        id,
        puente,
        formulario,
        estudiante);
    // Fixture de evidencia disponible, siguiendo PublicacionInspeccionIntegrationTest.
    for (String categoria : List.of("ACCESO", "SUPERESTRUCTURA", "SUBESTRUCTURA", "CAUCE")) {
      UUID archivo = UUID.randomUUID();
      jdbc.update(
          """
          insert into archivo(id,tipo_uso,categoria_foto,nombre_almacenado,
            clave_objeto_original,clave_objeto_miniatura,mime_real,tamano_bytes,
            checksum_sha256,ancho_px,alto_px,ubicacion_captura,capturado_en,
            subido_por_id,confirmado_en)
          values (?,'FOTO_INSPECCION',?,?,?,?,'image/jpeg',100,?,10,10,
            ST_SetSRID(ST_MakePoint(-90,14),4326)::geography,now(),?,now())
          """,
          archivo,
          categoria,
          archivo + ".jpg",
          "fotos/" + archivo + ".jpg",
          "miniaturas/" + archivo + ".jpg",
          "c".repeat(64),
          estudiante);
      jdbc.update(
          "insert into archivo_inspeccion(archivo_id,tipo_uso,inspeccion_id) values (?,'FOTO_INSPECCION',?)",
          archivo,
          id);
    }
    String ruta = "/api/v1/inspecciones/" + id + "/" + accion;
    var primera = pedir(conFiltro, ruta, null, "envio-valido", estudiante, RoleName.ESTUDIANTE);
    var repetida = pedir(conFiltro, ruta, null, "envio-valido", estudiante, RoleName.ESTUDIANTE);
    assertThat(primera.getResponse().getStatus()).isEqualTo(200);
    assertThat(repetida.getResponse().getStatus()).isEqualTo(200);
    assertThat(repetida.getResponse().getHeader("Idempotent-Replay")).isEqualTo("true");
    assertThat(repetida.getResponse().getContentAsByteArray())
        .isEqualTo(primera.getResponse().getContentAsByteArray());
    assertThat(jdbc.queryForObject("select estado from inspeccion where id=?", String.class, id))
        .isEqualTo("ENVIADA");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from historial_estado_inspeccion where inspeccion_id=?",
                Integer.class,
                id))
        .isEqualTo(1);
  }

  @Test
  void preparacionConservaVersionPeroCrearUsaLaActivaPosterior() throws Exception {
    var preparada =
        salidas.preparar(autor, new PrepararSalidaRequest("fase0-equipo", List.of(puente)));
    assertThat(preparada.paquete().formulario().id()).isEqualTo(formulario);
    assertThat(preparada.paquete().puentes().getFirst().id()).isEqualTo(puente);
    UUID posterior = versionActiva();
    UUID id = UUID.randomUUID();
    // Sin clave/filtro, para aislar el contrato de versión del conflicto E10 ya reproducido.
    var respuesta =
        pedir(sinFiltro, "/api/v1/inspecciones", solicitud(id), null, autor, RoleName.CATEDRATICO);
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(201);
    assertThat(
            json.readTree(respuesta.getResponse().getContentAsString())
                .path("versionFormularioId")
                .asText())
        .isEqualTo(posterior.toString());
    assertThat(
            jdbc.queryForObject(
                "select version_formulario_id from inspeccion where id=?", UUID.class, id))
        .isEqualTo(posterior)
        .isNotEqualTo(formulario);
    assertThat(
            jdbc.queryForObject(
                "select version_formulario_id from preparacion_salida where id=?",
                UUID.class,
                preparada.id()))
        .isEqualTo(formulario);
  }

  @Test
  void identificadorCompuestoLocalNoEsUnUuidAceptadoPorElContratoHttp() throws Exception {
    var cuerpo = json.valueToTree(solicitud(UUID.randomUUID()));
    ((com.fasterxml.jackson.databind.node.ObjectNode) cuerpo)
        .put("puenteId", "salida:" + autor + ":" + puente);
    var respuesta =
        pedir(
            conFiltro, "/api/v1/inspecciones", cuerpo, "puente-local", autor, RoleName.CATEDRATICO);
    assertThat(respuesta.getResponse().getStatus()).isEqualTo(400);
    assertThat(json.readTree(respuesta.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("validation_error");
  }
}
