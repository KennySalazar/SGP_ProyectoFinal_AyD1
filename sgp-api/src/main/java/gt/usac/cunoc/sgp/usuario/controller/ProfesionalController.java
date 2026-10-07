package gt.usac.cunoc.sgp.usuario.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.usuario.dto.ProfesionalResponse;
import gt.usac.cunoc.sgp.usuario.service.ProfesionalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profesionales")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(
    name = "Profesionales externos",
    description = "Verificación del número de colegiado por el Administrador")
public class ProfesionalController {

  private final ProfesionalService profesionalService;

  public ProfesionalController(ProfesionalService profesionalService) {
    this.profesionalService = profesionalService;
  }

  @GetMapping
  @Operation(
      summary = "Listar profesionales externos",
      description =
          "Solo administradores. Filtro opcional por verificado (true o false). Página desde 0, "
              + "tamaño entre 1 y 100. Orden por fecha de registro descendente.")
  public Page<ProfesionalResponse> listar(
      @RequestParam(required = false) Boolean verificado,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return profesionalService.listar(verificado, pagina, tamanio);
  }

  @PostMapping("/{usuarioId}/verificacion-colegiado")
  @Operation(
      summary = "Verificar el número de colegiado",
      description =
          "Solo administradores. Habilita por completo al Profesional Externo, incluidas las "
              + "acciones de inspección (RN-USR-04). Un colegiado ya verificado responde 409.")
  public ProfesionalResponse verificarColegiado(
      @PathVariable UUID usuarioId, Authentication authentication) {
    profesionalService.verificarColegiado(usuarioId, administradorId(authentication));
    return profesionalService.obtener(usuarioId);
  }

  private UUID administradorId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para verificar colegiados.");
    }
    return jwtData.userId();
  }
}
