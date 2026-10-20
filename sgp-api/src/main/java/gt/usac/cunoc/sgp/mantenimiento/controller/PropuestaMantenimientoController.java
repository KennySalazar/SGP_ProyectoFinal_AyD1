package gt.usac.cunoc.sgp.mantenimiento.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.mantenimiento.dto.GeneracionPropuestasResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.service.GeneradorPropuestasMantenimientoService;
import gt.usac.cunoc.sgp.mantenimiento.service.OrdenesDeInspeccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Propuestas de mantenimiento que nacen de una inspección publicada (HU-046). */
@RestController
@RequestMapping("/api/v1/inspecciones/{inspeccionId}")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Mantenimiento", description = "Ciclo de vida de las órdenes de mantenimiento")
public class PropuestaMantenimientoController {

  private final GeneradorPropuestasMantenimientoService generador;
  private final OrdenesDeInspeccionService ordenesDeInspeccion;

  public PropuestaMantenimientoController(
      GeneradorPropuestasMantenimientoService generador,
      OrdenesDeInspeccionService ordenesDeInspeccion) {
    this.generador = generador;
    this.ordenesDeInspeccion = ordenesDeInspeccion;
  }

  @GetMapping("/ordenes-mantenimiento")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Órdenes de mantenimiento que originó una inspección publicada",
      description =
          "Cualquier usuario autenticado las consulta. Una inspección que no está publicada "
              + "responde 404 (RN-INS-05).")
  public ResponseEntity<List<OrdenMantenimientoResponse>> listar(@PathVariable UUID inspeccionId) {
    return ResponseEntity.ok(ordenesDeInspeccion.listar(inspeccionId));
  }

  @PostMapping("/propuestas-mantenimiento/regenerar")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Regenerar las propuestas de mantenimiento de una inspección publicada",
      description =
          "Vuelve a evaluar las reglas activas y crea solo las propuestas que faltan; no duplica "
              + "las ya generadas ni las que tienen una orden en curso. 409 si la inspección no "
              + "está publicada.")
  public ResponseEntity<GeneracionPropuestasResponse> regenerar(
      @PathVariable UUID inspeccionId, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.OK)
        .body(generador.regenerar(inspeccionId, usuarioId(authentication)));
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida para regenerar propuestas de mantenimiento.");
    }
    return jwtData.userId();
  }
}
