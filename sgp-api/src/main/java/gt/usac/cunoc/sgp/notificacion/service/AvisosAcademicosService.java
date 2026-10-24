package gt.usac.cunoc.sgp.notificacion.service;

import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.AprobacionCalibracionRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.ReglaMantenimientoRepository;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvisosAcademicosService {
  private final VersionFormularioRepository versiones;
  private final AprobacionCalibracionRepository aprobaciones;
  private final ReglaMantenimientoRepository reglas;
  private final UserAccountRepository usuarios;
  private final NotificacionService notificaciones;

  public AvisosAcademicosService(
      VersionFormularioRepository versiones,
      AprobacionCalibracionRepository aprobaciones,
      ReglaMantenimientoRepository reglas,
      UserAccountRepository usuarios,
      NotificacionService notificaciones) {
    this.versiones = versiones;
    this.aprobaciones = aprobaciones;
    this.reglas = reglas;
    this.usuarios = usuarios;
    this.notificaciones = notificaciones;
  }

  @Transactional
  public void calibracionPendiente(UUID versionId) {
    var version = versiones.findById(versionId).orElse(null);
    if (version == null
        || version.getEstado() != EstadoVersionFormulario.PUBLICADA
        || aprobaciones.existsByVersionFormularioId(versionId)) return;
    avisar(
        versionId,
        "CALIBRACION_PENDIENTE",
        "Calibracion pendiente de aprobacion",
        "Hay una version publicada pendiente de respaldo academico. Consulte el editor de la version.",
        "version_formulario",
        "calibracion-pendiente:");
  }

  @Transactional
  public void reglasPendientes(UUID versionId) {
    if (!reglas.tieneReglasPendientes(versionId)) return;
    avisar(
        versionId,
        "REGLAS_PENDIENTES_VALIDAR",
        "Reglas pendientes de validacion",
        "Hay reglas activas pendientes de validacion academica. Consulte el catalogo de mantenimiento.",
        "catalogo_reglas",
        "reglas-pendientes-validar:");
  }

  @Transactional
  public void revisarPendientes() {
    for (var version : versiones.findPublicadasSinAprobacion())
      calibracionPendiente(version.getId());
    for (var versionId : reglas.findVersionesConReglasPendientes()) reglasPendientes(versionId);
  }

  private void avisar(
      UUID versionId, String tipo, String titulo, String mensaje, String entidad, String prefijo) {
    for (var usuario : usuarios.findIdsActivosPorRol(RoleName.CATEDRATICO)) {
      notificaciones.notificar(
          usuario, tipo, titulo, mensaje, entidad, versionId, prefijo + versionId);
    }
  }
}
