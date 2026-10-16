package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.ValidacionEvidenciaResponse;
import gt.usac.cunoc.sgp.inspeccion.service.EvidenciaFotograficaService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inspecciones/{id}/evidencia-fotografica")
@PreAuthorize("isAuthenticated()")
public class EvidenciaFotograficaController {
  private final EvidenciaFotograficaService servicio;

  public EvidenciaFotograficaController(EvidenciaFotograficaService servicio) {
    this.servicio = servicio;
  }

  @GetMapping
  public ResponseEntity<ValidacionEvidenciaResponse> consultar(
      @PathVariable UUID id, Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwt)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore().cachePrivate())
        .body(servicio.consultar(id, jwt.userId()));
  }
}
