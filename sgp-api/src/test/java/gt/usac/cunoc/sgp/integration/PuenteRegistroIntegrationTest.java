package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.puente.dto.CrearPuenteRequest;
import gt.usac.cunoc.sgp.puente.exception.CercaniaPuenteException;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapperImpl;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.puente.service.CatalogoTerritorialService;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
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
  PuenteService.class,
  PuenteMapperImpl.class,
  CatalogoTerritorialService.class,
  PuenteRegistroIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@WithMockUser(roles = "ADMINISTRADOR")
@Testcontainers
class PuenteRegistroIntegrationTest {

  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000003");

  private static final Instant AHORA = Instant.parse("2026-10-02T23:00:00Z");

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_hu009_test")
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

  @Autowired private PuenteRepository puenteRepository;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private CatalogoTerritorialService catalogoTerritorialService;

  private UUID departamentoId;
  private UUID municipioId;

  @BeforeEach
  void prepararDatos() {
    jdbc.update("DELETE FROM puente");

    jdbc.update(
        """
            UPDATE municipio
            SET ultimo_correlativo_puente = 0
            """);

    jdbc.update(
        """
            INSERT INTO usuario (
              id, email, password_hash, rol_id,
              verificado, activado, activo, nombre_completo
            )
            SELECT ?, 'admin.hu009@ejemplo.com', 'hash-de-prueba', id,
                   true, true, true, 'Administrador de prueba HU009'
            FROM rol
            WHERE nombre = 'ADMINISTRADOR'
            ON CONFLICT (id) DO NOTHING
            """,
        ADMINISTRADOR_ID);

    departamentoId =
        jdbc.queryForObject("SELECT id FROM departamento WHERE codigo_ine = '01'", UUID.class);

    municipioId =
        jdbc.queryForObject("SELECT id FROM municipio WHERE codigo_ine = '0114'", UUID.class);
  }

  @Test
  void registraDatosMinimosYGuardaUbicacionWgs84() {
    var response =
        puenteService.registrar(
            solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);

    assertEquals("GT-01-0114-0001", response.codigo());
    assertTrue(response.activo());
    assertEquals("Sin evaluar", response.estadoActual());
    assertNull(response.indiceCondicionActual());
    assertNull(response.fechaUltimaInspeccion());
    assertNull(response.kilometraje());
    assertEquals(7, response.id().version());
    assertEquals(AHORA, response.creadoEn().toInstant());
    assertEquals(ZoneOffset.ofHours(-6), response.creadoEn().getOffset());

    assertEquals(1L, puenteRepository.count());
    assertEquals(1, correlativoActual());

    assertEquals(
        ADMINISTRADOR_ID,
        jdbc.queryForObject(
            "SELECT creado_por_id FROM puente WHERE id = ?", UUID.class, response.id()));

    assertEquals(
        4326,
        jdbc.queryForObject(
            "SELECT ST_SRID(ubicacion::geometry) FROM puente WHERE id = ?",
            Integer.class,
            response.id()));

    assertEquals(
        -90.615,
        jdbc.queryForObject(
            "SELECT ST_X(ubicacion::geometry) FROM puente WHERE id = ?",
            Double.class,
            response.id()),
        0.0000001);

    assertEquals(
        14.481,
        jdbc.queryForObject(
            "SELECT ST_Y(ubicacion::geometry) FROM puente WHERE id = ?",
            Double.class,
            response.id()),
        0.0000001);

    assertEquals(
        0L,
        jdbc.queryForObject(
            "SELECT count(*) FROM inspeccion WHERE puente_id = ?", Long.class, response.id()));
  }

  @Test
  void rechazaMunicipioDeOtroDepartamentoSinConsumirCorrelativo() {
    UUID otroDepartamento =
        jdbc.queryForObject("SELECT id FROM departamento WHERE codigo_ine = '20'", UUID.class);

    ApiException exception =
        assertThrows(
            ApiException.class,
            () ->
                puenteService.registrar(
                    solicitud(otroDepartamento, "14.481", "-90.615", false), ADMINISTRADOR_ID));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
    assertEquals("municipio_departamento_incongruente", exception.getCode());
    assertEquals(0L, puenteRepository.count());
    assertEquals(0, correlativoActual());
  }

  @Test
  void advierteCercaniaYPermiteRegistrarAlConfirmar() {
    var primero =
        puenteService.registrar(
            solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);

    var cercanos = puenteRepository.findCercanos(14.4811, -90.615, PageRequest.of(0, 100));

    assertEquals(1L, cercanos.getTotalElements());
    assertEquals(primero.id(), cercanos.getContent().getFirst().getId());

    double distancia = cercanos.getContent().getFirst().getDistanciaMetros();

    assertTrue(distancia > 10 && distancia < 12);

    assertThrows(
        CercaniaPuenteException.class,
        () ->
            puenteService.registrar(
                solicitud(departamentoId, "14.4811", "-90.615", false), ADMINISTRADOR_ID));

    assertEquals(1L, puenteRepository.count());
    assertEquals(1, correlativoActual());

    var segundo =
        puenteService.registrar(
            solicitud(departamentoId, "14.4811", "-90.615", true), ADMINISTRADOR_ID);

    assertEquals("GT-01-0114-0002", segundo.codigo());
    assertNotEquals(primero.id(), segundo.id());
    assertTrue(segundo.activo());
    assertEquals("Sin evaluar", segundo.estadoActual());
    assertEquals(2L, puenteRepository.count());
    assertEquals(2, correlativoActual());
  }

  @ParameterizedTest
  @CsvSource({"-93.0, 15, 32615", "-87.0, 16, 32616"})
  void convierteUtmEnElMeridianoCentral(double longitud, int zona, int epsg) {
    var utm = puenteRepository.calcularUtm(15.0, longitud);

    assertEquals(zona, utm.getZona().intValue());
    assertEquals(epsg, utm.getEpsg().intValue());
    assertEquals(500000.0, utm.getEste(), 0.01);
    assertTrue(utm.getNorte() > 1650000);
    assertTrue(utm.getNorte() < 1670000);

    Double latitudRecuperada =
        jdbc.queryForObject(
            """
                    SELECT ST_Y(
                      ST_Transform(
                        ST_SetSRID(ST_MakePoint(?, ?), ?),
                        4326
                      )
                    )
                    """,
            Double.class,
            utm.getEste(),
            utm.getNorte(),
            epsg);

    Double longitudRecuperada =
        jdbc.queryForObject(
            """
                    SELECT ST_X(
                      ST_Transform(
                        ST_SetSRID(ST_MakePoint(?, ?), ?),
                        4326
                      )
                    )
                    """,
            Double.class,
            utm.getEste(),
            utm.getNorte(),
            epsg);

    assertEquals(15.0, latitudRecuperada, 0.0000001);
    assertEquals(longitud, longitudRecuperada, 0.0000001);
  }

  @Test
  void revierteCorrelativoSiFallaLaPersistencia() {
    UUID usuarioInexistente = UUID.fromString("019a0000-0000-7000-8000-000000000099");

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            puenteService.registrar(
                solicitud(departamentoId, "14.481", "-90.615", false), usuarioInexistente));

    assertEquals(0L, puenteRepository.count());
    assertEquals(0, correlativoActual());

    var response =
        puenteService.registrar(
            solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);

    assertEquals("GT-01-0114-0001", response.codigo());
    assertEquals(1, correlativoActual());
  }

  @ParameterizedTest
  @CsvSource({"99.99, true", "100.01, false", "101.0, false"})
  void aplicaLimiteDeCercaniaEnMetros(double distanciaObjetivo, boolean esperaAdvertencia) {
    var primero =
        puenteService.registrar(
            solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);

    var coordenadas =
        jdbc.queryForMap(
            """
                    SELECT ST_Y(punto::geometry) AS latitud,
                           ST_X(punto::geometry) AS longitud,
                           ST_Distance(ubicacion, punto) AS distancia
                    FROM (
                      SELECT ubicacion,
                             ST_Project(ubicacion, ?, radians(90.0)) AS punto
                      FROM puente
                      WHERE id = ?
                    ) q
                    """,
            distanciaObjetivo,
            primero.id());

    double latitud = ((Number) coordenadas.get("latitud")).doubleValue();

    double longitud = ((Number) coordenadas.get("longitud")).doubleValue();

    double distanciaReal = ((Number) coordenadas.get("distancia")).doubleValue();

    assertEquals(distanciaObjetivo, distanciaReal, 0.001);

    var cercanos = puenteRepository.findCercanos(latitud, longitud, PageRequest.of(0, 100));

    assertEquals(esperaAdvertencia, cercanos.hasContent());
    assertEquals(esperaAdvertencia ? 1L : 0L, cercanos.getTotalElements());
  }

  @Test
  void generaCodigosDistintosEnAltasSimultaneas() throws Exception {
    var preparados = new CountDownLatch(2);
    var iniciar = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);

    try {
      var primera = executor.submit(() -> registrarConcurrentemente("14.481", preparados, iniciar));

      var segunda = executor.submit(() -> registrarConcurrentemente("14.501", preparados, iniciar));

      assertTrue(preparados.await(10, TimeUnit.SECONDS));
      iniciar.countDown();

      String primerCodigo = primera.get(30, TimeUnit.SECONDS);
      String segundoCodigo = segunda.get(30, TimeUnit.SECONDS);

      assertNotEquals(primerCodigo, segundoCodigo);

      assertEquals(
          Set.of("GT-01-0114-0001", "GT-01-0114-0002"), Set.of(primerCodigo, segundoCodigo));

      assertEquals(2L, puenteRepository.count());
      assertEquals(2, correlativoActual());

      assertEquals(
          2L, jdbc.queryForObject("SELECT count(DISTINCT codigo) FROM puente", Long.class));
    } finally {
      iniciar.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void rechazaAltaCuandoElCorrelativoEstaAgotado() {
    jdbc.update(
        """
            UPDATE municipio
            SET ultimo_correlativo_puente = 9999
            WHERE id = ?
            """,
        municipioId);

    ApiException exception =
        assertThrows(
            ApiException.class,
            () ->
                puenteService.registrar(
                    solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID));

    assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    assertEquals("correlativo_agotado", exception.getCode());
    assertEquals(0L, puenteRepository.count());
    assertEquals(9999, correlativoActual());
  }

  @Test
  void listaDepartamentosActivosConPaginacion() {
    SecurityContextHolder.clearContext();
    long totalActivos =
        jdbc.queryForObject("SELECT COUNT(*) FROM departamento WHERE activo = true", Long.class);

    var primeraPagina = catalogoTerritorialService.listarDepartamentos(0, 5);

    var segundaPagina = catalogoTerritorialService.listarDepartamentos(1, 5);

    assertThat(primeraPagina.getTotalElements()).isEqualTo(totalActivos);
    assertThat(primeraPagina.getContent()).hasSize(5);
    assertThat(primeraPagina.getNumber()).isZero();
    assertThat(segundaPagina.getNumber()).isEqualTo(1);
    assertThat(segundaPagina.getContent()).hasSize(5);

    var idsPrimeraPagina = primeraPagina.getContent().stream().map(item -> item.id()).toList();

    assertThat(segundaPagina.getContent())
        .allSatisfy(item -> assertThat(idsPrimeraPagina).doesNotContain(item.id()));
  }

  @Test
  void catalogoPublicoExcluyeBajasYFiltraDepartamento() {
    var activo =
        puenteService.registrar(
            solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);
    var inactivo =
        puenteService.registrar(
            solicitud(departamentoId, "14.501", "-90.615", false), ADMINISTRADOR_ID);
    jdbc.update(
        """
        UPDATE puente SET activo = false, inactivado_en = CURRENT_TIMESTAMP,
          inactivado_por_id = ?, motivo_inactivacion = 'Baja de prueba'
        WHERE id = ?
        """,
        ADMINISTRADOR_ID,
        inactivo.id());

    SecurityContextHolder.clearContext();
    var resultado = puenteService.listarCatalogo(departamentoId, "Sin evaluar", 0, 100);
    assertThat(resultado.getTotalElements()).isEqualTo(1);
    var publico = resultado.getContent().getFirst();
    assertThat(publico.id()).isEqualTo(activo.id());
    assertThat(publico.departamento().id()).isEqualTo(departamentoId);
    assertThat(publico.municipio().id()).isEqualTo(municipioId);
    assertThat(publico.latitud()).isEqualTo(14.481);
    assertThat(publico.longitud()).isEqualTo(-90.615);
    assertThat(publico.estadoActual()).isEqualTo("Sin evaluar");
    assertThat(publico.activo()).isTrue();

    UUID otroDepartamento =
        jdbc.queryForObject("SELECT id FROM departamento WHERE codigo_ine = '02'", UUID.class);
    assertThat(puenteService.listarCatalogo(otroDepartamento, null, 0, 20).getContent()).isEmpty();
    assertThat(puenteService.listarCatalogo(null, null, 0, 20).getTotalElements()).isEqualTo(1);
  }

  @Test
  void catalogoPaginaSinRepetirPuentesConElMismoNombre() {
    puenteService.registrar(
        solicitud(departamentoId, "14.481", "-90.615", false), ADMINISTRADOR_ID);
    puenteService.registrar(
        solicitud(departamentoId, "14.501", "-90.615", false), ADMINISTRADOR_ID);
    SecurityContextHolder.clearContext();

    var primera = puenteService.listarCatalogo(null, null, 0, 1);
    var segunda = puenteService.listarCatalogo(null, null, 1, 1);
    assertThat(primera.getTotalElements()).isEqualTo(2);
    assertThat(primera.getTotalPages()).isEqualTo(2);
    assertThat(primera.getContent()).hasSize(1);
    assertThat(segunda.getContent()).hasSize(1);
    assertThat(primera.getContent().getFirst().id())
        .isNotEqualTo(segunda.getContent().getFirst().id());
    assertThat(puenteService.listarCatalogo(null, null, 2, 1).getContent()).isEmpty();
  }

  @Test
  void excluyeDepartamentosInactivosDelCatalogo() {
    long totalActivos =
        jdbc.queryForObject("SELECT COUNT(*) FROM departamento WHERE activo = true", Long.class);

    jdbc.update("UPDATE departamento SET activo = false WHERE id = ?", departamentoId);

    try {
      var resultado = catalogoTerritorialService.listarDepartamentos(0, 100);

      assertThat(resultado.getTotalElements()).isEqualTo(totalActivos - 1);

      assertThat(resultado.getContent()).noneMatch(item -> item.id().equals(departamentoId));
    } finally {
      jdbc.update("UPDATE departamento SET activo = true WHERE id = ?", departamentoId);
    }
  }

  @Test
  void listaSoloMunicipiosActivosDelDepartamentoSeleccionado() {
    long totalMunicipios =
        jdbc.queryForObject(
            """
                    SELECT COUNT(*)
                    FROM municipio
                    WHERE departamento_id = ? AND activo = true
                    """,
            Long.class,
            departamentoId);

    jdbc.update("UPDATE municipio SET activo = false WHERE id = ?", municipioId);

    try {
      var resultado = catalogoTerritorialService.listarMunicipios(departamentoId, 0, 100);

      assertThat(resultado.getTotalElements()).isEqualTo(totalMunicipios - 1);

      assertThat(resultado.getContent()).isNotEmpty();

      assertThat(resultado.getContent())
          .allSatisfy(item -> assertThat(item.departamentoId()).isEqualTo(departamentoId));

      assertThat(resultado.getContent()).noneMatch(item -> item.id().equals(municipioId));
    } finally {
      jdbc.update("UPDATE municipio SET activo = true WHERE id = ?", municipioId);
    }
  }

  @Test
  void rechazaConsultaDeMunicipiosDeDepartamentoInexistente() {
    UUID departamentoInexistente = UUID.randomUUID();

    assertThatThrownBy(
            () -> catalogoTerritorialService.listarMunicipios(departamentoInexistente, 0, 100))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(exception.getCode()).isEqualTo("departamento_no_encontrado");
            });
  }

  @Test
  void rechazaConsultaDeMunicipiosDeDepartamentoInactivo() {
    jdbc.update("UPDATE departamento SET activo = false WHERE id = ?", departamentoId);

    try {
      assertThatThrownBy(() -> catalogoTerritorialService.listarMunicipios(departamentoId, 0, 100))
          .isInstanceOfSatisfying(
              ApiException.class,
              exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception.getCode()).isEqualTo("departamento_no_encontrado");
              });
    } finally {
      jdbc.update("UPDATE departamento SET activo = true WHERE id = ?", departamentoId);
    }
  }

  @ParameterizedTest
  @CsvSource({"-1, 10", "0, 0", "0, 101"})
  void rechazaPaginacionInvalidaEnAmbosCatalogos(int pagina, int tamanio) {
    assertThatThrownBy(() -> catalogoTerritorialService.listarDepartamentos(pagina, tamanio))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(exception.getCode()).isEqualTo("paginacion_invalida");
            });

    assertThatThrownBy(
            () -> catalogoTerritorialService.listarMunicipios(departamentoId, pagina, tamanio))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(exception.getCode()).isEqualTo("paginacion_invalida");
            });
  }

  private String registrarConcurrentemente(
      String latitud, CountDownLatch preparados, CountDownLatch iniciar)
      throws InterruptedException {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "admin.hu009@ejemplo.com",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));

    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    try {
      preparados.countDown();

      if (!iniciar.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException(
            "No se recibio la señal para iniciar las altas simultaneas");
      }

      return puenteService
          .registrar(solicitud(departamentoId, latitud, "-90.615", false), ADMINISTRADOR_ID)
          .codigo();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private CrearPuenteRequest solicitud(
      UUID departamento, String latitud, String longitud, boolean confirmarCercania) {
    return new CrearPuenteRequest(
        "Puente de prueba HU009",
        departamento,
        municipioId,
        "CA-9",
        null,
        new BigDecimal(latitud),
        new BigDecimal(longitud),
        confirmarCercania);
  }

  private int correlativoActual() {
    return jdbc.queryForObject(
        """
            SELECT ultimo_correlativo_puente
            FROM municipio
            WHERE id = ?
            """,
        Integer.class,
        municipioId);
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class Config {

    @Bean
    Clock clock() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Bean
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }
  }
}
