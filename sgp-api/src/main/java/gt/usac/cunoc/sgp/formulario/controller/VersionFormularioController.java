package gt.usac.cunoc.sgp.formulario.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.dto.ActualizarBorradorRequest;
import gt.usac.cunoc.sgp.formulario.dto.AnalisisMapeoResponse;
import gt.usac.cunoc.sgp.formulario.dto.ConfiguracionIcElementoRequest;
import gt.usac.cunoc.sgp.formulario.dto.ConfiguracionIcElementoResponse;
import gt.usac.cunoc.sgp.formulario.dto.CrearVersionRequest;
import gt.usac.cunoc.sgp.formulario.dto.DeclaracionMapeoRequest;
import gt.usac.cunoc.sgp.formulario.dto.FormularioActivoResponse;
import gt.usac.cunoc.sgp.formulario.dto.MapeoCampoResponse;
import gt.usac.cunoc.sgp.formulario.dto.PublicarVersionRequest;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResponse;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResumenResponse;
import gt.usac.cunoc.sgp.formulario.service.VersionFormularioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/formularios")
@SecurityRequirement(name = "bearerAuth")
@Tag(
    name = "Formularios",
    description = "Gestión de versiones del formulario SIECA y cálculo de IC")
public class VersionFormularioController {

  private final VersionFormularioService service;

  public VersionFormularioController(VersionFormularioService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Listar todas las versiones de formulario",
      description = "Lista resumida de versiones creadas en el sistema ordenadas cronológicamente.")
  public ResponseEntity<List<VersionFormularioResumenResponse>> listarVersiones() {
    return ResponseEntity.ok(service.listarVersiones());
  }

  @GetMapping("/activo")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Consultar la versión de formulario activa",
      description =
          "Retorna el esquema, vínculos de almacenamiento y versión del formulario activo para captura de inspecciones.")
  public ResponseEntity<FormularioActivoResponse> obtenerActiva() {
    return ResponseEntity.ok(service.obtenerActiva());
  }

  @GetMapping("/{id}/esquema")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Consultar el esquema de una versión publicada",
      description =
          "Retorna el esquema de una versión publicada, activa o anterior, para mostrar una "
              + "inspección con la estructura de su versión original. Los borradores responden 404.")
  public ResponseEntity<FormularioActivoResponse> obtenerEsquemaPublicado(@PathVariable UUID id) {
    return ResponseEntity.ok(service.obtenerPublicadaPorId(id));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Consultar el detalle de una versión de formulario",
      description = "Retorna el esquema completo, pesos configurados y mapeos declarados.")
  public ResponseEntity<VersionFormularioResponse> obtenerPorId(@PathVariable UUID id) {
    return ResponseEntity.ok(service.obtenerPorId(id));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Crear una nueva versión de formulario",
      description =
          "Crea una versión en estado BORRADOR a partir de una plantilla, versión base o esquema inicial.")
  public ResponseEntity<VersionFormularioResponse> crear(
      @Valid @RequestBody CrearVersionRequest request, Authentication authentication) {
    VersionFormularioResponse response = service.crear(request, usuarioId(authentication));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/plantilla-sieca")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Cargar versión inicial desde plantilla SIECA",
      description =
          "Carga la plantilla sieca-v1 con sus ocho secciones, reporte fotográfico, recomendaciones y pesos.")
  public ResponseEntity<VersionFormularioResponse> cargarPlantillaSieca(
      @RequestParam(required = false, defaultValue = "SIECA-v1") String codigo,
      Authentication authentication) {
    VersionFormularioResponse response =
        service.cargarPlantillaSiecaInicial(codigo, usuarioId(authentication));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Actualizar borrador de una versión de formulario",
      description =
          "Permite modificar el esquema, vínculos de almacenamiento y reglas de evaluación mientras siga en estado BORRADOR.")
  public ResponseEntity<VersionFormularioResponse> actualizarBorrador(
      @PathVariable UUID id,
      @Valid @RequestBody ActualizarBorradorRequest request,
      Authentication authentication) {
    VersionFormularioResponse response =
        service.actualizarBorrador(
            id,
            request.esquema(),
            request.vinculosAlmacenamiento(),
            request.reglasEvaluacionIc(),
            request.codigo(),
            usuarioId(authentication));
    return ResponseEntity.ok(response);
  }

  @PutMapping("/{id}/pesos")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Calibrar pesos de los elementos evaluables",
      description = "Reemplaza los pesos configurados para los elementos de cálculo de IC.")
  public ResponseEntity<List<ConfiguracionIcElementoResponse>> calibrarPesos(
      @PathVariable UUID id,
      @Valid @RequestBody List<ConfiguracionIcElementoRequest> request,
      Authentication authentication) {
    List<ConfiguracionIcElementoResponse> response =
        service.calibrarPesos(id, request, usuarioId(authentication));
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}/mapeos/analisis")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Analizar diferencias y mapeos con la versión anterior",
      description =
          "Compara los campos del esquema respecto a la versión anterior e identifica campos pendientes de declarar.")
  public ResponseEntity<AnalisisMapeoResponse> analizarMapeo(@PathVariable UUID id) {
    return ResponseEntity.ok(service.analizarMapeo(id));
  }

  @PutMapping("/{id}/mapeos")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Declarar o actualizar mapeos con la versión anterior",
      description = "Declara las equivalencias, cambios de nombre, adiciones o eliminaciones.")
  public ResponseEntity<List<MapeoCampoResponse>> guardarMapeos(
      @PathVariable UUID id,
      @Valid @RequestBody List<DeclaracionMapeoRequest> request,
      Authentication authentication) {
    List<MapeoCampoResponse> response =
        service.guardarMapeos(id, request, usuarioId(authentication));
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/publicar")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Publicar y activar una versión de formulario",
      description =
          "Valida esquema, mapeos obligatorios y pesos completos. Desactiva la versión activa previa y publica la actual.")
  public ResponseEntity<VersionFormularioResponse> publicar(
      @PathVariable UUID id,
      @RequestBody(required = false) PublicarVersionRequest request,
      Authentication authentication) {
    VersionFormularioResponse response = service.publicar(id, request, usuarioId(authentication));
    return ResponseEntity.ok(response);
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para gestionar versiones del formulario.");
    }
    return jwtData.userId();
  }

  @PutMapping("/{id}/escala")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Configurar escala de severidad del IC",
      description = "Configura los valores de severidad de una version en BORRADOR.")
  public ResponseEntity<VersionFormularioResponse> configurarEscala(
      @PathVariable UUID id,
      @Valid @RequestBody gt.usac.cunoc.sgp.formulario.dto.ConfigurarEscalaIcRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(service.configurarEscala(id, request, usuarioId(authentication)));
  }
}
