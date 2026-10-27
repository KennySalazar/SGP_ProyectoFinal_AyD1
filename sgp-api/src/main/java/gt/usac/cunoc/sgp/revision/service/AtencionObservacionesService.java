package gt.usac.cunoc.sgp.revision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.service.TransicionInspeccionService;
import gt.usac.cunoc.sgp.revision.dto.ObservacionResponse;
import gt.usac.cunoc.sgp.revision.dto.ResolverObservacionRequest;
import gt.usac.cunoc.sgp.revision.dto.RevisionResultadoResponse;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.mapper.ObservacionMapper;
import gt.usac.cunoc.sgp.revision.mapper.RevisionResultadoMapper;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import gt.usac.cunoc.sgp.revision.repository.ObservacionRevisionRepository;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ESTUDIANTE')")
public class AtencionObservacionesService {
  private final RevisionRepository revisiones;
  private final ObservacionRevisionRepository observaciones;
  private final InspeccionRepository inspecciones;
  private final TransicionInspeccionService transiciones;
  private final ObservacionMapper mapper;
  private final RevisionResultadoMapper resultados;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Validator validator;
  private final Clock clock;

  public AtencionObservacionesService(
      RevisionRepository revisiones,
      ObservacionRevisionRepository observaciones,
      InspeccionRepository inspecciones,
      TransicionInspeccionService transiciones,
      ObservacionMapper mapper,
      RevisionResultadoMapper resultados,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Validator validator,
      Clock clock) {
    this.revisiones = revisiones;
    this.observaciones = observaciones;
    this.inspecciones = inspecciones;
    this.transiciones = transiciones;
    this.mapper = mapper;
    this.resultados = resultados;
    this.auditoria = auditoria;
    this.json = json;
    this.validator = validator;
    this.clock = clock;
  }

  @Transactional
  public RevisionResultadoResponse atender(UUID id, UUID actor) {
    var ciclo = autorizar(id, actor);
    String estado = ciclo.getInspeccion().getEstado();
    if ("CAMBIOS_SOLICITADOS".equals(estado))
      transiciones.devolverABorrador(ciclo.getInspeccion().getId(), actor, id);
    else if (!"BORRADOR".equals(estado)) throw cerrado();
    return resultados.toResponse(ciclo);
  }

  @Transactional
  public ObservacionResponse resolver(
      UUID revisionId, UUID id, UUID actor, ResolverObservacionRequest request) {
    var ciclo = autorizar(revisionId, actor);
    if (!"BORRADOR".equals(ciclo.getInspeccion().getEstado())) throw cerrado();
    if (request != null && !validator.validate(request).isEmpty())
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "respuesta_invalida",
          "La respuesta admite hasta 2000 caracteres.");
    String respuesta =
        request == null || request.respuesta() == null ? null : request.respuesta().strip();
    if (respuesta != null && respuesta.isBlank()) respuesta = null;
    var obs = observaciones.findByIdAndRevisionId(id, revisionId).orElseThrow(this::noEncontrada);
    if (obs.getEstado() == EstadoObservacion.RESUELTA) {
      if (!Objects.equals(obs.getRespuestaAutor(), respuesta)
          || !actor.equals(obs.getResueltoPorId()))
        throw error(
            HttpStatus.CONFLICT,
            "observacion_ya_resuelta",
            "La observación ya fue resuelta con otra respuesta.");
      return mapper.toResponse(obs);
    }
    obs.resolver(actor, respuesta, clock.instant());
    observaciones.flush();
    auditoria.registrarEnTransaccion(
        actor,
        AccionAuditoria.MODIFICAR,
        "observacion_revision",
        id,
        json.createObjectNode().put("estado", "PENDIENTE"),
        json.createObjectNode().put("estado", "RESUELTA"),
        "HU-039");
    return mapper.toResponse(obs);
  }

  private Revision autorizar(UUID id, UUID actor) {
    if (!observaciones.existeCicloLegible(id, actor)) throw noEncontrada();
    try {
      var ciclo = revisiones.findByIdForUpdate(id).orElseThrow(this::noEncontrada);
      var inspeccion =
          inspecciones
              .findByIdAndEliminadoEnIsNullForUpdate(ciclo.getInspeccion().getId())
              .orElseThrow(this::noEncontrada);
      if (!actor.equals(inspeccion.getAutorId())) throw noEncontrada();
      if (!"ESTUDIANTE".equals(inspeccion.getRolAutorCreacion())
          || !"RESUELTA".equals(ciclo.getEstado())
          || !"CAMBIOS_SOLICITADOS".equals(ciclo.getVeredicto())
          || revisiones.ultimoCiclo(inspeccion.getId()) != ciclo.getNumeroCiclo()) throw cerrado();
      return ciclo;
    } catch (PessimisticLockingFailureException e) {
      throw error(
          HttpStatus.CONFLICT,
          "revision_en_proceso",
          "Otra operación modifica el ciclo. Reintente.");
    }
  }

  private ApiException noEncontrada() {
    return error(
        HttpStatus.NOT_FOUND,
        "revision_no_encontrada",
        "La revisión no está disponible para este usuario.");
  }

  private ApiException cerrado() {
    return error(
        HttpStatus.CONFLICT,
        "observacion_no_resoluble",
        "Solo el autor puede atender y resolver las observaciones del último ciclo con cambios solicitados, antes de reenviar.");
  }

  private ApiException error(HttpStatus status, String code, String detail) {
    return new ApiException(status, code, "Atención de observaciones", detail);
  }
}
