package gt.usac.cunoc.sgp.puente.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.dto.ActualizarPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.CrearPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.DarBajaPuenteRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
      summary = "Consultar el catálogo público de puentes",
      description =
          "Acceso sin autenticación para puentes activos. Filtros opcionales por departamentoId (UUID), estado "
              + "(Bueno, Regular, Malo, Sin evaluar) y activo (solo administradores). Página desde 0, tamaño entre 1 y 100. "
              + "Orden por nombre e ID.")
  public Page<PuenteCatalogoResponse> listarCatalogo(
      @RequestParam(required = false) UUID departamentoId,
      @RequestParam(required = false) String estado,
      @RequestParam(required = false) Boolean activo,
      @RequestParam(required = false, defaultValue = "false") boolean todos,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio,
      Authentication authentication) {
    boolean esAdmin =
        authentication != null
            && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMINISTRADOR".equals(a.getAuthority()));
    if (esAdmin && (activo != null || todos)) {
      return puenteService.listarCatalogo(
          departamentoId, estado, todos ? null : activo, pagina, tamanio);
    }
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

  @GetMapping("/{id}")
  @SecurityRequirement(name = "bearerAuth")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Obtener un puente por su identificador",
      description = "Retorna los datos de un puente activo para administradores.")
  public ResponseEntity<PuenteResponse> obtenerPorId(@PathVariable UUID id) {
    return ResponseEntity.ok(puenteService.obtenerPorId(id));
  }

  @PutMapping("/{id}")
  @SecurityRequirement(name = "bearerAuth")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Actualizar los datos generales de un puente",
      description =
          "Actualiza nombre, ruta, kilometraje y coordenadas de un puente existente. "
              + "El código y los datos calculados son inmutables.")
  public ResponseEntity<PuenteResponse> actualizar(
      @PathVariable UUID id,
      @Valid @RequestBody ActualizarPuenteRequest request,
      Authentication authentication) {

    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para actualizar un puente.");
    }

    PuenteResponse response = puenteService.actualizar(id, request, jwtData.userId());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/baja")
  @SecurityRequirement(name = "bearerAuth")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Dar de baja lógica a un puente",
      description = "Marca un puente como inactivo documentando el motivo. Solo administradores.")
  public ResponseEntity<PuenteResponse> darDeBaja(
      @PathVariable UUID id,
      @Valid @RequestBody DarBajaPuenteRequest request,
      Authentication authentication) {

    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para dar de baja un puente.");
    }

    PuenteResponse response = puenteService.darDeBaja(id, request, jwtData.userId());

    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/reactivar")
  @SecurityRequirement(name = "bearerAuth")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Reactivar un puente",
      description = "Reactiva un puente previamente dado de baja lógica. Solo administradores.")
  public ResponseEntity<PuenteResponse> reactivar(
      @PathVariable UUID id, Authentication authentication) {

    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para reactivar un puente.");
    }

    PuenteResponse response = puenteService.reactivar(id, jwtData.userId());

    return ResponseEntity.ok(response);
  }
}
