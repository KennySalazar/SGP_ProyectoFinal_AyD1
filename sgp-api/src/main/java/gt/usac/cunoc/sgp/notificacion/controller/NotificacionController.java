package gt.usac.cunoc.sgp.notificacion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.notificacion.dto.MarcarTodasLeidasResponse;
import gt.usac.cunoc.sgp.notificacion.dto.NotificacionResponse;
import gt.usac.cunoc.sgp.notificacion.dto.ResumenNotificacionesResponse;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notificaciones", description = "Consulta y gestión de avisos dentro de la aplicación")
public class NotificacionController {

  private final NotificacionService notificacionService;

  public NotificacionController(NotificacionService notificacionService) {
    this.notificacionService = notificacionService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Listar avisos del usuario",
      description =
          "Devuelve la lista paginada de avisos del usuario autenticado, de más reciente a más antiguo. "
              + "Permite filtrar por solo no leídos. Tamaño máximo de página: 100.")
  public Page<NotificacionResponse> listar(
      @RequestParam(defaultValue = "false") boolean soloNoLeidos,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio,
      Authentication authentication) {
    return notificacionService.listar(usuarioId(authentication), soloNoLeidos, pagina, tamanio);
  }

  @GetMapping("/resumen")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Resumen de avisos no leídos",
      description = "Devuelve el número total de avisos no leídos del usuario autenticado.")
  public ResumenNotificacionesResponse resumen(Authentication authentication) {
    long noLeidas = notificacionService.contarNoLeidas(usuarioId(authentication));
    return new ResumenNotificacionesResponse(noLeidas);
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Consultar un aviso",
      description =
          "Devuelve el detalle de un aviso propio. Si pertenece a otro usuario responde 404 Not Found (DT-SEC-04).")
  public NotificacionResponse obtener(@PathVariable UUID id, Authentication authentication) {
    return notificacionService.obtener(id, usuarioId(authentication));
  }

  @PatchMapping("/{id}/leer")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Marcar un aviso como leído",
      description =
          "Marca el aviso como leído. Si pertenece a otro usuario responde 404 Not Found (DT-SEC-04).")
  public NotificacionResponse marcarLeida(@PathVariable UUID id, Authentication authentication) {
    return notificacionService.marcarLeida(id, usuarioId(authentication));
  }

  @PostMapping("/marcar-todas-leidas")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Marcar todos los avisos como leídos",
      description = "Marca todos los avisos sin leer del usuario autenticado como leídos.")
  public MarcarTodasLeidasResponse marcarTodasLeidas(Authentication authentication) {
    int marcadas = notificacionService.marcarTodasLeidas(usuarioId(authentication));
    return new MarcarTodasLeidasResponse(marcadas);
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para consultar avisos.");
    }
    return jwtData.userId();
  }
}
