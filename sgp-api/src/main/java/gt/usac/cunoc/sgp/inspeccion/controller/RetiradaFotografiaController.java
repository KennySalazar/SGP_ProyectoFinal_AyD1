package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.service.RetiradaFotografiaService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inspecciones/{id}/fotografias")
@PreAuthorize("isAuthenticated()")
public class RetiradaFotografiaController {
  private final RetiradaFotografiaService servicio;

  public RetiradaFotografiaController(RetiradaFotografiaService servicio) {
    this.servicio = servicio;
  }

  @DeleteMapping("/{fotoId}")
  public ResponseEntity<Void> retirar(
      @PathVariable UUID id, @PathVariable UUID fotoId, Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwt)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    }
    servicio.retirar(id, fotoId, jwt.userId());
    return ResponseEntity.noContent().build();
  }
}
