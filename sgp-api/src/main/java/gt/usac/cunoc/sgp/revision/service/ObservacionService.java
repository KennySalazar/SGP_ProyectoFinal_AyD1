package gt.usac.cunoc.sgp.revision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.validation.AnclajeInvalidoException;
import gt.usac.cunoc.sgp.formulario.validation.ValidadorAnclajeFormulario;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.validation.ValidadorCategoriaFotografica;
import gt.usac.cunoc.sgp.revision.dto.*;
import gt.usac.cunoc.sgp.revision.entity.ObservacionRevision;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.mapper.ObservacionMapper;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import gt.usac.cunoc.sgp.revision.model.TipoDestinoObservacion;
import gt.usac.cunoc.sgp.revision.repository.ObservacionRevisionRepository;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('CATEDRATICO')")
@Transactional(readOnly = true)
public class ObservacionService {
  private final RevisionRepository revisiones;
  private final InspeccionRepository inspecciones;
  private final ObservacionRevisionRepository observaciones;
  private final VersionFormularioRepository versiones;
  private final ValidadorAnclajeFormulario anclajes;
  private final ValidadorCategoriaFotografica categorias;
  private final Validator validator;
  private final AuditoriaWriter auditoria;
  private final ObservacionMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;

  public ObservacionService(
      RevisionRepository revisiones,
      InspeccionRepository inspecciones,
      ObservacionRevisionRepository observaciones,
      VersionFormularioRepository versiones,
      ValidadorAnclajeFormulario anclajes,
      ValidadorCategoriaFotografica categorias,
      Validator validator,
      AuditoriaWriter auditoria,
      ObservacionMapper mapper,
      ObjectMapper json,
      Clock clock) {
    this.revisiones = revisiones;
    this.inspecciones = inspecciones;
    this.observaciones = observaciones;
    this.versiones = versiones;
    this.anclajes = anclajes;
    this.categorias = categorias;
    this.validator = validator;
    this.auditoria = auditoria;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  @PreAuthorize("hasRole('ESTUDIANTE')")
  public Page<CicloObservacionesResponse> recibidas(UUID actor, int pagina, int tamanio) {
    if (actor == null)
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    if (pagina < 0 || tamanio < 1 || tamanio > 100)
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser no negativa y el tamaño debe estar entre 1 y 100.");
    return observaciones
        .ciclosDelAutor(actor, PageRequest.of(pagina, tamanio))
        .map(
            r ->
                new CicloObservacionesResponse(
                    r.getId(),
                    r.getInspeccion().getId(),
                    r.getNumeroCiclo(),
                    r.getEstado(),
                    r.getEnviadaEn()));
  }

  @Transactional
  public ObservacionResponse crear(UUID revisionId, UUID actor, CrearObservacionRequest request) {
    Revision ciclo = autorizar(revisionId, actor, true);
    validar(request);
    validarDestino(ciclo, request.destino());
    var destino = request.destino();
    UUID id = UuidV7Generator.generate();
    ObservacionRevision observacion =
        destino.tipo() == TipoDestinoObservacion.ELEMENTO
            ? ObservacionRevision.paraElemento(
                id,
                ciclo,
                destino.elementoRef(),
                destino.instanciaElementoId(),
                request.texto(),
                actor,
                clock.instant())
            : ObservacionRevision.paraCategoriaFotografica(
                id, ciclo, destino.categoriaFotografica(), request.texto(), actor, clock.instant());
    observaciones.saveAndFlush(observacion);
    var respuesta = mapper.toResponse(observacion);
    auditar(actor, AccionAuditoria.CREAR, id, null, json.valueToTree(respuesta));
    return respuesta;
  }

  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ESTUDIANTE')")
  public ListadoObservacionesResponse listar(UUID revisionId, UUID actor) {
    autorizar(revisionId, actor, false);
    var lista =
        observaciones.findByRevisionIdOrderByCreadoEnAscIdAsc(revisionId).stream()
            .map(mapper::toResponse)
            .toList();
    long pendientes = lista.stream().filter(o -> o.estado() == EstadoObservacion.PENDIENTE).count();
    return new ListadoObservacionesResponse(
        revisionId,
        lista,
        lista.size(),
        pendientes,
        new ObservacionGeneralResponse(revisionId, observaciones.buscarGeneral(revisionId)));
  }

  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ESTUDIANTE')")
  public ObservacionResponse obtener(UUID revisionId, UUID id, UUID actor) {
    autorizar(revisionId, actor, false);
    return mapper.toResponse(buscar(revisionId, id));
  }

  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ESTUDIANTE')")
  public ContadorObservacionesResponse contar(UUID revisionId, UUID actor) {
    autorizar(revisionId, actor, false);
    var cuenta = observaciones.contarPorCiclo(revisionId, EstadoObservacion.PENDIENTE);
    return new ContadorObservacionesResponse(revisionId, cuenta.getTotal(), cuenta.getPendientes());
  }

  @Transactional
  public ObservacionResponse editar(
      UUID revisionId, UUID id, UUID actor, EditarObservacionRequest request) {
    autorizar(revisionId, actor, true);
    var observacion = buscar(revisionId, id);
    validar(request);
    JsonNode antes = json.valueToTree(mapper.toResponse(observacion));
    observacion.editarTexto(request.texto());
    observaciones.flush();
    var respuesta = mapper.toResponse(observacion);
    auditar(actor, AccionAuditoria.MODIFICAR, id, antes, json.valueToTree(respuesta));
    return respuesta;
  }

  @Transactional
  public void eliminar(UUID revisionId, UUID id, UUID actor) {
    autorizar(revisionId, actor, true);
    var observacion = buscar(revisionId, id);
    JsonNode antes = json.valueToTree(mapper.toResponse(observacion));
    observaciones.delete(observacion);
    observaciones.flush();
    // El catálogo existente no contiene ELIMINAR: MODIFICAR con posterior null registra la baja.
    auditar(actor, AccionAuditoria.MODIFICAR, id, antes, null);
  }

  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ESTUDIANTE')")
  public ObservacionGeneralResponse obtenerGeneral(UUID revisionId, UUID actor) {
    autorizar(revisionId, actor, false);
    return new ObservacionGeneralResponse(revisionId, observaciones.buscarGeneral(revisionId));
  }

  @Transactional
  public ObservacionGeneralResponse guardarGeneral(
      UUID revisionId, UUID actor, ObservacionGeneralRequest request) {
    autorizar(revisionId, actor, true);
    validar(request);
    var antes = new ObservacionGeneralResponse(revisionId, observaciones.buscarGeneral(revisionId));
    observaciones.actualizarGeneral(revisionId, request.texto());
    var despues = new ObservacionGeneralResponse(revisionId, request.texto());
    auditoria.registrarEnTransaccion(
        actor,
        antes.texto() == null ? AccionAuditoria.CREAR : AccionAuditoria.MODIFICAR,
        "revision_observacion_general",
        revisionId,
        json.valueToTree(antes),
        json.valueToTree(despues),
        null);
    return despues;
  }

  /**
   * Orden compartido con iniciar: ciclo (su consulta une inspección), después inspección. Ambos
   * bloqueos se mantienen hasta confirmar datos y bitácora. Un cierre que solo bloquee inspección
   * también se serializa. TransicionInspeccionService.aprobar/solicitarCambios/rechazar todavía
   * cambian solo la inspección: su futura coordinación deberá resolver también Revision.
   */
  private Revision autorizar(UUID id, UUID actor, boolean escritura) {
    if (actor == null)
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    // Autorizar antes del bloqueo evita revelar al ajeno si un ciclo existente está ocupado.
    if (!(escritura
        ? observaciones.existeCicloDelRevisor(id, actor)
        : observaciones.existeCicloLegible(id, actor))) throw noEncontrada();
    Revision ciclo;
    try {
      ciclo =
          (escritura ? revisiones.findByIdForUpdate(id) : revisiones.findById(id))
              .orElseThrow(this::noEncontrada);
      if ((!ciclo.getRevisorId().equals(actor)
              && (escritura || !ciclo.getInspeccion().getAutorId().equals(actor)))
          || ciclo.getInspeccion().getEliminadoEn() != null) throw noEncontrada();
      if (escritura) {
        var inspeccion =
            inspecciones
                .findByIdAndEliminadoEnIsNullForUpdate(ciclo.getInspeccion().getId())
                .orElseThrow(this::noEncontrada);
        if (!"EN_CURSO".equals(ciclo.getEstado())
            || !"EN_REVISION".equals(inspeccion.getEstado())) {
          throw new ApiException(
              HttpStatus.CONFLICT,
              "observaciones_ciclo_cerrado",
              "Ciclo no editable",
              "Las observaciones requieren un ciclo EN_CURSO y una inspección EN_REVISION.");
        }
      }
    } catch (PessimisticLockingFailureException e) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "revision_en_proceso",
          "Revisión en proceso",
          "Otra operación está modificando la revisión. Reintente la operación.");
    }
    return ciclo;
  }

  private ObservacionRevision buscar(UUID revisionId, UUID id) {
    return observaciones
        .findByIdAndRevisionId(id, revisionId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "observacion_no_encontrada",
                    "Observación no encontrada",
                    "La observación no pertenece al ciclo solicitado."));
  }

  private void validarDestino(Revision ciclo, DestinoObservacion destino) {
    try {
      if (destino.tipo() == TipoDestinoObservacion.CATEGORIA_FOTOGRAFICA) {
        categorias.validar(destino.categoriaFotografica().name());
        return;
      }
      JsonNode contenido = ciclo.getContenidoEnviado();
      UUID versionId;
      try {
        versionId = UUID.fromString(contenido.path("versionFormularioId").asText());
      } catch (IllegalArgumentException | NullPointerException e) {
        throw snapshotInvalido();
      }
      var version = versiones.findById(versionId).orElseThrow(this::snapshotInvalido);
      if (!contenido.path("datos").isObject()) throw snapshotInvalido();
      anclajes.validar(
          version.getEsquema(),
          contenido.path("datos"),
          destino.elementoRef(),
          destino.instanciaElementoId());
    } catch (AnclajeInvalidoException e) {
      String campo =
          switch (e.getMotivo()) {
            case INSTANCIA_REQUERIDA,
                    INSTANCIA_NO_ENCONTRADA,
                    INSTANCIA_DUPLICADA,
                    INSTANCIA_NO_PERMITIDA,
                    DATOS_REPETIBLE_INVALIDOS ->
                "destino.instanciaElementoId";
            case CATEGORIA_FOTOGRAFICA_INVALIDA -> "destino.categoriaFotografica";
            default -> "destino.elementoRef";
          };
      throw invalido(List.of(Map.of("campo", campo, "mensaje", e.getMessage())));
    }
  }

  private void validar(Object request) {
    if (request == null)
      throw invalido(
          List.of(Map.of("campo", "solicitud", "mensaje", "La solicitud es obligatoria")));
    var errores =
        validator.validate(request).stream()
            .map(e -> Map.of("campo", e.getPropertyPath().toString(), "mensaje", e.getMessage()))
            .sorted(Comparator.comparing(e -> e.get("campo")))
            .toList();
    if (!errores.isEmpty()) throw invalido(errores);
  }

  private void auditar(
      UUID actor, AccionAuditoria accion, UUID id, JsonNode antes, JsonNode despues) {
    auditoria.registrarEnTransaccion(
        actor, accion, "observacion_revision", id, antes, despues, null);
  }

  private ApiException noEncontrada() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "revision_no_encontrada",
        "Revisión no encontrada",
        "No existe la revisión solicitada o no corresponde al catedrático autenticado.");
  }

  private ApiException snapshotInvalido() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "revision_contenido_invalido",
        "Contenido enviado no disponible",
        "El ciclo no contiene una versión histórica y datos congelados válidos.");
  }

  private ApiException invalido(List<Map<String, String>> errores) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "observacion_invalida",
        "Observación inválida",
        "Revise el texto y el destino de la observación.",
        errores);
  }
}
