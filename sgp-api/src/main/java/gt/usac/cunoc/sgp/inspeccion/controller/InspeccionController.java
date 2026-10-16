package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarRespuestasRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ValidarRespuestasRequest;
import gt.usac.cunoc.sgp.inspeccion.service.InspeccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspecciones")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inspecciones", description = "Gestión del ciclo de vida y respuestas de inspecciones")
public class InspeccionController {

  private final InspeccionService service;

  public InspeccionController(InspeccionService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Iniciar una nueva inspección",
      description =
          "Crea una inspección en BORRADOR asociada a un puente y versión publicada de formulario.")
  public ResponseEntity<InspeccionResumenResponse> iniciar(
      @Valid @RequestBody IniciarInspeccionRequest request, Authentication authentication) {
    JwtData jwt = jwtData(authentication);
    InspeccionResumenResponse response =
        service.iniciarInspeccion(
            request, jwt.userId(), jwt.role() != null ? jwt.role().name() : "INSPECTOR");
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Consultar inspección por ID",
      description = "Obtiene los detalles y datos capturados de una inspección.")
  public ResponseEntity<InspeccionResumenResponse> consultar(@PathVariable UUID id) {
    return ResponseEntity.ok(service.consultarPorId(id));
  }

  @PutMapping("/{id}/datos")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Guardar respuestas de una inspección en borrador",
      description =
          "Valida las respuestas contra el esquema de la versión de la inspección en modo borrador y actualiza los datos.")
  public ResponseEntity<InspeccionResumenResponse> guardarRespuestas(
      @PathVariable UUID id,
      @Valid @RequestBody GuardarRespuestasRequest request,
      Authentication authentication) {
    JwtData jwt = jwtData(authentication);
    return ResponseEntity.ok(service.guardarRespuestas(id, request, jwt.userId()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Guardar respuestas de una inspección",
      description = "Ruta alternativa para actualizar los datos de una inspección.")
  public ResponseEntity<InspeccionResumenResponse> actualizar(
      @PathVariable UUID id,
      @Valid @RequestBody GuardarRespuestasRequest request,
      Authentication authentication) {
    JwtData jwt = jwtData(authentication);
    return ResponseEntity.ok(service.guardarRespuestas(id, request, jwt.userId()));
  }

  @PostMapping("/{id}/enviar")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Enviar inspección para revisión",
      description =
          "Valida exhaustivamente todos los campos obligatorios del esquema de su versión y cambia el estado a ENVIADA.")
  public ResponseEntity<InspeccionResumenResponse> enviar(
      @PathVariable UUID id, Authentication authentication) {
    JwtData jwt = jwtData(authentication);
    return ResponseEntity.ok(service.enviarInspeccion(id, jwt.userId()));
  }

  @PostMapping("/{id}/validar")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Validar respuestas contra el esquema de la inspección",
      description =
          "Ejecuta la validación de un payload de respuestas contra el esquema de la versión de la inspección sin persistir cambios.")
  public ResponseEntity<Map<String, Object>> validar(
      @PathVariable UUID id, @Valid @RequestBody ValidarRespuestasRequest request) {
    service.validarRespuestasInspeccion(id, request.datos(), request.esBorrador());
    return ResponseEntity.ok(Map.of("valido", true, "mensaje", "Las respuestas son válidas"));
  }

  private JwtData jwtData(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para realizar operaciones de inspección.");
    }
    return jwtData;
  }
}
