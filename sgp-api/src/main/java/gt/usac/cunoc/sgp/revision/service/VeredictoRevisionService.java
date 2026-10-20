package gt.usac.cunoc.sgp.revision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmacionEstadoIcResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.service.ConfirmacionEstadoIcService;
import gt.usac.cunoc.sgp.inspeccion.service.TransicionInspeccionService;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.revision.dto.EmitirVeredictoRequest;
import gt.usac.cunoc.sgp.revision.dto.RevisionResultadoResponse;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.mapper.RevisionResultadoMapper;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import gt.usac.cunoc.sgp.revision.model.VeredictoRevision;
import gt.usac.cunoc.sgp.revision.repository.ObservacionRevisionRepository;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('CATEDRATICO')")
public class VeredictoRevisionService {
  private final RevisionRepository revisiones;
  private final ObservacionRevisionRepository observaciones;
  private final InspeccionRepository inspecciones;
  private final PuenteRepository puentes;
  private final TransicionInspeccionService transiciones;
  private final ConfirmacionEstadoIcService confirmaciones;
  private final RevisionResultadoMapper mapper;
  private final AuditoriaWriter auditoria;
  private final NotificacionService notificaciones;
  private final Validator validator;
  private final ObjectMapper json;
  private final Clock clock;

  public VeredictoRevisionService(
      RevisionRepository revisiones,
      ObservacionRevisionRepository observaciones,
      InspeccionRepository inspecciones,
      PuenteRepository puentes,
      TransicionInspeccionService transiciones,
      ConfirmacionEstadoIcService confirmaciones,
      RevisionResultadoMapper mapper,
      AuditoriaWriter auditoria,
      NotificacionService notificaciones,
      Validator validator,
      ObjectMapper json,
      Clock clock) {
    this.revisiones = revisiones;
    this.observaciones = observaciones;
    this.inspecciones = inspecciones;
    this.puentes = puentes;
    this.transiciones = transiciones;
    this.confirmaciones = confirmaciones;
    this.mapper = mapper;
    this.auditoria = auditoria;
    this.notificaciones = notificaciones;
    this.validator = validator;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public RevisionResultadoResponse emitir(UUID id, UUID actor, EmitirVeredictoRequest request) {
    if (!observaciones.existeCicloDelRevisor(id, actor)) throw noEncontrada();
    if (request == null || !validator.validate(request).isEmpty())
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "veredicto_invalido",
          "Indique un veredicto y una calificación entre 0 y 100 con hasta dos decimales.");
    // Antes de tomar ciclo/inspección, la aprobación bloquea el puente como toda publicación.
    if (request.veredicto() == VeredictoRevision.APROBADA) {
      var puenteId = revisiones.findPuenteId(id).orElseThrow(this::noEncontrada);
      puentes.findByIdForUpdate(puenteId).orElseThrow(this::noEncontrada);
    }
    Revision ciclo;
    try {
      ciclo = revisiones.findByIdForUpdate(id).orElseThrow(this::noEncontrada);
      var inspeccion =
          inspecciones
              .findByIdAndEliminadoEnIsNullForUpdate(ciclo.getInspeccion().getId())
              .orElseThrow(this::noEncontrada);
      if (!ciclo.getRevisorId().equals(actor)) throw noEncontrada();
      if (!"EN_CURSO".equals(ciclo.getEstado()) || !"EN_REVISION".equals(inspeccion.getEstado()))
        throw error(
            HttpStatus.CONFLICT,
            "revision_ya_resuelta",
            "El veredicto requiere un ciclo EN_CURSO y una inspección EN_REVISION.");
    } catch (PessimisticLockingFailureException e) {
      throw error(
          HttpStatus.CONFLICT,
          "revision_en_proceso",
          "Otra operación está modificando el ciclo. Reintente.");
    }
    String general =
        request.observacionGeneral() == null
            ? ciclo.getObservacionGeneral()
            : request.observacionGeneral().strip();
    if (general != null && general.isBlank()) general = null;
    if (request.veredicto() != VeredictoRevision.APROBADA && general == null)
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "observacion_general_obligatoria",
          "Solicitar cambios o rechazar requiere observación general.");
    UUID inspeccionId = ciclo.getInspeccion().getId();
    String motivo = "Veredicto del ciclo " + ciclo.getNumeroCiclo();
    switch (request.veredicto()) {
      case APROBADA -> {
        if (observaciones.countByRevisionIdAndEstado(id, EstadoObservacion.PENDIENTE) > 0)
          throw error(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "observaciones_pendientes",
              "No puede aprobar un ciclo con observaciones pendientes.");
        ConfirmarEstadoIcRequest decision = request.confirmacionEstado();
        if (decision == null) {
          var calculo = confirmaciones.consultarRevision(id).calculo();
          decision =
              new ConfirmarEstadoIcRequest(calculo.estadoCalculado(), calculo.hashEntrada(), null);
        }
        confirmaciones.confirmarRevision(id, decision);
        transiciones.aprobar(inspeccionId, actor, id);
      }
      case CAMBIOS_SOLICITADOS -> transiciones.solicitarCambios(inspeccionId, actor, motivo, id);
      case RECHAZADA -> transiciones.rechazar(inspeccionId, actor, motivo, id);
    }
    ciclo.resolver(request.veredicto().name(), general, request.calificacion(), clock.instant());
    revisiones.saveAndFlush(ciclo);
    // La nota y el texto privado nunca se copian a bitácoras, avisos o respuestas públicas.
    auditoria.registrarEnTransaccion(
        actor,
        AccionAuditoria.CAMBIAR_ESTADO,
        "revision",
        id,
        json.createObjectNode().put("estado", "EN_CURSO"),
        json.createObjectNode()
            .put("estado", "RESUELTA")
            .put("veredicto", request.veredicto().name()),
        "HU-038");
    notificaciones.registrar(
        ciclo.getInspeccion().getAutorId(),
        "REVISION_RESUELTA",
        "Revisión resuelta",
        "Tu revisión tiene un resultado. Consulta el detalle privado.",
        "revision",
        id,
        "revision-resuelta:" + id);
    return mapper.toResponse(ciclo);
  }

  @Transactional
  public ConfirmacionEstadoIcResponse estadoPuente(UUID id, UUID actor) {
    if (!observaciones.existeCicloDelRevisor(id, actor)) throw noEncontrada();
    return confirmaciones.consultarRevision(id);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAnyRole('CATEDRATICO','ESTUDIANTE')")
  public RevisionResultadoResponse resultado(UUID id, UUID actor) {
    if (!observaciones.existeCicloLegible(id, actor)) throw noEncontrada();
    return mapper.toResponse(revisiones.findById(id).orElseThrow(this::noEncontrada));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAnyRole('CATEDRATICO','ESTUDIANTE')")
  public List<RevisionResultadoResponse> historial(UUID inspeccionId, UUID actor) {
    var ciclos = revisiones.historialPrivado(inspeccionId, actor);
    if (ciclos.isEmpty()) throw noEncontrada();
    return ciclos.stream().map(mapper::toResponse).toList();
  }

  private ApiException noEncontrada() {
    return error(
        HttpStatus.NOT_FOUND,
        "revision_no_encontrada",
        "La revisión no está disponible para este usuario.");
  }

  private ApiException error(HttpStatus status, String codigo, String detalle) {
    return new ApiException(status, codigo, "Revisión académica", detalle);
  }
}
