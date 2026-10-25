package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.ComparacionIcResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ResultadoIcHistorialResponse;
import gt.usac.cunoc.sgp.inspeccion.service.RecalculoIcService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inspecciones/{id}")
public class RecalculoIcController {
  private final RecalculoIcService servicio;

  public RecalculoIcController(RecalculoIcService servicio) {
    this.servicio = servicio;
  }

  @PostMapping("/recalculos-ic")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  public ResponseEntity<ResultadoIcHistorialResponse> recalcular(
      @PathVariable UUID id, Authentication autenticacion) {
    if (autenticacion == null || !(autenticacion.getDetails() instanceof JwtData datos))
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticacion requerida",
          "Se requiere una sesion valida.");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(servicio.recalcular(id, datos.userId()));
  }

  @GetMapping("/resultados-ic")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ComparacionIcResponse> historial(@PathVariable UUID id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.historial(id));
  }
}
