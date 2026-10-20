package gt.usac.cunoc.sgp.revision.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.revision.dto.CicloObservacionesResponse;
import gt.usac.cunoc.sgp.revision.service.ObservacionService;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Consulta HU-037; no es bandeja de revisión ni flujo de corrección. */
@RestController
@RequestMapping("/api/v1/observaciones/recibidas")
@PreAuthorize("hasRole('ESTUDIANTE')")
public class ObservacionAutorController {
  private final ObservacionService observaciones;

  public ObservacionAutorController(ObservacionService observaciones) {
    this.observaciones = observaciones;
  }

  @GetMapping
  public ResponseEntity<Page<CicloObservacionesResponse>> listar(
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio,
      Authentication auth) {
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
        .body(observaciones.recibidas(jwt.userId(), pagina, tamanio));
  }
}
