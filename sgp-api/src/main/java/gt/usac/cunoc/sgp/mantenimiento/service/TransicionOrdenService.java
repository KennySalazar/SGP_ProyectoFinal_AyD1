package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.archivo.entity.ArchivoEvidenciaMantenimiento;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoEvidenciaMantenimientoRepository;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.mantenimiento.dto.CerrarComoEjecutadaRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.CerrarSinEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.IniciarEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.ProgramarOrdenRequest;
import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimientoEjecucion;
import gt.usac.cunoc.sgp.mantenimiento.mapper.OrdenMantenimientoMapper;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.EventoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.HistorialEstadoOrdenRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoEjecucionRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única puerta de entrada para cambiar el estado de una orden de mantenimiento (DT-BE-07). Valida
 * la transición con {@link MaquinaEstadosOrden}, controla la versión optimista, registra el
 * historial y la auditoría, y avisa a quien creó la orden (RN-MTO-04, RN-MTO-09, RN-USR-09).
 */
@Service
public class TransicionOrdenService {

  static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");
  static final int LONGITUD_MAXIMA_MOTIVO = 2000;
  static final String TIPO_AVISO = "ORDEN_MANTENIMIENTO";
  static final String ENTIDAD = "orden_mantenimiento";

  private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final OrdenMantenimientoRepository ordenes;
  private final HistorialEstadoOrdenRepository historial;
  private final OrdenMantenimientoEjecucionRepository ejecuciones;
  private final ArchivoEvidenciaMantenimientoRepository evidencias;
  private final AlmacenamientoArchivos almacenamiento;
  private final AuditoriaWriter auditoria;
  private final NotificacionService notificaciones;
  private final OrdenMantenimientoMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;

  public TransicionOrdenService(
      OrdenMantenimientoRepository ordenes,
      HistorialEstadoOrdenRepository historial,
      OrdenMantenimientoEjecucionRepository ejecuciones,
      ArchivoEvidenciaMantenimientoRepository evidencias,
      AlmacenamientoArchivos almacenamiento,
      AuditoriaWriter auditoria,
      NotificacionService notificaciones,
      OrdenMantenimientoMapper mapper,
      ObjectMapper json,
      Clock clock) {
    this.ordenes = ordenes;
    this.historial = historial;
    this.ejecuciones = ejecuciones;
    this.evidencias = evidencias;
    this.almacenamiento = almacenamiento;
    this.auditoria = auditoria;
    this.notificaciones = notificaciones;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  /** PROPUESTA a PROGRAMADA: fecha no pasada; se guardan quién aceptó y cuándo. */
  @Transactional
  public OrdenMantenimientoResponse aceptarYProgramar(
      UUID ordenId, ProgramarOrdenRequest request, UUID usuarioId) {
    Long version = exigirVersion(request.version());
    LocalDate fecha = request.fechaProgramada();
    if (fecha == null) {
      throw invalida("fechaProgramada", "La fecha programada es obligatoria.");
    }
    if (fecha.isBefore(LocalDate.now(clock.withZone(ZONA_GUATEMALA)))) {
      throw invalida("fechaProgramada", "La fecha programada no puede ser anterior a hoy.");
    }

    OrdenMantenimiento orden = buscarConVersion(ordenId, version);
    EstadoOrden anterior = orden.getEstado();
    EstadoOrden destino = MaquinaEstadosOrden.destino(anterior, EventoOrden.ACEPTAR_Y_PROGRAMAR);
    Instant ahora = clock.instant();

    orden.registrarAceptacion(usuarioId, ahora, fecha);
    ObjectNode extra = json.createObjectNode().put("fechaProgramada", fecha.toString());
    registrarCambio(
        orden, anterior, destino, EventoOrden.ACEPTAR_Y_PROGRAMAR, usuarioId, null, ahora, extra);
    avisarAlCreador(
        orden,
        destino,
        "Orden de mantenimiento programada",
        "La orden de mantenimiento que creaste fue programada para el "
            + FORMATO_FECHA.format(fecha)
            + ".");
    return mapper.toResponse(ordenes.saveAndFlush(orden));
  }

  /** PROPUESTA a DESCARTADA con motivo obligatorio (RN-MTO-09). */
  @Transactional
  public OrdenMantenimientoResponse descartar(
      UUID ordenId, CerrarSinEjecucionRequest request, UUID usuarioId) {
    return cerrarSinEjecucion(
        ordenId,
        request,
        usuarioId,
        EventoOrden.DESCARTAR,
        "Orden de mantenimiento descartada",
        "La orden de mantenimiento que creaste fue descartada. Motivo: ");
  }

  /** PROGRAMADA a CANCELADA con motivo obligatorio (RN-MTO-09). */
  @Transactional
  public OrdenMantenimientoResponse cancelar(
      UUID ordenId, CerrarSinEjecucionRequest request, UUID usuarioId) {
    return cerrarSinEjecucion(
        ordenId,
        request,
        usuarioId,
        EventoOrden.CANCELAR,
        "Orden de mantenimiento cancelada",
        "La orden de mantenimiento que creaste fue cancelada. Motivo: ");
  }

  /** PROGRAMADA a EN_EJECUCION: crea el registro de ejecución con la fecha de inicio. */
  @Transactional
  public OrdenMantenimientoResponse iniciarEjecucion(
      UUID ordenId, IniciarEjecucionRequest request, UUID usuarioId) {
    Long version = exigirVersion(request.version());
    OrdenMantenimiento orden = buscarConVersion(ordenId, version);
    EstadoOrden anterior = orden.getEstado();
    EstadoOrden destino = MaquinaEstadosOrden.destino(anterior, EventoOrden.INICIAR_EJECUCION);
    Instant ahora = clock.instant();

    ejecuciones.save(new OrdenMantenimientoEjecucion(orden.getId(), ahora, usuarioId));
    registrarCambio(
        orden, anterior, destino, EventoOrden.INICIAR_EJECUCION, usuarioId, null, ahora, null);
    return mapper.toResponse(ordenes.saveAndFlush(orden));
  }

  /**
   * EN_EJECUCION a EJECUTADA (RN-MTO-05). Exige fecha de ejecución, responsable y al menos una
   * fotografía de evidencia disponible en el almacenamiento; si falta algo responde 422 con el
   * detalle y la orden no cambia. El estado, el historial y la auditoría se guardan juntos.
   */
  @Transactional
  public OrdenMantenimientoResponse cerrarComoEjecutada(
      UUID ordenId, CerrarComoEjecutadaRequest request, UUID usuarioId) {
    Long version = exigirVersion(request == null ? null : request.version());
    OrdenMantenimiento orden = conVersion(ordenes.findByIdForUpdate(ordenId), ordenId, version);
    EstadoOrden anterior = orden.getEstado();
    EstadoOrden destino = MaquinaEstadosOrden.destino(anterior, EventoOrden.CERRAR_COMO_EJECUTADA);

    Optional<OrdenMantenimientoEjecucion> ejecucion = ejecuciones.findById(ordenId);
    LocalDate fecha = ejecucion.map(OrdenMantenimientoEjecucion::getFechaEjecucion).orElse(null);
    UUID responsableId =
        ejecucion.map(OrdenMantenimientoEjecucion::getResponsableUsuarioId).orElse(null);
    String responsableNombre =
        ejecucion.map(OrdenMantenimientoEjecucion::getResponsableNombre).orElse(null);
    long fotografias = evidenciasAlmacenadas(ordenId);

    List<Map<String, String>> faltantes = new ArrayList<>();
    if (fecha == null) {
      faltantes.add(faltante("fechaEjecucion", "Falta la fecha de ejecución."));
    }
    if (responsableId == null && (responsableNombre == null || responsableNombre.isBlank())) {
      faltantes.add(faltante("responsable", "Falta el responsable de la intervención."));
    }
    if (fotografias == 0) {
      faltantes.add(faltante("evidencias", "Falta al menos una fotografía de evidencia."));
    }
    if (!faltantes.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "cierre_incompleto",
          "Cierre incompleto",
          faltantes.stream()
              .map(f -> f.get("mensaje"))
              .collect(Collectors.joining(" ", "No se puede cerrar la orden como ejecutada. ", "")),
          faltantes);
    }

    Instant ahora = clock.instant();
    ObjectNode extra =
        json.createObjectNode()
            .put("fechaEjecucion", fecha.toString())
            .put("responsableUsuarioId", responsableId == null ? null : responsableId.toString())
            .put("responsableNombre", responsableNombre)
            .put("evidencias", fotografias);
    registrarCambio(
        orden, anterior, destino, EventoOrden.CERRAR_COMO_EJECUTADA, usuarioId, null, ahora, extra);
    avisarAlCreador(
        orden,
        destino,
        "Orden de mantenimiento ejecutada",
        "La orden de mantenimiento que creaste se ejecutó el " + FORMATO_FECHA.format(fecha) + ".");
    return mapper.toResponse(ordenes.saveAndFlush(orden));
  }

  /** Evidencias disponibles cuyo objeto sigue en el almacenamiento; 503 si este no responde. */
  private long evidenciasAlmacenadas(UUID ordenId) {
    long disponibles = 0;
    for (ArchivoEvidenciaMantenimiento evidencia :
        evidencias.findByOrdenMantenimientoIdAndArchivo_EstadoOrderByCreadoEnAsc(
            ordenId, EstadoArchivo.DISPONIBLE)) {
      try {
        if (almacenamiento.existe(evidencia.getArchivo().getClaveObjetoOriginal())) {
          disponibles++;
        }
      } catch (IOException e) {
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "almacenamiento_no_disponible",
            "Almacenamiento no disponible",
            "No se pudo verificar la evidencia fotográfica. La orden sigue EN_EJECUCION; intente"
                + " de nuevo más tarde.");
      }
    }
    return disponibles;
  }

  private static Map<String, String> faltante(String campo, String mensaje) {
    return Map.of("campo", campo, "mensaje", mensaje);
  }

  private OrdenMantenimientoResponse cerrarSinEjecucion(
      UUID ordenId,
      CerrarSinEjecucionRequest request,
      UUID usuarioId,
      EventoOrden evento,
      String tituloAviso,
      String mensajeAviso) {
    Long version = exigirVersion(request.version());
    String motivo = exigirMotivo(request.motivo());
    OrdenMantenimiento orden = buscarConVersion(ordenId, version);
    EstadoOrden anterior = orden.getEstado();
    EstadoOrden destino = MaquinaEstadosOrden.destino(anterior, evento);
    Instant ahora = clock.instant();

    orden.registrarMotivoCierre(motivo);
    registrarCambio(orden, anterior, destino, evento, usuarioId, motivo, ahora, null);
    avisarAlCreador(orden, destino, tituloAviso, mensajeAviso + motivo);
    return mapper.toResponse(ordenes.saveAndFlush(orden));
  }

  /** Aplica el estado validado, agrega la fila de historial y registra la auditoría. */
  private void registrarCambio(
      OrdenMantenimiento orden,
      EstadoOrden anterior,
      EstadoOrden destino,
      EventoOrden evento,
      UUID usuarioId,
      String motivo,
      Instant ahora,
      ObjectNode extra) {
    orden.aplicarTransicion(destino, ahora);
    historial.save(
        new HistorialEstadoOrden(
            UuidV7Generator.generate(),
            orden.getId(),
            historial.ultimaSecuencia(orden.getId()) + 1,
            anterior.name(),
            destino.name(),
            usuarioId,
            motivo,
            ahora));

    ObjectNode antes = json.createObjectNode().put("estado", anterior.name());
    ObjectNode despues =
        json.createObjectNode().put("estado", destino.name()).put("evento", evento.name());
    if (motivo != null) {
      despues.put("motivo", motivo);
    }
    if (extra != null) {
      despues.setAll(extra);
    }
    auditoria.registrarEnTransaccion(
        usuarioId, AccionAuditoria.CAMBIAR_ESTADO, ENTIDAD, orden.getId(), antes, despues, null);
  }

  /**
   * Avisa a quien creó la orden manualmente (HU-040). Las propuestas generadas por el sistema no
   * tienen creador y su aviso inicial lo emite HU-046.
   */
  private void avisarAlCreador(
      OrdenMantenimiento orden, EstadoOrden destino, String titulo, String mensaje) {
    if (orden.getCreadoPorId() == null) {
      return;
    }
    notificaciones.notificar(
        orden.getCreadoPorId(),
        TIPO_AVISO,
        titulo,
        mensaje,
        ENTIDAD,
        orden.getId(),
        "orden-mantenimiento:" + orden.getId() + ":" + destino.name());
  }

  private OrdenMantenimiento buscarConVersion(UUID ordenId, Long version) {
    return conVersion(ordenes.findById(ordenId), ordenId, version);
  }

  private OrdenMantenimiento conVersion(
      Optional<OrdenMantenimiento> encontrada, UUID ordenId, Long version) {
    OrdenMantenimiento orden =
        encontrada.orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "orden_no_encontrada",
                    "Orden no encontrada",
                    "No existe la orden de mantenimiento " + ordenId));
    if (!Objects.equals(orden.getVersion(), version)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "version_desactualizada",
          "La orden cambio",
          "Otro usuario modifico la orden. Vuelva a consultarla antes de reintentar.");
    }
    return orden;
  }

  private Long exigirVersion(Long version) {
    if (version == null) {
      throw invalida("version", "La version de la orden es obligatoria.");
    }
    return version;
  }

  private String exigirMotivo(String motivo) {
    if (motivo == null || motivo.isBlank()) {
      throw invalida("motivo", "El motivo es obligatorio.");
    }
    String limpio = motivo.strip();
    if (limpio.length() > LONGITUD_MAXIMA_MOTIVO) {
      throw invalida(
          "motivo", "El motivo no puede exceder " + LONGITUD_MAXIMA_MOTIVO + " caracteres.");
    }
    return limpio;
  }

  private static ApiException invalida(String campo, String mensaje) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida",
        "Solicitud invalida",
        mensaje,
        List.of(Map.of("campo", campo, "mensaje", mensaje)));
  }
}
