package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.aspect.Auditable;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.mantenimiento.dto.CrearReglaMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.EditarReglaMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.ReglaMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.ResumenReglasVersionResponse;
import gt.usac.cunoc.sgp.mantenimiento.entity.ReglaMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.mapper.ReglaMantenimientoMapper;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.ReglaMantenimientoRepository;
import gt.usac.cunoc.sgp.notificacion.service.AvisosAcademicosService;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReglaMantenimientoService {

  private static final Logger log = LoggerFactory.getLogger(ReglaMantenimientoService.class);
  private static final int MAX_PAGE_SIZE = 100;

  private final ReglaMantenimientoRepository reglaMantenimientoRepository;
  private final VersionFormularioRepository versionFormularioRepository;
  private final UserAccountRepository userAccountRepository;
  private final ReglaMantenimientoMapper reglaMantenimientoMapper;
  private final ValidadorReglaMantenimiento validadorReglaMantenimiento;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final AvisosAcademicosService avisosAcademicos;

  public ReglaMantenimientoService(
      ReglaMantenimientoRepository reglaMantenimientoRepository,
      VersionFormularioRepository versionFormularioRepository,
      UserAccountRepository userAccountRepository,
      ReglaMantenimientoMapper reglaMantenimientoMapper,
      ValidadorReglaMantenimiento validadorReglaMantenimiento,
      ObjectMapper objectMapper,
      Clock clock,
      AvisosAcademicosService avisosAcademicos) {
    this.reglaMantenimientoRepository = reglaMantenimientoRepository;
    this.versionFormularioRepository = versionFormularioRepository;
    this.userAccountRepository = userAccountRepository;
    this.reglaMantenimientoMapper = reglaMantenimientoMapper;
    this.validadorReglaMantenimiento = validadorReglaMantenimiento;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.avisosAcademicos = avisosAcademicos;
  }

  @Transactional
  public int sembrarCatalogoProvisionalSiNoExiste(UUID versionFormularioId, UUID usuarioId) {
    if (reglaMantenimientoRepository.existsByVersionFormularioId(versionFormularioId)) {
      log.debug(
          "La versión {} ya cuenta con reglas de mantenimiento registradas", versionFormularioId);
      return 0;
    }

    VersionFormulario version =
        versionFormularioRepository
            .findById(versionFormularioId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Versión de formulario no encontrada",
                        "No existe versión de formulario con id: " + versionFormularioId));

    UUID creadorId = usuarioId;
    if (creadorId == null) {
      creadorId =
          userAccountRepository
              .findByRole_NameAndActiveTrueOrderByEmailAsc(RoleName.ADMINISTRADOR)
              .stream()
              .findFirst()
              .map(UserAccount::getId)
              .orElse(version.getCreadoPorId());
    }

    JsonNode reglasArray;
    try (InputStream is =
        new ClassPathResource("mantenimiento/reglas-sieca-v1-semilla.json").getInputStream()) {
      reglasArray = objectMapper.readTree(is);
    } catch (Exception e) {
      log.error("Error al cargar la semilla de reglas de mantenimiento", e);
      throw new IllegalStateException("No fue posible cargar el archivo de semilla de reglas", e);
    }

    Instant ahora = clock.instant();
    List<ReglaMantenimiento> sembradas = new ArrayList<>();

    for (JsonNode nodo : reglasArray) {
      String codigo = nodo.path("codigo").asText();
      String elementoRef = nodo.path("elemento_ref").asText();
      JsonNode condicion = nodo.path("condicion");
      TipoOrden tipo = TipoOrden.valueOf(nodo.path("tipo_mantenimiento").asText());
      String intervencion = nodo.path("intervencion_sugerida").asText();
      JsonNode reglaPrioridad = nodo.path("regla_prioridad");

      // Validar elemento e indicador contra el esquema
      validadorReglaMantenimiento.validar(version.getEsquema(), elementoRef, condicion);

      ReglaMantenimiento regla =
          new ReglaMantenimiento(
              UUID.randomUUID(),
              codigo,
              1,
              version,
              elementoRef,
              condicion,
              tipo,
              intervencion,
              reglaPrioridad,
              true,
              creadorId,
              null,
              null,
              ahora);

      sembradas.add(regla);
    }

    reglaMantenimientoRepository.saveAll(sembradas);
    avisosAcademicos.reglasPendientes(versionFormularioId);
    log.info(
        "Sembradas exitosamente {} reglas de mantenimiento para versión {}",
        sembradas.size(),
        version.getCodigo());
    return sembradas.size();
  }

  @Transactional(readOnly = true)
  public Page<ReglaMantenimientoResponse> listar(
      UUID versionFormularioId,
      TipoOrden tipo,
      String elemento,
      Boolean activa,
      Pageable pageable) {
    Pageable paginaAjustada =
        pageable.getPageSize() > MAX_PAGE_SIZE
            ? PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
            : pageable;

    Specification<ReglaMantenimiento> spec =
        Specification.where(
            (root, query, cb) -> {
              query.distinct(true);
              return cb.conjunction();
            });

    if (versionFormularioId != null) {
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.equal(root.get("versionFormulario").get("id"), versionFormularioId));
    }

    if (tipo != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("tipoMantenimiento"), tipo));
    }

    if (elemento != null && !elemento.isBlank()) {
      String termino = "%" + elemento.trim().toLowerCase() + "%";
      spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("elementoRef")), termino));
    }

    if (activa != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("activa"), activa));
    }

    if (paginaAjustada.getSort().isUnsorted()) {
      paginaAjustada =
          PageRequest.of(
              paginaAjustada.getPageNumber(),
              paginaAjustada.getPageSize(),
              Sort.by(Sort.Order.asc("codigo"), Sort.Order.desc("numeroRevision")));
    }

    return reglaMantenimientoRepository
        .findAll(spec, paginaAjustada)
        .map(reglaMantenimientoMapper::toResponse);
  }

  @Transactional(readOnly = true)
  public ReglaMantenimientoResponse obtenerPorId(UUID id) {
    return reglaMantenimientoRepository
        .findById(id)
        .map(reglaMantenimientoMapper::toResponse)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "regla_no_encontrada",
                    "Regla no encontrada",
                    "No existe regla de mantenimiento con ID: " + id));
  }

  @Transactional
  @Auditable(
      accion = AccionAuditoria.CREAR,
      entidad = "regla_mantenimiento",
      tipo = ReglaMantenimiento.class)
  public ReglaMantenimientoResponse crearRegla(
      CrearReglaMantenimientoRequest request, UUID usuarioId) {
    VersionFormulario version =
        versionFormularioRepository
            .findById(request.versionFormularioId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Versión de formulario no encontrada",
                        "No existe versión con ID: " + request.versionFormularioId()));

    String codigoLimpio = request.codigo().trim();
    if (reglaMantenimientoRepository
        .findMaxNumeroRevision(codigoLimpio, version.getId())
        .isPresent()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "codigo_duplicado",
          "Código de regla ya existe",
          "Ya existe una regla con código '"
              + codigoLimpio
              + "' para esta versión. Utilice la edición para crear una nueva revisión.");
    }

    validadorReglaMantenimiento.validar(
        version.getEsquema(), request.elementoRef().trim(), request.condicion());

    boolean activa = request.activa() == null || request.activa();
    Instant ahora = clock.instant();

    ReglaMantenimiento regla =
        new ReglaMantenimiento(
            UUID.randomUUID(),
            codigoLimpio,
            1,
            version,
            request.elementoRef().trim(),
            request.condicion(),
            request.tipoMantenimiento(),
            request.intervencionSugerida().trim(),
            request.reglaPrioridad(),
            activa,
            usuarioId,
            null,
            null,
            ahora);

    ReglaMantenimiento guardada = reglaMantenimientoRepository.save(regla);
    avisosAcademicos.reglasPendientes(version.getId());
    return reglaMantenimientoMapper.toResponse(guardada);
  }

  @Transactional
  @Auditable(
      accion = AccionAuditoria.MODIFICAR,
      entidad = "regla_mantenimiento",
      tipo = ReglaMantenimiento.class)
  public ReglaMantenimientoResponse editarRegla(
      UUID id, EditarReglaMantenimientoRequest request, UUID usuarioId) {
    ReglaMantenimiento existente =
        reglaMantenimientoRepository
            .findByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "regla_no_encontrada",
                        "Regla no encontrada",
                        "No existe regla con ID: " + id));

    VersionFormulario version = existente.getVersionFormulario();
    validadorReglaMantenimiento.validar(
        version.getEsquema(), request.elementoRef().trim(), request.condicion());

    int siguienteRevision =
        reglaMantenimientoRepository
                .findMaxNumeroRevision(existente.getCodigo(), version.getId())
                .orElse(existente.getNumeroRevision())
            + 1;

    boolean activa = request.activa() == null || request.activa();
    if (activa) {
      reglaMantenimientoRepository.desactivarTodasLasRevisiones(
          existente.getCodigo(), version.getId());
    }

    Instant ahora = clock.instant();
    ReglaMantenimiento nuevaRevision =
        new ReglaMantenimiento(
            UUID.randomUUID(),
            existente.getCodigo(),
            siguienteRevision,
            version,
            request.elementoRef().trim(),
            request.condicion(),
            request.tipoMantenimiento(),
            request.intervencionSugerida().trim(),
            request.reglaPrioridad(),
            activa,
            usuarioId,
            null,
            null,
            ahora);

    ReglaMantenimiento guardada = reglaMantenimientoRepository.save(nuevaRevision);
    avisosAcademicos.reglasPendientes(version.getId());
    return reglaMantenimientoMapper.toResponse(guardada);
  }

  @Transactional
  @Auditable(
      accion = AccionAuditoria.CAMBIAR_ESTADO,
      entidad = "regla_mantenimiento",
      tipo = ReglaMantenimiento.class)
  public ReglaMantenimientoResponse cambiarEstadoActiva(UUID id, boolean activa) {
    ReglaMantenimiento regla =
        reglaMantenimientoRepository
            .findByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "regla_no_encontrada",
                        "Regla no encontrada",
                        "No existe regla con ID: " + id));

    if (activa) {
      reglaMantenimientoRepository.desactivarOtrasRevisiones(
          regla.getCodigo(), regla.getVersionFormulario().getId(), regla.getId());
      regla.setActiva(true);
    } else {
      regla.setActiva(false);
    }

    ReglaMantenimiento guardada = reglaMantenimientoRepository.save(regla);
    if (activa) avisosAcademicos.reglasPendientes(guardada.getVersionFormulario().getId());
    return reglaMantenimientoMapper.toResponse(guardada);
  }

  @Transactional
  @Auditable(
      accion = AccionAuditoria.MODIFICAR,
      entidad = "regla_mantenimiento",
      tipo = ReglaMantenimiento.class,
      idArg = "id",
      recargarPosteriores = true)
  public ReglaMantenimientoResponse validarRegla(UUID id, UUID usuarioId) {
    ReglaMantenimiento regla =
        reglaMantenimientoRepository
            .findByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "regla_no_encontrada",
                        "Regla no encontrada",
                        "No existe regla con ID: " + id));

    if (!regla.isActiva()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "regla_no_vigente",
          "Regla no vigente",
          "Solo se puede validar la revision activa de una regla de mantenimiento.");
    }
    if (!regla.isProvisional()) return reglaMantenimientoMapper.toResponse(regla);
    UserAccount validador =
        userAccountRepository
            .findById(usuarioId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "usuario_no_encontrado",
                        "Usuario no encontrado",
                        "No existe el usuario que valida la regla."));
    regla.validar(usuarioId, clock.instant());
    ReglaMantenimiento guardada = reglaMantenimientoRepository.save(regla);
    String nombre = validador.getNombreCompleto();
    if (nombre == null || nombre.isBlank()) nombre = validador.getEmail();
    return reglaMantenimientoMapper.toResponse(guardada).conNombreValidador(nombre.trim());
  }

  @Transactional(readOnly = true)
  public ResumenReglasVersionResponse obtenerResumenVersion(UUID versionFormularioId) {
    VersionFormulario version =
        versionFormularioRepository.findById(versionFormularioId).orElse(null);
    String codigoVersion = version != null ? version.getCodigo() : "";

    long total = reglaMantenimientoRepository.countByVersionFormularioId(versionFormularioId);
    long activas =
        reglaMantenimientoRepository.countByVersionFormularioIdAndActivaTrue(versionFormularioId);
    long provisionales =
        reglaMantenimientoRepository.countByVersionFormularioIdAndValidadoPorIdIsNull(
            versionFormularioId);
    long validadas = total - provisionales;

    String advertencia =
        activas == 0 ? "Esta versión no tiene reglas de mantenimiento activas" : null;

    return new ResumenReglasVersionResponse(
        versionFormularioId,
        codigoVersion,
        activas > 0,
        total,
        activas,
        provisionales,
        validadas,
        advertencia);
  }
}
