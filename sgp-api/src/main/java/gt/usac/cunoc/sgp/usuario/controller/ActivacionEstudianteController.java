package gt.usac.cunoc.sgp.usuario.controller;

import gt.usac.cunoc.sgp.usuario.dto.ActivarEstudianteRequest;
import gt.usac.cunoc.sgp.usuario.dto.CursoDisponibleResponse;
import gt.usac.cunoc.sgp.usuario.dto.EstudiantePendienteResponse;
import gt.usac.cunoc.sgp.usuario.dto.MessageResponse;
import gt.usac.cunoc.sgp.usuario.service.ActivacionEstudianteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activaciones-estudiantes")
@PreAuthorize("hasRole('CATEDRATICO')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Activacion de estudiantes")
public class ActivacionEstudianteController {

  private final ActivacionEstudianteService activaciones;

  public ActivacionEstudianteController(ActivacionEstudianteService activaciones) {
    this.activaciones = activaciones;
  }

  @GetMapping("/pendientes")
  @Operation(summary = "Listar estudiantes pendientes de activacion")
  public List<EstudiantePendienteResponse> listarPendientes() {
    return activaciones.listarPendientes();
  }

  @GetMapping("/cursos")
  @Operation(summary = "Listar cursos vigentes del catedratico")
  public List<CursoDisponibleResponse> listarCursos(Authentication authentication) {
    return activaciones.listarCursosVigentes(authentication.getName());
  }

  @PostMapping
  @Operation(summary = "Activar estudiante y vincularlo a un curso")
  public MessageResponse activar(
      Authentication authentication, @Valid @RequestBody ActivarEstudianteRequest request) {
    return activaciones.activar(authentication.getName(), request);
  }
}
