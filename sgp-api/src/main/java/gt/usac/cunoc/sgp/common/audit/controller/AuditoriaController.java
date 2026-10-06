package gt.usac.cunoc.sgp.common.audit.controller;

import gt.usac.cunoc.sgp.common.audit.dto.AuditoriaDetalleResponse;
import gt.usac.cunoc.sgp.common.audit.dto.AuditoriaResponse;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
@Tag(name = "Auditoría", description = "Registro de acciones relevantes del sistema")
public class AuditoriaController {

  private final AuditoriaConsultaService consulta;

  public AuditoriaController(AuditoriaConsultaService consulta) {
    this.consulta = consulta;
  }

  @GetMapping
  @SecurityRequirement(name = "bearerAuth")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Consultar la auditoría",
      description =
          "Listado paginado de acciones auditables, ordenado por fecha descendente. "
              + "Filtros opcionales por usuarioId (UUID), entidad y accion. "
              + "Fechas desde/hasta con zona horaria ISO-8601. "
              + "Página desde 0, tamaño entre 1 y 100.")
  public Page<AuditoriaResponse> consultar(
      @RequestParam(required = false) UUID usuarioId,
      @RequestParam(required = false) String entidad,
      @RequestParam(required = false) String accion,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime hasta,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return consulta.consultar(
        usuarioId,
        entidad,
        accion,
        desde == null ? null : desde.toInstant(),
        hasta == null ? null : hasta.toInstant(),
        pagina,
        tamanio);
  }

  @GetMapping("/{id}")
  @SecurityRequirement(name = "bearerAuth")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(summary = "Consultar el detalle legible de una acción auditada")
  public AuditoriaDetalleResponse detalle(@PathVariable UUID id) {
    return consulta.detalle(id);
  }
}
