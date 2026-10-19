package gt.usac.cunoc.sgp.revision.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.revision.dto.*;
import gt.usac.cunoc.sgp.revision.service.ObservacionService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/revisiones/{revisionId}/observaciones")
@PreAuthorize("hasRole('CATEDRATICO')")
public class ObservacionController {
  private final ObservacionService observaciones;

  public ObservacionController(ObservacionService observaciones) {
    this.observaciones = observaciones;
  }

  @GetMapping
  public ListadoObservacionesResponse listar(@PathVariable UUID revisionId, Authentication auth) {
    return observaciones.listar(revisionId, actor(auth));
  }

  @GetMapping("/contador")
  public ContadorObservacionesResponse contar(@PathVariable UUID revisionId, Authentication auth) {
    return observaciones.contar(revisionId, actor(auth));
  }

  @GetMapping("/{observacionId}")
  public ObservacionResponse obtener(
      @PathVariable UUID revisionId, @PathVariable UUID observacionId, Authentication auth) {
    return observaciones.obtener(revisionId, observacionId, actor(auth));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ObservacionResponse crear(
      @PathVariable UUID revisionId,
      @RequestBody CrearObservacionRequest request,
      Authentication auth) {
    return observaciones.crear(revisionId, actor(auth), request);
  }

  @PutMapping("/{observacionId}")
  public ObservacionResponse editar(
      @PathVariable UUID revisionId,
      @PathVariable UUID observacionId,
      @RequestBody EditarObservacionRequest request,
      Authentication auth) {
    return observaciones.editar(revisionId, observacionId, actor(auth), request);
  }

  @DeleteMapping("/{observacionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void eliminar(
      @PathVariable UUID revisionId, @PathVariable UUID observacionId, Authentication auth) {
    observaciones.eliminar(revisionId, observacionId, actor(auth));
  }

  @GetMapping("/general")
  public ObservacionGeneralResponse general(@PathVariable UUID revisionId, Authentication auth) {
    return observaciones.obtenerGeneral(revisionId, actor(auth));
  }

  @PutMapping("/general")
  public ObservacionGeneralResponse guardarGeneral(
      @PathVariable UUID revisionId,
      @RequestBody ObservacionGeneralRequest request,
      Authentication auth) {
    return observaciones.guardarGeneral(revisionId, actor(auth), request);
  }

  private UUID actor(Authentication auth) {
    if (auth == null
        || !auth.isAuthenticated()
        || !(auth.getDetails() instanceof JwtData jwt)
        || jwt.userId() == null) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    }
    return jwt.userId();
  }
}
