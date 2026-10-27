package gt.usac.cunoc.sgp.revision.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.revision.dto.*;
import gt.usac.cunoc.sgp.revision.service.AtencionObservacionesService;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/revisiones")
@PreAuthorize("hasRole('ESTUDIANTE')")
public class AtencionObservacionesController {
  private final AtencionObservacionesService servicio;

  public AtencionObservacionesController(AtencionObservacionesService servicio) {
    this.servicio = servicio;
  }

  @PostMapping("/{id}/atender-cambios")
  public ResponseEntity<RevisionResultadoResponse> atender(
      @PathVariable UUID id, Authentication auth) {
    return privada(servicio.atender(id, actor(auth)));
  }

  @PutMapping("/{revisionId}/observaciones/{id}/resolver")
  public ResponseEntity<ObservacionResponse> resolver(
      @PathVariable UUID revisionId,
      @PathVariable UUID id,
      @RequestBody(required = false) ResolverObservacionRequest request,
      Authentication auth) {
    return privada(servicio.resolver(revisionId, id, actor(auth), request));
  }

  private <T> ResponseEntity<T> privada(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
  }

  private UUID actor(Authentication auth) {
    if (auth == null
        || !auth.isAuthenticated()
        || !(auth.getDetails() instanceof JwtData jwt)
        || jwt.userId() == null)
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    return jwt.userId();
  }
}
