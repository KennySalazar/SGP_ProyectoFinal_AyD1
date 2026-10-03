package gt.usac.cunoc.sgp.puente.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.dto.CrearPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.PuenteResponse;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/puentes")
@Tag(name = "Puentes", description = "Catálogo de puentes")
@SecurityRequirement(name = "bearerAuth")
public class PuenteController {

  private final PuenteService puenteService;

  public PuenteController(PuenteService puenteService) {
    this.puenteService = puenteService;
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Registrar un puente",
      description =
          "Registra un puente activo con estado Sin evaluar. "
              + "Si existen puentes a menos de 100 metros, solicita "
              + "confirmación mediante confirmarCercania.")
  public ResponseEntity<PuenteResponse> registrar(
      @Valid @RequestBody CrearPuenteRequest request, Authentication authentication) {

    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para registrar un puente.");
    }

    PuenteResponse response = puenteService.registrar(request, jwtData.userId());

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
