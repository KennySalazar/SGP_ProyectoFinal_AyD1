package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.formulario.mapper.VersionFormularioMapperImpl;
import gt.usac.cunoc.sgp.formulario.service.AprobacionCalibracionService;
import gt.usac.cunoc.sgp.formulario.service.VersionFormularioService;
import gt.usac.cunoc.sgp.formulario.validation.CampoSchemaExtractor;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.mantenimiento.mapper.ReglaMantenimientoMapperImpl;
import gt.usac.cunoc.sgp.mantenimiento.service.ReglaMantenimientoService;
import gt.usac.cunoc.sgp.mantenimiento.service.ValidadorReglaMantenimiento;
import gt.usac.cunoc.sgp.notificacion.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  AvisosAcademicosService.class,
  AvisosAcademicosInitializer.class,
  NotificacionService.class,
  AprobacionCalibracionService.class,
  AuditoriaWriter.class,
  VersionFormularioService.class,
  VersionFormularioMapperImpl.class,
  JsonSchemaValidatorService.class,
  CampoSchemaExtractor.class,
  ReglaMantenimientoService.class,
  ReglaMantenimientoMapperImpl.class,
  ValidadorReglaMantenimiento.class,
  AvisosAcademicosIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class AvisosAcademicosIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_hu056")
          .withUsername("sgp")
          .withPassword("sgp");

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.url", postgres::getJdbcUrl);
    r.add("spring.flyway.user", postgres::getUsername);
    r.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired AvisosAcademicosService avisos;
  @Autowired AvisosAcademicosInitializer inicio;
  @Autowired NotificacionService notificaciones;
  @Autowired AprobacionCalibracionService aprobaciones;
  @Autowired VersionFormularioService formularios;
  @Autowired ReglaMantenimientoService reglas;
  @Autowired PlatformTransactionManager transacciones;
  @Autowired ObjectMapper json;
  UUID docente;
  UUID admin;
  UUID inactivo;
  UUID adminInactivo;
  UUID noActivado;
  UUID estudiante;
  UUID externo;
  UUID version;

  @BeforeEach
  void preparar() {
    jdbc.execute("TRUNCATE version_formulario, usuario CASCADE");
    docente = usuario("CATEDRATICO", true, true);
    admin = usuario("ADMINISTRADOR", true, true);
    inactivo = usuario("CATEDRATICO", false, true);
    adminInactivo = usuario("ADMINISTRADOR", false, true);
    noActivado = usuario("CATEDRATICO", true, false);
    estudiante = usuario("ESTUDIANTE", true, true);
    externo = usuario("PROFESIONAL_EXTERNO", true, true);
    version = version("PUBLICADA");
    autenticar("CATEDRATICO");
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void pendientesSoloParaRolActivoYUnaVezEntreVariasEjecuciones() {
    regla(version, true, null);
    regla(version, true, null);
    for (int i = 0; i < 3; i++) inicio.revisar();
    assertThat(total("CALIBRACION_PENDIENTE", docente)).isEqualTo(1);
    assertThat(total("REGLAS_PENDIENTES_VALIDAR", docente)).isEqualTo(1);
    for (var usuario : List.of(admin, inactivo, adminInactivo, noActivado, estudiante, externo)) {
      assertThat(total(null, usuario)).isZero();
    }
  }

  @Test
  void aprobacionAvisaAdministradoresActivosConTipoExactoSinObservaciones() {
    avisos.calibracionPendiente(version);
    aprobaciones.aprobar(version, docente, "Observacion privada HU056");
    assertThat(total("CALIBRACION_APROBADA", admin)).isEqualTo(1);
    assertThat(total("CALIBRACION_APROBADA", adminInactivo)).isZero();
    assertThat(total("CALIBRACION_APROBADA", docente)).isZero();
    inicio.revisar();
    inicio.revisar();
    assertThat(total("CALIBRACION_APROBADA", admin)).isEqualTo(1);
    var aviso = notificaciones.listar(admin, false, 0, 20).getContent().getFirst();
    assertThat(aviso.tipo()).isEqualTo("CALIBRACION_APROBADA");
    assertThat(aviso.mensaje()).doesNotContain("Observacion privada HU056");
    assertThat(aviso.rutaDestino()).isEqualTo("/admin/formularios/" + version);
  }

  @Test
  void versionNuevaGeneraAvisosNuevosAunqueLosAnterioresEstenLeidos() {
    regla(version, true, null);
    inicio.revisar();
    notificaciones.marcarTodasLeidas(docente);
    var nueva = version("PUBLICADA");
    regla(nueva, true, null);
    inicio.revisar();
    inicio.revisar();
    assertThat(total("CALIBRACION_PENDIENTE", docente)).isEqualTo(2);
    assertThat(total("REGLAS_PENDIENTES_VALIDAR", docente)).isEqualTo(2);
    assertThat(notificaciones.contarNoLeidas(docente)).isEqualTo(2);
  }

  @Test
  void borradorAprobadaYReglasInactivasOValidadasNoProducenPendientes() {
    var borrador = version("BORRADOR");
    avisos.calibracionPendiente(borrador);
    aprobaciones.aprobar(version, docente, null);
    regla(version, false, null);
    regla(version, true, docente);
    inicio.revisar();
    assertThat(total(null, docente)).isZero();
  }

  @Test
  void destinosYLecturaConservanClaveSinRecrearElAviso() {
    regla(version, true, null);
    inicio.revisar();
    var lista = notificaciones.listar(docente, false, 0, 20).getContent();
    assertThat(lista).hasSize(2);
    for (var aviso : lista) {
      assertThat(aviso.mensaje()).doesNotContain("secreto", "umbral", "0.5", "correo@");
      assertThat(aviso.entidadId()).isEqualTo(version);
      String ruta =
          aviso.tipo().equals("CALIBRACION_PENDIENTE")
              ? "/admin/formularios/" + version
              : "/mantenimiento/reglas?versionId=" + version;
      assertThat(aviso.rutaDestino()).isEqualTo(ruta);
      assertThat(json.valueToTree(aviso).path("rutaDestino").asText()).isEqualTo(ruta);
      var leida = notificaciones.marcarLeida(aviso.id(), docente);
      assertThat(leida.leido()).isTrue();
      assertThat(leida.leidoEn()).isNotNull();
      assertThatThrownBy(() -> notificaciones.marcarLeida(aviso.id(), admin))
          .isInstanceOf(gt.usac.cunoc.sgp.common.exception.ApiException.class);
    }
    inicio.revisar();
    assertThat(notificaciones.contarNoLeidas(docente)).isZero();
    assertThat(total(null, docente)).isEqualTo(2);
  }

  @Test
  void siembraYPublicacionRealAvisanSinEsperarOtroArranque() {
    autenticar("ADMINISTRADOR");
    UUID nueva =
        new TransactionTemplate(transacciones)
            .execute(estado -> formularios.sembrarPlantillaInicial("SIECA-HU056", admin).id());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notificacion WHERE tipo = 'CALIBRACION_PENDIENTE' AND entidad_id = ?",
                Integer.class,
                nueva))
        .isEqualTo(1);
    reglas.sembrarCatalogoProvisionalSiNoExiste(nueva, admin);
    reglas.sembrarCatalogoProvisionalSiNoExiste(nueva, admin);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notificacion WHERE tipo = 'REGLAS_PENDIENTES_VALIDAR' AND entidad_id = ?",
                Integer.class,
                nueva))
        .isEqualTo(1);
  }

  @Test
  void eventosConcurrentesNoDuplicanNiDejanTransaccionesFallidas() throws Exception {
    var ejecutor = Executors.newFixedThreadPool(2);
    var barrera = new CyclicBarrier(2);
    try {
      Callable<Boolean> tarea =
          () -> {
            barrera.await(10, TimeUnit.SECONDS);
            avisos.calibracionPendiente(version);
            return true;
          };
      var a = ejecutor.submit(tarea);
      var b = ejecutor.submit(tarea);
      assertThat(a.get(30, TimeUnit.SECONDS)).isTrue();
      assertThat(b.get(30, TimeUnit.SECONDS)).isTrue();
      assertThat(total("CALIBRACION_PENDIENTE", docente)).isEqualTo(1);
    } finally {
      ejecutor.shutdownNow();
    }
  }

  @Test
  void migracionNuevaEstaAplicada() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '23' AND success",
                Integer.class))
        .isEqualTo(1);
  }

  private long total(String tipo, UUID usuario) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM notificacion WHERE usuario_id = ? AND (?::text IS NULL OR tipo = ?)",
        Long.class,
        usuario,
        tipo,
        tipo);
  }

  private UUID usuario(String rol, boolean activo, boolean activado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario (id,email,password_hash,rol_id,verificado,activado,activo,nombre_completo) SELECT ?, ?, 'hash', id, true, ?, ?, 'Docente' FROM rol WHERE nombre = ?",
        id,
        id + "@ejemplo.com",
        activado,
        activo,
        rol);
    return id;
  }

  private UUID version(String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO version_formulario (id,codigo,estado,activa,esquema,reglas_evaluacion_ic,hash_contenido,version_algoritmo_ic,creado_por_id,publicado_por_id,publicado_en) VALUES (?, ?, ?, false, '{}'::jsonb, '{\"calibracion\":{\"provisional\":true,\"secreto\":\"correo@\",\"umbral\":0.5}}'::jsonb, ?, 'ic-v1', ?, ?, CURRENT_TIMESTAMP)",
        id,
        "HU056-" + id,
        estado,
        "a".repeat(64),
        admin,
        admin);
    return id;
  }

  private void regla(UUID v, boolean activa, UUID validador) {
    jdbc.update(
        "INSERT INTO regla_mantenimiento (id,codigo,numero_revision,version_formulario_id,elemento_ref,condicion,tipo_mantenimiento,intervencion_sugerida,regla_prioridad,activa,creado_por_id,validado_por_id,validado_en) VALUES (?, ?, 1, ?, '/superestructura/apoyos', '{}'::jsonb, 'PREVENTIVO', 'Revision', '{}'::jsonb, ?, ?, ?, ?)",
        UUID.randomUUID(),
        "RM-" + UUID.randomUUID(),
        v,
        activa,
        admin,
        validador,
        validador == null ? null : java.sql.Timestamp.from(Instant.now()));
  }

  private void autenticar(String rol) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-24T18:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }
  }
}
