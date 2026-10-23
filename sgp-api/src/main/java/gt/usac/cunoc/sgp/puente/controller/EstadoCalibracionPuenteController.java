package gt.usac.cunoc.sgp.puente.controller;

import gt.usac.cunoc.sgp.formulario.dto.EstadoCalibracionResponse;
import gt.usac.cunoc.sgp.puente.service.EstadoCalibracionPuenteService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/puentes")
public class EstadoCalibracionPuenteController {
  private final EstadoCalibracionPuenteService servicio;

  public EstadoCalibracionPuenteController(EstadoCalibracionPuenteService servicio) {
    this.servicio = servicio;
  }

  @GetMapping("/{id}/estado-calibracion")
  @PreAuthorize("permitAll()")
  public ResponseEntity<EstadoCalibracionResponse> consultar(@PathVariable UUID id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.consultar(id));
  }
}
