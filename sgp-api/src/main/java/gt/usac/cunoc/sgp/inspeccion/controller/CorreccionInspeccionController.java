package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.CrearCorreccionRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.service.CorreccionInspeccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspecciones")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inspecciones", description = "Gestión e inicio de inspecciones de puentes")
public class CorreccionInspeccionController {

  private final CorreccionInspeccionService correcciones;

  public CorreccionInspeccionController(CorreccionInspeccionService correcciones) {
    this.correcciones = correcciones;
  }

  @PostMapping("/{id}/correcciones")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Corregir una inspección publicada",
      description =
          "Crea una inspección de tipo CORRECCION en BORRADOR relacionada con la original mediante "
              + "supersede_a; la original no cambia. Solo el autor o un Catedrático (403), con "
              + "motivo obligatorio (422). Un borrador u otra inspección no publicada responde 409.")
  public ResponseEntity<InspeccionResumenResponse> crear(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) CrearCorreccionRequest request,
      Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwt)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida para corregir una inspección.");
    }
    InspeccionResumenResponse correccion =
        correcciones.crear(id, request == null ? null : request.motivo(), jwt.userId(), jwt.role());
    return ResponseEntity.created(URI.create("/api/v1/inspecciones/" + correccion.id()))
        .body(correccion);
  }
}
