package gt.usac.cunoc.sgp.sync.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.sync.dto.*;
import gt.usac.cunoc.sgp.sync.dto.PaqueteSalidaResponse.PuenteSalida;
import gt.usac.cunoc.sgp.sync.service.PreparacionSalidaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/preparaciones-salida")
@Validated
@PreAuthorize("hasAnyRole('ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO')")
@SecurityRequirement(name = "bearerAuth")
@Tag(
    name = "Preparacion de salida",
    description = "HU-041: datos autorizados para trabajo sin conexion")
public class PreparacionSalidaController {
  private final PreparacionSalidaService servicio;

  public PreparacionSalidaController(PreparacionSalidaService servicio) {
    this.servicio = servicio;
  }

  @PostMapping
  @Operation(
      summary = "Preparar y registrar una salida",
      description =
          "Devuelve el paquete completo y registra su contexto autorizado. Usar Idempotency-Key para reintentos. El cliente debe guardar el paquete completo en una sola transaccion local; este registro no confirma persistencia en el dispositivo.")
  @ApiResponse(responseCode = "201", description = "Paquete completo y salida registrada")
  @ApiResponse(responseCode = "401", description = "Sesion requerida")
  @ApiResponse(responseCode = "403", description = "Rol, colegiado o seleccion no autorizados")
  public ResponseEntity<PreparacionSalidaResponse> preparar(
      Authentication auth, @Valid @RequestBody PrepararSalidaRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(servicio.preparar(usuario(auth), request));
  }

  @PostMapping("/estimacion")
  @Operation(
      summary = "Estimar la descarga sin registrar una salida",
      description =
          "Calcula bytes UTF-8 del paquete JSON sin comprimir. La seleccion y el volumen pueden cambiar hasta ejecutar Preparar salida.")
  public EstimacionSalidaResponse estimar(
      Authentication auth, @Valid @RequestBody PrepararSalidaRequest request) {
    return servicio.estimar(usuario(auth), request);
  }

  @GetMapping("/puentes")
  @Operation(summary = "Buscar puentes autorizados por nombre, codigo o departamento")
  public Page<PuenteSalida> disponibles(
      Authentication auth,
      @RequestParam(required = false) UUID departamentoId,
      @RequestParam(defaultValue = "") @Size(max = 200) String busqueda,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return servicio.disponibles(usuario(auth), departamentoId, busqueda, pagina, tamanio);
  }

  @GetMapping("/ultima")
  @Operation(
      summary = "Consultar el ultimo registro propio de un dispositivo",
      description =
          "No confirma que el dispositivo conserve los datos locales. Devuelve 204 si no existe registro para ese usuario y dispositivo.")
  public ResponseEntity<UltimaPreparacionResponse> ultima(
      Authentication auth, @RequestParam @NotBlank @Size(max = 100) String dispositivoId) {
    return servicio
        .ultima(usuario(auth), dispositivoId)
        .map(r -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(r))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  private UUID usuario(Authentication auth) {
    if (auth == null || !(auth.getDetails() instanceof JwtData jwt)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Sesion requerida",
          "Se requiere una sesion valida.");
    }
    return jwt.userId();
  }
}
