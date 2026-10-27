package gt.usac.cunoc.sgp.inspeccion.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.ConflictoSincronizacionResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.DatosPermanentesResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ExportacionInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarBorradorRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarRespuestasRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionVersionadaRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionVersionadaResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionBorradorResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionPublicadaDetalleResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionPublicadaResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ValidarRespuestasRequest;
import gt.usac.cunoc.sgp.inspeccion.service.InspeccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspecciones")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inspecciones", description = "Gestión e inicio de inspecciones de puentes")
public class InspeccionController {

  private final InspeccionService inspeccionService;

  public InspeccionController(InspeccionService inspeccionService) {
    this.inspeccionService = inspeccionService;
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Iniciar una inspección técnica de puente",
      description =
          "Crea una inspección en estado BORRADOR para un puente activo usando la versión activa de"
              + " formulario. Los Estudiantes requieren una asignación académica vigente; también"
              + " está disponible para Catedráticos y Profesionales externos verificados. Soporta"
              + " idempotencia mediante el encabezado Idempotency-Key y el UUID de cliente.",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<IniciarInspeccionResponse> iniciarInspeccion(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody IniciarInspeccionRequest request,
      Authentication authentication) {
    UUID usuarioId = usuarioId(authentication);
    IniciarInspeccionResponse response =
        inspeccionService.iniciarInspeccionHu024(request, usuarioId, idempotencyKey);
    return ResponseEntity.created(URI.create("/api/v1/inspecciones/" + response.id()))
        .body(response);
  }

  @PostMapping("/iniciar")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Iniciar una inspección con una versión publicada específica",
      description =
          "Crea una inspección en BORRADOR con la versión publicada indicada y valida las"
              + " respuestas iniciales contra su esquema. Los Estudiantes requieren una asignación"
              + " académica vigente. No sustituye la versión solicitada por la versión activa.",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<IniciarInspeccionVersionadaResponse> iniciarConVersion(
      @Valid @RequestBody IniciarInspeccionVersionadaRequest request,
      Authentication authentication) {
    UUID usuarioId = usuarioId(authentication);
    String rolUsuario = rol(authentication);
    if ("ESTUDIANTE".equals(rolUsuario) && request.asignacionPuenteId() == null) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "puente_no_asignado",
          "Puente no asignado",
          "El estudiante debe indicar una asignación vigente para iniciar la inspección.");
    }
    var response = inspeccionService.iniciarInspeccion(request, usuarioId, rolUsuario);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/puentes/{puenteId}/datos-permanentes")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Consultar datos permanentes de la última inspección publicada",
      description =
          "Obtiene los datos técnicos permanentes (dimensiones, materiales, tipología, año de"
              + " construcción) del puente para la confirmación explícita de precarga al iniciar"
              + " una inspección.",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<DatosPermanentesResponse> obtenerDatosPermanentes(
      @PathVariable UUID puenteId) {
    return inspeccionService
        .obtenerDatosPermanentesVigentes(puenteId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PutMapping("/{id}/borrador")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Guardar avances parciales de una inspección en borrador",
      description =
          "Persiste los datos válidos ingresados permitiendo campos pendientes. Solo el autor puede"
              + " editar, únicamente en estado BORRADOR, y se rechazan modificaciones obsoletas"
              + " (409).",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<InspeccionBorradorResponse> guardarBorrador(
      @PathVariable UUID id,
      @RequestBody GuardarBorradorRequest request,
      @RequestHeader(value = "X-Sync-Conflict-Policy", required = false) String politicaConflicto,
      Authentication authentication,
      jakarta.servlet.http.HttpServletRequest servletRequest) {
    UUID usuarioId = usuarioId(authentication);
    boolean clientePrevalece = "client-wins".equals(politicaConflicto);
    InspeccionBorradorResponse response;
    if (clientePrevalece) {
      UUID operacionId = (UUID) servletRequest.getAttribute("operacionIdempotenteId");
      response = inspeccionService.guardarBorrador(id, request, usuarioId, true, operacionId);
    } else {
      response = inspeccionService.guardarBorrador(id, request, usuarioId);
    }
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}/conflictos-sincronizacion")
  @PreAuthorize("isAuthenticated()")
  public List<ConflictoSincronizacionResponse> listarConflictosSincronizacion(
      @PathVariable UUID id, Authentication authentication) {
    return inspeccionService.listarConflictosSincronizacion(id, usuarioId(authentication));
  }

  @GetMapping("/{id}/borrador")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Consultar borrador de una inspección para reanudar edición",
      description =
          "Retorna la información guardada del borrador, el avance en las ocho secciones y el"
              + " cálculo de IC preliminar si aplica. Es privado: usuarios no autorizados reciben"
              + " 404.",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<InspeccionBorradorResponse> obtenerBorrador(
      @PathVariable UUID id, Authentication authentication) {
    UUID usuarioId = usuarioId(authentication);
    return ResponseEntity.ok(inspeccionService.obtenerBorrador(id, usuarioId));
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Listar inspecciones del inspector autenticado",
      description =
          "Retorna la lista paginada de inspecciones no eliminadas del inspector autenticado, con"
              + " filtro opcional por estado. Página desde 0, tamaño entre 1 y 100. Orden"
              + " descendente por última actividad y fecha de creación.",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<Page<InspeccionResumenResponse>> listarMisInspecciones(
      @RequestParam(required = false) String estado,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio,
      Authentication authentication) {
    UUID usuarioId = usuarioId(authentication);
    Page<InspeccionResumenResponse> response =
        inspeccionService.listarMisInspecciones(usuarioId, estado, pagina, tamanio);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping({"/{id}", "/{id}/borrador"})
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Eliminar lógicamente un borrador de inspección",
      description =
          "Elimina lógicamente un borrador propio. Retorna 404 si no existe o pertenece a otro"
              + " usuario. Retorna 409 si la inspección está publicada o no es borrador. Retorna"
              + " 204 en caso exitoso.",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<Void> eliminarBorrador(
      @PathVariable UUID id, Authentication authentication) {
    UUID usuarioId = usuarioId(authentication);
    inspeccionService.eliminarBorrador(id, usuarioId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/puentes/{puenteId}/publicadas")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Consultar historial paginado de inspecciones publicadas de un puente")
  public ResponseEntity<Page<InspeccionPublicadaResumenResponse>> listarPublicadas(
      @PathVariable UUID puenteId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanio) {
    return ResponseEntity.ok(
        inspeccionService.listarPublicadasPorPuente(puenteId, pagina, tamanio));
  }

  @GetMapping("/{id}/publicada")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Consultar detalle publicado con versión original e IC persistido")
  public ResponseEntity<InspeccionPublicadaDetalleResponse> consultarPublicada(
      @PathVariable UUID id) {
    return ResponseEntity.ok(inspeccionService.consultarPublicada(id));
  }

  @GetMapping("/{id}/exportar")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Exportar inspección publicada con esquema original y datos técnicos",
      description =
          "Descarga un archivo JSON estructurado y sanitizado con los datos capturados y el esquema"
              + " de la versión original con que se levantó. Excluye datos de otros usuarios y"
              + " referencias internas.",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ResponseEntity<ExportacionInspeccionResponse> exportar(@PathVariable UUID id) {
    ExportacionInspeccionResponse exportacion = inspeccionService.exportarInspeccion(id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("inspeccion-" + id + ".json")
                .build()
                .toString())
        .body(exportacion);
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<InspeccionResumenResponse> consultar(
      @PathVariable UUID id, Authentication authentication) {
    return ResponseEntity.ok(inspeccionService.consultarPorId(id, usuarioId(authentication)));
  }

  @PutMapping({"/{id}", "/{id}/datos"})
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<InspeccionResumenResponse> guardarRespuestas(
      @PathVariable UUID id,
      @Valid @RequestBody GuardarRespuestasRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        inspeccionService.guardarRespuestas(id, request, usuarioId(authentication)));
  }

  @PostMapping("/{id}/validar")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> validar(
      @PathVariable UUID id,
      @Valid @RequestBody ValidarRespuestasRequest request,
      Authentication authentication) {
    inspeccionService.validarRespuestasInspeccion(
        id, request.datos(), request.esBorrador(), usuarioId(authentication));
    return ResponseEntity.ok(Map.of("valido", true, "mensaje", "Las respuestas son válidas"));
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida para iniciar inspecciones.");
    }
    return jwtData.userId();
  }

  private String rol(Authentication authentication) {
    return authentication != null && authentication.getDetails() instanceof JwtData jwtData
        ? jwtData.role() != null ? jwtData.role().name() : "INSPECTOR"
        : "INSPECTOR";
  }
}
