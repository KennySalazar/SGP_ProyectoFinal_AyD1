package gt.usac.cunoc.sgp.usuario.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.usuario.dto.CambiarRolRequest;
import gt.usac.cunoc.sgp.usuario.dto.DesactivarUsuarioRequest;
import gt.usac.cunoc.sgp.usuario.dto.UsuarioResponse;
import gt.usac.cunoc.sgp.usuario.model.EstadoUsuario;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Usuarios", description = "Gestión de cuentas por el Administrador")
public class UsuarioController {

  private final UsuarioService usuarioService;

  public UsuarioController(UsuarioService usuarioService) {
    this.usuarioService = usuarioService;
  }

  @GetMapping
  @Operation(
      summary = "Listar usuarios",
      description =
          "Solo administradores. Filtros opcionales por rol y estado (ACTIVO, PENDIENTE, INACTIVO). "
              + "Página desde 0, tamaño entre 1 y 100. Orden por correo.")
  public Page<UsuarioResponse> listar(
      @RequestParam(required = false) RoleName rol,
      @RequestParam(required = false) EstadoUsuario estado,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return usuarioService.listar(rol, estado, pagina, tamanio);
  }

  @PostMapping("/{usuarioId}/desactivacion")
  @Operation(
      summary = "Desactivar un usuario",
      description =
          "Baja lógica con motivo opcional (RN-USR-08): la cuenta no se elimina y sus registros "
              + "siguen atribuidos. Cierra sus sesiones. Un administrador no puede desactivarse "
              + "a sí mismo (422).")
  public UsuarioResponse desactivar(
      @PathVariable UUID usuarioId,
      @Valid @RequestBody(required = false) DesactivarUsuarioRequest request,
      Authentication authentication) {
    usuarioService.desactivar(usuarioId, request, administradorId(authentication));
    return usuarioService.obtener(usuarioId);
  }

  @PostMapping("/{usuarioId}/reactivacion")
  @Operation(
      summary = "Reactivar un usuario",
      description = "La cuenta recupera el acceso con el mismo rol que tenía.")
  public UsuarioResponse reactivar(@PathVariable UUID usuarioId) {
    usuarioService.reactivar(usuarioId);
    return usuarioService.obtener(usuarioId);
  }

  @PutMapping("/{usuarioId}/rol")
  @Operation(
      summary = "Cambiar el rol de un usuario",
      description =
          "Pasar a Profesional Externo exige numeroColegiado, que queda pendiente de verificación "
              + "(RN-USR-04). Cierra las sesiones del usuario. Un administrador no puede cambiar "
              + "su propio rol (422).")
  public UsuarioResponse cambiarRol(
      @PathVariable UUID usuarioId,
      @Valid @RequestBody CambiarRolRequest request,
      Authentication authentication) {
    usuarioService.cambiarRol(usuarioId, request, administradorId(authentication));
    return usuarioService.obtener(usuarioId);
  }

  private UUID administradorId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para gestionar usuarios.");
    }
    return jwtData.userId();
  }
}
