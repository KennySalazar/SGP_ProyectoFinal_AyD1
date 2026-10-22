package gt.usac.cunoc.sgp.mantenimiento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.mantenimiento.dto.CrearOrdenMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.CambiarPrioridadOrdenRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.FiltroOrdenesRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.TotalesOrdenesResponse;
import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.mapper.OrdenMantenimientoMapperImpl;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.HistorialEstadoOrdenRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

class OrdenMantenimientoServiceTest {

  private static final Instant AHORA = Instant.parse("2026-10-20T15:00:00Z");
  private static final UUID PUENTE_ID = UUID.fromString("019a0000-0000-7000-8000-000000004711");
  private static final UUID OTRO_PUENTE_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000004712");
  private static final UUID INSPECCION_ID = UUID.fromString("019a0000-0000-7000-8000-000000004713");
  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000004714");
  private static final UUID ORDEN_ID = UUID.fromString("019a0000-0000-7000-8000-000000004715");

  private OrdenMantenimientoRepository ordenes;
  private HistorialEstadoOrdenRepository historial;
  private PuenteRepository puentes;
  private InspeccionRepository inspecciones;
  private AuditoriaWriter auditoria;
  private OrdenMantenimientoService service;
  private Puente puente;

  @BeforeEach
  void setUp() {
    ordenes = mock(OrdenMantenimientoRepository.class);
    historial = mock(HistorialEstadoOrdenRepository.class);
    puentes = mock(PuenteRepository.class);
    inspecciones = mock(InspeccionRepository.class);
    auditoria = mock(AuditoriaWriter.class);
    service =
        new OrdenMantenimientoService(
            ordenes,
            historial,
            puentes,
            inspecciones,
            auditoria,
            new OrdenMantenimientoMapperImpl(),
            new ObjectMapper(),
            Clock.fixed(AHORA, ZoneOffset.UTC));
    puente = mock(Puente.class);
    when(puente.getId()).thenReturn(PUENTE_ID);
    when(puente.isActivo()).thenReturn(true);
    when(puentes.findById(PUENTE_ID)).thenReturn(Optional.of(puente));
    when(ordenes.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @ParameterizedTest
  @EnumSource(TipoOrden.class)
  void cadaTipologiaValidaCreaUnaPropuesta(TipoOrden tipo) {
    OrdenMantenimientoResponse response =
        service.crear(solicitud(tipo.name(), "  Limpiar drenajes  ", "ALTA"), USUARIO_ID);

    assertThat(response.estado()).isEqualTo(EstadoOrden.PROPUESTA);
    assertThat(response.tipo()).isEqualTo(tipo);
    assertThat(response.descripcion()).isEqualTo("Limpiar drenajes");
    assertThat(response.prioridadSugerida()).isEqualTo(PrioridadOrden.ALTA);
    assertThat(response.prioridadConfirmada()).isEqualTo(PrioridadOrden.ALTA);
    assertThat(response.creadoPorId()).isEqualTo(USUARIO_ID);
    assertThat(response.puenteId()).isEqualTo(PUENTE_ID);
    assertThat(response.inspeccionOrigenId()).isNull();
  }

  @Test
  void laCreacionRegistraHistorialInicialYAuditoria() {
    service.crear(solicitud("CORRECTIVO", "Reparar junta", "MEDIA"), USUARIO_ID);

    ArgumentCaptor<HistorialEstadoOrden> fila = ArgumentCaptor.forClass(HistorialEstadoOrden.class);
    verify(historial).save(fila.capture());
    assertThat(fila.getValue().getSecuencia()).isEqualTo(1);
    assertThat(fila.getValue().getEstadoAnterior()).isNull();
    assertThat(fila.getValue().getEstadoNuevo()).isEqualTo("PROPUESTA");
    assertThat(fila.getValue().getRealizadoPorId()).isEqualTo(USUARIO_ID);

    ArgumentCaptor<JsonNode> despues = ArgumentCaptor.forClass(JsonNode.class);
    verify(auditoria)
        .registrarEnTransaccion(
            eq(USUARIO_ID),
            eq(AccionAuditoria.CREAR),
            eq("orden_mantenimiento"),
            any(UUID.class),
            isNull(),
            despues.capture(),
            isNull());
    assertThat(despues.getValue().get("estado").asText()).isEqualTo("PROPUESTA");
    assertThat(despues.getValue().get("tipo").asText()).isEqualTo("CORRECTIVO");
    assertThat(despues.getValue().has("inspeccionOrigenId")).isFalse();
    assertThat(despues.getValue().has("elementoRef")).isFalse();
  }

  @Test
  void unaTipologiaNoReconocidaResponde422() {
    assertThatThrownBy(
            () -> service.crear(solicitud("URGENTE", "Reparar junta", "MEDIA"), USUARIO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("orden_invalida");
              assertThat(e.getErrores()).extracting(m -> m.get("campo")).containsExactly("tipo");
            });
    verifyNoInteractions(ordenes, puentes);
  }

  @Test
  void reportaTodosLosCamposInvalidosEnUnSolo422() {
    var request =
        new CrearOrdenMantenimientoRequest(
            null, " ", "x".repeat(4001), "ALTISIMA", "/e".repeat(151), UUID.randomUUID(), null);

    assertThatThrownBy(() -> service.crear(request, USUARIO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.getErrores())
                    .extracting(m -> m.get("campo"))
                    .containsExactlyInAnyOrder(
                        "puenteId", "tipo", "prioridad", "descripcion", "elementoRef"));
  }

  @Test
  void laInstanciaRequiereElElemento() {
    var request =
        new CrearOrdenMantenimientoRequest(
            PUENTE_ID, "RUTINARIO", "Pintar barandal", "BAJA", null, UUID.randomUUID(), null);

    assertThatThrownBy(() -> service.crear(request, USUARIO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.getErrores())
                    .containsExactly(
                        Map.of(
                            "campo",
                            "instanciaElementoId",
                            "mensaje",
                            "La instancia requiere indicar el elemento.")));
  }

  @Test
  void laDescripcionEsObligatoria() {
    assertThatThrownBy(() -> service.crear(solicitud("RUTINARIO", null, "BAJA"), USUARIO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.getErrores())
                    .extracting(m -> m.get("campo"))
                    .containsExactly("descripcion"));
  }

  @Test
  void unPuenteInexistenteResponde404() {
    when(puentes.findById(PUENTE_ID)).thenReturn(Optional.empty());

    assertCodigo(
        () -> service.crear(solicitud("RUTINARIO", "Pintar barandal", "BAJA"), USUARIO_ID),
        HttpStatus.NOT_FOUND,
        "puente_no_encontrado");
  }

  @Test
  void unPuenteDadoDeBajaNoAdmiteOrdenesNuevas() {
    when(puente.isActivo()).thenReturn(false);

    assertCodigo(
        () -> service.crear(solicitud("RUTINARIO", "Pintar barandal", "BAJA"), USUARIO_ID),
        HttpStatus.CONFLICT,
        "puente_inactivo");
    verify(ordenes, never()).saveAndFlush(any());
  }

  @Test
  void laInspeccionDebeSerDelMismoPuente() {
    when(inspecciones.findPuenteIdByInspeccionId(INSPECCION_ID))
        .thenReturn(Optional.of(OTRO_PUENTE_ID));

    assertCodigo(
        () -> service.crear(conInspeccion(), USUARIO_ID),
        HttpStatus.CONFLICT,
        "inspeccion_de_otro_puente");
    verify(ordenes, never()).saveAndFlush(any());
  }

  @Test
  void unaInspeccionInexistenteResponde404() {
    when(inspecciones.findPuenteIdByInspeccionId(INSPECCION_ID)).thenReturn(Optional.empty());

    assertCodigo(
        () -> service.crear(conInspeccion(), USUARIO_ID),
        HttpStatus.NOT_FOUND,
        "inspeccion_no_encontrada");
  }

  @Test
  void unaOrdenConInspeccionDelMismoPuenteLaRegistraYAudita() {
    when(inspecciones.findPuenteIdByInspeccionId(INSPECCION_ID)).thenReturn(Optional.of(PUENTE_ID));

    OrdenMantenimientoResponse response = service.crear(conInspeccion(), USUARIO_ID);

    assertThat(response.inspeccionOrigenId()).isEqualTo(INSPECCION_ID);
    assertThat(response.elementoRef()).isEqualTo("/superestructura/drenaje");
    ArgumentCaptor<JsonNode> despues = ArgumentCaptor.forClass(JsonNode.class);
    verify(auditoria)
        .registrarEnTransaccion(any(), any(), any(), any(), isNull(), despues.capture(), isNull());
    assertThat(despues.getValue().get("inspeccionOrigenId").asText())
        .isEqualTo(INSPECCION_ID.toString());
    assertThat(despues.getValue().get("elementoRef").asText())
        .isEqualTo("/superestructura/drenaje");
  }

  @Test
  void listarCombinaTodosLosFiltrosOrdenandoDeLaMasReciente() {
    when(ordenes.filtrar(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(orden())));

    Page<OrdenMantenimientoResponse> pagina =
        service.listar(
            new FiltroOrdenesRequest(
                PUENTE_ID,
                List.of("DESCARTADA", "CANCELADA"),
                "CORRECTIVO",
                "ALTA",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 20)),
            1,
            100);

    assertThat(pagina.getContent()).hasSize(1);
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(ordenes)
        .filtrar(
            eq(EnumSet.of(EstadoOrden.DESCARTADA, EstadoOrden.CANCELADA)),
            eq(PUENTE_ID),
            eq(TipoOrden.CORRECTIVO),
            eq(PrioridadOrden.ALTA),
            // Días completos en hora de Guatemala (UTC-6); el final es exclusivo.
            eq(Instant.parse("2026-10-01T06:00:00Z")),
            eq(Instant.parse("2026-10-21T06:00:00Z")),
            captor.capture());
    assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
    assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    assertThat(captor.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));
  }

  @Test
  void listarSinFiltrosIncluyeTodosLosEstadosYFechas() {
    when(ordenes.filtrar(any(), isNull(), isNull(), isNull(), any(), any(), any()))
        .thenReturn(Page.empty());

    assertThat(
            service.listar(
                new FiltroOrdenesRequest(null, List.of(" "), null, " ", null, null), 0, 20))
        .isEmpty();
    assertThat(service.listar(new FiltroOrdenesRequest(null, null, null, null, null, null), 0, 20))
        .isEmpty();

    ArgumentCaptor<java.util.Collection<EstadoOrden>> estados = ArgumentCaptor.captor();
    ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> hasta = ArgumentCaptor.forClass(Instant.class);
    verify(ordenes, org.mockito.Mockito.times(2))
        .filtrar(
            estados.capture(),
            isNull(),
            isNull(),
            isNull(),
            desde.capture(),
            hasta.capture(),
            any());
    assertThat(estados.getValue()).containsExactlyInAnyOrder(EstadoOrden.values());
    assertThat(desde.getValue()).isBefore(Instant.parse("2000-01-01T00:00:00Z"));
    assertThat(hasta.getValue()).isAfter(Instant.parse("3000-01-01T00:00:00Z"));
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,101"})
  void unaPaginacionInvalidaResponde422(int pagina, int tamanio) {
    assertCodigo(
        () -> service.listar(sinFiltros(), pagina, tamanio),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "paginacion_invalida");
  }

  @Test
  void filtrosInexistentesORangoInvertidoResponden422ConCadaCampo() {
    assertThatThrownBy(
            () ->
                service.listar(
                    new FiltroOrdenesRequest(
                        null,
                        List.of("ARCHIVADA"),
                        "PINTURA",
                        "URGENTE",
                        LocalDate.of(2026, 10, 20),
                        LocalDate.of(2026, 10, 1)),
                    0,
                    20))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("filtro_invalido");
              assertThat(e.getErrores())
                  .extracting(error -> error.get("campo"))
                  .containsExactly("estado", "tipo", "prioridad", "desde");
            });
    verifyNoInteractions(ordenes);
  }

  @Test
  void losTotalesIncluyenTodosLosEstadosAunqueValganCero() {
    when(ordenes.contarPorEstado(eq(PUENTE_ID), isNull(), eq(PrioridadOrden.ALTA), any(), any()))
        .thenReturn(
            List.of(
                new Object[] {EstadoOrden.PROPUESTA, 3L},
                new Object[] {EstadoOrden.CANCELADA, 2L}));

    TotalesOrdenesResponse totales =
        service.totales(new FiltroOrdenesRequest(PUENTE_ID, null, null, "ALTA", null, null));

    assertThat(totales.total()).isEqualTo(5);
    assertThat(totales.porEstado())
        .containsEntry(EstadoOrden.PROPUESTA, 3L)
        .containsEntry(EstadoOrden.CANCELADA, 2L)
        .containsEntry(EstadoOrden.EJECUTADA, 0L)
        .hasSize(EstadoOrden.values().length);
  }

  private static FiltroOrdenesRequest sinFiltros() {
    return new FiltroOrdenesRequest(null, null, null, null, null, null);
  }

  @Test
  void obtenerYHistorialDeUnaOrdenInexistenteResponden404() {
    when(ordenes.findById(ORDEN_ID)).thenReturn(Optional.empty());

    assertCodigo(() -> service.obtener(ORDEN_ID), HttpStatus.NOT_FOUND, "orden_no_encontrada");
    assertCodigo(() -> service.historial(ORDEN_ID), HttpStatus.NOT_FOUND, "orden_no_encontrada");
  }

  @Test
  void elHistorialSeDevuelveOrdenadoPorSecuencia() {
    when(ordenes.findById(ORDEN_ID)).thenReturn(Optional.of(orden()));
    when(historial.findByOrdenMantenimientoIdOrderBySecuenciaAsc(ORDEN_ID))
        .thenReturn(
            List.of(
                new HistorialEstadoOrden(
                    UUID.randomUUID(), ORDEN_ID, 1, null, "PROPUESTA", USUARIO_ID, null, AHORA),
                new HistorialEstadoOrden(
                    UUID.randomUUID(),
                    ORDEN_ID,
                    2,
                    "PROPUESTA",
                    "DESCARTADA",
                    USUARIO_ID,
                    "Duplicada",
                    AHORA)));

    assertThat(service.historial(ORDEN_ID))
        .extracting(h -> h.secuencia() + ":" + h.estadoNuevo())
        .containsExactly("1:PROPUESTA", "2:DESCARTADA");
    assertThat(service.obtener(ORDEN_ID).id()).isEqualTo(ORDEN_ID);
  }

  @Test
  void cambiarPrioridadConservaLaSugeridaYAuditaActorMotivoYValores() {
    OrdenMantenimiento orden = orden();
    when(ordenes.findById(ORDEN_ID)).thenReturn(Optional.of(orden));

    var response =
        service.cambiarPrioridad(
            ORDEN_ID, new CambiarPrioridadOrdenRequest("ALTA", "Requiere atención inmediata"), USUARIO_ID);

    assertThat(response.prioridadSugerida()).isEqualTo(PrioridadOrden.BAJA);
    assertThat(response.prioridadConfirmada()).isEqualTo(PrioridadOrden.ALTA);
    assertThat(response.justificacionPrioridad()).isEqualTo("Requiere atención inmediata");
    ArgumentCaptor<JsonNode> anterior = ArgumentCaptor.forClass(JsonNode.class);
    ArgumentCaptor<JsonNode> posterior = ArgumentCaptor.forClass(JsonNode.class);
    verify(auditoria)
        .registrarEnTransaccion(
            eq(USUARIO_ID),
            eq(AccionAuditoria.MODIFICAR),
            eq("orden_mantenimiento"),
            eq(ORDEN_ID),
            anterior.capture(),
            posterior.capture(),
            isNull());
    assertThat(anterior.getValue().get("prioridadConfirmada").asText()).isEqualTo("BAJA");
    assertThat(posterior.getValue().get("prioridadConfirmada").asText()).isEqualTo("ALTA");
    assertThat(posterior.getValue().get("justificacionPrioridad").asText())
        .isEqualTo("Requiere atención inmediata");
  }

  @Test
  void cambiarPrioridadSinJustificacionResponde422() {
    assertCodigo(
        () -> service.cambiarPrioridad(ORDEN_ID, new CambiarPrioridadOrdenRequest("ALTA", " "), USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "prioridad_invalida");
    verifyNoInteractions(ordenes);
  }

  @ParameterizedTest
  @EnumSource(
      value = EstadoOrden.class,
      names = {"EJECUTADA", "CANCELADA", "DESCARTADA"})
  void cambiarPrioridadEnEstadoTerminalResponde409(EstadoOrden estado) {
    OrdenMantenimiento orden = orden();
    orden.aplicarTransicion(estado, AHORA);
    when(ordenes.findById(ORDEN_ID)).thenReturn(Optional.of(orden));

    assertCodigo(
        () -> service.cambiarPrioridad(ORDEN_ID, new CambiarPrioridadOrdenRequest("ALTA", "Motivo"), USUARIO_ID),
        HttpStatus.CONFLICT,
        "orden_terminal");
    verify(ordenes, never()).saveAndFlush(any());
  }

  private OrdenMantenimiento orden() {
    return new OrdenMantenimiento(
        ORDEN_ID,
        PUENTE_ID,
        null,
        null,
        null,
        TipoOrden.PREVENTIVO,
        "Revisar apoyos",
        PrioridadOrden.BAJA,
        USUARIO_ID,
        AHORA);
  }

  private static CrearOrdenMantenimientoRequest solicitud(
      String tipo, String descripcion, String prioridad) {
    return new CrearOrdenMantenimientoRequest(
        PUENTE_ID, tipo, descripcion, prioridad, null, null, null);
  }

  private static CrearOrdenMantenimientoRequest conInspeccion() {
    return new CrearOrdenMantenimientoRequest(
        PUENTE_ID,
        "CORRECTIVO",
        "Limpiar drenaje",
        "ALTA",
        "/superestructura/drenaje",
        null,
        INSPECCION_ID);
  }

  private static void assertCodigo(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable accion,
      HttpStatus status,
      String codigo) {
    assertThatThrownBy(accion)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(status);
              assertThat(e.getCode()).isEqualTo(codigo);
            });
  }
}
