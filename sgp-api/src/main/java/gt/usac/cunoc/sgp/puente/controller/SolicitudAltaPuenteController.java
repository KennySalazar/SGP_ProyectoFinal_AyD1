package gt.usac.cunoc.sgp.puente.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.dto.AprobarSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.CrearSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.RechazarSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.SolicitudAltaPuenteResponse;
import gt.usac.cunoc.sgp.puente.dto.SolicitudRevisionDetalleResponse;
import gt.usac.cunoc.sgp.puente.dto.SolicitudRevisionResponse;
import gt.usac.cunoc.sgp.puente.model.EstadoSolicitudAltaPuente;
import gt.usac.cunoc.sgp.puente.service.SolicitudAltaPuenteService;
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
@RequestMapping("/api/v1/solicitudes-puente")
@SecurityRequirement(name = "bearerAuth")
@Tag(
    name = "Solicitudes de alta de puente",
    description = "Solicitudes del Catedrático y su revisión por el Administrador")
public class SolicitudAltaPuenteController {

  private final SolicitudAltaPuenteService solicitudService;

  public SolicitudAltaPuenteController(SolicitudAltaPuenteService solicitudService) {
    this.solicitudService = solicitudService;
  }

  @PostMapping
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Operation(
      summary = "Solicitar el alta de un puente",
      description =
          "Crea una solicitud en estado PENDIENTE para que el Administrador la evalúe. "
              + "El puente no existe en el catálogo hasta que la solicitud sea aprobada. "
              + "Si hay puentes a menos de 100 metros responde 409 hasta que se envíe confirmarCercania.")
  public ResponseEntity<SolicitudAltaPuenteResponse> crear(
      @Valid @RequestBody CrearSolicitudAltaPuenteRequest request, Authentication authentication) {
    SolicitudAltaPuenteResponse response =
        solicitudService.crear(request, usuarioId(authentication));

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/mias")
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Operation(
      summary = "Consultar mis solicitudes de alta",
      description =
          "Lista las solicitudes del Catedrático autenticado con su estado actual "
              + "(PENDIENTE, APROBADA o RECHAZADA) y el motivo de rechazo cuando aplica. "
              + "Página desde 0, tamaño entre 1 y 100. Orden por fecha de creación descendente.")
  public Page<SolicitudAltaPuenteResponse> listarMias(
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio,
      Authentication authentication) {
    return solicitudService.listarMias(usuarioId(authentication), pagina, tamanio);
  }

  @GetMapping("/mias/{id}")
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Operation(
      summary = "Consultar el detalle de una solicitud propia",
      description =
          "Devuelve la solicitud con su estado, el motivo de rechazo y el código del puente creado. "
              + "Una solicitud de otro usuario responde 404.")
  public SolicitudAltaPuenteResponse obtenerMia(
      @PathVariable UUID id, Authentication authentication) {
    return solicitudService.obtenerMia(id, usuarioId(authentication));
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Listar solicitudes de alta para revisión",
      description =
          "Solo administradores. Filtra por estado (PENDIENTE por defecto). Las pendientes se ordenan "
              + "por antigüedad; el resto, de la más reciente a la más antigua. "
              + "Página desde 0, tamaño entre 1 y 100.")
  public Page<SolicitudRevisionResponse> listarParaRevision(
      @RequestParam(required = false) EstadoSolicitudAltaPuente estado,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return solicitudService.listarParaRevision(estado, pagina, tamanio);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Consultar una solicitud de alta para revisión",
      description =
          "Solo administradores. Incluye los puentes existentes a menos de 100 metros "
              + "(advertencia de posible duplicado, RN-INV-06).")
  public SolicitudRevisionDetalleResponse obtenerParaRevision(@PathVariable UUID id) {
    return solicitudService.obtenerParaRevision(id);
  }

  @PostMapping("/{id}/aprobar")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Aprobar una solicitud de alta",
      description =
          "Crea el puente en el catálogo con su código único y notifica al solicitante. "
              + "Si hay puentes a menos de 100 metros responde 409 hasta que se envíe confirmarCercania.")
  public SolicitudAltaPuenteResponse aprobar(
      @PathVariable UUID id,
      @Valid @RequestBody AprobarSolicitudAltaPuenteRequest request,
      Authentication authentication) {
    return solicitudService.aprobar(id, request, usuarioId(authentication));
  }

  @PostMapping("/{id}/rechazar")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Rechazar una solicitud de alta",
      description =
          "Marca la solicitud como rechazada con el motivo indicado, sin crear ningún puente, "
              + "y notifica al solicitante.")
  public SolicitudAltaPuenteResponse rechazar(
      @PathVariable UUID id,
      @Valid @RequestBody RechazarSolicitudAltaPuenteRequest request,
      Authentication authentication) {
    return solicitudService.rechazar(id, request, usuarioId(authentication));
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para gestionar solicitudes de alta de puente.");
    }
    return jwtData.userId();
  }
}
