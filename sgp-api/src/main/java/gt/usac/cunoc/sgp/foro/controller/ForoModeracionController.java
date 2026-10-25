package gt.usac.cunoc.sgp.foro.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.EditarComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.dto.InspeccionSinRevisionResponse;
import gt.usac.cunoc.sgp.foro.dto.MotivoModeracionRequest;
import gt.usac.cunoc.sgp.foro.service.ForoModeracionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Edición, eliminación y moderación del foro (HU-065). El autor edita y elimina sus comentarios
 * (RN-FOR-05); solo el Catedrático y el Administrador ocultan comentarios, cierran hilos y revisan
 * las inspecciones publicadas sin revisión (RN-FOR-08, RN-REV-02).
 */
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Foro", description = "Hilos por puente y por inspección, y sus comentarios")
public class ForoModeracionController {

  private final ForoModeracionService moderacion;

  public ForoModeracionController(ForoModeracionService moderacion) {
    this.moderacion = moderacion;
  }

  @PutMapping("/comentarios/{comentarioId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Editar un comentario propio",
      description =
          "Solo su autor (403 si no) y dentro de los 15 minutos siguientes a publicarlo según la "
              + "hora del servidor (409 plazo_edicion_vencido si no). 409 si el comentario está "
              + "eliminado u oculto o el hilo está cerrado; 422 si el texto falta o excede 2000 "
              + "caracteres.")
  public ComentarioResponse editar(
      @PathVariable UUID comentarioId,
      @RequestBody EditarComentarioRequest request,
      Authentication authentication) {
    return moderacion.editar(comentarioId, request, usuarioId(authentication));
  }

  @DeleteMapping("/comentarios/{comentarioId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Eliminar un comentario propio",
      description =
          "Solo su autor (403 si no). Queda la marca de comentario eliminado y sus respuestas se "
              + "conservan. Repetirlo no cambia nada. 409 si el comentario está oculto o el hilo "
              + "está cerrado.")
  public ComentarioResponse eliminar(
      @PathVariable UUID comentarioId, Authentication authentication) {
    return moderacion.eliminar(comentarioId, usuarioId(authentication));
  }

  @PostMapping("/comentarios/{comentarioId}/ocultar")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Ocultar un comentario por moderación",
      description =
          "Catedrático o Administrador. Exige un motivo (422 sin él). Los demás usuarios ven "
              + "«Oculto por moderación»; los moderadores, el texto y el motivo. 409 si ya está "
              + "oculto o fue eliminado.")
  public ComentarioResponse ocultar(
      @PathVariable UUID comentarioId,
      @RequestBody(required = false) MotivoModeracionRequest request,
      Authentication authentication) {
    return moderacion.ocultar(comentarioId, request, usuarioId(authentication));
  }

  @PostMapping("/hilos/{hiloId}/cerrar")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Cerrar un hilo",
      description =
          "Catedrático o Administrador. Exige un motivo (422 sin él), que queda visible en el "
              + "hilo. El hilo sigue visible para lectura y comentar en él responde 409. 409 si ya "
              + "estaba cerrado.")
  public HiloResponse cerrar(
      @PathVariable UUID hiloId,
      @RequestBody(required = false) MotivoModeracionRequest request,
      Authentication authentication) {
    return moderacion.cerrar(hiloId, request, usuarioId(authentication));
  }

  @GetMapping("/moderacion/inspecciones-sin-revision")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Inspecciones publicadas sin revisión previa",
      description =
          "Catedrático o Administrador. Las que su autor Catedrático o Profesional externo publicó "
              + "sin revisión, de las más recientes a las más antiguas, con su hilo (nulo si aún "
              + "no se abrió) para moderarlas a posteriori. Página desde 0, tamaño entre 1 y 100 "
              + "(20 por defecto).")
  public Page<InspeccionSinRevisionResponse> listarInspeccionesSinRevision(
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return moderacion.listarInspeccionesSinRevision(pagina, tamanio);
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida para participar en el foro.");
    }
    return jwtData.userId();
  }
}
