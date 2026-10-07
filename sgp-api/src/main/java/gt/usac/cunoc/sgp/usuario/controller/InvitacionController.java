package gt.usac.cunoc.sgp.usuario.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.usuario.dto.AceptarInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.CrearInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.InvitacionPublicaResponse;
import gt.usac.cunoc.sgp.usuario.dto.InvitacionResponse;
import gt.usac.cunoc.sgp.usuario.dto.MessageResponse;
import gt.usac.cunoc.sgp.usuario.dto.TokenInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.model.EstadoInvitacion;
import gt.usac.cunoc.sgp.usuario.service.InvitacionService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitaciones")
@Tag(name = "Invitaciones", description = "Alta de cuentas por invitación del Administrador")
public class InvitacionController {

  private final InvitacionService invitacionService;

  public InvitacionController(InvitacionService invitacionService) {
    this.invitacionService = invitacionService;
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Invitar a un usuario",
      description =
          "Solo administradores. Crea la cuenta pendiente de invitación y envía un enlace de "
              + "activación de un solo uso y vigencia limitada. Por ahora solo admite el rol "
              + "CATEDRATICO. Un correo ya registrado responde 409.")
  public ResponseEntity<InvitacionResponse> invitar(
      @Valid @RequestBody CrearInvitacionRequest request, Authentication authentication) {
    InvitacionResponse response =
        invitacionService.invitar(request, administradorId(authentication));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Listar invitaciones",
      description =
          "Solo administradores. Filtro opcional por estado (PENDIENTE, VENCIDA, ACEPTADA, "
              + "CANCELADA). Página desde 0, tamaño entre 1 y 100. Orden por fecha de creación "
              + "descendente.")
  public Page<InvitacionResponse> listar(
      @RequestParam(required = false) EstadoInvitacion estado,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return invitacionService.listar(estado, pagina, tamanio);
  }

  @PostMapping("/{id}/reenvio")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Reenviar una invitación",
      description =
          "Solo administradores. Cancela el enlace anterior (pendiente o vencido) y envía uno "
              + "nuevo con vigencia renovada.")
  public ResponseEntity<InvitacionResponse> reenviar(
      @PathVariable UUID id, Authentication authentication) {
    InvitacionResponse response = invitacionService.reenviar(id, administradorId(authentication));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/validacion")
  @Operation(
      summary = "Validar un enlace de invitación",
      description =
          "Acceso sin autenticación. Devuelve el correo y el rol de la invitación. Un enlace "
              + "vencido responde 410; uno inválido o ya utilizado, 400.")
  public InvitacionPublicaResponse validar(@Valid @RequestBody TokenInvitacionRequest request) {
    return invitacionService.validar(request);
  }

  @PostMapping("/aceptacion")
  @Operation(
      summary = "Aceptar una invitación",
      description =
          "Acceso sin autenticación. Define la contraseña (mínimo 10 caracteres con letra y "
              + "dígito) y activa la cuenta con el rol de la invitación.")
  public MessageResponse aceptar(@Valid @RequestBody AceptarInvitacionRequest request) {
    return invitacionService.aceptar(request);
  }

  private UUID administradorId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para gestionar invitaciones.");
    }
    return jwtData.userId();
  }
}
