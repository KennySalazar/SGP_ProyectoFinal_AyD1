package gt.usac.cunoc.sgp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.puente.service.UbicacionTerritorialService;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UbicacionTerritorialService.class, UbicacionTerritorialIntegrationTest.Config.class})
@WithMockUser(roles = "ADMINISTRADOR")
@Testcontainers
class UbicacionTerritorialIntegrationTest {

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_ubicacion_test")
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

  @Autowired private UbicacionTerritorialService service;

  @Autowired private JdbcTemplate jdbc;

  @ParameterizedTest
  @CsvSource({
    "14.481, -90.615, 01, 0114, 15N",
    "14.8, -89.55, 20, 2001, 16N",
    "14.844673, -91.521161, 09, 0901, 15N"
  })
  void resuelveTerritorioYZonaUtm(
      double latitud,
      double longitud,
      String codigoDepartamento,
      String codigoMunicipio,
      String zonaUtm) {

    var respuesta = service.resolver(latitud, longitud);

    assertEquals(latitud, respuesta.latitud());
    assertEquals(longitud, respuesta.longitud());
    assertEquals(zonaUtm, respuesta.zonaUtm());
    assertFalse(respuesta.requiereSeleccion());
    assertEquals(1, respuesta.candidatos().size());

    var candidato = respuesta.candidatos().getFirst();

    assertEquals(codigoDepartamento, candidato.departamento().codigoIne());
    assertEquals(codigoMunicipio, candidato.municipio().codigoIne());
    assertEquals(candidato.departamento().id(), candidato.municipio().departamentoId());
  }

  @ParameterizedTest
  @CsvSource({
    "91, -90",
    "-91, -90",
    "14, 181",
    "14, -181",
    "NaN, -90",
    "14, NaN",
    "Infinity, -90",
    "14, -Infinity"
  })
  void rechazaCoordenadasInvalidas(double latitud, double longitud) {
    var excepcion = assertThrows(ApiException.class, () -> service.resolver(latitud, longitud));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, excepcion.getStatus());
    assertEquals("coordenadas_invalidas", excepcion.getCode());
  }

  @Test
  void rechazaPuntoFueraDeGuatemala() {
    var excepcion = assertThrows(ApiException.class, () -> service.resolver(0, 0));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, excepcion.getStatus());
    assertEquals("ubicacion_fuera_de_guatemala", excepcion.getCode());
  }

  @Test
  void excluyeMunicipioInactivo() {
    jdbc.update("UPDATE municipio SET activo = false WHERE codigo_ine = '0114'");

    var excepcion = assertThrows(ApiException.class, () -> service.resolver(14.481, -90.615));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, excepcion.getStatus());
    assertEquals("ubicacion_sin_municipio_activo", excepcion.getCode());
  }

  @Test
  void excluyeDepartamentoInactivo() {
    jdbc.update("UPDATE departamento SET activo = false WHERE codigo_ine = '01'");

    var excepcion = assertThrows(ApiException.class, () -> service.resolver(14.481, -90.615));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, excepcion.getStatus());
    assertEquals("ubicacion_sin_municipio_activo", excepcion.getCode());
  }

  @Test
  void devuelveAmbosCandidatosSobreLimiteCompartido() {
    // Geometrías controladas únicamente para esta prueba.
    // Comparten el meridiano -90.615.
    jdbc.update(
        """
                UPDATE municipio_limite
                SET geometria = ST_Multi(
                    ST_MakeEnvelope(-90.616, 14.480, -90.615, 14.482, 4326)
                )
                WHERE municipio_id = (
                    SELECT id FROM municipio WHERE codigo_ine = '0114'
                )
                """);

    jdbc.update(
        """
                UPDATE municipio_limite
                SET geometria = ST_Multi(
                    ST_MakeEnvelope(-90.615, 14.480, -90.614, 14.482, 4326)
                )
                WHERE municipio_id = (
                    SELECT id FROM municipio WHERE codigo_ine = '0115'
                )
                """);

    var respuesta = service.resolver(14.481, -90.615);

    assertTrue(respuesta.requiereSeleccion());
    assertEquals(2, respuesta.candidatos().size());

    var codigos =
        respuesta.candidatos().stream()
            .map(candidato -> candidato.municipio().codigoIne())
            .collect(Collectors.toSet());

    assertEquals(Set.of("0114", "0115"), codigos);
  }

  @Test
  void consultarUbicacionNoCreaPuentesNiConsumeCorrelativos() {
    Long puentesAntes = jdbc.queryForObject("SELECT count(*) FROM puente", Long.class);

    Long correlativosAntes =
        jdbc.queryForObject("SELECT sum(ultimo_correlativo_puente) FROM municipio", Long.class);

    service.resolver(14.481, -90.615);
    service.resolver(14.844673, -91.521161);

    assertEquals(puentesAntes, jdbc.queryForObject("SELECT count(*) FROM puente", Long.class));

    assertEquals(
        correlativosAntes,
        jdbc.queryForObject("SELECT sum(ultimo_correlativo_puente) FROM municipio", Long.class));
  }

  @Test
  @WithMockUser(roles = "ESTUDIANTE")
  void rechazaConsultaConRolNoAutorizado() {
    assertThrows(AccessDeniedException.class, () -> service.resolver(14.481, -90.615));
  }

  @Test
  @WithMockUser(roles = "PROFESIONAL_EXTERNO")
  void rechazaConsultaAlProfesionalExterno() {
    assertThrows(AccessDeniedException.class, () -> service.resolver(14.481, -90.615));
  }

  @Test
  @WithMockUser(roles = "CATEDRATICO")
  void permiteConsultaAlCatedratico() {
    var respuesta = service.resolver(14.481, -90.615);

    assertFalse(respuesta.candidatos().isEmpty());
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class Config {}
}
