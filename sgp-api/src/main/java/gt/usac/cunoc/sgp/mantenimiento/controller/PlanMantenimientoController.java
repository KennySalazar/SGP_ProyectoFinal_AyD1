package gt.usac.cunoc.sgp.mantenimiento.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.mantenimiento.dto.ActualizarPlanMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.PlanMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.service.PlanMantenimientoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/planes-mantenimiento")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Mantenimiento", description = "Planes rutinarios de mantenimiento por puente")
public class PlanMantenimientoController {

  private final PlanMantenimientoService planes;

  public PlanMantenimientoController(PlanMantenimientoService planes) {
    this.planes = planes;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<PlanMantenimientoResponse> listar() {
    return planes.listar();
  }

  @GetMapping("/puente/{puenteId}")
  @PreAuthorize("isAuthenticated()")
  public PlanMantenimientoResponse obtenerPorPuente(@PathVariable UUID puenteId) {
    return planes.obtenerPorPuente(puenteId);
  }

  @PutMapping("/puente/{puenteId}")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  public PlanMantenimientoResponse actualizar(
      @PathVariable UUID puenteId,
      @Valid @RequestBody ActualizarPlanMantenimientoRequest request,
      Authentication authentication) {
    return planes.actualizar(puenteId, request, usuarioId(authentication));
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida para gestionar planes de mantenimiento.");
    }
    return jwtData.userId();
  }
}
