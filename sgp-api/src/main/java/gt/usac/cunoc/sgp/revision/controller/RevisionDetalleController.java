package gt.usac.cunoc.sgp.revision.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.revision.dto.RevisionDetalleResponse;
import gt.usac.cunoc.sgp.revision.service.RevisionDetalleService;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/revisiones")
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ESTUDIANTE')")
public class RevisionDetalleController {
  private final RevisionDetalleService detalle;

  public RevisionDetalleController(RevisionDetalleService detalle) {
    this.detalle = detalle;
  }

  @GetMapping("/{revisionId}")
  public ResponseEntity<RevisionDetalleResponse> obtener(
      @PathVariable UUID revisionId, Authentication auth) {
    if (auth == null
        || !auth.isAuthenticated()
        || !(auth.getDetails() instanceof JwtData jwt)
        || jwt.userId() == null)
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore().cachePrivate())
        .body(detalle.obtener(revisionId, jwt.userId()));
  }
}
