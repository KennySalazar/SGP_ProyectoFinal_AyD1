package gt.usac.cunoc.sgp.foro.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.CrearComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.CrearHiloRequest;
import gt.usac.cunoc.sgp.foro.dto.HiloInspeccionResponse;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.service.FiltroComentarios;
import gt.usac.cunoc.sgp.foro.service.ForoInspeccionService;
import gt.usac.cunoc.sgp.foro.service.ForoInspeccionService.Apertura;
import gt.usac.cunoc.sgp.foro.service.ForoService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Foro por puente (HU-063) y por inspección publicada (HU-064). Leer es público (RN-FOR-09); crear
 * hilos y comentar exige una sesión activa de cualquier rol.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Foro", description = "Hilos por puente y por inspección, y sus comentarios")
public class ForoController {

  private final ForoService foro;
  private final ForoInspeccionService foroInspeccion;

  public ForoController(ForoService foro, ForoInspeccionService foroInspeccion) {
    this.foro = foro;
    this.foroInspeccion = foroInspeccion;
  }

  @GetMapping("/puentes/{puenteId}/hilos")
  @PreAuthorize("permitAll()")
  @Operation(
      summary = "Listar los hilos de un puente",
      description =
          "Público. Solo los hilos propios del puente, del de actividad más reciente al más "
              + "antiguo, con su total de comentarios. Página desde 0, tamaño entre 1 y 100 "
              + "(20 por defecto). 404 si el puente no existe o está inactivo.")
  public Page<HiloResponse> listarHilos(
      @PathVariable UUID puenteId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return foro.listarHilosDePuente(puenteId, pagina, tamanio);
  }

  @PostMapping("/puentes/{puenteId}/hilos")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Abrir un hilo en un puente",
      description =
          "Cualquier usuario autenticado y activo. Exige título (hasta 120 caracteres), categoría "
              + "(DISCUSION_TECNICA, PROPUESTA_INTERVENCION, CONSULTA, REFERENCIA_DOCUMENTAL o "
              + "ALERTA) y primer comentario (hasta 2000 caracteres); 422 con el detalle de lo "
              + "que falte o exceda, sin crear nada.")
  public ResponseEntity<HiloResponse> crearHilo(
      @PathVariable UUID puenteId,
      @RequestBody CrearHiloRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(foro.crearHilo(puenteId, request, usuarioId(authentication)));
  }

  @GetMapping("/hilos/{hiloId}")
  @PreAuthorize("permitAll()")
  @Operation(summary = "Consultar un hilo", description = "Público. 404 si no existe.")
  public HiloResponse obtenerHilo(@PathVariable UUID hiloId) {
    return foro.obtenerHilo(hiloId);
  }

  @GetMapping("/inspecciones/{inspeccionId}/hilo")
  @PreAuthorize("permitAll()")
  @Operation(
      summary = "Consultar el hilo de una inspección publicada",
      description =
          "Público. Devuelve el hilo y cuántos comentarios tiene cada campo o elemento anclado. "
              + "404 si la inspección no existe, no está publicada o aún no tiene hilo; esta "
              + "consulta nunca lo crea.")
  public HiloInspeccionResponse obtenerHiloDeInspeccion(@PathVariable UUID inspeccionId) {
    return foroInspeccion.obtener(inspeccionId);
  }

  @PostMapping("/inspecciones/{inspeccionId}/hilo")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Abrir el hilo de una inspección publicada",
      description =
          "Cualquier usuario autenticado y activo. Es idempotente: crea el hilo si aún no existe "
              + "(201), por ejemplo en una inspección publicada antes de esta función, y si ya "
              + "existe lo devuelve (200). 404 si la inspección no existe o no está publicada.")
  public ResponseEntity<HiloInspeccionResponse> abrirHiloDeInspeccion(
      @PathVariable UUID inspeccionId, Authentication authentication) {
    Apertura apertura = foroInspeccion.abrir(inspeccionId, usuarioId(authentication));
    return ResponseEntity.status(apertura.creado() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(apertura.hilo());
  }

  @GetMapping("/hilos/{hiloId}/comentarios")
  @PreAuthorize("permitAll()")
  @Operation(
      summary = "Listar los comentarios de un hilo",
      description =
          "Público. Comentarios y respuestas en una sola lista, de los más antiguos a los más "
              + "nuevos; una respuesta lleva el padreId de su comentario principal y llega siempre "
              + "después de él. Se puede filtrar por un campo o elemento (elementoRef, y sin "
              + "instanciaElementoId los del elemento sin instancia), por una sección entera "
              + "(seccion, su clave sin barras, con todo lo que contiene) o por los generales "
              + "(soloGenerales, los que no están anclados); dos filtros a la vez dan 422. Página "
              + "desde 0, tamaño entre 1 y 100 (20 por defecto). Un comentario eliminado llega "
              + "sin texto con eliminado=true; uno oculto llega sin texto con oculto=true, salvo "
              + "para el Catedrático y el Administrador, que ven el texto y el motivo.")
  public Page<ComentarioResponse> listarComentarios(
      @PathVariable UUID hiloId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio,
      @RequestParam(required = false) String elementoRef,
      @RequestParam(required = false) UUID instanciaElementoId,
      @RequestParam(required = false) String seccion,
      @RequestParam(defaultValue = "false") boolean soloGenerales,
      Authentication authentication) {
    return foro.listarComentariosPara(
        hiloId,
        pagina,
        tamanio,
        new FiltroComentarios(elementoRef, instanciaElementoId, seccion, soloGenerales),
        rolLector(authentication));
  }

  @PostMapping("/hilos/{hiloId}/comentarios")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Comentar un hilo o responder a un comentario",
      description =
          "Cualquier usuario autenticado y activo. El contenido es obligatorio (hasta 2000 "
              + "caracteres). Con padreId responde a un comentario principal del mismo hilo; no "
              + "se puede responder a una respuesta (422). En el hilo de una inspección, "
              + "elementoRef (y instanciaElementoId si el elemento es repetible) ancla el "
              + "comentario a un campo o elemento que debe existir en el formulario con que se "
              + "levantó la inspección (422 si no); una respuesta toma el ancla de su comentario. "
              + "Un hilo cerrado responde 409.")
  public ResponseEntity<ComentarioResponse> crearComentario(
      @PathVariable UUID hiloId,
      @RequestBody CrearComentarioRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(foro.crearComentario(hiloId, request, usuarioId(authentication)));
  }

  /** El rol de quien lee, o nulo si es un visitante sin sesión. */
  private RoleName rolLector(Authentication authentication) {
    return authentication != null && authentication.getDetails() instanceof JwtData jwtData
        ? jwtData.role()
        : null;
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
