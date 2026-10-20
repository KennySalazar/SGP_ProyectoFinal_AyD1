package gt.usac.cunoc.sgp.mantenimiento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.entity.Archivo;
import gt.usac.cunoc.sgp.archivo.entity.ArchivoEvidenciaMantenimiento;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.model.TipoUsoArchivo;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoEvidenciaMantenimientoRepository;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.mantenimiento.dto.CerrarComoEjecutadaRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.CerrarSinEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.IniciarEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.ProgramarOrdenRequest;
import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimientoEjecucion;
import gt.usac.cunoc.sgp.mantenimiento.mapper.OrdenMantenimientoMapperImpl;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.HistorialEstadoOrdenRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoEjecucionRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class TransicionOrdenServiceTest {

  /** 15:00 UTC del 20/10/2026 equivale a las 09:00 del mismo día en Guatemala. */
  private static final Instant AHORA = Instant.parse("2026-10-20T15:00:00Z");

  private static final LocalDate HOY = LocalDate.of(2026, 10, 20);
  private static final UUID ORDEN_ID = UUID.fromString("019a0000-0000-7000-8000-000000004701");
  private static final UUID PUENTE_ID = UUID.fromString("019a0000-0000-7000-8000-000000004702");
  private static final UUID CREADOR_ID = UUID.fromString("019a0000-0000-7000-8000-000000004703");
  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000004704");

  private OrdenMantenimientoRepository ordenes;
  private HistorialEstadoOrdenRepository historial;
  private OrdenMantenimientoEjecucionRepository ejecuciones;
  private ArchivoEvidenciaMantenimientoRepository evidencias;
  private AlmacenamientoArchivos almacenamiento;
  private AuditoriaWriter auditoria;
  private NotificacionService notificaciones;
  private TransicionOrdenService service;

  @BeforeEach
  void setUp() {
    ordenes = mock(OrdenMantenimientoRepository.class);
    historial = mock(HistorialEstadoOrdenRepository.class);
    ejecuciones = mock(OrdenMantenimientoEjecucionRepository.class);
    evidencias = mock(ArchivoEvidenciaMantenimientoRepository.class);
    almacenamiento = mock(AlmacenamientoArchivos.class);
    auditoria = mock(AuditoriaWriter.class);
    notificaciones = mock(NotificacionService.class);
    service =
        new TransicionOrdenService(
            ordenes,
            historial,
            ejecuciones,
            evidencias,
            almacenamiento,
            auditoria,
            notificaciones,
            new OrdenMantenimientoMapperImpl(),
            new ObjectMapper(),
            Clock.fixed(AHORA, ZoneOffset.UTC));
    when(ordenes.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(historial.ultimaSecuencia(ORDEN_ID)).thenReturn(1);
  }

  @Test
  void aceptarYProgramarGuardaQuienAceptoCuandoYLaFecha() {
    OrdenMantenimiento orden = orden(EstadoOrden.PROPUESTA, CREADOR_ID);

    OrdenMantenimientoResponse response =
        service.aceptarYProgramar(
            ORDEN_ID, new ProgramarOrdenRequest(HOY.plusDays(5), 3L), USUARIO_ID);

    assertThat(response.estado()).isEqualTo(EstadoOrden.PROGRAMADA);
    assertThat(orden.getAceptadoPorId()).isEqualTo(USUARIO_ID);
    assertThat(orden.getAceptadoEn()).isEqualTo(AHORA);
    assertThat(orden.getFechaProgramada()).isEqualTo(HOY.plusDays(5));
    assertThat(orden.getActualizadoEn()).isEqualTo(AHORA);

    HistorialEstadoOrden fila = historialGuardado();
    assertThat(fila.getSecuencia()).isEqualTo(2);
    assertThat(fila.getEstadoAnterior()).isEqualTo("PROPUESTA");
    assertThat(fila.getEstadoNuevo()).isEqualTo("PROGRAMADA");
    assertThat(fila.getRealizadoPorId()).isEqualTo(USUARIO_ID);
    assertThat(fila.getMotivo()).isNull();
    assertThat(fila.getCreadoEn()).isEqualTo(AHORA);

    JsonNode despues = auditoriaDeCambio("PROPUESTA");
    assertThat(despues.get("estado").asText()).isEqualTo("PROGRAMADA");
    assertThat(despues.get("evento").asText()).isEqualTo("ACEPTAR_Y_PROGRAMAR");
    assertThat(despues.get("fechaProgramada").asText()).isEqualTo("2026-10-25");
    assertThat(despues.has("motivo")).isFalse();

    verify(notificaciones)
        .notificar(
            eq(CREADOR_ID),
            eq("ORDEN_MANTENIMIENTO"),
            eq("Orden de mantenimiento programada"),
            contains("25/10/2026"),
            eq("orden_mantenimiento"),
            eq(ORDEN_ID),
            eq("orden-mantenimiento:" + ORDEN_ID + ":PROGRAMADA"));
  }

  @Test
  void sePuedeProgramarParaHoy() {
    orden(EstadoOrden.PROPUESTA, CREADOR_ID);

    assertThat(
            service
                .aceptarYProgramar(ORDEN_ID, new ProgramarOrdenRequest(HOY, 3L), USUARIO_ID)
                .fechaProgramada())
        .isEqualTo(HOY);
  }

  @Test
  void programarConFechaAnteriorAHoyResponde422SinCambiarElEstado() {
    OrdenMantenimiento orden = orden(EstadoOrden.PROPUESTA, CREADOR_ID);

    assertError(
        () ->
            service.aceptarYProgramar(
                ORDEN_ID, new ProgramarOrdenRequest(HOY.minusDays(1), 3L), USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PROPUESTA);
    verifyNoInteractions(ordenes, historial, auditoria, notificaciones);
  }

  @Test
  void programarSinFechaResponde422() {
    assertError(
        () -> service.aceptarYProgramar(ORDEN_ID, new ProgramarOrdenRequest(null, 3L), USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
  }

  @Test
  void laVersionEsObligatoria() {
    assertError(
        () -> service.aceptarYProgramar(ORDEN_ID, new ProgramarOrdenRequest(HOY, null), USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
    assertError(
        () ->
            service.descartar(ORDEN_ID, new CerrarSinEjecucionRequest("Motivo", null), USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
    assertError(
        () -> service.iniciarEjecucion(ORDEN_ID, new IniciarEjecucionRequest(null), USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
    verifyNoInteractions(ordenes);
  }

  @Test
  void unaOrdenInexistenteResponde404() {
    when(ordenes.findById(ORDEN_ID)).thenReturn(Optional.empty());

    assertError(
        () -> service.iniciarEjecucion(ORDEN_ID, new IniciarEjecucionRequest(3L), USUARIO_ID),
        HttpStatus.NOT_FOUND,
        "orden_no_encontrada");
  }

  @Test
  void unaVersionDesactualizadaResponde409SinCambiarElEstado() {
    OrdenMantenimiento orden = orden(EstadoOrden.PROPUESTA, CREADOR_ID);

    assertError(
        () -> service.aceptarYProgramar(ORDEN_ID, new ProgramarOrdenRequest(HOY, 2L), USUARIO_ID),
        HttpStatus.CONFLICT,
        "version_desactualizada");
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PROPUESTA);
    verify(ordenes, never()).saveAndFlush(any());
  }

  @Test
  void descartarConservaElMotivoYAvisaAlCreador() {
    OrdenMantenimiento orden = orden(EstadoOrden.PROPUESTA, CREADOR_ID);

    OrdenMantenimientoResponse response =
        service.descartar(
            ORDEN_ID, new CerrarSinEjecucionRequest("  Ya fue reparado  ", 3L), USUARIO_ID);

    assertThat(response.estado()).isEqualTo(EstadoOrden.DESCARTADA);
    assertThat(response.motivoCierreSinEjecucion()).isEqualTo("Ya fue reparado");
    assertThat(orden.getMotivoCierreSinEjecucion()).isEqualTo("Ya fue reparado");

    HistorialEstadoOrden fila = historialGuardado();
    assertThat(fila.getEstadoNuevo()).isEqualTo("DESCARTADA");
    assertThat(fila.getMotivo()).isEqualTo("Ya fue reparado");
    assertThat(fila.getRealizadoPorId()).isEqualTo(USUARIO_ID);

    JsonNode despues = auditoriaDeCambio("PROPUESTA");
    assertThat(despues.get("motivo").asText()).isEqualTo("Ya fue reparado");

    verify(notificaciones)
        .notificar(
            eq(CREADOR_ID),
            anyString(),
            eq("Orden de mantenimiento descartada"),
            contains("Ya fue reparado"),
            eq("orden_mantenimiento"),
            eq(ORDEN_ID),
            eq("orden-mantenimiento:" + ORDEN_ID + ":DESCARTADA"));
  }

  @Test
  void cancelarUnaOrdenProgramadaConservaElMotivo() {
    OrdenMantenimiento orden = orden(EstadoOrden.PROGRAMADA, CREADOR_ID);

    OrdenMantenimientoResponse response =
        service.cancelar(
            ORDEN_ID, new CerrarSinEjecucionRequest("Sin presupuesto", 3L), USUARIO_ID);

    assertThat(response.estado()).isEqualTo(EstadoOrden.CANCELADA);
    assertThat(orden.getMotivoCierreSinEjecucion()).isEqualTo("Sin presupuesto");
    assertThat(historialGuardado().getEstadoAnterior()).isEqualTo("PROGRAMADA");
    verify(notificaciones)
        .notificar(
            eq(CREADOR_ID),
            anyString(),
            eq("Orden de mantenimiento cancelada"),
            contains("Sin presupuesto"),
            anyString(),
            eq(ORDEN_ID),
            eq("orden-mantenimiento:" + ORDEN_ID + ":CANCELADA"));
  }

  @Test
  void descartarOCancelarSinMotivoResponde422SinCambiarElEstado() {
    OrdenMantenimiento orden = orden(EstadoOrden.PROPUESTA, CREADOR_ID);

    assertError(
        () -> service.descartar(ORDEN_ID, new CerrarSinEjecucionRequest(null, 3L), USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
    assertError(
        () -> service.cancelar(ORDEN_ID, new CerrarSinEjecucionRequest("   ", 3L), USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PROPUESTA);
    verifyNoInteractions(historial, auditoria, notificaciones);
  }

  @Test
  void unMotivoDemasiadoLargoResponde422() {
    String motivo = "a".repeat(TransicionOrdenService.LONGITUD_MAXIMA_MOTIVO + 1);

    assertError(
        () -> service.descartar(ORDEN_ID, new CerrarSinEjecucionRequest(motivo, 3L), USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
  }

  @Test
  void iniciarEjecucionCreaElRegistroConLaFechaDeInicio() {
    orden(EstadoOrden.PROGRAMADA, CREADOR_ID);

    OrdenMantenimientoResponse response =
        service.iniciarEjecucion(ORDEN_ID, new IniciarEjecucionRequest(3L), USUARIO_ID);

    assertThat(response.estado()).isEqualTo(EstadoOrden.EN_EJECUCION);
    ArgumentCaptor<OrdenMantenimientoEjecucion> captor =
        ArgumentCaptor.forClass(OrdenMantenimientoEjecucion.class);
    verify(ejecuciones).save(captor.capture());
    assertThat(captor.getValue().getOrdenMantenimientoId()).isEqualTo(ORDEN_ID);
    assertThat(captor.getValue().getIniciadoEn()).isEqualTo(AHORA);
    assertThat(captor.getValue().getResponsableUsuarioId()).isEqualTo(USUARIO_ID);
    assertThat(historialGuardado().getEstadoNuevo()).isEqualTo("EN_EJECUCION");
    assertThat(auditoriaDeCambio("PROGRAMADA").has("fechaProgramada")).isFalse();
    verifyNoInteractions(notificaciones);
  }

  @Test
  void unaPropuestaGeneradaPorElSistemaNoAvisaANadie() {
    orden(EstadoOrden.PROPUESTA, null);

    service.descartar(ORDEN_ID, new CerrarSinEjecucionRequest("Duplicada", 3L), USUARIO_ID);

    verifyNoInteractions(notificaciones);
  }

  @Test
  void deProgramarAEjecucionDirectoDesdePropuestaResponde409() {
    OrdenMantenimiento orden = orden(EstadoOrden.PROPUESTA, CREADOR_ID);

    assertError(
        () -> service.iniciarEjecucion(ORDEN_ID, new IniciarEjecucionRequest(3L), USUARIO_ID),
        HttpStatus.CONFLICT,
        "transicion_invalida");
    assertSinCambios(orden, EstadoOrden.PROPUESTA);
  }

  @Test
  void cancelarUnaPropuestaResponde409() {
    OrdenMantenimiento orden = orden(EstadoOrden.PROPUESTA, CREADOR_ID);

    assertError(
        () -> service.cancelar(ORDEN_ID, new CerrarSinEjecucionRequest("Motivo", 3L), USUARIO_ID),
        HttpStatus.CONFLICT,
        "transicion_invalida");
    assertSinCambios(orden, EstadoOrden.PROPUESTA);
  }

  @Test
  void cancelarUnaOrdenEnEjecucionResponde409() {
    OrdenMantenimiento orden = orden(EstadoOrden.EN_EJECUCION, CREADOR_ID);

    assertError(
        () -> service.cancelar(ORDEN_ID, new CerrarSinEjecucionRequest("Motivo", 3L), USUARIO_ID),
        HttpStatus.CONFLICT,
        "transicion_invalida");
    assertSinCambios(orden, EstadoOrden.EN_EJECUCION);
  }

  @ParameterizedTest
  @EnumSource(
      value = EstadoOrden.class,
      names = {"EJECUTADA", "DESCARTADA", "CANCELADA"})
  void unaOrdenTerminalNoAdmiteNingunaTransicion(EstadoOrden terminal) {
    OrdenMantenimiento orden = orden(terminal, CREADOR_ID);
    CerrarSinEjecucionRequest motivo = new CerrarSinEjecucionRequest("Motivo", 3L);

    assertError(
        () -> service.descartar(ORDEN_ID, motivo, USUARIO_ID),
        HttpStatus.CONFLICT,
        "estado_terminal");
    assertError(
        () -> service.cancelar(ORDEN_ID, motivo, USUARIO_ID),
        HttpStatus.CONFLICT,
        "estado_terminal");
    assertError(
        () -> service.aceptarYProgramar(ORDEN_ID, new ProgramarOrdenRequest(HOY, 3L), USUARIO_ID),
        HttpStatus.CONFLICT,
        "estado_terminal");
    assertError(
        () -> service.iniciarEjecucion(ORDEN_ID, new IniciarEjecucionRequest(3L), USUARIO_ID),
        HttpStatus.CONFLICT,
        "estado_terminal");
    assertSinCambios(orden, terminal);
  }

  @Test
  void cerrarComoEjecutadaGuardaElCierreYAvisaAlCreador() throws IOException {
    OrdenMantenimiento orden = orden(EstadoOrden.EN_EJECUCION, CREADOR_ID);
    ejecucion(HOY, null, "Constructora Los Altos");
    evidencia("evidencia-1.jpg");
    when(almacenamiento.existe("evidencia-1.jpg")).thenReturn(true);

    OrdenMantenimientoResponse response =
        service.cerrarComoEjecutada(ORDEN_ID, new CerrarComoEjecutadaRequest(3L), USUARIO_ID);

    assertThat(response.estado()).isEqualTo(EstadoOrden.EJECUTADA);
    assertThat(orden.getActualizadoEn()).isEqualTo(AHORA);
    HistorialEstadoOrden fila = historialGuardado();
    assertThat(fila.getEstadoAnterior()).isEqualTo("EN_EJECUCION");
    assertThat(fila.getEstadoNuevo()).isEqualTo("EJECUTADA");
    assertThat(fila.getRealizadoPorId()).isEqualTo(USUARIO_ID);

    JsonNode despues = auditoriaDeCambio("EN_EJECUCION");
    assertThat(despues.get("evento").asText()).isEqualTo("CERRAR_COMO_EJECUTADA");
    assertThat(despues.get("fechaEjecucion").asText()).isEqualTo("2026-10-20");
    assertThat(despues.get("responsableNombre").asText()).isEqualTo("Constructora Los Altos");
    assertThat(despues.get("evidencias").asLong()).isEqualTo(1);
    verify(notificaciones)
        .notificar(
            eq(CREADOR_ID),
            eq("ORDEN_MANTENIMIENTO"),
            eq("Orden de mantenimiento ejecutada"),
            contains("20/10/2026"),
            eq("orden_mantenimiento"),
            eq(ORDEN_ID),
            eq("orden-mantenimiento:" + ORDEN_ID + ":EJECUTADA"));
  }

  @Test
  void cerrarSinFechaNiEvidenciaResponde422ConLoQueFaltaYLaOrdenSigueEnEjecucion() {
    OrdenMantenimiento orden = orden(EstadoOrden.EN_EJECUCION, CREADOR_ID);
    ejecucion(null, USUARIO_ID, null);
    when(evidencias.findByOrdenMantenimientoIdAndArchivo_EstadoOrderByCreadoEnAsc(
            ORDEN_ID, EstadoArchivo.DISPONIBLE))
        .thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.cerrarComoEjecutada(
                    ORDEN_ID, new CerrarComoEjecutadaRequest(3L), USUARIO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("cierre_incompleto");
              assertThat(e.getErrores())
                  .extracting(error -> error.get("campo"))
                  .containsExactly("fechaEjecucion", "evidencias");
            });
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.EN_EJECUCION);
    verify(historial, never()).save(any());
    verify(ordenes, never()).saveAndFlush(any());
    verifyNoInteractions(auditoria, notificaciones);
  }

  @Test
  void cerrarSinResponsableResponde422() {
    orden(EstadoOrden.EN_EJECUCION, CREADOR_ID);
    when(ejecuciones.findById(ORDEN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.cerrarComoEjecutada(
                    ORDEN_ID, new CerrarComoEjecutadaRequest(3L), USUARIO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.getErrores())
                    .extracting(error -> error.get("campo"))
                    .contains("responsable"));
  }

  @Test
  void unResponsableEnBlancoNoCuentaParaElCierre() {
    orden(EstadoOrden.EN_EJECUCION, CREADOR_ID);
    ejecucion(HOY, null, "   ");

    assertThatThrownBy(
            () ->
                service.cerrarComoEjecutada(
                    ORDEN_ID, new CerrarComoEjecutadaRequest(3L), USUARIO_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.getErrores())
                    .extracting(error -> error.get("campo"))
                    .containsExactly("responsable", "evidencias"));
  }

  @Test
  void cerrarSinCuerpoExigeLaVersion() {
    assertError(
        () -> service.cerrarComoEjecutada(ORDEN_ID, null, USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida");
    verifyNoInteractions(ordenes, auditoria);
  }

  @Test
  void unaEvidenciaCuyoObjetoNoExisteNoCuenta() throws IOException {
    orden(EstadoOrden.EN_EJECUCION, CREADOR_ID);
    ejecucion(HOY, USUARIO_ID, null);
    evidencia("perdida.jpg");
    when(almacenamiento.existe("perdida.jpg")).thenReturn(false);

    assertError(
        () -> service.cerrarComoEjecutada(ORDEN_ID, new CerrarComoEjecutadaRequest(3L), USUARIO_ID),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "cierre_incompleto");
  }

  @Test
  void siElAlmacenamientoNoRespondeElCierreResponde503SinCambiarElEstado() throws IOException {
    OrdenMantenimiento orden = orden(EstadoOrden.EN_EJECUCION, CREADOR_ID);
    ejecucion(HOY, USUARIO_ID, null);
    evidencia("evidencia-1.jpg");
    when(almacenamiento.existe("evidencia-1.jpg")).thenThrow(new IOException("sin conexión"));

    assertError(
        () -> service.cerrarComoEjecutada(ORDEN_ID, new CerrarComoEjecutadaRequest(3L), USUARIO_ID),
        HttpStatus.SERVICE_UNAVAILABLE,
        "almacenamiento_no_disponible");
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.EN_EJECUCION);
    verify(ordenes, never()).saveAndFlush(any());
    verifyNoInteractions(auditoria, notificaciones);
  }

  @ParameterizedTest
  @EnumSource(
      value = EstadoOrden.class,
      names = {"PROPUESTA", "PROGRAMADA"})
  void soloUnaOrdenEnEjecucionSeCierraComoEjecutada(EstadoOrden estado) {
    OrdenMantenimiento orden = orden(estado, CREADOR_ID);

    assertError(
        () -> service.cerrarComoEjecutada(ORDEN_ID, new CerrarComoEjecutadaRequest(3L), USUARIO_ID),
        HttpStatus.CONFLICT,
        "transicion_invalida");
    assertSinCambios(orden, estado);
  }

  @Test
  void unaOrdenEjecutadaNoSeVuelveACerrar() {
    OrdenMantenimiento orden = orden(EstadoOrden.EJECUTADA, CREADOR_ID);

    assertError(
        () -> service.cerrarComoEjecutada(ORDEN_ID, new CerrarComoEjecutadaRequest(3L), USUARIO_ID),
        HttpStatus.CONFLICT,
        "estado_terminal");
    assertSinCambios(orden, EstadoOrden.EJECUTADA);
  }

  @Test
  void cerrarConVersionDesactualizadaResponde409() {
    orden(EstadoOrden.EN_EJECUCION, CREADOR_ID);

    assertError(
        () -> service.cerrarComoEjecutada(ORDEN_ID, new CerrarComoEjecutadaRequest(2L), USUARIO_ID),
        HttpStatus.CONFLICT,
        "version_desactualizada");
  }

  private void ejecucion(LocalDate fecha, UUID responsableId, String responsableNombre) {
    OrdenMantenimientoEjecucion ejecucion =
        new OrdenMantenimientoEjecucion(ORDEN_ID, AHORA.minusSeconds(3600), USUARIO_ID);
    ejecucion.registrar(fecha, responsableId, responsableNombre, AHORA);
    when(ejecuciones.findById(ORDEN_ID)).thenReturn(Optional.of(ejecucion));
  }

  private void evidencia(String clave) {
    Archivo archivo =
        new Archivo(
            UUID.randomUUID(),
            TipoUsoArchivo.EVIDENCIA_MANTENIMIENTO,
            null,
            null,
            clave,
            clave,
            "mini-" + clave,
            "image/jpeg",
            100,
            "a".repeat(64),
            10,
            10,
            null,
            null,
            USUARIO_ID,
            AHORA);
    when(evidencias.findByOrdenMantenimientoIdAndArchivo_EstadoOrderByCreadoEnAsc(
            ORDEN_ID, EstadoArchivo.DISPONIBLE))
        .thenReturn(List.of(new ArchivoEvidenciaMantenimiento(archivo, ORDEN_ID, null, AHORA)));
  }

  private OrdenMantenimiento orden(EstadoOrden estado, UUID creadoPorId) {
    OrdenMantenimiento orden =
        new OrdenMantenimiento(
            ORDEN_ID,
            PUENTE_ID,
            null,
            "/superestructura/drenaje",
            null,
            TipoOrden.CORRECTIVO,
            "Limpiar drenajes obstruidos",
            PrioridadOrden.MEDIA,
            creadoPorId,
            AHORA.minusSeconds(86_400));
    ReflectionTestUtils.setField(orden, "estado", estado);
    ReflectionTestUtils.setField(orden, "version", 3L);
    when(ordenes.findById(ORDEN_ID)).thenReturn(Optional.of(orden));
    when(ordenes.findByIdForUpdate(ORDEN_ID)).thenReturn(Optional.of(orden));
    return orden;
  }

  private HistorialEstadoOrden historialGuardado() {
    ArgumentCaptor<HistorialEstadoOrden> captor =
        ArgumentCaptor.forClass(HistorialEstadoOrden.class);
    verify(historial).save(captor.capture());
    assertThat(captor.getValue().getOrdenMantenimientoId()).isEqualTo(ORDEN_ID);
    assertThat(captor.getValue().getId()).isNotNull();
    return captor.getValue();
  }

  private JsonNode auditoriaDeCambio(String estadoAnterior) {
    ArgumentCaptor<JsonNode> antes = ArgumentCaptor.forClass(JsonNode.class);
    ArgumentCaptor<JsonNode> despues = ArgumentCaptor.forClass(JsonNode.class);
    verify(auditoria)
        .registrarEnTransaccion(
            eq(USUARIO_ID),
            eq(AccionAuditoria.CAMBIAR_ESTADO),
            eq("orden_mantenimiento"),
            eq(ORDEN_ID),
            antes.capture(),
            despues.capture(),
            isNull());
    assertThat(antes.getValue().get("estado").asText()).isEqualTo(estadoAnterior);
    return despues.getValue();
  }

  private void assertSinCambios(OrdenMantenimiento orden, EstadoOrden estado) {
    assertThat(orden.getEstado()).isEqualTo(estado);
    verify(historial, never()).save(any());
    verify(ordenes, never()).saveAndFlush(any());
    verifyNoInteractions(auditoria, notificaciones, ejecuciones);
  }

  private static void assertError(
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
