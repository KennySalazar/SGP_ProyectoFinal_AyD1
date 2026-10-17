package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.service.CalculoIndiceCondicionService;
import gt.usac.cunoc.sgp.inspeccion.service.ConfirmacionEstadoIcService;
import gt.usac.cunoc.sgp.inspeccion.service.PreparadorEntradaIcService;
import gt.usac.cunoc.sgp.inspeccion.service.PublicacionIndiceCondicionService;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapperImpl;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
  ConfirmacionEstadoIcService.class,
  PuenteMapperImpl.class,
  ConfirmacionEstadoIcIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ConfirmacionEstadoIcIntegrationTest {

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

  @Autowired private ConfirmacionEstadoIcService confirmaciones;
  @Autowired private PublicacionIndiceCondicionService publicaciones;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ObjectMapper json;
  @Autowired private PuenteRepository puentes;
  @Autowired private PuenteMapper mapper;
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
    autenticar(usuarioId);
  }

  @AfterEach
  void limpiarSesion() {
    SecurityContextHolder.clearContext();
  }

  private void autenticar(UUID id) {
    var sesion =
        new UsernamePasswordAuthenticationToken(
            "evaluador@ejemplo.com",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
    sesion.setDetails(new JwtData("evaluador@ejemplo.com", id, RoleName.ADMINISTRADOR, 0));
    SecurityContextHolder.getContext().setAuthentication(sesion);
  }

  @Test
  void consultaCalculaSinGuardarConfirmacionNiCambiarPuente() {
    var respuesta = confirmaciones.consultar(inspeccionId);
    assertThat(respuesta.calculo().indiceCalculado()).isEqualByComparingTo("80");
    assertThat(respuesta.confirmacionVigente()).isFalse();
    assertThat(respuesta.estadoConfirmado()).isNull();
    assertThat(
            numero(
                "SELECT count(*) FROM confirmacion_estado_inspeccion WHERE inspeccion_id = ?",
                inspeccionId))
        .isZero();
    assertThat(estadoPuente()).isEqualTo("Sin evaluar");
  }

  @Test
  void confirmaEstadoSinJustificacionYRegistraAutorFechaYAuditoria() {
    var respuesta =
        confirmaciones.confirmar(
            inspeccionId,
            new ConfirmarEstadoIcRequest(EstadoCondicion.BUENO, "b".repeat(64), null));
    assertThat(respuesta.estadoConfirmado()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(respuesta.justificacion()).isNull();
    assertThat(respuesta.realizadoPorId()).isEqualTo(usuarioId);
    assertThat(respuesta.realizadoEn()).isEqualTo(AHORA);
    assertThat(confirmaciones.consultar(inspeccionId).confirmacionVigente()).isTrue();
    assertThat(numero("SELECT count(*) FROM auditoria WHERE entidad_id = ?", inspeccionId))
        .isEqualTo(1);
    assertThat(estadoPuente()).isEqualTo("Sin evaluar");
  }

  @Test
  void ajustaEstadoConservandoValorAutomaticoYDesglose() throws Exception {
    var respuesta =
        confirmaciones.confirmar(
            inspeccionId,
            new ConfirmarEstadoIcRequest(
                EstadoCondicion.REGULAR, "b".repeat(64), "  Criterio profesional documentado  "));
    assertThat(respuesta.calculo().indiceCalculado()).isEqualByComparingTo("80");
    assertThat(respuesta.calculo().estadoCalculado()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(respuesta.estadoConfirmado()).isEqualTo(EstadoCondicion.REGULAR);
    assertThat(respuesta.justificacion()).isEqualTo("Criterio profesional documentado");
    var fila =
        jdbc.queryForMap(
            "SELECT * FROM confirmacion_estado_inspeccion WHERE inspeccion_id = ?", inspeccionId);
    assertThat(fila.get("estado_calculado")).isEqualTo("BUENO");
    assertThat(fila.get("estado_confirmado")).isEqualTo("REGULAR");
    assertThat((BigDecimal) fila.get("indice_calculado")).isEqualByComparingTo("80");
    assertThat(confirmaciones.consultar(inspeccionId).estadoConfirmado())
        .isEqualTo(EstadoCondicion.REGULAR);
    var registro =
        jdbc.queryForMap(
            "SELECT usuario_id, valores_posteriores::text AS posteriores FROM auditoria WHERE entidad_id = ?",
            inspeccionId);
    assertThat(registro.get("usuario_id")).isEqualTo(usuarioId);
    assertThat(json.readTree((String) registro.get("posteriores")).path("justificacion").asText())
        .isEqualTo("Criterio profesional documentado");
    assertThat(estadoPuente()).isEqualTo("Sin evaluar");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n"})
  void rechazaAjusteSinJustificacion(String justificacion) {
    var excepcion =
        assertThrows(
            ApiException.class,
            () ->
                confirmaciones.confirmar(
                    inspeccionId,
                    new ConfirmarEstadoIcRequest(
                        EstadoCondicion.REGULAR, "b".repeat(64), justificacion)));
    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(
            numero(
                "SELECT count(*) FROM confirmacion_estado_inspeccion WHERE inspeccion_id = ?",
                inspeccionId))
        .isZero();
    assertThat(numero("SELECT count(*) FROM auditoria WHERE entidad_id = ?", inspeccionId))
        .isZero();
  }

  @Test
  void condicionCriticaPermiteAjusteJustificadoYConservaCondicion() {
    when(calculador.calcular(any())).thenReturn(resultado("80", EstadoCondicion.MALO, true));
    var respuesta =
        confirmaciones.confirmar(
            inspeccionId,
            new ConfirmarEstadoIcRequest(
                EstadoCondicion.REGULAR,
                "b".repeat(64),
                "Evaluacion profesional del hallazgo critico"));
    assertThat(respuesta.calculo().estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
    assertThat(respuesta.calculo().condicionesAnulacion()).hasSize(1);
    assertThat(respuesta.calculo().indiceCalculado()).isEqualByComparingTo("80");
    assertThat(respuesta.estadoConfirmado()).isEqualTo(EstadoCondicion.REGULAR);
    assertThat(numero("SELECT count(*) FROM auditoria WHERE entidad_id = ?", inspeccionId))
        .isEqualTo(1);
  }

  @Test
  void administradorAjenoAlAutorNoPuedeConsultarNiConfirmar() {
    autenticar(UUID.randomUUID());
    assertThat(
            assertThrows(ApiException.class, () -> confirmaciones.consultar(inspeccionId))
                .getStatus())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            assertThrows(
                    ApiException.class,
                    () ->
                        confirmaciones.confirmar(
                            inspeccionId,
                            new ConfirmarEstadoIcRequest(
                                EstadoCondicion.BUENO, "b".repeat(64), null)))
                .getStatus())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void publicadaRechazaAjusteConConflicto() {
    jdbc.update(
        "UPDATE inspeccion SET estado = 'PUBLICADA', publicado_en = ? WHERE id = ?",
        OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
        inspeccionId);
    var excepcion =
        assertThrows(
            ApiException.class,
            () ->
                confirmaciones.confirmar(
                    inspeccionId,
                    new ConfirmarEstadoIcRequest(
                        EstadoCondicion.REGULAR, "b".repeat(64), "Cambio")));
    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            numero(
                "SELECT count(*) FROM confirmacion_estado_inspeccion WHERE inspeccion_id = ?",
                inspeccionId))
        .isZero();
  }

  @Test
  void rechazaHuellaDesactualizadaSinGuardar() {
    var excepcion =
        assertThrows(
            ApiException.class,
            () ->
                confirmaciones.confirmar(
                    inspeccionId,
                    new ConfirmarEstadoIcRequest(
                        EstadoCondicion.REGULAR, "c".repeat(64), "Cambio")));
    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            numero(
                "SELECT count(*) FROM confirmacion_estado_inspeccion WHERE inspeccion_id = ?",
                inspeccionId))
        .isZero();
  }

  @Test
  void cambioDelCalculoInvalidaConfirmacionAnterior() {
    confirmaciones.confirmar(
        inspeccionId, new ConfirmarEstadoIcRequest(EstadoCondicion.BUENO, "b".repeat(64), null));
    when(calculador.calcular(any())).thenReturn(resultado("60", EstadoCondicion.REGULAR, false));
    var respuesta = confirmaciones.consultar(inspeccionId);
    assertThat(respuesta.confirmacionVigente()).isFalse();
    assertThat(respuesta.estadoConfirmado()).isNull();
    assertThat(respuesta.calculo().indiceCalculado()).isEqualByComparingTo("60");
  }

  @Test
  void volverAlEstadoCalculadoLimpiaJustificacionYAuditaAntesYDespues() throws Exception {
    confirmaciones.confirmar(
        inspeccionId,
        new ConfirmarEstadoIcRequest(
            EstadoCondicion.REGULAR, "b".repeat(64), "Criterio profesional"));
    var respuesta =
        confirmaciones.confirmar(
            inspeccionId,
            new ConfirmarEstadoIcRequest(EstadoCondicion.BUENO, "b".repeat(64), null));
    assertThat(respuesta.justificacion()).isNull();
    var fila =
        jdbc.queryForMap(
            "SELECT valores_anteriores::text AS anteriores, valores_posteriores::text AS posteriores FROM auditoria WHERE entidad_id = ? AND accion = 'MODIFICAR'",
            inspeccionId);
    assertThat(json.readTree((String) fila.get("anteriores")).path("estadoConfirmado").asText())
        .isEqualTo("REGULAR");
    assertThat(json.readTree((String) fila.get("posteriores")).path("estadoConfirmado").asText())
        .isEqualTo("BUENO");
  }

  @Test
  void reversionDeTransaccionNoDejaConfirmacionNiAuditoria() {
    assertThrows(
        IllegalStateException.class,
        () ->
            transaccion.execute(
                status -> {
                  confirmaciones.confirmar(
                      inspeccionId,
                      new ConfirmarEstadoIcRequest(EstadoCondicion.BUENO, "b".repeat(64), null));
                  throw new IllegalStateException("Fallo posterior");
                }));
    assertThat(
            numero(
                "SELECT count(*) FROM confirmacion_estado_inspeccion WHERE inspeccion_id = ?",
                inspeccionId))
        .isZero();
    assertThat(numero("SELECT count(*) FROM auditoria WHERE entidad_id = ?", inspeccionId))
        .isZero();
  }

  @Test
  void exigeSesionConIdentidadJwt() {
    SecurityContextHolder.clearContext();
    assertThat(
            assertThrows(ApiException.class, () -> confirmaciones.consultar(inspeccionId))
                .getStatus())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void publicacionConservaEstadoAutomaticoYGuardaAjusteDefinitivo() {
    confirmaciones.confirmar(
        inspeccionId,
        new ConfirmarEstadoIcRequest(
            EstadoCondicion.REGULAR, "b".repeat(64), "Criterio profesional"));
    var resultado = publicarConDecision();
    assertThat(resultado.getEstadoCalculado()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(resultado.getEstadoConfirmado()).isEqualTo(EstadoCondicion.REGULAR);
    assertThat(resultado.getIndiceCalculado()).isEqualByComparingTo("80");
    assertThat(resultado.getJustificacionAjuste()).isEqualTo("Criterio profesional");
    assertThat(resultado.getConfirmadoPorId()).isEqualTo(usuarioId);
    assertThat(resultado.getConfirmadoEn()).isEqualTo(AHORA);
    assertThat(estadoPuente()).isEqualTo("Regular");
    var fila =
        jdbc.queryForMap(
            "SELECT estado_calculado, estado_confirmado, justificacion_ajuste, confirmado_por_id, confirmado_en, indice_calculado FROM resultado_ic WHERE id = ?",
            resultado.getId());
    assertThat(fila.get("estado_calculado")).isEqualTo("BUENO");
    assertThat(fila.get("estado_confirmado")).isEqualTo("REGULAR");
    assertThat(fila.get("justificacion_ajuste")).isEqualTo("Criterio profesional");
    assertThat(fila.get("confirmado_por_id")).isEqualTo(usuarioId);
    assertThat(fila.get("confirmado_en")).isNotNull();
    assertThat((BigDecimal) fila.get("indice_calculado")).isEqualByComparingTo("80");
    var excepcion =
        assertThrows(
            ApiException.class,
            () ->
                confirmaciones.confirmar(
                    inspeccionId,
                    new ConfirmarEstadoIcRequest(
                        EstadoCondicion.MALO, "b".repeat(64), "Cambio posterior")));
    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void publicacionDeConfirmacionSinAjusteConservaEstadoCalculado() {
    when(calculador.calcular(any())).thenReturn(resultado("60", EstadoCondicion.REGULAR, false));
    confirmaciones.confirmar(
        inspeccionId, new ConfirmarEstadoIcRequest(EstadoCondicion.REGULAR, "b".repeat(64), null));
    var resultado = publicarConDecision();
    assertThat(resultado.getEstadoCalculado()).isEqualTo(EstadoCondicion.REGULAR);
    assertThat(resultado.getEstadoConfirmado()).isEqualTo(EstadoCondicion.REGULAR);
    assertThat(resultado.getJustificacionAjuste()).isNull();
    assertThat(resultado.getConfirmadoPorId()).isNull();
    assertThat(resultado.getConfirmadoEn()).isNull();
    assertThat(estadoPuente()).isEqualTo("Regular");
  }

  @Test
  void publicacionAplicaAjusteDeCondicionCriticaSinCambiarIc() {
    when(calculador.calcular(any())).thenReturn(resultado("80", EstadoCondicion.MALO, true));
    confirmaciones.confirmar(
        inspeccionId,
        new ConfirmarEstadoIcRequest(
            EstadoCondicion.REGULAR, "b".repeat(64), "Criterio sobre condicion critica"));
    var resultado = publicarConDecision();
    assertThat(resultado.getEstadoPorRango()).isEqualTo(EstadoCondicion.BUENO);
    assertThat(resultado.getEstadoCalculado()).isEqualTo(EstadoCondicion.MALO);
    assertThat(resultado.getEstadoConfirmado()).isEqualTo(EstadoCondicion.REGULAR);
    assertThat(resultado.getCondicionesAnulacion().size()).isEqualTo(1);
    assertThat(resultado.getIndiceCalculado()).isEqualByComparingTo("80");
    assertThat(estadoPuente()).isEqualTo("Regular");
    assertThat(
            jdbc.queryForObject(
                "SELECT indice_condicion_actual FROM puente WHERE id = ?",
                BigDecimal.class,
                puenteId))
        .isEqualByComparingTo("80");
  }

  @Test
  void publicacionConCalculoDistintoRevierteTransicionYNoGuardaResultado() {
    confirmaciones.confirmar(
        inspeccionId,
        new ConfirmarEstadoIcRequest(
            EstadoCondicion.REGULAR, "b".repeat(64), "Criterio profesional"));
    when(calculador.calcular(any())).thenReturn(resultado("20", EstadoCondicion.MALO, false));
    var excepcion = assertThrows(ApiException.class, this::publicarConDecision);
    assertThat(excepcion.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM inspeccion WHERE id = ?", String.class, inspeccionId))
        .isEqualTo("BORRADOR");
    assertThat(numero("SELECT count(*) FROM resultado_ic WHERE inspeccion_id = ?", inspeccionId))
        .isZero();
    assertThat(estadoPuente()).isEqualTo("Sin evaluar");
    assertThat(numero("SELECT count(*) FROM auditoria WHERE entidad_id = ?", puenteId)).isZero();
    assertThat(
            numero(
                "SELECT count(*) FROM confirmacion_estado_inspeccion WHERE inspeccion_id = ?",
                inspeccionId))
        .isEqualTo(1);
  }

  private gt.usac.cunoc.sgp.inspeccion.entity.ResultadoIc publicarConDecision() {
    return transaccion.execute(
        status -> {
          jdbc.update(
              "UPDATE inspeccion SET estado = 'PUBLICADA', publicado_en = ? WHERE id = ?",
              OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
              inspeccionId);
          return publicaciones.integrarPublicacion(inspeccionId, usuarioId);
        });
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

  @TestConfiguration
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }
  }
}
