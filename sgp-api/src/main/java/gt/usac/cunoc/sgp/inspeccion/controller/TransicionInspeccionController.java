package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.service.TransicionInspeccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acciones del autor que cambian el estado de una inspección. No modifica el estado: delega en
 * {@link TransicionInspeccionService}, la única puerta de entrada (DT-BE-07).
 */
@RestController
@RequestMapping("/api/v1/inspecciones")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inspecciones", description = "Gestión e inicio de inspecciones de puentes")
public class TransicionInspeccionController {

  private final TransicionInspeccionService transiciones;

  public TransicionInspeccionController(TransicionInspeccionService transiciones) {
    this.transiciones = transiciones;
  }

  @PostMapping({"/{id}/enviar", "/{id}/reenviar"})
  @PreAuthorize("hasRole('ESTUDIANTE')")
  @Operation(
      summary = "Enviar una inspección a revisión",
      description =
          "Solo el Estudiante autor, desde BORRADOR. Exige los campos obligatorios de su versión "
              + "y fotografías de accesos, superestructura, subestructura y cauce (422 con el "
              + "detalle de lo que falta). Una transición no permitida responde 409.")
  public InspeccionResumenResponse enviar(
      @PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      Authentication authentication) {
    UUID usuarioId = usuarioId(authentication);
    return idempotencyKey == null || idempotencyKey.isBlank()
        ? transiciones.enviar(id, usuarioId)
        : transiciones.enviar(id, usuarioId, idempotencyKey);
  }

  @PostMapping("/{id}/publicar")
  @PreAuthorize("hasAnyRole('CATEDRATICO','PROFESIONAL_EXTERNO')")
  @Operation(
      summary = "Publicar una inspección sin revisión previa",
      description =
          "Solo el autor Catedrático o Profesional externo verificado, desde BORRADOR. Pasa por "
              + "ENVIADA y queda PUBLICADA en una sola operación, calcula el IC definitivo y "
              + "actualiza el estado del puente; si algo falla no se publica nada (RN-REV-02).")
  public InspeccionResumenResponse publicar(@PathVariable UUID id, Authentication authentication) {
    return transiciones.publicar(id, usuarioId(authentication));
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida para cambiar el estado de una inspección.");
    }
    return jwtData.userId();
  }
}
