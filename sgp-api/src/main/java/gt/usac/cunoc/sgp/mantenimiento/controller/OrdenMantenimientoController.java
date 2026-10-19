package gt.usac.cunoc.sgp.mantenimiento.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.mantenimiento.dto.CerrarSinEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.CrearOrdenMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.HistorialEstadoOrdenResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.IniciarEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.ProgramarOrdenRequest;
import gt.usac.cunoc.sgp.mantenimiento.service.OrdenMantenimientoService;
import gt.usac.cunoc.sgp.mantenimiento.service.TransicionOrdenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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

/**
 * Órdenes de mantenimiento. No asigna estados: los cambios pasan por {@link
 * TransicionOrdenService}, la única puerta de entrada (DT-BE-07).
 */
@RestController
@RequestMapping("/api/v1/ordenes-mantenimiento")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Mantenimiento", description = "Ciclo de vida de las órdenes de mantenimiento")
public class OrdenMantenimientoController {

  private final OrdenMantenimientoService ordenes;
  private final TransicionOrdenService transiciones;

  public OrdenMantenimientoController(
      OrdenMantenimientoService ordenes, TransicionOrdenService transiciones) {
    this.ordenes = ordenes;
    this.transiciones = transiciones;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Crear una orden manual",
      description =
          "Nace en PROPUESTA para un puente activo (409 si está dado de baja). Tipo, descripción y "
              + "prioridad son obligatorios; tipo RUTINARIO, PREVENTIVO, CORRECTIVO o EMERGENCIA "
              + "(422 en otro caso). Si indica una inspección, debe ser del mismo puente (409).")
  public ResponseEntity<OrdenMantenimientoResponse> crear(
      @RequestBody CrearOrdenMantenimientoRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ordenes.crear(request, usuarioId(authentication)));
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Listar órdenes de mantenimiento",
      description =
          "Filtros opcionales puenteId y estado. Incluye las órdenes de puentes dados de baja. "
              + "Página desde 0, tamaño entre 1 y 100; de la más reciente a la más antigua.")
  public Page<OrdenMantenimientoResponse> listar(
      @RequestParam(required = false) UUID puenteId,
      @RequestParam(required = false) String estado,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return ordenes.listar(puenteId, estado, pagina, tamanio);
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Consultar una orden de mantenimiento")
  public OrdenMantenimientoResponse obtener(@PathVariable UUID id) {
    return ordenes.obtener(id);
  }

  @GetMapping("/{id}/historial")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Historial de estados de una orden",
      description = "Ordenado por secuencia; cada fila es inmutable.")
  public List<HistorialEstadoOrdenResponse> historial(@PathVariable UUID id) {
    return ordenes.historial(id);
  }

  @PostMapping("/{id}/programar")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Aceptar y programar una orden propuesta",
      description =
          "PROPUESTA a PROGRAMADA. La fecha no puede ser anterior a hoy (422). Exige la versión "
              + "leída de la orden; si otro usuario la cambió responde 409.")
  public OrdenMantenimientoResponse programar(
      @PathVariable UUID id,
      @RequestBody ProgramarOrdenRequest request,
      Authentication authentication) {
    return transiciones.aceptarYProgramar(id, request, usuarioId(authentication));
  }

  @PostMapping("/{id}/descartar")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Descartar una orden propuesta",
      description = "PROPUESTA a DESCARTADA con motivo obligatorio (422 sin motivo).")
  public OrdenMantenimientoResponse descartar(
      @PathVariable UUID id,
      @RequestBody CerrarSinEjecucionRequest request,
      Authentication authentication) {
    return transiciones.descartar(id, request, usuarioId(authentication));
  }

  @PostMapping("/{id}/iniciar")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Iniciar la ejecución de una orden programada",
      description = "PROGRAMADA a EN_EJECUCION; crea el registro de ejecución.")
  public OrdenMantenimientoResponse iniciar(
      @PathVariable UUID id,
      @RequestBody IniciarEjecucionRequest request,
      Authentication authentication) {
    return transiciones.iniciarEjecucion(id, request, usuarioId(authentication));
  }

  @PostMapping("/{id}/cancelar")
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Operation(
      summary = "Cancelar una orden programada",
      description = "PROGRAMADA a CANCELADA con motivo obligatorio (422 sin motivo).")
  public OrdenMantenimientoResponse cancelar(
      @PathVariable UUID id,
      @RequestBody CerrarSinEjecucionRequest request,
      Authentication authentication) {
    return transiciones.cancelar(id, request, usuarioId(authentication));
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
