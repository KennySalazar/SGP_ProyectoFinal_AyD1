package gt.usac.cunoc.sgp.mantenimiento.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.mantenimiento.dto.EjecucionOrdenResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.EvidenciaMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.RegistrarEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.ResponsableDisponibleResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.RetirarEvidenciaRequest;
import gt.usac.cunoc.sgp.mantenimiento.service.EjecucionOrdenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ejecución de una orden: responsable, fecha y fotografías de evidencia (HU-048). El cierre como
 * ejecutada es una transición y está en {@link OrdenMantenimientoController}.
 */
@RestController
@RequestMapping("/api/v1/ordenes-mantenimiento")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Mantenimiento", description = "Ciclo de vida de las órdenes de mantenimiento")
public class EjecucionOrdenController {

  private final EjecucionOrdenService ejecucion;

  public EjecucionOrdenController(EjecucionOrdenService ejecucion) {
    this.ejecucion = ejecucion;
  }

  @GetMapping("/responsables")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(summary = "Usuarios activos que pueden ser responsables de una intervención")
  public List<ResponsableDisponibleResponse> responsables() {
    return ejecucion.listarResponsables();
  }

  @GetMapping("/{id}/ejecucion")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Consultar el registro de ejecución de una orden",
      description = "404 si la orden todavía no inició su ejecución.")
  public EjecucionOrdenResponse obtenerEjecucion(@PathVariable UUID id) {
    return ejecucion.obtenerEjecucion(id);
  }

  @PutMapping("/{id}/ejecucion")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Registrar la fecha de ejecución y el responsable",
      description =
          "Solo para órdenes EN_EJECUCION (409 si ya está EJECUTADA). El responsable es un "
              + "usuario del sistema o el nombre de una persona o empresa externa (422 si falta). "
              + "La fecha no puede ser futura ni anterior al día en que se inició (422).")
  public EjecucionOrdenResponse registrarEjecucion(
      @PathVariable UUID id,
      @RequestBody RegistrarEjecucionRequest request,
      Authentication authentication) {
    return ejecucion.registrarEjecucion(id, request, usuarioId(authentication));
  }

  @GetMapping("/{id}/evidencias")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Listar las fotografías de evidencia disponibles de una orden")
  public List<EvidenciaMantenimientoResponse> evidencias(@PathVariable UUID id) {
    return ejecucion.listarEvidencias(id);
  }

  @PostMapping(value = "/{id}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Adjuntar una fotografía de evidencia",
      description =
          "Valida el tipo real por firma binaria (JPEG o WebP) y el tamaño (422), genera el "
              + "nombre y la miniatura en el servidor. 409 si la orden no está EN_EJECUCION; 503 "
              + "si el almacenamiento no responde.")
  public ResponseEntity<EvidenciaMantenimientoResponse> adjuntarEvidencia(
      @PathVariable UUID id,
      @RequestPart("archivo") MultipartFile archivo,
      @RequestParam(required = false) String elementoRef,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ejecucion.adjuntarEvidencia(id, elementoRef, archivo, usuarioId(authentication)));
  }

  @PostMapping("/{id}/evidencias/{evidenciaId}/retirar")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Retirar una evidencia subida por error",
      description = "Motivo obligatorio (422). 409 si la orden ya está EJECUTADA.")
  public ResponseEntity<Void> retirarEvidencia(
      @PathVariable UUID id,
      @PathVariable UUID evidenciaId,
      @RequestBody RetirarEvidenciaRequest request,
      Authentication authentication) {
    ejecucion.retirarEvidencia(id, evidenciaId, request, usuarioId(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/evidencias/{evidenciaId}/contenido")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Fotografía de evidencia (extremo autorizado)")
  public ResponseEntity<InputStreamResource> contenido(
      @PathVariable UUID id, @PathVariable UUID evidenciaId) {
    return imagen(id, evidenciaId, false);
  }

  @GetMapping("/{id}/evidencias/{evidenciaId}/miniatura")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Miniatura de una fotografía de evidencia (extremo autorizado)")
  public ResponseEntity<InputStreamResource> miniatura(
      @PathVariable UUID id, @PathVariable UUID evidenciaId) {
    return imagen(id, evidenciaId, true);
  }

  private ResponseEntity<InputStreamResource> imagen(UUID id, UUID evidenciaId, boolean miniatura) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(evidenciaId + (miniatura ? "-mini" : "") + ".jpg")
                .build()
                .toString())
        .body(new InputStreamResource(ejecucion.contenidoEvidencia(id, evidenciaId, miniatura)));
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida para gestionar órdenes de mantenimiento.");
    }
    return jwtData.userId();
  }
}
