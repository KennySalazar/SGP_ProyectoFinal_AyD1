package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.aspect.AuditoriaAspect;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.mantenimiento.dto.EditarReglaMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.mapper.ReglaMantenimientoMapperImpl;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.service.ReglaMantenimientoAuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.mantenimiento.service.ReglaMantenimientoService;
import gt.usac.cunoc.sgp.mantenimiento.service.ValidadorReglaMantenimiento;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  ReglaMantenimientoService.class,
  ReglaMantenimientoMapperImpl.class,
  ValidadorReglaMantenimiento.class,
  ReglaMantenimientoAuditoriaSnapshotProvider.class,
  AuditoriaAspect.class,
  AuditoriaSnapshotService.class,
  AuditoriaWriter.class,
  ValidacionReglaMantenimientoIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ValidacionReglaMantenimientoIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu055")
          .withUsername("sgp")
          .withPassword("sgp");

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", postgres::getJdbcUrl);
    registro.add("spring.datasource.username", postgres::getUsername);
    registro.add("spring.datasource.password", postgres::getPassword);
    registro.add("spring.flyway.url", postgres::getJdbcUrl);
    registro.add("spring.flyway.user", postgres::getUsername);
    registro.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ReglaMantenimientoService servicio;
  @Autowired private ObjectMapper mapper;
  private UUID version;
  private UUID docente;
  private UUID otro;
  private UUID regla;
  private static final Instant AHORA = Instant.parse("2026-10-24T02:30:00Z");

  @BeforeEach
  void preparar() throws Exception {
    jdbc.execute("TRUNCATE version_formulario, usuario CASCADE");
    docente = usuario("Kenny Salazar");
    otro = usuario("Mario Yancor");
    version = UUID.randomUUID();
    String esquema;
    try (var entrada =
        new ClassPathResource("formulario/sieca-v1/esquema.schema.json").getInputStream()) {
      esquema = new String(entrada.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
    jdbc.update(
        "INSERT INTO version_formulario (id,codigo,estado,activa,esquema,reglas_evaluacion_ic,hash_contenido,version_algoritmo_ic,creado_por_id,publicado_por_id,publicado_en) VALUES (?, 'SIECA-HU055', 'PUBLICADA', true, ?::jsonb, '{}'::jsonb, ?, 'ic-v1', ?, ?, CURRENT_TIMESTAMP)",
        version,
        esquema,
        "a".repeat(64),
        docente,
        docente);
    regla = regla(1, true);
    autenticar(docente);
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validaYActualizaResumenLecturasYAuditoriaSinAlterarContenido() {
    var antes = jdbc.queryForMap("SELECT * FROM regla_mantenimiento WHERE id = ?", regla);
    assertThat(servicio.obtenerResumenVersion(version).totalProvisionales()).isEqualTo(1);
    var resultado = servicio.validarRegla(regla, docente);
    assertThat(resultado.validadoPorId()).isEqualTo(docente);
    assertThat(resultado.validadoEn()).isEqualTo(AHORA);
    assertThat(resultado.validadoPorNombre()).isEqualTo("Kenny Salazar");
    assertThat(resultado.rotuloValidacion()).isEqualTo("Validada por Kenny Salazar el 23/10/2026");
    assertThat(servicio.obtenerPorId(regla)).isEqualTo(resultado);
    assertThat(servicio.listar(version, null, null, true, PageRequest.of(0, 20)).getContent())
        .containsExactly(resultado);
    var resumen = servicio.obtenerResumenVersion(version);
    assertThat(resumen.totalProvisionales()).isZero();
    assertThat(resumen.totalValidadas()).isEqualTo(1);
    var despues = jdbc.queryForMap("SELECT * FROM regla_mantenimiento WHERE id = ?", regla);
    antes.remove("validado_por_id");
    antes.remove("validado_en");
    despues.remove("validado_por_id");
    despues.remove("validado_en");
    assertThat(despues).isEqualTo(antes);
    var auditoria =
        jdbc.queryForMap(
            "SELECT * FROM auditoria WHERE entidad = 'regla_mantenimiento' AND entidad_id = ?",
            regla);
    assertThat(auditoria.get("usuario_id")).isEqualTo(docente);
    assertThat(mapper.valueToTree(resultado).path("rotuloValidacion").asText())
        .isEqualTo(resultado.rotuloValidacion());
    try {
      var previos = mapper.readTree(auditoria.get("valores_anteriores").toString());
      var posteriores = mapper.readTree(auditoria.get("valores_posteriores").toString());
      assertThat(previos.path("validadoPorId").isNull()).isTrue();
      assertThat(posteriores.path("validadoPorId").asText()).isEqualTo(docente.toString());
      assertThat(posteriores.path("validadoEn").isNull()).isFalse();
    } catch (java.io.IOException ex) {
      throw new AssertionError(ex);
    }
  }

  @Test
  void reintentoNoCambiaValidacionNiDuplicaAuditoria() {
    var original = servicio.validarRegla(regla, docente);
    autenticar(otro);
    assertThat(servicio.validarRegla(regla, otro)).isEqualTo(original);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'regla_mantenimiento' AND entidad_id = ?",
                Integer.class,
                regla))
        .isEqualTo(1);
  }

  @Test
  void revisionAnteriorInactivaRecibeConflicto() {
    jdbc.update("UPDATE regla_mantenimiento SET activa = false WHERE id = ?", regla);
    regla(2, true);
    assertThatThrownBy(() -> servicio.validarRegla(regla, docente))
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    assertThat(
            jdbc.queryForObject(
                "SELECT validado_por_id FROM regla_mantenimiento WHERE id = ?", UUID.class, regla))
        .isNull();
  }

  @Test
  void editarReglaValidadaGeneraRevisionSinValidarYConservaHistorial() throws Exception {
    servicio.validarRegla(regla, docente);
    var request =
        new EditarReglaMantenimientoRequest(
            "/superestructura/elemento_portante_acero",
            mapper.readTree(
                "{\"indicador\":\"pintura\",\"operador\":\"IGUAL\",\"umbral\":\"PESIMO\"}"),
            TipoOrden.CORRECTIVO,
            "Reemplazo",
            mapper.readTree("{\"prioridad_base\":\"ALTA\"}"),
            true);
    var nueva = servicio.editarRegla(regla, request, docente);
    assertThat(nueva.id()).isNotEqualTo(regla);
    assertThat(nueva.numeroRevision()).isEqualTo(2);
    assertThat(nueva.provisional()).isTrue();
    assertThat(nueva.validadoPorId()).isNull();
    assertThat(nueva.validadoEn()).isNull();
    assertThat(nueva.rotuloValidacion()).isEqualTo("Provisional · sin validar");
    var anterior = servicio.obtenerPorId(regla);
    assertThat(anterior.activa()).isFalse();
    assertThat(anterior.validadoPorId()).isEqualTo(docente);
    assertThatThrownBy(() -> servicio.validarRegla(regla, otro)).isInstanceOf(ApiException.class);
  }

  @Test
  void nombreVacioUsaCorreoEnConsultaYValidacion() {
    jdbc.update("UPDATE usuario SET nombre_completo = '  ' WHERE id = ?", docente);
    var respuesta = servicio.validarRegla(regla, docente);
    assertThat(respuesta.validadoPorNombre()).isEqualTo(docente + "@ejemplo.com");
    assertThat(servicio.obtenerPorId(regla).rotuloValidacion())
        .isEqualTo(respuesta.rotuloValidacion());
  }

  @Test
  void validacionesSimultaneasConservanUnSoloAutorYFecha() throws Exception {
    var executor = Executors.newFixedThreadPool(2);
    var barrera = new CyclicBarrier(2);
    try {
      var primera = executor.submit(() -> validarConcurrente(docente, barrera));
      var segunda = executor.submit(() -> validarConcurrente(otro, barrera));
      var a = primera.get(30, TimeUnit.SECONDS);
      var b = segunda.get(30, TimeUnit.SECONDS);
      assertThat(a.validadoPorId()).isEqualTo(b.validadoPorId()).isIn(docente, otro);
      assertThat(a.validadoEn()).isEqualTo(b.validadoEn());
      assertThat(servicio.obtenerPorId(regla).validadoPorId()).isEqualTo(a.validadoPorId());
    } finally {
      executor.shutdownNow();
    }
  }

  private gt.usac.cunoc.sgp.mantenimiento.dto.ReglaMantenimientoResponse validarConcurrente(
      UUID usuario, CyclicBarrier barrera) throws Exception {
    autenticar(usuario);
    try {
      barrera.await(10, TimeUnit.SECONDS);
      return servicio.validarRegla(regla, usuario);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private UUID regla(int revision, boolean activa) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO regla_mantenimiento (id,codigo,numero_revision,version_formulario_id,elemento_ref,condicion,tipo_mantenimiento,intervencion_sugerida,regla_prioridad,activa,creado_por_id) VALUES (?, 'RM-HU055', ?, ?, '/superestructura/elemento_portante_acero', '{\"indicador\":\"pintura\",\"operador\":\"IGUAL\",\"umbral\":\"MALO\"}'::jsonb, 'PREVENTIVO', 'Pintura', '{\"prioridad_base\":\"MEDIA\"}'::jsonb, ?, ?)",
        id,
        revision,
        version,
        activa,
        docente);
    return id;
  }

  private UUID usuario(String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario (id,email,password_hash,rol_id,verificado,activado,nombre_completo) SELECT ?, ?, 'hash', id, true, true, ? FROM rol WHERE nombre = 'CATEDRATICO'",
        id,
        id + "@ejemplo.com",
        nombre);
    return id;
  }

  private void autenticar(UUID usuario) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "docente", null, List.of(new SimpleGrantedAuthority("ROLE_CATEDRATICO")));
    auth.setDetails(new JwtData(usuario + "@ejemplo.com", usuario, RoleName.CATEDRATICO, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @TestConfiguration
  @EnableAspectJAutoProxy
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
