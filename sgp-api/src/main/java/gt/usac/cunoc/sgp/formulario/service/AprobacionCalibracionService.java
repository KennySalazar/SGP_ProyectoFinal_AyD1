package gt.usac.cunoc.sgp.formulario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.formulario.dto.*;
import gt.usac.cunoc.sgp.formulario.entity.AprobacionCalibracion;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.*;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AprobacionCalibracionService {
  private final VersionFormularioRepository versiones;
  private final AprobacionCalibracionRepository aprobaciones;
  private final UserAccountRepository usuarios;
  private final AuditoriaWriter auditoria;
  private final NotificacionService notificaciones;
  private final ObjectMapper json;
  private final Clock clock;

  public AprobacionCalibracionService(
      VersionFormularioRepository versiones,
      AprobacionCalibracionRepository aprobaciones,
      UserAccountRepository usuarios,
      AuditoriaWriter auditoria,
      NotificacionService notificaciones,
      ObjectMapper json,
      Clock clock) {
    this.versiones = versiones;
    this.aprobaciones = aprobaciones;
    this.usuarios = usuarios;
    this.auditoria = auditoria;
    this.notificaciones = notificaciones;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  public AprobacionCalibracionResponse aprobar(
      UUID versionId, UUID usuarioId, String observaciones) {
    VersionFormulario version =
        versiones.findByIdForUpdate(versionId).orElseThrow(this::noEncontrada);
    if (version.getEstado() != EstadoVersionFormulario.PUBLICADA) {
      throw error(
          HttpStatus.CONFLICT,
          "calibracion_version_no_publicada",
          "La aprobacion se da sobre una version publicada, no sobre un borrador.");
    }
    if (aprobaciones.existsByVersionFormularioId(versionId)) {
      throw error(
          HttpStatus.CONFLICT,
          "calibracion_ya_aprobada",
          "La calibracion ya tiene una aprobacion. La aprobacion original se conserva.");
    }
    if (observaciones != null && observaciones.length() > 2000) {
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "observaciones_demasiado_largas",
          "Las observaciones no deben superar 2000 caracteres.");
    }
    var responsable =
        usuarios
            .findById(usuarioId)
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.FORBIDDEN,
                        "aprobador_no_disponible",
                        "El usuario aprobador no esta disponible."));
    if (!responsable.isActive() || responsable.getRole().getName() != RoleName.CATEDRATICO) {
      throw error(
          HttpStatus.FORBIDDEN,
          "aprobador_no_autorizado",
          "Solo un Catedratico activo puede aprobar.");
    }
    String nombre = responsable.getNombreCompleto();
    nombre = nombre == null || nombre.isBlank() ? responsable.getEmail() : nombre.strip();
    String notas = observaciones == null || observaciones.isBlank() ? null : observaciones.strip();
    var aprobacion =
        aprobaciones.saveAndFlush(
            new AprobacionCalibracion(
                UuidV7Generator.generate(), version, usuarioId, nombre, clock.instant(), notas));
    var respuesta = AprobacionCalibracionResponse.de(aprobacion);
    auditoria.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.CREAR,
        "aprobacion_calibracion_ic",
        aprobacion.getId(),
        null,
        json.valueToTree(respuesta),
        "HU-054: aprobacion academica de calibracion IC");
    for (var admin : usuarios.findByRole_NameAndActiveTrueOrderByEmailAsc(RoleName.ADMINISTRADOR)) {
      notificaciones.notificar(
          admin.getId(),
          "CALIBRACION_IC_APROBADA",
          "Calibracion del IC aprobada",
          "Se registro una aprobacion academica del IC. Consulte el detalle de la version.",
          "version_formulario",
          versionId,
          "calibracion-ic-aprobada:" + versionId);
    }
    return respuesta;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  public AprobacionCalibracionResponse consultar(UUID versionId) {
    versiones.findById(versionId).orElseThrow(this::noEncontrada);
    return aprobaciones
        .findByVersionFormularioId(versionId)
        .map(AprobacionCalibracionResponse::de)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public EstadoCalibracionResponse estado(UUID versionId) {
    var version = versiones.findById(versionId).orElseThrow(this::noEncontrada);
    if (version.getEstado() != EstadoVersionFormulario.PUBLICADA) {
      throw error(
          HttpStatus.CONFLICT, "calibracion_version_no_publicada", "La version no esta publicada.");
    }
    var aprobacion =
        aprobaciones
            .findByVersionFormularioId(versionId)
            .map(AprobacionCalibracionResponse::de)
            .orElse(null);
    return EstadoCalibracionResponse.de(versionId, aprobacion);
  }

  private ApiException noEncontrada() {
    return error(
        HttpStatus.NOT_FOUND,
        "version_no_encontrada",
        "No existe la version de formulario solicitada.");
  }

  private ApiException error(HttpStatus estado, String codigo, String detalle) {
    return new ApiException(estado, codigo, "Aprobacion de calibracion", detalle);
  }
}
