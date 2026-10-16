package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmacionEstadoIcResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest;
import gt.usac.cunoc.sgp.inspeccion.service.ConfirmacionEstadoIcService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspecciones/{inspeccionId}/confirmacion-estado")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Confirmacion del estado IC")
@PreAuthorize("isAuthenticated()")
public class ConfirmacionEstadoIcController {

  private final ConfirmacionEstadoIcService service;

  public ConfirmacionEstadoIcController(ConfirmacionEstadoIcService service) {
    this.service = service;
  }

  @GetMapping
  public ConfirmacionEstadoIcResponse consultar(@PathVariable UUID inspeccionId) {
    return service.consultar(inspeccionId);
  }

  @PutMapping
  public ConfirmacionEstadoIcResponse confirmar(
      @PathVariable UUID inspeccionId, @Valid @RequestBody ConfirmarEstadoIcRequest request) {
    return service.confirmar(inspeccionId, request);
  }
}
