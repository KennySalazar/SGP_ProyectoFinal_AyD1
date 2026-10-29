package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.service.PermisosCorreccionService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inspecciones")
public class PermisosCorreccionController {
  private final InspeccionRepository inspecciones;
  private final PermisosCorreccionService permisos;

  public PermisosCorreccionController(
      InspeccionRepository inspecciones, PermisosCorreccionService permisos) {
    this.inspecciones = inspecciones;
    this.permisos = permisos;
  }

  @GetMapping("/{id}/permisos-correccion")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public PermisosCorreccionService.Permisos consultar(@PathVariable UUID id, Authentication auth) {
    if (auth == null || !(auth.getDetails() instanceof JwtData usuario))
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "autenticacion_requerida", "Autenticacion", "Inicie sesion.");
    var inspeccion = inspecciones.findByIdAndEliminadoEnIsNull(id).orElse(null);
    if (inspeccion == null || !usuario.userId().equals(inspeccion.getAutorId()))
      throw new ApiException(
          HttpStatus.NOT_FOUND,
          "inspeccion_no_encontrada",
          "Inspeccion",
          "Inspeccion no encontrada.");
    return permisos.consultar(inspeccion);
  }
}
