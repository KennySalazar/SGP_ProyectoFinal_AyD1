package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.aspect.AuditoriaAspect;
import gt.usac.cunoc.sgp.common.audit.mapper.AuditoriaMapperImpl;
import gt.usac.cunoc.sgp.common.audit.repository.AuditoriaRepository;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaConsultaService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.dto.ActualizarPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.CrearPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.PuenteResponse;
import gt.usac.cunoc.sgp.puente.exception.CercaniaPuenteException;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapperImpl;
import gt.usac.cunoc.sgp.puente.service.CatalogoTerritorialService;
import gt.usac.cunoc.sgp.puente.service.PuenteAuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
  AuditoriaAspect.class,
  AuditoriaWriter.class,
  AuditoriaSnapshotService.class,
  AuditoriaConsultaService.class,
  AuditoriaMapperImpl.class,
  PuenteAuditoriaSnapshotProvider.class,
  PuenteService.class,
  PuenteMapperImpl.class,
  CatalogoTerritorialService.class,
  PuenteEdicionIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@WithMockUser(roles = "ADMINISTRADOR")
@Testcontainers
class PuenteEdicionIntegrationTest {

  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000003");

  private static final Instant AHORA = Instant.parse("2026-10-02T23:00:00Z");

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_edicion_test")
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

  @Autowired private PuenteService puenteService;

  @Autowired private AuditoriaRepository auditoriaRepository;

  @Autowired private JdbcTemplate jdbc;

  private UUID departamentoId;
  private UUID municipioId;

  @BeforeEach
  void prepararDatos() {
    jdbc.update("DELETE FROM auditoria");
    jdbc.update("DELETE FROM puente");
    jdbc.update("UPDATE municipio SET ultimo_correlativo_puente = 0");

    jdbc.update(
        """
            INSERT INTO usuario (
              id, email, password_hash, rol_id,
              verificado, activado, activo, nombre_completo
            )
            SELECT ?, 'admin.edicion@ejemplo.com', 'hash-de-prueba', id,
                   true, true, true, 'Administrador de prueba'
            FROM rol
            WHERE nombre = 'ADMINISTRADOR'
            ON CONFLICT (id) DO NOTHING
            """,
        ADMINISTRADOR_ID);

    departamentoId =
        jdbc.queryForObject("SELECT id FROM departamento WHERE codigo_ine = '01'", UUID.class);
    municipioId =
        jdbc.queryForObject("SELECT id FROM municipio WHERE codigo_ine = '0114'", UUID.class);

    autenticar(ADMINISTRADOR_ID);
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  private void autenticar(UUID usuarioId) {
    var jwtData = new JwtData("admin.edicion@ejemplo.com", usuarioId, RoleName.ADMINISTRADOR, 1);
    var token =
        new UsernamePasswordAuthenticationToken(
            "admin.edicion@ejemplo.com",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
    token.setDetails(jwtData);
    SecurityContextHolder.getContext().setAuthentication(token);
  }

  @Test
  void edicionExitosaDeDatosGeneralesAuditaCambios() {
    PuenteResponse creado =
        puenteService.registrar(
            new CrearPuenteRequest(
                "Puente Inicial",
                departamentoId,
                municipioId,
                "CA-1 Oriente",
                new BigDecimal("10.500"),
                new BigDecimal("14.481"),
                new BigDecimal("-90.615"),
                false),
            ADMINISTRADOR_ID);

    ActualizarPuenteRequest actualizacion =
        new ActualizarPuenteRequest(
            "Puente Modificado",
            departamentoId,
            municipioId,
            "RN-10",
            new BigDecimal("12.750"),
            new BigDecimal("14.482"),
            new BigDecimal("-90.616"),
            false,
            creado.codigo());

    PuenteResponse actualizado =
        puenteService.actualizar(creado.id(), actualizacion, ADMINISTRADOR_ID);

    assertEquals("Puente Modificado", actualizado.nombre());
    assertEquals("RN-10", actualizado.ruta());
    assertEquals(new BigDecimal("12.750"), actualizado.kilometraje());
    assertEquals(14.482, actualizado.latitud(), 0.0001);
    assertEquals(-90.616, actualizado.longitud(), 0.0001);
    assertEquals(creado.codigo(), actualizado.codigo());

    Map<String, Object> registroAuditoria =
        jdbc.queryForMap(
            """
            SELECT accion, entidad, entidad_id, usuario_id,
                   valores_anteriores::text AS anteriores,
                   valores_posteriores::text AS posteriores
            FROM auditoria
            WHERE entidad_id = ? AND accion = 'MODIFICAR'
            ORDER BY creado_en DESC
            LIMIT 1
            """,
            creado.id());

    assertEquals("MODIFICAR", registroAuditoria.get("accion"));
    assertEquals("puente", registroAuditoria.get("entidad"));
    assertEquals(creado.id(), registroAuditoria.get("entidad_id"));
    assertEquals(ADMINISTRADOR_ID, registroAuditoria.get("usuario_id"));

    String anteriores = (String) registroAuditoria.get("anteriores");
    String posteriores = (String) registroAuditoria.get("posteriores");
    assertThat(anteriores).contains("Puente Inicial").contains("CA-1 Oriente");
    assertThat(posteriores).contains("Puente Modificado").contains("RN-10");
  }

  @Test
  void camposCalculadosNoSonEditables() {
    PuenteResponse creado =
        puenteService.registrar(
            new CrearPuenteRequest(
                "Puente Original",
                departamentoId,
                municipioId,
                "CA-9",
                null,
                new BigDecimal("14.481"),
                new BigDecimal("-90.615"),
                false),
            ADMINISTRADOR_ID);

    PuenteResponse consultado = puenteService.obtenerPorId(creado.id());

    assertEquals("Sin evaluar", consultado.estadoActual());
    assertNull(consultado.indiceCondicionActual());
    assertNull(consultado.fechaUltimaInspeccion());

    ActualizarPuenteRequest actualizacion =
        new ActualizarPuenteRequest(
            "Puente Editado",
            departamentoId,
            municipioId,
            "CA-9 Norte",
            new BigDecimal("5.000"),
            new BigDecimal("14.481"),
            new BigDecimal("-90.615"),
            false,
            null);

    PuenteResponse actualizado =
        puenteService.actualizar(creado.id(), actualizacion, ADMINISTRADOR_ID);

    assertEquals("Sin evaluar", actualizado.estadoActual());
    assertNull(actualizado.indiceCondicionActual());
    assertNull(actualizado.fechaUltimaInspeccion());
  }

  @Test
  void rechazaModificacionDeCodigoInmutable() {
    PuenteResponse creado =
        puenteService.registrar(
            new CrearPuenteRequest(
                "Puente Codigo Fijo",
                departamentoId,
                municipioId,
                "CA-2",
                null,
                new BigDecimal("14.481"),
                new BigDecimal("-90.615"),
                false),
            ADMINISTRADOR_ID);

    ActualizarPuenteRequest intentoAlteracion =
        new ActualizarPuenteRequest(
            "Puente Codigo Fijo",
            departamentoId,
            municipioId,
            "CA-2",
            null,
            new BigDecimal("14.481"),
            new BigDecimal("-90.615"),
            false,
            "GT-99-9999-9999");

    ApiException exception =
        assertThrows(
            ApiException.class,
            () -> puenteService.actualizar(creado.id(), intentoAlteracion, ADMINISTRADOR_ID));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
    assertEquals("codigo_inmutable", exception.getCode());

    String codigoEnDb =
        jdbc.queryForObject("SELECT codigo FROM puente WHERE id = ?", String.class, creado.id());
    assertEquals(creado.codigo(), codigoEnDb);
  }

  @Test
  void edicionDeCoordenadasRevalidaCercaniaConOtrosPuentes() {
    PuenteResponse puenteExistente =
        puenteService.registrar(
            new CrearPuenteRequest(
                "Puente Vecino",
                departamentoId,
                municipioId,
                "CA-1",
                null,
                new BigDecimal("14.4810"),
                new BigDecimal("-90.6150"),
                false),
            ADMINISTRADOR_ID);

    PuenteResponse puenteAEditar =
        puenteService.registrar(
            new CrearPuenteRequest(
                "Puente Lejano",
                departamentoId,
                municipioId,
                "CA-1",
                null,
                new BigDecimal("14.4850"),
                new BigDecimal("-90.6150"),
                false),
            ADMINISTRADOR_ID);

    ActualizarPuenteRequest acercamientoSinConfirmar =
        new ActualizarPuenteRequest(
            "Puente Lejano Movido",
            departamentoId,
            municipioId,
            "CA-1",
            null,
            new BigDecimal("14.4811"),
            new BigDecimal("-90.6150"),
            false,
            puenteAEditar.codigo());

    assertThrows(
        CercaniaPuenteException.class,
        () ->
            puenteService.actualizar(
                puenteAEditar.id(), acercamientoSinConfirmar, ADMINISTRADOR_ID));

    ActualizarPuenteRequest acercamientoConfirmado =
        new ActualizarPuenteRequest(
            "Puente Lejano Movido",
            departamentoId,
            municipioId,
            "CA-1",
            null,
            new BigDecimal("14.4811"),
            new BigDecimal("-90.6150"),
            true,
            puenteAEditar.codigo());

    PuenteResponse actualizado =
        puenteService.actualizar(puenteAEditar.id(), acercamientoConfirmado, ADMINISTRADOR_ID);

    assertEquals(14.4811, actualizado.latitud(), 0.0001);
    assertNotNull(actualizado.utm());
  }

  @Test
  void noDetectaCercaniaConsigoMismoAlEditar() {
    PuenteResponse creado =
        puenteService.registrar(
            new CrearPuenteRequest(
                "Puente Solitario",
                departamentoId,
                municipioId,
                "CA-1",
                null,
                new BigDecimal("14.481"),
                new BigDecimal("-90.615"),
                false),
            ADMINISTRADOR_ID);

    ActualizarPuenteRequest sinCambioCoordenadas =
        new ActualizarPuenteRequest(
            "Puente Solitario Renombrado",
            departamentoId,
            municipioId,
            "CA-1",
            null,
            new BigDecimal("14.481"),
            new BigDecimal("-90.615"),
            false,
            creado.codigo());

    PuenteResponse actualizado =
        puenteService.actualizar(creado.id(), sinCambioCoordenadas, ADMINISTRADOR_ID);

    assertEquals("Puente Solitario Renombrado", actualizado.nombre());
  }

  @Test
  void rechazaMunicipioDeOtroDepartamentoAlEditar() {
    PuenteResponse creado =
        puenteService.registrar(
            new CrearPuenteRequest(
                "Puente Territorio",
                departamentoId,
                municipioId,
                "CA-1",
                null,
                new BigDecimal("14.481"),
                new BigDecimal("-90.615"),
                false),
            ADMINISTRADOR_ID);

    UUID otroDepartamento =
        jdbc.queryForObject("SELECT id FROM departamento WHERE codigo_ine = '20'", UUID.class);

    ActualizarPuenteRequest incongruente =
        new ActualizarPuenteRequest(
            "Puente Territorio",
            otroDepartamento,
            municipioId,
            "CA-1",
            null,
            new BigDecimal("14.481"),
            new BigDecimal("-90.615"),
            false,
            creado.codigo());

    ApiException exception =
        assertThrows(
            ApiException.class,
            () -> puenteService.actualizar(creado.id(), incongruente, ADMINISTRADOR_ID));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
    assertEquals("municipio_departamento_incongruente", exception.getCode());
  }

  @TestConfiguration
  @EnableMethodSecurity
  @EnableAspectJAutoProxy(proxyTargetClass = true)
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
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }
  }
}
