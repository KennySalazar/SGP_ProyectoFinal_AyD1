package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.DatosPermanentesResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarBorradorRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionBorradorResponse;
import gt.usac.cunoc.sgp.inspeccion.service.InspeccionService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspecciones")
@Tag(name = "Inspecciones", description = "Gestión e inicio de inspecciones de puentes")
public class InspeccionController {

  private final InspeccionService inspeccionService;

  public InspeccionController(InspeccionService inspeccionService) {
    this.inspeccionService = inspeccionService;
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Iniciar una inspección técnica de puente",
      description =
          "Crea una inspección en estado BORRADOR para un puente activo usando la versión activa de formulario. "
              + "Disponible únicamente para Catedráticos y Profesionales externos verificados. "
              + "Soporta idempotencia mediante el encabezado Idempotency-Key y el UUID de cliente.",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<IniciarInspeccionResponse> iniciarInspeccion(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody IniciarInspeccionRequest request,
      Authentication authentication) {
    UUID usuarioId = usuarioId(authentication);
    IniciarInspeccionResponse response =
        inspeccionService.iniciarInspeccion(request, usuarioId, idempotencyKey);
    return ResponseEntity.created(URI.create("/api/v1/inspecciones/" + response.id()))
        .body(response);
  }

  @GetMapping("/puentes/{puenteId}/datos-permanentes")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Consultar datos permanentes de la última inspección publicada",
      description =
          "Obtiene los datos técnicos permanentes (dimensiones, materiales, tipología, año de construcción) "
              + "del puente para la confirmación explícita de precarga al iniciar una inspección.",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<DatosPermanentesResponse> obtenerDatosPermanentes(
      @PathVariable UUID puenteId) {
    return inspeccionService
        .obtenerDatosPermanentesVigentes(puenteId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PutMapping({"/{id}", "/{id}/borrador"})
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Guardar avances parciales de una inspección en borrador",
      description =
          "Persiste los datos válidos ingresados permitiendo campos pendientes. "
              + "Solo el autor puede editar, únicamente en estado BORRADOR, y se rechazan modificaciones obsoletas (409).",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<InspeccionBorradorResponse> guardarBorrador(
      @PathVariable UUID id,
      @RequestBody GuardarBorradorRequest request,
      Authentication authentication) {
    UUID usuarioId = usuarioId(authentication);
    InspeccionBorradorResponse response = inspeccionService.guardarBorrador(id, request, usuarioId);
    return ResponseEntity.ok(response);
  }

  @GetMapping({"/{id}", "/{id}/borrador"})
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Consultar borrador de una inspección para reanudar edición",
      description =
          "Retorna la información guardada del borrador, el avance en las ocho secciones y el cálculo de IC preliminar si aplica. "
              + "Es privado: usuarios no autorizados reciben 404.",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<InspeccionBorradorResponse> obtenerBorrador(
      @PathVariable UUID id, Authentication authentication) {
    UUID usuarioId = usuarioId(authentication);
    return ResponseEntity.ok(inspeccionService.obtenerBorrador(id, usuarioId));
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida para iniciar inspecciones.");
    }
    return jwtData.userId();
  }
}
