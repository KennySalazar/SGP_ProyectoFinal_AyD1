package gt.usac.cunoc.sgp.formulario.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.dto.*;
import gt.usac.cunoc.sgp.formulario.service.AprobacionCalibracionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/formularios/{id}")
public class AprobacionCalibracionController {
  private final AprobacionCalibracionService servicio;

  public AprobacionCalibracionController(AprobacionCalibracionService servicio) {
    this.servicio = servicio;
  }

  @PostMapping("/aprobacion-calibracion")
  @PreAuthorize("hasRole('CATEDRATICO')")
  public ResponseEntity<AprobacionCalibracionResponse> aprobar(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) AprobarCalibracionRequest solicitud,
      Authentication autenticacion) {
    if (autenticacion == null || !(autenticacion.getDetails() instanceof JwtData datos)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticacion requerida",
          "Se requiere una sesion valida.");
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            servicio.aprobar(
                id, datos.userId(), solicitud == null ? null : solicitud.observaciones()));
  }

  @GetMapping("/aprobacion-calibracion")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  public ResponseEntity<AprobacionCalibracionResponse> consultar(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(servicio.consultar(id));
  }

  @GetMapping("/estado-calibracion")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<EstadoCalibracionResponse> estado(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(servicio.estado(id));
  }
}
