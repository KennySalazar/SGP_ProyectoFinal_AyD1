package gt.usac.cunoc.sgp.puente.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.dto.CrearPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.PuenteCatalogoResponse;
import gt.usac.cunoc.sgp.puente.dto.PuenteResponse;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/puentes")
@Tag(name = "Puentes", description = "Catálogo de puentes")
public class PuenteController {

  private final PuenteService puenteService;

  public PuenteController(PuenteService puenteService) {
    this.puenteService = puenteService;
  }

  @GetMapping
  @Operation(
      summary = "Consultar el catálogo público de puentes activos",
      description =
          "Acceso sin autenticación. Filtros opcionales por departamentoId (UUID) y estado "
              + "(Bueno, Regular, Malo, Sin evaluar). Página desde 0, tamaño entre 1 y 100. "
              + "Orden por nombre e ID. En este sprint todos los puentes están Sin evaluar.")
  public Page<PuenteCatalogoResponse> listarCatalogo(
      @RequestParam(required = false) UUID departamentoId,
      @RequestParam(required = false) String estado,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return puenteService.listarCatalogo(departamentoId, estado, pagina, tamanio);
  }

  @PostMapping
  @SecurityRequirement(name = "bearerAuth")
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
