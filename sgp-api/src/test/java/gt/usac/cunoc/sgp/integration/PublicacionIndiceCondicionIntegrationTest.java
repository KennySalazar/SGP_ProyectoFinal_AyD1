package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.service.CalculoIndiceCondicionService;
import gt.usac.cunoc.sgp.inspeccion.service.PreparadorEntradaIcService;
import gt.usac.cunoc.sgp.inspeccion.service.PublicacionIndiceCondicionService;
import gt.usac.cunoc.sgp.puente.dto.ActualizarPuenteRequest;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapperImpl;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
  PublicacionIndiceCondicionService.class,
  PreparadorEntradaIcService.class,
  AuditoriaWriter.class,
  PuenteService.class,
  gt.usac.cunoc.sgp.puente.service.EstadoCalibracionPuenteService.class,
  PuenteMapperImpl.class,
  PublicacionIndiceCondicionIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PublicacionIndiceCondicionIntegrationTest {

  private static final Instant AHORA = Instant.parse("2026-10-14T20:00:00Z");
  private static final String ALGORITMO = "ic-hu020-prueba";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sgp_ic_publicacion_test")
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

  @Autowired private PublicacionIndiceCondicionService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ObjectMapper json;
  @Autowired private PuenteRepository puentes;
  @Autowired private PuenteMapper mapper;
  @Autowired private PuenteService puenteService;
  @Autowired private gt.usac.cunoc.sgp.puente.service.EstadoCalibracionPuenteService respaldo;
  @MockBean private CalculoIndiceCondicionService calculador;

  private UUID usuarioId;
  private UUID puenteId;
  private UUID versionId;
  private UUID inspeccionId;
  private TransactionTemplate transaccion;

  @BeforeEach
  void preparar() {
    usuarioId = UUID.randomUUID();
    puenteId = UUID.randomUUID();
    versionId = UUID.randomUUID();
    inspeccionId = UUID.randomUUID();
    transaccion = new TransactionTemplate(transactionManager);

    jdbc.update(
        """
                INSERT INTO usuario (id, email, password_hash, rol_id,
                  verificado, activado, activo, nombre_completo)
                SELECT ?, ?, 'hash-prueba', id, true, true, true, 'Publicador de prueba'
                FROM rol WHERE nombre = 'ADMINISTRADOR'
                """,
        usuarioId,
        usuarioId + "@ejemplo.com");

    jdbc.update(
        """
                INSERT INTO version_formulario (id, codigo, estado, activa, esquema,
                  reglas_evaluacion_ic, hash_contenido, version_algoritmo_ic,
                  creado_por_id, publicado_por_id, publicado_en)
                VALUES (?, ?, 'PUBLICADA', false, '{"type":"object"}'::jsonb,
                  '{"formulario":"sieca-v1","elementos":[{"elemento_ref":"/vigas"}],
                    "reglas_evaluacion_ic":{"version_algoritmo":"ic-hu020-prueba"}}'::jsonb,
                  ?, ?, ?, ?, ?)
                """,
        versionId,
        "HU020-" + versionId,
        "a".repeat(64),
        ALGORITMO,
        usuarioId,
        usuarioId,
        OffsetDateTime.ofInstant(AHORA.minusSeconds(3600), ZoneOffset.UTC));

    jdbc.update(
        """
                INSERT INTO configuracion_ic_elemento
                  (id, version_formulario_id, elemento_ref, peso, descripcion)
                VALUES (?, ?, '/vigas', 1, 'Peso de prueba')
                """,
        UUID.randomUUID(),
        versionId);

    jdbc.update(
        """
                INSERT INTO puente (id, codigo, correlativo_municipal, municipio_id,
                  nombre, ruta, ubicacion, creado_por_id)
                SELECT ?, ?, ?, id, 'Puente de prueba', 'CA-1',
                  ST_SetSRID(ST_MakePoint(-90.615, 14.481), 4326)::geography, ?
                FROM municipio WHERE codigo_ine = '0114'
                """,
        puenteId,
        "T-" + puenteId,
        siguienteCorrelativo(),
        usuarioId);

    assertThat(numero("SELECT count(*) FROM puente WHERE id = ?", puenteId)).isEqualTo(1);
    crearInspeccion(inspeccionId, LocalDate.of(2026, 10, 14));
    when(calculador.calcular(any())).thenReturn(resultado("80", EstadoCondicion.BUENO, false));
  }

  @Test
  void respaldoDelIcVigenteConservaVersionHistoricaAunqueSePubliqueOtroFormulario() {
    publicar(inspeccionId, AHORA);
    UUID nuevaActiva = UUID.randomUUID();
    jdbc.update("UPDATE version_formulario SET activa = false WHERE activa = true");
    jdbc.update(
        """
                INSERT INTO version_formulario (id, codigo, estado, activa, esquema, reglas_evaluacion_ic,
                  hash_contenido, version_algoritmo_ic, creado_por_id, publicado_por_id, publicado_en)
                SELECT ?, ?, 'PUBLICADA', true, esquema, reglas_evaluacion_ic, hash_contenido,
                  version_algoritmo_ic, creado_por_id, publicado_por_id, CURRENT_TIMESTAMP
                FROM version_formulario WHERE id = ?
                """,
        nuevaActiva,
        "HU054-activa-" + nuevaActiva,
        versionId);
    assertThat(respaldo.consultar(puenteId).versionFormularioId()).isEqualTo(versionId);
    assertThat(respaldo.consultar(puenteId).estado()).isEqualTo("SIN_APROBAR");
    jdbc.update(
        """
                INSERT INTO aprobacion_calibracion_ic (id, version_formulario_id, aprobado_por_id,
                  aprobado_por_nombre, aprobado_en, observaciones)
                VALUES (?, ?, ?, 'Nombre academico', CURRENT_TIMESTAMP, 'Nota privada')
                """,
        UUID.randomUUID(),
        versionId,
        usuarioId);
    var resumen = respaldo.consultar(puenteId);
    assertThat(resumen.estado()).isEqualTo("APROBADA");
    assertThat(resumen.aprobadoPorNombre()).isEqualTo("Nombre academico");
    assertThat(resumen.versionFormularioId()).isEqualTo(versionId).isNotEqualTo(nuevaActiva);
    transaccion.executeWithoutResult(
        estado -> {
          var puente = puentes.findPuenteConRelacionesById(puenteId).orElseThrow();
          assertThat(mapper.toAltaResponse(puente, null).versionFormularioIdIcVigente())
              .isEqualTo(versionId)
              .isNotEqualTo(nuevaActiva);
          var pagina = puentes.findCatalogo(null, true, PageRequest.of(0, 100));
          var fila =
              pagina.getContent().stream()
                  .filter(p -> p.getId().equals(puenteId))
                  .findFirst()
                  .orElseThrow();
          assertThat(mapper.toCatalogoResponse(fila).versionFormularioIdIcVigente())
              .isEqualTo(versionId);
        });
  }

  @Test
  void guardaResultadoDefinitivoActualizaPuenteYAudita() throws Exception {
    UUID resultadoId = publicar(inspeccionId, AHORA);

    assertThat(estadoPuente()).isEqualTo("Bueno");
    assertThat(
            jdbc.queryForObject(
                "SELECT indice_condicion_actual FROM puente WHERE id = ?",
                BigDecimal.class,
                puenteId))
        .isEqualByComparingTo("80");
    assertThat(
            jdbc.queryForObject(
                "SELECT resultado_ic_vigente_id FROM puente WHERE id = ?", UUID.class, puenteId))
        .isEqualTo(resultadoId);
    assertThat(
            jdbc.queryForObject(
                "SELECT fecha_ultima_inspeccion FROM puente WHERE id = ?",
                LocalDate.class,
                puenteId))
        .isEqualTo(LocalDate.of(2026, 10, 14));
    assertThat(
            jdbc.queryForObject(
                "SELECT tipo FROM resultado_ic WHERE id = ?", String.class, resultadoId))
        .isEqualTo("PUBLICACION");
    assertThat(
            jdbc.queryForObject(
                "SELECT version_algoritmo FROM resultado_ic WHERE id = ?",
                String.class,
                resultadoId))
        .isEqualTo(ALGORITMO);
    assertThat(
            jdbc.queryForObject(
                "SELECT version_configuracion_id FROM resultado_ic WHERE id = ?",
                UUID.class,
                resultadoId))
        .isEqualTo(versionId);
    var desglose =
        json.readTree(
            jdbc.queryForObject(
                "SELECT desglose::text FROM resultado_ic WHERE id = ?", String.class, resultadoId));
    assertThat(desglose.get("desglose").size()).isEqualTo(1);

    var registro =
        jdbc.queryForMap(
            """
                    SELECT usuario_id, accion, creado_en, valores_anteriores::text AS anteriores,
                      valores_posteriores::text AS posteriores
                    FROM auditoria WHERE entidad = 'puente' AND entidad_id = ?
                    """,
            puenteId);
    assertThat(registro.get("usuario_id")).isEqualTo(usuarioId);
    assertThat(registro.get("accion")).isEqualTo("MODIFICAR");
    assertThat(registro.get("creado_en")).isNotNull();
    assertThat(json.readTree((String) registro.get("anteriores")).get("estadoActual").asText())
        .isEqualTo("Sin evaluar");
    assertThat(json.readTree((String) registro.get("posteriores")).get("estadoActual").asText())
        .isEqualTo("Bueno");
  }

  @Test
  void condicionCriticaConservaIndiceYFuerzaMalo() throws Exception {
    when(calculador.calcular(any())).thenReturn(resultado("80", EstadoCondicion.MALO, true));
    UUID resultadoId = publicar(inspeccionId, AHORA);
    assertThat(estadoPuente()).isEqualTo("Malo");
    var fila =
        jdbc.queryForMap(
            """
                    SELECT indice_calculado, estado_por_rango, estado_calculado,
                      condiciones_anulacion::text AS condiciones
                    FROM resultado_ic WHERE id = ?
                    """,
            resultadoId);
    assertThat((BigDecimal) fila.get("indice_calculado")).isEqualByComparingTo("80");
    assertThat(fila.get("estado_por_rango")).isEqualTo("BUENO");
    assertThat(fila.get("estado_calculado")).isEqualTo("MALO");
    assertThat(json.readTree((String) fila.get("condiciones")).size()).isEqualTo(1);
  }

  @Test
  void falloDelCalculoReviertePublicacionResultadoPuenteYAuditoria() {
    when(calculador.calcular(any())).thenThrow(new IllegalArgumentException("Fallo de calculo"));
    assertThrows(IllegalArgumentException.class, () -> publicar(inspeccionId, AHORA));
    comprobarReversion();
  }

  @Test
  void falloPosteriorALaIntegracionRevierteTodo() {
    assertThrows(
        IllegalStateException.class,
        () ->
            transaccion.execute(
                status -> {
                  marcarPublicada(inspeccionId, AHORA);
                  service.integrarPublicacion(inspeccionId, usuarioId);
                  throw new IllegalStateException("Fallo despues de guardar");
                }));
    comprobarReversion();
  }

  @Test
  void falloDeAuditoriaRevierteResultadoYPublicacion() {
    assertThrows(
        RuntimeException.class,
        () ->
            transaccion.execute(
                status -> {
                  marcarPublicada(inspeccionId, AHORA);
                  service.integrarPublicacion(inspeccionId, UUID.randomUUID());
                  return null;
                }));
    comprobarReversion();
  }

  @Test
  void publicacionMasRecienteDeterminaEstadoAunqueInspeccionSeaAnterior() {
    UUID primerResultadoId = publicar(inspeccionId, AHORA);
    UUID segunda = UUID.randomUUID();
    crearInspeccion(segunda, LocalDate.of(2026, 9, 1));
    when(calculador.calcular(any())).thenReturn(resultado("60", EstadoCondicion.REGULAR, false));
    publicar(segunda, AHORA.plusSeconds(60));

    assertThat(estadoPuente()).isEqualTo("Regular");
    assertThat(
            jdbc.queryForObject(
                "SELECT inspeccion_vigente_id FROM puente WHERE id = ?", UUID.class, puenteId))
        .isEqualTo(segunda);
    assertThat(
            jdbc.queryForObject(
                "SELECT fecha_ultima_inspeccion FROM puente WHERE id = ?",
                LocalDate.class,
                puenteId))
        .isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(
            jdbc.queryForObject(
                "SELECT indice_calculado FROM resultado_ic WHERE id = ?",
                BigDecimal.class,
                primerResultadoId))
        .isEqualByComparingTo("80");

    UUID antigua = UUID.randomUUID();
    crearInspeccion(antigua, LocalDate.of(2026, 8, 1));
    when(calculador.calcular(any())).thenReturn(resultado("20", EstadoCondicion.MALO, false));
    publicar(antigua, AHORA.minusSeconds(60));
    assertThat(estadoPuente()).isEqualTo("Regular");
    assertThat(numero("SELECT count(*) FROM auditoria WHERE entidad_id = ?", puenteId))
        .isEqualTo(2);
  }

  @Test
  void rechazaDuplicarElResultadoDefinitivo() {
    publicar(inspeccionId, AHORA);
    assertThrows(
        ApiException.class,
        () -> transaccion.execute(status -> service.integrarPublicacion(inspeccionId, usuarioId)));
    assertThat(numero("SELECT count(*) FROM resultado_ic WHERE inspeccion_id = ?", inspeccionId))
        .isEqualTo(1);
  }

  @Test
  void borradorNoCambiaElPuente() {
    assertThrows(
        ApiException.class,
        () -> transaccion.execute(status -> service.integrarPublicacion(inspeccionId, usuarioId)));
    comprobarReversion();
  }

  @Test
  void exigeTransaccionDePublicacion() {
    assertThrows(
        IllegalTransactionStateException.class,
        () -> service.integrarPublicacion(inspeccionId, usuarioId));
    comprobarReversion();
  }

  @ParameterizedTest
  @CsvSource({"Sin evaluar,0", "Bueno,80", "Regular,60", "Malo,20"})
  void mapperYFiltroReflejanElEstadoPersistido(String estado, int indice) {
    if (!"Sin evaluar".equals(estado)) {
      EstadoCondicion condicion =
          switch (estado) {
            case "Bueno" -> EstadoCondicion.BUENO;
            case "Regular" -> EstadoCondicion.REGULAR;
            default -> EstadoCondicion.MALO;
          };
      when(calculador.calcular(any()))
          .thenReturn(resultado(Integer.toString(indice), condicion, false));
      publicar(inspeccionId, AHORA);
    }

    transaccion.execute(
        status -> {
          var puente = puentes.findPuenteConRelacionesById(puenteId).orElseThrow();
          var detalle = mapper.toAltaResponse(puente, null);
          var catalogo = mapper.toCatalogoResponse(puente);
          assertThat(detalle.estadoActual()).isEqualTo(estado);
          assertThat(catalogo.estadoActual()).isEqualTo(estado);
          if ("Sin evaluar".equals(estado)) {
            assertThat(detalle.versionFormularioIdIcVigente()).isNull();
            assertThat(catalogo.versionFormularioIdIcVigente()).isNull();
            assertThat(detalle.indiceCondicionActual()).isNull();
            assertThat(catalogo.indiceCondicionActual()).isNull();
            assertThat(catalogo.fechaUltimaInspeccion()).isNull();
            assertThat(detalle.fechaUltimaInspeccion()).isNull();
          } else {
            assertThat(detalle.versionFormularioIdIcVigente()).isEqualTo(versionId);
            assertThat(catalogo.versionFormularioIdIcVigente()).isEqualTo(versionId);
            assertThat(detalle.indiceCondicionActual())
                .isEqualByComparingTo(Integer.toString(indice));
            assertThat(detalle.fechaUltimaInspeccion()).isEqualTo(LocalDate.of(2026, 10, 14));
            assertThat(catalogo.indiceCondicionActual())
                .isEqualByComparingTo(Integer.toString(indice));
            assertThat(catalogo.fechaUltimaInspeccion()).isEqualTo(LocalDate.of(2026, 10, 14));
          }

          UUID departamentoId = puente.getMunicipio().getDepartamento().getId();
          var pageable =
              PageRequest.of(0, 1, Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id")));
          var pagina = puentes.findCatalogoPorEstado(departamentoId, true, estado, pageable);
          Long totalEsperado =
              jdbc.queryForObject(
                  """
                                      SELECT count(*) FROM puente p
                                      JOIN municipio m ON m.id = p.municipio_id
                                      WHERE m.departamento_id = ? AND p.activo = true AND p.estado_actual = ?
                                      """,
                  Long.class,
                  departamentoId,
                  estado);
          assertThat(pagina.getTotalElements()).isEqualTo(totalEsperado);
          assertThat(pagina.getContent()).hasSize(1);
          assertThat(pagina.getContent())
              .allSatisfy(
                  item ->
                      assertThat(mapper.toCatalogoResponse(item).estadoActual()).isEqualTo(estado));

          var todos =
              puentes.findCatalogoPorEstado(
                  departamentoId, true, estado, org.springframework.data.domain.Pageable.unpaged());
          assertThat(todos.getContent()).extracting(item -> item.getId()).contains(puenteId);
          var otroDepartamento =
              puentes.findCatalogoPorEstado(UUID.randomUUID(), true, estado, pageable);
          assertThat(otroDepartamento.getTotalElements()).isZero();
          return null;
        });
  }

  @Test
  void filtroDeActividadConservaEstadoDePuenteInactivo() {
    publicar(inspeccionId, AHORA);
    jdbc.update(
        """
                        UPDATE puente SET activo = false, inactivado_en = ?, inactivado_por_id = ?,
                          motivo_inactivacion = 'Baja de prueba'
                        WHERE id = ?
                        """,
        OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
        usuarioId,
        puenteId);

    transaccion.execute(
        status -> {
          var puente = puentes.findPuenteConRelacionesById(puenteId).orElseThrow();
          UUID departamentoId = puente.getMunicipio().getDepartamento().getId();
          var pageable = org.springframework.data.domain.Pageable.unpaged();
          var activos = puentes.findCatalogoPorEstado(departamentoId, true, "Bueno", pageable);
          var inactivos = puentes.findCatalogoPorEstado(departamentoId, false, "Bueno", pageable);
          var todos = puentes.findCatalogoPorEstado(departamentoId, null, "Bueno", pageable);
          assertThat(activos.getContent())
              .extracting(item -> item.getId())
              .doesNotContain(puenteId);
          assertThat(inactivos.getContent()).extracting(item -> item.getId()).contains(puenteId);
          assertThat(todos.getContent()).extracting(item -> item.getId()).contains(puenteId);
          assertThat(mapper.toAltaResponse(puente, null).estadoActual()).isEqualTo("Bueno");
          return null;
        });
  }

  @Test
  void editarDatosGeneralesConservaEstadoIcFechaYPublicacionVigente() {
    when(calculador.calcular(any())).thenReturn(resultado("80", EstadoCondicion.MALO, true));
    UUID resultadoId = publicar(inspeccionId, AHORA);
    UUID departamentoId =
        jdbc.queryForObject(
            "SELECT m.departamento_id FROM puente p JOIN municipio m ON m.id = p.municipio_id WHERE p.id = ?",
            UUID.class,
            puenteId);
    UUID municipioId =
        jdbc.queryForObject("SELECT municipio_id FROM puente WHERE id = ?", UUID.class, puenteId);

    var solicitud =
        new ActualizarPuenteRequest(
            "Puente renombrado",
            departamentoId,
            municipioId,
            "RN-10",
            new BigDecimal("12.750"),
            new BigDecimal("14.481"),
            new BigDecimal("-90.615"),
            true,
            null);
    var actualizado = puenteService.actualizar(puenteId, solicitud, usuarioId);

    assertThat(actualizado.nombre()).isEqualTo("Puente renombrado");
    assertThat(actualizado.ruta()).isEqualTo("RN-10");
    assertThat(actualizado.kilometraje()).isEqualByComparingTo("12.750");
    assertThat(actualizado.estadoActual()).isEqualTo("Malo");
    assertThat(actualizado.indiceCondicionActual()).isEqualByComparingTo("80");
    assertThat(actualizado.fechaUltimaInspeccion()).isEqualTo(LocalDate.of(2026, 10, 14));
    comprobarFichaYCatalogo("Malo", "80", LocalDate.of(2026, 10, 14));
    assertThat(
            jdbc.queryForObject(
                "SELECT resultado_ic_vigente_id FROM puente WHERE id = ?", UUID.class, puenteId))
        .isEqualTo(resultadoId);
    assertThat(
            jdbc.queryForObject(
                "SELECT inspeccion_vigente_id FROM puente WHERE id = ?", UUID.class, puenteId))
        .isEqualTo(inspeccionId);
    assertThat(
            jdbc.queryForObject(
                "SELECT indice_calculado FROM resultado_ic WHERE id = ?",
                BigDecimal.class,
                resultadoId))
        .isEqualByComparingTo("80");
  }

  @Test
  void fichaYCatalogoReflejanNuevaPublicacionAlVolverAConsultar() {
    publicar(inspeccionId, AHORA);
    comprobarFichaYCatalogo("Bueno", "80", LocalDate.of(2026, 10, 14));

    UUID segunda = UUID.randomUUID();
    crearInspeccion(segunda, LocalDate.of(2026, 10, 15));
    when(calculador.calcular(any())).thenReturn(resultado("60", EstadoCondicion.REGULAR, false));
    publicar(segunda, AHORA.plusSeconds(60));

    comprobarFichaYCatalogo("Regular", "60", LocalDate.of(2026, 10, 15));
  }

  private void comprobarFichaYCatalogo(String estado, String indice, LocalDate fecha) {
    var ficha = puenteService.obtenerPorId(puenteId);
    assertThat(ficha.estadoActual()).isEqualTo(estado);
    assertThat(ficha.indiceCondicionActual()).isEqualByComparingTo(indice);
    assertThat(ficha.fechaUltimaInspeccion()).isEqualTo(fecha);

    transaccion.execute(
        status -> {
          var puente = puentes.findPuenteConRelacionesById(puenteId).orElseThrow();
          UUID departamentoId = puente.getMunicipio().getDepartamento().getId();
          var pagina = puenteService.listarCatalogo(departamentoId, estado, 0, 100);
          var catalogo =
              pagina.getContent().stream()
                  .filter(item -> item.id().equals(puenteId))
                  .findFirst()
                  .orElseThrow();
          assertThat(catalogo.estadoActual()).isEqualTo(estado);
          assertThat(catalogo.indiceCondicionActual()).isEqualByComparingTo(indice);
          assertThat(catalogo.fechaUltimaInspeccion()).isEqualTo(fecha);
          return null;
        });
  }

  private UUID publicar(UUID id, Instant publicadaEn) {
    return transaccion.execute(
        status -> {
          marcarPublicada(id, publicadaEn);
          return service.integrarPublicacion(id, usuarioId).getId();
        });
  }

  private void marcarPublicada(UUID id, Instant publicadaEn) {
    jdbc.update(
        """
                UPDATE inspeccion SET estado = 'PUBLICADA', publicado_en = ?,
                  actualizado_en = ?, version = version + 1 WHERE id = ?
                """,
        OffsetDateTime.ofInstant(publicadaEn, ZoneOffset.UTC),
        OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
        id);
  }

  private void crearInspeccion(UUID id, LocalDate fecha) {
    jdbc.update(
        """
                INSERT INTO inspeccion (id, puente_id, version_formulario_id, autor_id,
                  rol_autor_creacion, nombre_autor_capturado, fecha_inspeccion, dispositivo_id, datos)
                VALUES (?, ?, ?, ?, 'ADMINISTRADOR', 'Autor de prueba', ?, 'equipo-prueba', '{}'::jsonb)
                """,
        id,
        puenteId,
        versionId,
        usuarioId,
        fecha);
  }

  private int siguienteCorrelativo() {
    return jdbc.queryForObject(
        """
                SELECT COALESCE(MAX(p.correlativo_municipal), 0) + 1
                FROM puente p JOIN municipio m ON m.id = p.municipio_id
                WHERE m.codigo_ine = '0114'
                """,
        Integer.class);
  }

  private ResultadoCalculoIc resultado(String indice, EstadoCondicion estado, boolean critica) {
    return new ResultadoCalculoIc(
        versionId,
        "sieca-v1",
        ALGORITMO,
        "b".repeat(64),
        new BigDecimal(indice),
        new BigDecimal(indice).compareTo(BigDecimal.valueOf(75)) >= 0
            ? EstadoCondicion.BUENO
            : new BigDecimal(indice).compareTo(BigDecimal.valueOf(50)) >= 0
                ? EstadoCondicion.REGULAR
                : EstadoCondicion.MALO,
        estado,
        BigDecimal.ONE,
        BigDecimal.ZERO,
        List.of(
            new ResultadoCalculoIc.DesgloseElemento(
                "/vigas", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, "MAX", List.of())),
        critica
            ? List.of(
                new ResultadoCalculoIc.CondicionAnulacion(
                    "critica-prueba",
                    "Elemento portante cortado",
                    "/vigas",
                    null,
                    "/vigas",
                    "cortado",
                    "true",
                    EstadoCondicion.MALO))
            : List.of());
  }

  private String estadoPuente() {
    return jdbc.queryForObject(
        "SELECT estado_actual FROM puente WHERE id = ?", String.class, puenteId);
  }

  private int numero(String consulta, UUID id) {
    return jdbc.queryForObject(consulta, Integer.class, id);
  }

  private void comprobarReversion() {
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM inspeccion WHERE id = ?", String.class, inspeccionId))
        .isEqualTo("BORRADOR");
    assertThat(estadoPuente()).isEqualTo("Sin evaluar");
    assertThat(
            jdbc.queryForObject(
                "SELECT indice_condicion_actual FROM puente WHERE id = ?",
                BigDecimal.class,
                puenteId))
        .isNull();
    assertThat(numero("SELECT count(*) FROM resultado_ic WHERE inspeccion_id = ?", inspeccionId))
        .isZero();
    assertThat(numero("SELECT count(*) FROM auditoria WHERE entidad_id = ?", puenteId)).isZero();
  }

  @TestConfiguration
  static class Config {
    @Bean
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }

    @Bean
    Clock clock() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
