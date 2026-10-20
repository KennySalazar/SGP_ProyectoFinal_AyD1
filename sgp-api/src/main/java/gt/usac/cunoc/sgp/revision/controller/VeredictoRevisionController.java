package gt.usac.cunoc.sgp.revision.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmacionEstadoIcResponse;
import gt.usac.cunoc.sgp.revision.dto.EmitirVeredictoRequest;
import gt.usac.cunoc.sgp.revision.dto.RevisionResultadoResponse;
import gt.usac.cunoc.sgp.revision.service.VeredictoRevisionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('CATEDRATICO','ESTUDIANTE')")
public class VeredictoRevisionController {
  private final VeredictoRevisionService servicio;

  public VeredictoRevisionController(VeredictoRevisionService servicio) {
    this.servicio = servicio;
  }

  @PostMapping("/revisiones/{id}/veredicto")
  @PreAuthorize("hasRole('CATEDRATICO')")
  public ResponseEntity<RevisionResultadoResponse> emitir(
      @PathVariable UUID id, @RequestBody EmitirVeredictoRequest request, Authentication auth) {
    return privada(servicio.emitir(id, actor(auth), request));
  }

  @GetMapping("/revisiones/{id}/resultado")
  public ResponseEntity<RevisionResultadoResponse> resultado(
      @PathVariable UUID id, Authentication auth) {
    return privada(servicio.resultado(id, actor(auth)));
  }

  @GetMapping("/revisiones/{id}/estado-puente")
  @PreAuthorize("hasRole('CATEDRATICO')")
  public ResponseEntity<ConfirmacionEstadoIcResponse> estado(
      @PathVariable UUID id, Authentication auth) {
    return privada(servicio.estadoPuente(id, actor(auth)));
  }

  @GetMapping("/inspecciones/{id}/revisiones")
  public ResponseEntity<List<RevisionResultadoResponse>> historial(
      @PathVariable UUID id, Authentication auth) {
    return privada(servicio.historial(id, actor(auth)));
  }

  private <T> ResponseEntity<T> privada(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
  }

  private UUID actor(Authentication auth) {
    if (auth == null
        || !auth.isAuthenticated()
        || !(auth.getDetails() instanceof JwtData jwt)
        || jwt.userId() == null)
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    return jwt.userId();
  }
}
