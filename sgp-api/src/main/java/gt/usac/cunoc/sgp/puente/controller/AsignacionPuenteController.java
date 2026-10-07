package gt.usac.cunoc.sgp.puente.controller;

import gt.usac.cunoc.sgp.puente.dto.AsignacionPuenteResponse;
import gt.usac.cunoc.sgp.puente.dto.AsignarPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.EstudianteAsignableResponse;
import gt.usac.cunoc.sgp.puente.dto.PuenteAsignableResponse;
import gt.usac.cunoc.sgp.puente.dto.RevocarAsignacionPuenteRequest;
import gt.usac.cunoc.sgp.puente.service.AsignacionPuenteService;
import gt.usac.cunoc.sgp.usuario.dto.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/asignaciones-puentes")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Asignaciones de puentes")
public class AsignacionPuenteController {

  private final AsignacionPuenteService asignaciones;

  public AsignacionPuenteController(AsignacionPuenteService asignaciones) {
    this.asignaciones = asignaciones;
  }

  @GetMapping("/estudiantes")
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Operation(summary = "Listar estudiantes activos vinculados a cursos vigentes propios")
  public List<EstudianteAsignableResponse> listarEstudiantes(Authentication authentication) {
    return asignaciones.listarEstudiantesAsignables(authentication.getName());
  }

  @GetMapping("/puentes")
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Operation(summary = "Listar puentes activos asignables")
  public List<PuenteAsignableResponse> listarPuentes(Authentication authentication) {
    return asignaciones.listarPuentesAsignables(authentication.getName());
  }

  @GetMapping
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Operation(summary = "Listar asignaciones activas de los cursos propios")
  public List<AsignacionPuenteResponse> listar(Authentication authentication) {
    return asignaciones.listarAsignaciones(authentication.getName());
  }

  @PostMapping
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Operation(summary = "Asignar un puente activo a un estudiante vinculado")
  public ResponseEntity<MessageResponse> asignar(
      Authentication authentication, @Valid @RequestBody AsignarPuenteRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(asignaciones.asignar(authentication.getName(), request));
  }

  @PatchMapping("/{asignacionId}/revocar")
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Operation(summary = "Revocar una asignación activa de puente")
  public MessageResponse revocar(
      Authentication authentication,
      @PathVariable UUID asignacionId,
      @Valid @RequestBody RevocarAsignacionPuenteRequest request) {
    return asignaciones.revocar(authentication.getName(), asignacionId, request);
  }

  @GetMapping("/mis-puentes")
  @PreAuthorize("hasRole('ESTUDIANTE')")
  @Operation(summary = "Listar los puentes activos asignados al estudiante actual")
  public List<AsignacionPuenteResponse> listarMisPuentes(Authentication authentication) {
    return asignaciones.listarMisPuentes(authentication.getName());
  }
}
