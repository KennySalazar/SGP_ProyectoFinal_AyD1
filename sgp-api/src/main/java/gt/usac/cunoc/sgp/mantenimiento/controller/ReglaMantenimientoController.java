package gt.usac.cunoc.sgp.mantenimiento.controller;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.mantenimiento.dto.CambiarEstadoReglaRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.CrearReglaMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.EditarReglaMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.ReglaMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.ResumenReglasVersionResponse;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.service.ReglaMantenimientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reglas-mantenimiento")
@Tag(
    name = "Reglas de Mantenimiento",
    description = "Gestión del catálogo provisional y reglas de mantenimiento daño -> intervención")
@SecurityRequirement(name = "bearerAuth")
public class ReglaMantenimientoController {

  private final ReglaMantenimientoService reglaMantenimientoService;
  private final VersionFormularioRepository versionFormularioRepository;

  public ReglaMantenimientoController(
      ReglaMantenimientoService reglaMantenimientoService,
      VersionFormularioRepository versionFormularioRepository) {
    this.reglaMantenimientoService = reglaMantenimientoService;
    this.versionFormularioRepository = versionFormularioRepository;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  @Operation(
      summary = "Consultar catálogo de reglas de mantenimiento",
      description =
          "Lista paginada de reglas con filtros por tipo, elemento, versión del formulario y estado activo.")
  public ResponseEntity<Page<ReglaMantenimientoResponse>> listar(
      @RequestParam(name = "versionId", required = false) UUID versionId,
      @RequestParam(name = "tipo", required = false) TipoOrden tipo,
      @RequestParam(name = "elemento", required = false) String elemento,
      @RequestParam(name = "activa", required = false) Boolean activa,
      Pageable pageable) {
    return ResponseEntity.ok(
        reglaMantenimientoService.listar(versionId, tipo, elemento, activa, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  @Operation(
      summary = "Obtener regla de mantenimiento por ID",
      description = "Recupera una regla de mantenimiento específica con todos sus detalles.")
  public ResponseEntity<ReglaMantenimientoResponse> obtenerPorId(@PathVariable UUID id) {
    return ResponseEntity.ok(reglaMantenimientoService.obtenerPorId(id));
  }

  @GetMapping("/resumen")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  @Operation(
      summary = "Obtener resumen de reglas de una versión",
      description =
          "Devuelve contadores de reglas activas, provisionales y validadas, así como advertencias si no hay reglas activas.")
  public ResponseEntity<ResumenReglasVersionResponse> obtenerResumen(
      @RequestParam(name = "versionId", required = false) UUID versionId) {
    UUID idEfectivo = versionId;
    if (idEfectivo == null) {
      idEfectivo =
          versionFormularioRepository
              .findByActivaTrue()
              .map(VersionFormulario::getId)
              .orElseThrow(
                  () ->
                      new ApiException(
                          HttpStatus.NOT_FOUND,
                          "version_no_encontrada",
                          "Sin versión activa",
                          "No existe una versión de formulario activa en el sistema"));
    }
    return ResponseEntity.ok(reglaMantenimientoService.obtenerResumenVersion(idEfectivo));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Crear nueva regla de mantenimiento",
      description = "Crea una nueva regla para la versión de formulario indicada con revisión 1.")
  public ResponseEntity<ReglaMantenimientoResponse> crear(
      @Valid @RequestBody CrearReglaMantenimientoRequest request, Authentication authentication) {
    ReglaMantenimientoResponse creada =
        reglaMantenimientoService.crearRegla(request, usuarioId(authentication));
    return ResponseEntity.status(HttpStatus.CREATED).body(creada);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Editar regla existente (crea nueva revisión)",
      description =
          "Crea una nueva revisión con el número siguiente, conservando la revisión anterior.")
  public ResponseEntity<ReglaMantenimientoResponse> editar(
      @PathVariable UUID id,
      @Valid @RequestBody EditarReglaMantenimientoRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        reglaMantenimientoService.editarRegla(id, request, usuarioId(authentication)));
  }

  @PatchMapping("/{id}/estado")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Activar o desactivar regla",
      description =
          "Cambia el estado de activación de una regla. Al activar, desactiva otras revisiones del mismo código.")
  public ResponseEntity<ReglaMantenimientoResponse> cambiarEstado(
      @PathVariable UUID id, @Valid @RequestBody CambiarEstadoReglaRequest request) {
    return ResponseEntity.ok(reglaMantenimientoService.cambiarEstadoActiva(id, request.activa()));
  }

  @PostMapping("/{id}/validar")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Validar regla de mantenimiento",
      description =
          "Marca una regla como validada por el Administrador, registrando quién y cuándo.")
  public ResponseEntity<ReglaMantenimientoResponse> validar(
      @PathVariable UUID id, Authentication authentication) {
    return ResponseEntity.ok(reglaMantenimientoService.validarRegla(id, usuarioId(authentication)));
  }

  @PostMapping("/sembrar")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(
      summary = "Sembrar catálogo provisional de reglas",
      description =
          "Carga las 18 reglas iniciales derivadas de la versión de formulario activa o indicada.")
  public ResponseEntity<Map<String, Object>> sembrar(
      @RequestParam(name = "versionId", required = false) UUID versionId,
      Authentication authentication) {
    UUID idVersion = versionId;
    if (idVersion == null) {
      idVersion =
          versionFormularioRepository
              .findByActivaTrue()
              .map(VersionFormulario::getId)
              .orElseThrow(
                  () ->
                      new ApiException(
                          HttpStatus.NOT_FOUND,
                          "version_no_encontrada",
                          "Sin versión activa",
                          "No existe una versión de formulario activa para sembrar el catálogo"));
    }
    int sembradas =
        reglaMantenimientoService.sembrarCatalogoProvisionalSiNoExiste(
            idVersion, usuarioId(authentication));
    return ResponseEntity.ok(
        Map.of(
            "mensaje", "Catálogo provisional procesado",
            "versionFormularioId", idVersion,
            "reglasSembradas", sembradas));
  }

  private UUID usuarioId(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwtData)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "authentication_required",
          "Autenticación requerida",
          "Se requiere una sesión válida para gestionar reglas de mantenimiento.");
    }
    return jwtData.userId();
  }
}
