package gt.usac.cunoc.sgp.foro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.EditarComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.dto.InspeccionSinRevisionResponse;
import gt.usac.cunoc.sgp.foro.dto.MotivoModeracionRequest;
import gt.usac.cunoc.sgp.foro.entity.ForoComentario;
import gt.usac.cunoc.sgp.foro.mapper.ForoMapper;
import gt.usac.cunoc.sgp.foro.repository.ForoAutorRepository;
import gt.usac.cunoc.sgp.foro.repository.ForoAutorRepository.Autor;
import gt.usac.cunoc.sgp.foro.repository.ForoComentarioRepository;
import gt.usac.cunoc.sgp.foro.repository.ForoHiloRepository;
import gt.usac.cunoc.sgp.foro.repository.ForoModeracionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edición, eliminación y moderación del foro (HU-065). El autor edita su comentario dentro del
 * plazo que decide la hora del servidor y lo elimina dejando la marca (RN-FOR-05). El Catedrático y
 * el Administrador ocultan comentarios y cierran hilos con un motivo (RN-FOR-08) y revisan a
 * posteriori las inspecciones publicadas sin revisión (RN-REV-02). Todo queda en la bitácora con
 * los valores anterior y posterior (RN-USR-09).
 *
 * <p>Qué rol puede moderar lo decide el controlador; aquí se decide qué le corresponde al autor.
 */
@Service
public class ForoModeracionService {

  /** Longitud máxima del motivo de una moderación. */
  static final int LONGITUD_MAXIMA_MOTIVO = 500;

  private final ForoHiloRepository hilos;
  private final ForoComentarioRepository comentarios;
  private final ForoAutorRepository autores;
  private final ForoModeracionRepository moderacion;
  private final ForoService foro;
  private final AvisosForo avisos;
  private final AuditoriaWriter auditoria;
  private final ForoMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;

  public ForoModeracionService(
      ForoHiloRepository hilos,
      ForoComentarioRepository comentarios,
      ForoAutorRepository autores,
      ForoModeracionRepository moderacion,
      ForoService foro,
      AvisosForo avisos,
      AuditoriaWriter auditoria,
      ForoMapper mapper,
      ObjectMapper json,
      Clock clock) {
    this.hilos = hilos;
    this.comentarios = comentarios;
    this.autores = autores;
    this.moderacion = moderacion;
    this.foro = foro;
    this.avisos = avisos;
    this.auditoria = auditoria;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  /**
   * El autor cambia el texto de su comentario. Solo dentro de los 15 minutos siguientes a
   * publicarlo, contados con la hora del servidor: la del cliente no cuenta (RN-FOR-05). Si la
   * edición agrega menciones, solo se avisa a los nuevos mencionados (RN-FOR-10).
   */
  @Transactional
  public ComentarioResponse editar(
      UUID comentarioId, EditarComentarioRequest request, UUID usuarioId) {
    ForoComentario comentario = delAutor(comentarioId, usuarioId);
    exigirModificable(comentario);
    String contenido = ContenidoForo.limpiarComentario(request.contenido());
    if (contenido == null || contenido.length() > ContenidoForo.LONGITUD_MAXIMA_COMENTARIO) {
      String mensaje =
          contenido == null
              ? "El comentario es obligatorio."
              : "El comentario no puede exceder "
                  + ContenidoForo.LONGITUD_MAXIMA_COMENTARIO
                  + " caracteres.";
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "comentario_invalido",
          "Comentario inválido",
          mensaje,
          List.of(ContenidoForo.error("contenido", mensaje)));
    }
    Instant ahora = clock.instant();
    if (ahora.isAfter(comentario.getEditableHasta())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "plazo_edicion_vencido",
          "Plazo de edición vencido",
          "Un comentario solo se puede editar en los 15 minutos siguientes a publicarlo.");
    }
    if (contenido.equals(comentario.getContenido())) return respuesta(comentario, false);

    ObjectNode antes = json.createObjectNode().put("contenido", comentario.getContenido());
    comentario.editar(contenido, ahora);
    comentarios.saveAndFlush(comentario);
    auditoria.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.MODIFICAR,
        "foro_comentario",
        comentario.getId(),
        antes,
        json.createObjectNode().put("contenido", contenido),
        null);
    avisos.avisarMenciones(hilos.findById(comentario.getHiloId()).orElseThrow(), comentario);
    return respuesta(comentario, false);
  }

  /**
   * El autor elimina su comentario: queda la marca «comentario eliminado» y sus respuestas se
   * conservan (RN-FOR-05). Eliminar uno ya eliminado no cambia nada.
   */
  @Transactional
  public ComentarioResponse eliminar(UUID comentarioId, UUID usuarioId) {
    ForoComentario comentario = delAutor(comentarioId, usuarioId);
    if (comentario.isEliminado()) return respuesta(comentario, false);
    exigirModificable(comentario);

    comentario.eliminar(clock.instant());
    comentarios.saveAndFlush(comentario);
    auditoria.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.MODIFICAR,
        "foro_comentario",
        comentario.getId(),
        json.createObjectNode().put("contenido", comentario.getContenido()).put("eliminado", false),
        json.createObjectNode().put("eliminado", true),
        null);
    return respuesta(comentario, false);
  }

  /**
   * Un moderador oculta un comentario con un motivo (RN-FOR-08). Los demás ven «Oculto por
   * moderación» en su lugar; los moderadores, el texto y el motivo. También en un hilo cerrado.
   */
  @Transactional
  public ComentarioResponse ocultar(
      UUID comentarioId, MotivoModeracionRequest request, UUID moderadorId) {
    String motivo = motivo(request);
    ForoComentario comentario = visible(comentarioId);
    if (comentario.isEliminado()) throw comentarioEliminado();
    if (comentario.isOculto()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "comentario_oculto",
          "Comentario oculto",
          "El comentario ya está oculto por moderación.");
    }

    comentario.ocultar(moderadorId, motivo, clock.instant());
    comentarios.saveAndFlush(comentario);
    auditoria.registrarEnTransaccion(
        moderadorId,
        AccionAuditoria.MODERAR,
        "foro_comentario",
        comentario.getId(),
        json.createObjectNode().put("oculto", false),
        json.createObjectNode().put("oculto", true).put("motivo", motivo),
        null);
    return respuesta(comentario, true);
  }

  /**
   * Un moderador cierra un hilo con un motivo (RN-FOR-08): sigue visible para lectura, pero ya no
   * admite comentarios.
   */
  @Transactional
  public HiloResponse cerrar(UUID hiloId, MotivoModeracionRequest request, UUID moderadorId) {
    String motivo = motivo(request);
    foro.obtenerHilo(hiloId);
    if (hilos.cerrar(hiloId, moderadorId, motivo, clock.instant()) == 0) {
      throw new ApiException(
          HttpStatus.CONFLICT, "hilo_cerrado", "Hilo cerrado", "El hilo ya está cerrado.");
    }
    auditoria.registrarEnTransaccion(
        moderadorId,
        AccionAuditoria.MODERAR,
        "foro_hilo",
        hiloId,
        json.createObjectNode().put("cerrado", false),
        json.createObjectNode().put("cerrado", true).put("motivo", motivo),
        null);
    return foro.obtenerHilo(hiloId);
  }

  /**
   * Inspecciones publicadas sin revisión previa por su autor Catedrático o Profesional externo, de
   * las más recientes a las más antiguas, con su hilo para moderarlas a posteriori (RN-REV-02).
   */
  @Transactional(readOnly = true)
  public Page<InspeccionSinRevisionResponse> listarInspeccionesSinRevision(
      int pagina, int tamanio) {
    ContenidoForo.validarPaginacion(pagina, tamanio);
    return moderacion
        .listarInspeccionesSinRevision(PageRequest.of(pagina, tamanio))
        .map(mapper::toInspeccionSinRevisionResponse);
  }

  /** El comentario de un hilo visible; si no existe o su puente está inactivo, 404. */
  private ForoComentario visible(UUID comentarioId) {
    ForoComentario comentario =
        comentarios
            .findById(comentarioId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "comentario_no_encontrado",
                        "Comentario no encontrado",
                        "El comentario no existe."));
    foro.obtenerHilo(comentario.getHiloId());
    return comentario;
  }

  /** Solo el autor edita o elimina su comentario; cualquier otro recibe 403 (RN-FOR-05). */
  private ForoComentario delAutor(UUID comentarioId, UUID usuarioId) {
    ForoComentario comentario = visible(comentarioId);
    if (!comentario.getAutorId().equals(usuarioId)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "comentario_ajeno",
          "Comentario ajeno",
          "Solo el autor puede editar o eliminar su comentario.");
    }
    return comentario;
  }

  /**
   * Un comentario eliminado u oculto por moderación ya no cambia, ni uno de un hilo cerrado: así se
   * conserva lo que el moderador vio y decidió.
   */
  private void exigirModificable(ForoComentario comentario) {
    if (comentario.isEliminado()) throw comentarioEliminado();
    if (comentario.isOculto()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "comentario_oculto",
          "Comentario oculto",
          "El comentario está oculto por moderación y ya no se puede modificar.");
    }
    if (hilos.findById(comentario.getHiloId()).map(h -> h.getCerradoEn() != null).orElse(false)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "hilo_cerrado",
          "Hilo cerrado",
          "El hilo está cerrado y sus comentarios ya no se pueden modificar.");
    }
  }

  /** El motivo es obligatorio: sin él, 422 (RN-FOR-08). */
  private String motivo(MotivoModeracionRequest request) {
    String motivo = request == null ? null : ContenidoForo.limpiarComentario(request.motivo());
    String mensaje = null;
    if (motivo == null) {
      mensaje = "Indica el motivo de la moderación.";
    } else if (motivo.length() > LONGITUD_MAXIMA_MOTIVO) {
      mensaje = "El motivo no puede exceder " + LONGITUD_MAXIMA_MOTIVO + " caracteres.";
    }
    if (mensaje != null) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "motivo_requerido",
          "Motivo requerido",
          mensaje,
          List.of(ContenidoForo.error("motivo", mensaje)));
    }
    return motivo;
  }

  private ComentarioResponse respuesta(ForoComentario comentario, boolean moderador) {
    Autor autor =
        autores.buscarPorIds(Set.of(comentario.getAutorId())).stream().findFirst().orElse(null);
    return mapper.toComentarioResponse(comentario, autor, moderador);
  }

  private static ApiException comentarioEliminado() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "comentario_eliminado",
        "Comentario eliminado",
        "El comentario fue eliminado.");
  }
}
