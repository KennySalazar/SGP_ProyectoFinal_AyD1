package gt.usac.cunoc.sgp.revision.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.revision.dto.BorradorEstudianteResponse;
import gt.usac.cunoc.sgp.revision.dto.ContadorRevisionesResponse;
import gt.usac.cunoc.sgp.revision.dto.RevisionBandejaResponse;
import gt.usac.cunoc.sgp.revision.dto.RevisionResueltaResponse;
import gt.usac.cunoc.sgp.revision.service.RevisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/revisiones")
@PreAuthorize("hasRole('CATEDRATICO')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Revisión académica", description = "Bandeja y ciclos de revisión de inspecciones")
public class RevisionController {

  private final RevisionService revisiones;

  public RevisionController(RevisionService revisiones) {
    this.revisiones = revisiones;
  }

  @GetMapping("/bandeja")
  @Operation(summary = "Listar la bandeja de revisión del catedrático autenticado")
  public Page<RevisionBandejaResponse> listar(
      @RequestParam(required = false) String estado,
      @RequestParam(required = false) String puente,
      @RequestParam(required = false) String estudiante,
      @RequestParam(required = false) String curso,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fechaDesde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fechaHasta,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio,
      Authentication authentication) {
    return revisiones.listarBandeja(
        usuarioId(authentication),
        estado,
        puente,
        estudiante,
        curso,
        fechaDesde,
        fechaHasta,
        pagina,
        tamanio);
  }

  @GetMapping("/revisadas")
  @Operation(summary = "Listar los ciclos resueltos por el catedrático autenticado")
  public ResponseEntity<Page<RevisionResueltaResponse>> listarResueltas(
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio,
      Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore().cachePrivate())
        .body(revisiones.listarResueltas(usuarioId(authentication), pagina, tamanio));
  }

  @GetMapping("/pendientes/contador")
  @Operation(summary = "Contar inspecciones pendientes o en curso del catedrático")
  public ContadorRevisionesResponse contar(Authentication authentication) {
    return revisiones.contarPendientes(usuarioId(authentication));
  }

  @GetMapping("/borradores")
  @Operation(summary = "Listar borradores de estudiantes de los cursos del catedrático")
  public Page<BorradorEstudianteResponse> listarBorradores(
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio,
      Authentication authentication) {
    return revisiones.listarBorradores(usuarioId(authentication), pagina, tamanio);
  }

  @PostMapping("/{revisionId}/iniciar")
  @Operation(summary = "Iniciar o reanudar un ciclo de revisión propio")
  public InspeccionResumenResponse iniciar(
      @PathVariable UUID revisionId, Authentication authentication) {
    return revisiones.iniciar(revisionId, usuarioId(authentication));
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida para consultar revisiones.");
    }
    return jwtData.userId();
  }
}
