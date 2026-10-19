package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.HistorialEstadoInspeccion;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapper;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoInspeccion;
import gt.usac.cunoc.sgp.inspeccion.model.EventoInspeccion;
import gt.usac.cunoc.sgp.inspeccion.model.InspeccionPublicadaEvent;
import gt.usac.cunoc.sgp.inspeccion.repository.HistorialEstadoInspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.usuario.entity.UsuarioProfesional;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UsuarioProfesionalRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única puerta de entrada para cambiar el estado de una inspección (DT-BE-07). Valida la transición
 * con {@link MaquinaEstadosInspeccion}, registra el historial y la auditoría, y publica de forma
 * atómica junto con el Índice de Condición (RN-INS-04, RN-INS-06, RN-INS-13, RN-REV-02).
 */
@Service
public class TransicionInspeccionService {

  /** Roles cuyas inspecciones se publican sin revisión previa (RN-REV-02). */
  private static final Set<String> ROLES_PUBLICACION_DIRECTA =
      Set.of(RoleName.CATEDRATICO.name(), RoleName.PROFESIONAL_EXTERNO.name());

  private final InspeccionRepository inspecciones;
  private final HistorialEstadoInspeccionRepository historial;
  private final PuenteRepository puentes;
  private final UsuarioProfesionalRepository profesionales;
  private final JsonSchemaValidatorService validadorEsquema;
  private final ReglasEvidenciaFotografica reglasEvidencia;
  private final PublicacionIndiceCondicionService publicacionIc;
  private final AuditoriaWriter auditoria;
  private final InspeccionMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;
  private ApplicationEventPublisher eventos;

  public TransicionInspeccionService(
      InspeccionRepository inspecciones,
      HistorialEstadoInspeccionRepository historial,
      PuenteRepository puentes,
      UsuarioProfesionalRepository profesionales,
      JsonSchemaValidatorService validadorEsquema,
      ReglasEvidenciaFotografica reglasEvidencia,
      PublicacionIndiceCondicionService publicacionIc,
      AuditoriaWriter auditoria,
      InspeccionMapper mapper,
      ObjectMapper json,
      Clock clock) {
    this.inspecciones = inspecciones;
    this.historial = historial;
    this.puentes = puentes;
    this.profesionales = profesionales;
    this.validadorEsquema = validadorEsquema;
    this.reglasEvidencia = reglasEvidencia;
    this.publicacionIc = publicacionIc;
    this.auditoria = auditoria;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  /**
   * Publicador de eventos internos (HU-046). Opcional, como las demás dependencias añadidas después
   * del constructor: sin él la publicación funciona igual, sin avisar al mantenimiento.
   */
  @Autowired(required = false)
  void setEventos(ApplicationEventPublisher eventos) {
    this.eventos = eventos;
  }

  /** El Estudiante envía su inspección completa a revisión (RN-INS-06, RN-REV-01). */
  @Transactional
  public InspeccionResumenResponse enviar(UUID inspeccionId, UUID usuarioId) {
    Inspeccion inspeccion = bloquear(inspeccionId);
    exigirAutor(inspeccion, usuarioId);
    if (publicaSinRevision(inspeccion)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "publicacion_directa",
          "Accion no aplicable",
          "Las inspecciones de Catedraticos y Profesionales externos se publican directamente.");
    }
    MaquinaEstadosInspeccion.destino(inspeccion.estadoActual(), EventoInspeccion.ENVIAR);
    exigirCompleta(inspeccion);

    aplicar(inspeccion, EventoInspeccion.ENVIAR, usuarioId, null);
    return mapper.toResumenResponse(inspecciones.saveAndFlush(inspeccion));
  }

  /**
   * El Catedrático o Profesional externo verificado publica sin revisión: la inspección pasa por
   * ENVIADA y queda PUBLICADA en la misma transacción, junto con su IC definitivo y el estado
   * vigente del puente. Cualquier fallo revierte toda la operación (RN-REV-02, RN-INS-04).
   */
  @Transactional
  public InspeccionResumenResponse publicar(UUID inspeccionId, UUID usuarioId) {
    Inspeccion inspeccion = bloquearParaPublicar(inspeccionId);
    exigirAutor(inspeccion, usuarioId);
    if (!publicaSinRevision(inspeccion)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "publicacion_requiere_revision",
          "Publicacion no permitida",
          "Las inspecciones de Estudiantes deben enviarse a revision de un Catedratico.");
    }
    exigirColegiadoVerificado(inspeccion);
    MaquinaEstadosInspeccion.destino(inspeccion.estadoActual(), EventoInspeccion.ENVIAR);
    exigirSinCorreccionPublicada(inspeccion);
    exigirCompleta(inspeccion);

    aplicar(inspeccion, EventoInspeccion.ENVIAR, usuarioId, null);
    aplicar(inspeccion, EventoInspeccion.PUBLICAR_SIN_REVISION, usuarioId, null);
    return publicarConIndice(inspeccion, usuarioId);
  }

  /** Un Catedrático toma una inspección enviada para revisarla. */
  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  public InspeccionResumenResponse iniciarRevision(UUID inspeccionId, UUID revisorId) {
    Inspeccion inspeccion = bloquear(inspeccionId);
    exigirRevisorDistintoDelAutor(inspeccion, revisorId);
    aplicar(inspeccion, EventoInspeccion.INICIAR_REVISION, revisorId, null);
    return mapper.toResumenResponse(inspecciones.saveAndFlush(inspeccion));
  }

  /** Aprobar publica la inspección con su IC definitivo en la misma transacción. */
  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  public InspeccionResumenResponse aprobar(UUID inspeccionId, UUID revisorId) {
    Inspeccion inspeccion = bloquearParaPublicar(inspeccionId);
    exigirRevisorDistintoDelAutor(inspeccion, revisorId);
    MaquinaEstadosInspeccion.destino(inspeccion.estadoActual(), EventoInspeccion.APROBAR);
    exigirSinCorreccionPublicada(inspeccion);
    exigirEvidenciaCompleta(inspeccion, List.of());
    aplicar(inspeccion, EventoInspeccion.APROBAR, revisorId, null);
    return publicarConIndice(inspeccion, revisorId);
  }

  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  public InspeccionResumenResponse solicitarCambios(
      UUID inspeccionId, UUID revisorId, String motivo) {
    return resolverConMotivo(inspeccionId, revisorId, motivo, EventoInspeccion.SOLICITAR_CAMBIOS);
  }

  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  public InspeccionResumenResponse rechazar(UUID inspeccionId, UUID revisorId, String motivo) {
    return resolverConMotivo(inspeccionId, revisorId, motivo, EventoInspeccion.RECHAZAR);
  }

  /** Con cambios solicitados, la inspección vuelve al autor como borrador editable (RN-REV-06). */
  @Transactional
  public InspeccionResumenResponse devolverABorrador(UUID inspeccionId, UUID usuarioId) {
    Inspeccion inspeccion = bloquear(inspeccionId);
    exigirAutor(inspeccion, usuarioId);
    aplicar(inspeccion, EventoInspeccion.DEVOLVER_A_BORRADOR, usuarioId, null);
    return mapper.toResumenResponse(inspecciones.saveAndFlush(inspeccion));
  }

  private InspeccionResumenResponse resolverConMotivo(
      UUID inspeccionId, UUID revisorId, String motivo, EventoInspeccion evento) {
    if (motivo == null || motivo.isBlank()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "motivo_requerido",
          "Motivo requerido",
          "Solicitar cambios o rechazar exige una observacion escrita (RN-REV-04).");
    }
    Inspeccion inspeccion = bloquear(inspeccionId);
    exigirRevisorDistintoDelAutor(inspeccion, revisorId);
    aplicar(inspeccion, evento, revisorId, motivo.strip());
    return mapper.toResumenResponse(inspecciones.saveAndFlush(inspeccion));
  }

  /**
   * Guarda PUBLICADA y su historial y calcula el IC definitivo dentro de la transacción en curso;
   * si el cálculo o la actualización del puente fallan, la excepción revierte todo (HU-020).
   */
  private InspeccionResumenResponse publicarConIndice(Inspeccion inspeccion, UUID usuarioId) {
    Inspeccion guardada = inspecciones.saveAndFlush(inspeccion);
    publicacionIc.integrarPublicacion(guardada.getId(), usuarioId);
    if (eventos != null) {
      eventos.publishEvent(new InspeccionPublicadaEvent(guardada.getId(), usuarioId));
    }
    return mapper.toResumenResponse(guardada);
  }

  /** Aplica la transición validada, con su historial y auditoría en la misma transacción. */
  private void aplicar(
      Inspeccion inspeccion, EventoInspeccion evento, UUID usuarioId, String motivo) {
    EstadoInspeccion anterior = inspeccion.estadoActual();
    EstadoInspeccion destino = MaquinaEstadosInspeccion.destino(anterior, evento);
    Instant ahora = clock.instant();

    inspeccion.aplicarEstado(destino, ahora);
    historial.save(
        new HistorialEstadoInspeccion(
            UuidV7Generator.generate(),
            inspeccion.getId(),
            historial.ultimaSecuencia(inspeccion.getId()) + 1,
            anterior.name(),
            destino.name(),
            usuarioId,
            evento.name(),
            motivo,
            ahora));

    ObjectNode antes = json.createObjectNode().put("estado", anterior.name());
    ObjectNode despues =
        json.createObjectNode().put("estado", destino.name()).put("evento", evento.name());
    if (motivo != null) {
      despues.put("motivo", motivo);
    }
    auditoria.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.CAMBIAR_ESTADO,
        "inspeccion",
        inspeccion.getId(),
        antes,
        despues,
        null);
  }

  /**
   * Campos obligatorios del esquema de su versión y fotografías mínimas por categoría (RN-INS-06,
   * RN-FRM-08). Detalla todo lo que falta en una sola respuesta 422.
   */
  private void exigirCompleta(Inspeccion inspeccion) {
    List<Map<String, String>> faltantes = new ArrayList<>();
    validadorEsquema
        .obtenerErroresValidacion(
            inspeccion.getVersionFormulario().getEsquema(), inspeccion.getDatos(), false)
        .forEach(
            error -> faltantes.add(Map.of("campo", error.campo(), "mensaje", error.mensaje())));
    exigirEvidenciaCompleta(inspeccion, faltantes);
  }

  private void exigirEvidenciaCompleta(
      Inspeccion inspeccion, List<Map<String, String>> erroresCampos) {
    List<Map<String, String>> faltantes = new ArrayList<>(erroresCampos);
    int campos = erroresCampos.size();
    var conteos =
        inspecciones.findConteosFotoDisponibles(inspeccion.getId()).stream()
            .collect(
                Collectors.toMap(
                    InspeccionRepository.ConteoFotoCategoria::getCategoria,
                    InspeccionRepository.ConteoFotoCategoria::getCantidad));
    reglasEvidencia
        .faltantesObligatorios(conteos)
        .forEach(
            faltante ->
                faltantes.add(
                    Map.of(
                        "campo", "fotografias." + faltante.categoria(),
                        "mensaje",
                            "Falta al menos una fotografia de la categoria "
                                + faltante.categoria())));

    if (!faltantes.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "inspeccion_incompleta",
          "La inspeccion no puede enviarse ni publicarse",
          "Faltan "
              + campos
              + " campos obligatorios y "
              + (faltantes.size() - campos)
              + " categorias de fotografia.",
          faltantes);
    }
  }

  /**
   * Una inspección original solo admite una corrección publicada (RN-INS-08). La base de datos lo
   * garantiza también con el índice uq_inspeccion_sucesora_publicada.
   */
  private void exigirSinCorreccionPublicada(Inspeccion inspeccion) {
    if (inspeccion.getSupersedeAId() != null
        && inspecciones.findCorreccionPublicadaId(inspeccion.getSupersedeAId()).isPresent()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "correccion_ya_publicada",
          "Correccion ya publicada",
          "La inspeccion original ya tiene una correccion publicada; corrija esa correccion.");
    }
  }

  private void exigirAutor(Inspeccion inspeccion, UUID usuarioId) {
    if (!inspeccion.getAutorId().equals(usuarioId)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "solo_autor",
          "Acceso denegado",
          "Solo el autor responsable puede enviar o publicar esta inspeccion (RN-INS-02).");
    }
  }

  private void exigirRevisorDistintoDelAutor(Inspeccion inspeccion, UUID revisorId) {
    if (inspeccion.getAutorId().equals(revisorId)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "revisor_es_autor",
          "Acceso denegado",
          "El revisor no puede ser el autor de la inspeccion (RN-REV-03).");
    }
  }

  /** El Profesional externo solo publica con su colegiado verificado (RN-USR-04). */
  private void exigirColegiadoVerificado(Inspeccion inspeccion) {
    if (!RoleName.PROFESIONAL_EXTERNO.name().equals(inspeccion.getRolAutorCreacion())) {
      return;
    }
    boolean verificado =
        profesionales
            .findById(inspeccion.getAutorId())
            .map(UsuarioProfesional::isColegiadoVerificado)
            .orElse(false);
    if (!verificado) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "colegiado_no_verificado",
          "Colegiado no verificado",
          "El Administrador debe verificar su numero de colegiado antes de publicar inspecciones.");
    }
  }

  private boolean publicaSinRevision(Inspeccion inspeccion) {
    return ROLES_PUBLICACION_DIRECTA.contains(inspeccion.getRolAutorCreacion());
  }

  private Inspeccion bloquear(UUID inspeccionId) {
    return inspecciones
        .findActivaByIdForUpdate(inspeccionId)
        .orElseThrow(() -> inspeccionNoEncontrada(inspeccionId));
  }

  /**
   * Bloquea primero el puente y después la inspección, el mismo orden que usa la integración del
   * IC, para que dos publicaciones simultáneas se serialicen sin interbloqueos.
   */
  private Inspeccion bloquearParaPublicar(UUID inspeccionId) {
    UUID puenteId =
        inspecciones
            .findPuenteIdByInspeccionId(inspeccionId)
            .orElseThrow(() -> inspeccionNoEncontrada(inspeccionId));
    puentes
        .findByIdForUpdate(puenteId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "puente_no_encontrado",
                    "Puente no encontrado",
                    "No existe el puente de la inspeccion."));
    return bloquear(inspeccionId);
  }

  private ApiException inspeccionNoEncontrada(UUID inspeccionId) {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "inspeccion_no_encontrada",
        "Inspeccion no encontrada",
        "No existe la inspeccion " + inspeccionId);
  }
}
