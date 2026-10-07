package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.puente.dto.AprobarSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.CrearSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.RechazarSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.SolicitudAltaPuenteResponse;
import gt.usac.cunoc.sgp.puente.exception.CercaniaPuenteException;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapperImpl;
import gt.usac.cunoc.sgp.puente.model.EstadoSolicitudAltaPuente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import gt.usac.cunoc.sgp.puente.service.SolicitudAltaPuenteService;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
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
  SolicitudAltaPuenteService.class,
  NotificacionService.class,
  PuenteService.class,
  PuenteMapperImpl.class,
  SolicitudAltaPuenteIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@WithMockUser(roles = "CATEDRATICO")
@Testcontainers
class SolicitudAltaPuenteIntegrationTest {

  private static final UUID CATEDRATICO_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000010");
  private static final UUID OTRO_CATEDRATICO_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000011");
  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000012");

  private static final Instant AHORA = Instant.parse("2026-10-02T23:00:00Z");

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_hu010_test")
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

  @Autowired private SolicitudAltaPuenteService solicitudService;

  @Autowired private PuenteRepository puenteRepository;

  @Autowired private JdbcTemplate jdbc;

  private UUID departamentoId;
  private UUID municipioId;

  @BeforeEach
  void prepararDatos() {
    jdbc.update("DELETE FROM notificacion");
    jdbc.update("DELETE FROM solicitud_alta_puente");
    jdbc.update("DELETE FROM puente");
    jdbc.update("UPDATE municipio SET ultimo_correlativo_puente = 0");

    insertarUsuario(CATEDRATICO_ID, "catedratico.hu010@ejemplo.com", "CATEDRATICO");
    insertarUsuario(OTRO_CATEDRATICO_ID, "otro.catedratico.hu010@ejemplo.com", "CATEDRATICO");
    insertarUsuario(ADMINISTRADOR_ID, "admin.hu010@ejemplo.com", "ADMINISTRADOR");

    departamentoId =
        jdbc.queryForObject("SELECT id FROM departamento WHERE codigo_ine = '01'", UUID.class);
    municipioId =
        jdbc.queryForObject("SELECT id FROM municipio WHERE codigo_ine = '0114'", UUID.class);
  }

  @Test
  void creaSolicitudPendienteSinCrearElPuente() {
    var response =
        solicitudService.crear(
            solicitud(
                "  Puente propuesto  ",
                departamentoId,
                municipioId,
                "14.481",
                "-90.615",
                "  Texto  "),
            CATEDRATICO_ID);

    assertThat(response.estado()).isEqualTo(EstadoSolicitudAltaPuente.PENDIENTE);
    assertThat(response.nombre()).isEqualTo("Puente propuesto");
    assertThat(response.justificacion()).isEqualTo("Texto");
    assertThat(response.departamento().id()).isEqualTo(departamentoId);
    assertThat(response.municipio().id()).isEqualTo(municipioId);
    assertThat(response.latitud()).isEqualTo(14.481);
    assertThat(response.longitud()).isEqualTo(-90.615);
    assertThat(response.motivoDecision()).isNull();
    assertThat(response.revisadoEn()).isNull();
    assertThat(response.creadoEn().toInstant()).isEqualTo(AHORA);
    assertThat(response.creadoEn().getOffset()).isEqualTo(ZoneOffset.ofHours(-6));
    assertThat(response.id().version()).isEqualTo(7);

    assertThat(puenteRepository.count()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT ultimo_correlativo_puente FROM municipio WHERE id = ?",
                Integer.class,
                municipioId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT solicitado_por_id FROM solicitud_alta_puente WHERE id = ?",
                UUID.class,
                response.id()))
        .isEqualTo(CATEDRATICO_ID);
    assertThat(
            jdbc.queryForObject(
                "SELECT ST_X(ubicacion::geometry) FROM solicitud_alta_puente WHERE id = ?",
                Double.class,
                response.id()))
        .isEqualTo(-90.615);
  }

  @Test
  void justificacionEnBlancoSeGuardaComoNula() {
    var response =
        solicitudService.crear(
            solicitud("Puente", departamentoId, municipioId, "14.481", "-90.615", "   "),
            CATEDRATICO_ID);

    assertThat(response.justificacion()).isNull();
  }

  @Test
  void rechazaUbicacionFueraDeGuatemala() {
    assertRechazada(
        solicitud("Puente", departamentoId, municipioId, "0", "0", null),
        "ubicacion_fuera_de_guatemala");
  }

  @Test
  void rechazaUbicacionDeOtroMunicipio() {
    assertRechazada(
        solicitud("Puente", departamentoId, municipioId, "14.844673", "-91.521161", null),
        "ubicacion_municipio_incongruente");
  }

  @Test
  void rechazaMunicipioQueNoPerteneceAlDepartamento() {
    UUID otroDepartamento =
        jdbc.queryForObject("SELECT id FROM departamento WHERE codigo_ine = '04'", UUID.class);

    assertRechazada(
        solicitud("Puente", otroDepartamento, municipioId, "14.481", "-90.615", null),
        "municipio_departamento_incongruente");
  }

  @Test
  void rechazaDepartamentoYMunicipioInexistentes() {
    assertRechazada(
        solicitud("Puente", UUID.randomUUID(), municipioId, "14.481", "-90.615", null),
        "departamento_invalido");
    assertRechazada(
        solicitud("Puente", departamentoId, UUID.randomUUID(), "14.481", "-90.615", null),
        "municipio_invalido");
  }

  @Test
  void listaSoloLasSolicitudesDelCatedraticoConSuEstado() {
    var propia =
        solicitudService.crear(
            solicitud("Propia", departamentoId, municipioId, "14.481", "-90.615", null),
            CATEDRATICO_ID);
    var rechazada =
        solicitudService.crear(
            solicitud("Rechazada", departamentoId, municipioId, "14.482", "-90.616", null),
            CATEDRATICO_ID);
    solicitudService.crear(
        solicitud("Ajena", departamentoId, municipioId, "14.483", "-90.617", null),
        OTRO_CATEDRATICO_ID);

    jdbc.update(
        """
        UPDATE solicitud_alta_puente
        SET estado = 'RECHAZADA', revisado_por_id = ?, revisado_en = ?, motivo_decision = ?
        WHERE id = ?
        """,
        ADMINISTRADOR_ID,
        java.sql.Timestamp.from(AHORA),
        "Ya existe en el catálogo",
        rechazada.id());

    var pagina = solicitudService.listarMias(CATEDRATICO_ID, 0, 20);

    assertThat(pagina.getTotalElements()).isEqualTo(2);
    assertThat(pagina.getContent())
        .extracting(s -> s.nombre(), s -> s.estado(), s -> s.motivoDecision())
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(
                "Propia", EstadoSolicitudAltaPuente.PENDIENTE, null),
            org.assertj.core.groups.Tuple.tuple(
                "Rechazada", EstadoSolicitudAltaPuente.RECHAZADA, "Ya existe en el catálogo"));
    assertThat(pagina.getContent()).extracting(s -> s.id()).contains(propia.id());
  }

  @Test
  void rechazaPaginacionInvalida() {
    assertThatThrownBy(() -> solicitudService.listarMias(CATEDRATICO_ID, -1, 20))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("paginacion_invalida"));
    assertThatThrownBy(() -> solicitudService.listarMias(CATEDRATICO_ID, 0, 101))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("paginacion_invalida"));
  }

  @Test
  @WithMockUser(roles = "ESTUDIANTE")
  void estudianteNoPuedeCrearNiConsultar() {
    var request = solicitud("Puente", departamentoId, municipioId, "14.481", "-90.615", null);

    assertThatThrownBy(() -> solicitudService.crear(request, CATEDRATICO_ID))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> solicitudService.listarMias(CATEDRATICO_ID, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void administradorNoPuedeCrearSolicitudes() {
    var request = solicitud("Puente", departamentoId, municipioId, "14.481", "-90.615", null);

    assertThatThrownBy(() -> solicitudService.crear(request, ADMINISTRADOR_ID))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void aprobarCreaElPuenteConCodigoUnicoYNotificaAlSolicitante() {
    var solicitud = crearSolicitud("Puente aprobado", "14.481", "-90.615");

    var aprobada =
        solicitudService.aprobar(
            solicitud.id(), new AprobarSolicitudAltaPuenteRequest(false), ADMINISTRADOR_ID);

    assertThat(aprobada.estado()).isEqualTo(EstadoSolicitudAltaPuente.APROBADA);
    assertThat(aprobada.puenteCreadoCodigo()).isEqualTo("GT-01-0114-0001");
    assertThat(aprobada.revisadoEn()).isNotNull();

    assertThat(puenteRepository.count()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT nombre FROM puente WHERE id = ?", String.class, aprobada.puenteCreadoId()))
        .isEqualTo("Puente aprobado");
    assertThat(
            jdbc.queryForObject(
                "SELECT creado_por_id FROM puente WHERE id = ?",
                UUID.class,
                aprobada.puenteCreadoId()))
        .isEqualTo(ADMINISTRADOR_ID);
    assertThat(
            jdbc.queryForObject(
                "SELECT revisado_por_id FROM solicitud_alta_puente WHERE id = ?",
                UUID.class,
                solicitud.id()))
        .isEqualTo(ADMINISTRADOR_ID);

    var notificacion =
        jdbc.queryForMap(
            "SELECT tipo, mensaje, entidad_id, leido_en FROM notificacion WHERE usuario_id = ?",
            CATEDRATICO_ID);
    assertThat(notificacion.get("tipo")).isEqualTo("SOLICITUD_PUENTE_APROBADA");
    assertThat((String) notificacion.get("mensaje")).contains("GT-01-0114-0001");
    assertThat(notificacion.get("entidad_id")).isEqualTo(solicitud.id());
    assertThat(notificacion.get("leido_en")).isNull();
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void aprobarConPuenteCercanoExigeConfirmacionYNoCambiaNada() {
    var primera = crearSolicitud("Primero", "14.481", "-90.615");
    solicitudService.aprobar(
        primera.id(), new AprobarSolicitudAltaPuenteRequest(false), ADMINISTRADOR_ID);
    var cercana = crearSolicitud("Cercano", "14.4811", "-90.615");

    assertThatThrownBy(
            () ->
                solicitudService.aprobar(
                    cercana.id(), new AprobarSolicitudAltaPuenteRequest(false), ADMINISTRADOR_ID))
        .isInstanceOf(CercaniaPuenteException.class);

    assertThat(puenteRepository.count()).isEqualTo(1);
    assertThat(estadoDe(cercana.id())).isEqualTo("PENDIENTE");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notificacion WHERE entidad_id = ?", Long.class, cercana.id()))
        .isZero();

    var aprobada =
        solicitudService.aprobar(
            cercana.id(), new AprobarSolicitudAltaPuenteRequest(true), ADMINISTRADOR_ID);

    assertThat(aprobada.puenteCreadoCodigo()).isEqualTo("GT-01-0114-0002");
    assertThat(puenteRepository.count()).isEqualTo(2);
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void rechazarGuardaElMotivoNoCreaPuenteYNotifica() {
    var solicitud = crearSolicitud("Puente rechazado", "14.481", "-90.615");

    var rechazada =
        solicitudService.rechazar(
            solicitud.id(),
            new RechazarSolicitudAltaPuenteRequest("  Ya existe en el catálogo  "),
            ADMINISTRADOR_ID);

    assertThat(rechazada.estado()).isEqualTo(EstadoSolicitudAltaPuente.RECHAZADA);
    assertThat(rechazada.motivoDecision()).isEqualTo("Ya existe en el catálogo");
    assertThat(rechazada.puenteCreadoId()).isNull();
    assertThat(puenteRepository.count()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT municipio.ultimo_correlativo_puente FROM municipio WHERE id = ?",
                Integer.class,
                municipioId))
        .isZero();

    var notificacion =
        jdbc.queryForMap(
            "SELECT tipo, mensaje FROM notificacion WHERE usuario_id = ?", CATEDRATICO_ID);
    assertThat(notificacion.get("tipo")).isEqualTo("SOLICITUD_PUENTE_RECHAZADA");
    assertThat((String) notificacion.get("mensaje")).contains("Ya existe en el catálogo");

    var mias =
        comoCatedratico(() -> solicitudService.listarMias(CATEDRATICO_ID, 0, 20)).getContent();
    assertThat(mias).hasSize(1);
    assertThat(mias.get(0).motivoDecision()).isEqualTo("Ya existe en el catálogo");
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void rechazarExigeMotivo() {
    var solicitud = crearSolicitud("Puente", "14.481", "-90.615");

    assertThatThrownBy(
            () ->
                solicitudService.rechazar(
                    solicitud.id(),
                    new RechazarSolicitudAltaPuenteRequest("   "),
                    ADMINISTRADOR_ID))
        .isInstanceOf(ConstraintViolationException.class);
    assertThat(estadoDe(solicitud.id())).isEqualTo("PENDIENTE");
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void unaSolicitudResueltaNoSePuedeResolverDeNuevo() {
    var aprobada = crearSolicitud("Aprobada", "14.481", "-90.615");
    solicitudService.aprobar(
        aprobada.id(), new AprobarSolicitudAltaPuenteRequest(false), ADMINISTRADOR_ID);
    var rechazada = crearSolicitud("Rechazada", "14.49", "-90.62");
    solicitudService.rechazar(
        rechazada.id(), new RechazarSolicitudAltaPuenteRequest("No procede"), ADMINISTRADOR_ID);

    assertNoPendiente(
        () ->
            solicitudService.rechazar(
                aprobada.id(), new RechazarSolicitudAltaPuenteRequest("Tarde"), ADMINISTRADOR_ID));
    assertNoPendiente(
        () ->
            solicitudService.aprobar(
                rechazada.id(), new AprobarSolicitudAltaPuenteRequest(true), ADMINISTRADOR_ID));
    assertNoPendiente(
        () ->
            solicitudService.aprobar(
                aprobada.id(), new AprobarSolicitudAltaPuenteRequest(true), ADMINISTRADOR_ID));

    assertThat(puenteRepository.count()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Long.class)).isEqualTo(2);
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void decidirUnaSolicitudInexistenteDevuelve404() {
    UUID inexistente = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                solicitudService.aprobar(
                    inexistente, new AprobarSolicitudAltaPuenteRequest(false), ADMINISTRADOR_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(e.getCode()).isEqualTo("solicitud_no_encontrada");
            });
    assertThatThrownBy(() -> solicitudService.obtenerParaRevision(inexistente))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("solicitud_no_encontrada"));
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void listaParaRevisionPorEstadoConElCorreoDelSolicitante() {
    var primera = crearSolicitud("Primera", "14.481", "-90.615");
    crearSolicitud("Segunda", "14.49", "-90.62");
    var rechazada = crearSolicitud("Tercera", "14.5", "-90.63");
    solicitudService.rechazar(
        rechazada.id(), new RechazarSolicitudAltaPuenteRequest("No procede"), ADMINISTRADOR_ID);

    var pendientes = solicitudService.listarParaRevision(null, 0, 20);

    assertThat(pendientes.getTotalElements()).isEqualTo(2);
    assertThat(pendientes.getContent())
        .extracting(r -> r.solicitud().nombre())
        .containsExactlyInAnyOrder("Primera", "Segunda");
    assertThat(pendientes.getContent())
        .extracting(r -> r.solicitanteEmail())
        .containsOnly("catedratico.hu010@ejemplo.com");
    assertThat(pendientes.getContent().get(0).solicitud().id()).isEqualTo(primera.id());

    var rechazadas =
        solicitudService.listarParaRevision(EstadoSolicitudAltaPuente.RECHAZADA, 0, 20);
    assertThat(rechazadas.getContent())
        .extracting(r -> r.solicitud().nombre())
        .containsExactly("Tercera");

    assertThatThrownBy(() -> solicitudService.listarParaRevision(null, 0, 101))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("paginacion_invalida"));
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void unaSolicitudResueltaNoMuestraAdvertenciaDeCercaniaYIndicaQuienLaRevisó() {
    var aprobada = crearSolicitud("Aprobada", "14.481", "-90.615");
    solicitudService.aprobar(
        aprobada.id(), new AprobarSolicitudAltaPuenteRequest(false), ADMINISTRADOR_ID);
    var rechazada = crearSolicitud("Rechazada", "14.49", "-90.62");
    solicitudService.rechazar(
        rechazada.id(), new RechazarSolicitudAltaPuenteRequest("No procede"), ADMINISTRADOR_ID);
    var pendiente = crearSolicitud("Pendiente", "14.5", "-90.63");

    var detalleAprobada = solicitudService.obtenerParaRevision(aprobada.id());
    assertThat(detalleAprobada.puentesCercanos()).isEmpty();
    assertThat(detalleAprobada.totalPuentesCercanos()).isZero();
    assertThat(detalleAprobada.revisadoPorEmail()).isEqualTo("admin.hu010@ejemplo.com");
    assertThat(detalleAprobada.solicitud().puenteCreadoCodigo()).isEqualTo("GT-01-0114-0001");

    var detalleRechazada = solicitudService.obtenerParaRevision(rechazada.id());
    assertThat(detalleRechazada.puentesCercanos()).isEmpty();
    assertThat(detalleRechazada.revisadoPorEmail()).isEqualTo("admin.hu010@ejemplo.com");
    assertThat(detalleRechazada.solicitud().motivoDecision()).isEqualTo("No procede");

    assertThat(solicitudService.obtenerParaRevision(pendiente.id()).revisadoPorEmail()).isNull();

    var rechazadas =
        solicitudService.listarParaRevision(EstadoSolicitudAltaPuente.RECHAZADA, 0, 20);
    assertThat(rechazadas.getContent())
        .extracting(r -> r.revisadoPorEmail())
        .containsExactly("admin.hu010@ejemplo.com");
  }

  @Test
  void elCatedraticoConsultaElDetalleDeSuSolicitudPeroNoElDeOtro() {
    var propia = crearSolicitud("Propia", "14.481", "-90.615");
    var ajena =
        comoCatedratico(
            () ->
                solicitudService.crear(
                    solicitud("Ajena", departamentoId, municipioId, "14.49", "-90.62", null),
                    OTRO_CATEDRATICO_ID));

    var detalle = solicitudService.obtenerMia(propia.id(), CATEDRATICO_ID);
    assertThat(detalle.nombre()).isEqualTo("Propia");
    assertThat(detalle.estado()).isEqualTo(EstadoSolicitudAltaPuente.PENDIENTE);

    assertThatThrownBy(() -> solicitudService.obtenerMia(ajena.id(), CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(e.getCode()).isEqualTo("solicitud_no_encontrada");
            });
    assertThatThrownBy(() -> solicitudService.obtenerMia(UUID.randomUUID(), CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("solicitud_no_encontrada"));
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void elAdministradorNoUsaElDetalleDelCatedratico() {
    assertThatThrownBy(() -> solicitudService.obtenerMia(UUID.randomUUID(), ADMINISTRADOR_ID))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void elDetalleIncluyeLosPuentesCercanosDeLaMismaAdvertenciaQueElAltaDirecta() {
    var existente = crearSolicitud("Existente", "14.481", "-90.615");
    var aprobada =
        solicitudService.aprobar(
            existente.id(), new AprobarSolicitudAltaPuenteRequest(false), ADMINISTRADOR_ID);
    var cercana = crearSolicitud("Cercana", "14.4811", "-90.615");
    var lejana = crearSolicitud("Lejana", "14.49", "-90.62");

    var detalleCercana = solicitudService.obtenerParaRevision(cercana.id());
    assertThat(detalleCercana.totalPuentesCercanos()).isEqualTo(1);
    assertThat(detalleCercana.puentesCercanos())
        .extracting(c -> c.codigo())
        .containsExactly(aprobada.puenteCreadoCodigo());
    assertThat(detalleCercana.solicitanteEmail()).isEqualTo("catedratico.hu010@ejemplo.com");
    assertThat(detalleCercana.solicitud().nombre()).isEqualTo("Cercana");

    assertThat(solicitudService.obtenerParaRevision(lejana.id()).puentesCercanos()).isEmpty();
  }

  @Test
  void elCatedraticoNoPuedeRevisarSolicitudes() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> solicitudService.listarParaRevision(null, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> solicitudService.obtenerParaRevision(id))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                solicitudService.aprobar(
                    id, new AprobarSolicitudAltaPuenteRequest(false), CATEDRATICO_ID))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                solicitudService.rechazar(
                    id, new RechazarSolicitudAltaPuenteRequest("No"), CATEDRATICO_ID))
        .isInstanceOf(AccessDeniedException.class);
  }

  private void assertNoPendiente(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion) {
    assertThatThrownBy(accion)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("solicitud_no_pendiente");
            });
  }

  private String estadoDe(UUID solicitudId) {
    return jdbc.queryForObject(
        "SELECT estado FROM solicitud_alta_puente WHERE id = ?", String.class, solicitudId);
  }

  /** Crea una solicitud como el Catedrático de prueba, sin depender del rol del test actual. */
  private SolicitudAltaPuenteResponse crearSolicitud(
      String nombre, String latitud, String longitud) {
    return comoCatedratico(
        () ->
            solicitudService.crear(
                solicitud(nombre, departamentoId, municipioId, latitud, longitud, null),
                CATEDRATICO_ID));
  }

  private <T> T comoCatedratico(Supplier<T> accion) {
    var autenticacionPrevia = SecurityContextHolder.getContext().getAuthentication();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "catedratico", null, List.of(new SimpleGrantedAuthority("ROLE_CATEDRATICO"))));
    try {
      return accion.get();
    } finally {
      SecurityContextHolder.getContext().setAuthentication(autenticacionPrevia);
    }
  }

  private void assertRechazada(CrearSolicitudAltaPuenteRequest request, String codigo) {
    assertThatThrownBy(() -> solicitudService.crear(request, CATEDRATICO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo(codigo);
            });

    assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitud_alta_puente", Long.class))
        .isZero();
  }

  private CrearSolicitudAltaPuenteRequest solicitud(
      String nombre,
      UUID departamento,
      UUID municipio,
      String latitud,
      String longitud,
      String justificacion) {
    return new CrearSolicitudAltaPuenteRequest(
        nombre,
        departamento,
        municipio,
        "CA-9",
        null,
        new BigDecimal(latitud),
        new BigDecimal(longitud),
        justificacion);
  }

  private void insertarUsuario(UUID id, String email, String rol) {
    jdbc.update(
        """
        INSERT INTO usuario (
          id, email, password_hash, rol_id,
          verificado, activado, activo, nombre_completo
        )
        SELECT ?, ?, 'hash-de-prueba', id, true, true, true, 'Usuario de prueba HU010'
        FROM rol
        WHERE nombre = ?
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        email,
        rol);
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
