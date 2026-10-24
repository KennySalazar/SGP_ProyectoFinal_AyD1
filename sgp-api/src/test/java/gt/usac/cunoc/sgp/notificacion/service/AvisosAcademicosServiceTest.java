package gt.usac.cunoc.sgp.notificacion.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.AprobacionCalibracionRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.ReglaMantenimientoRepository;
import gt.usac.cunoc.sgp.notificacion.dto.NotificacionResponse;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.util.*;
import org.junit.jupiter.api.Test;

class AvisosAcademicosServiceTest {
  final VersionFormularioRepository versiones = mock(VersionFormularioRepository.class);
  final AprobacionCalibracionRepository aprobaciones = mock(AprobacionCalibracionRepository.class);
  final ReglaMantenimientoRepository reglas = mock(ReglaMantenimientoRepository.class);
  final UserAccountRepository usuarios = mock(UserAccountRepository.class);
  final NotificacionService notificaciones = mock(NotificacionService.class);
  final AvisosAcademicosService servicio =
      new AvisosAcademicosService(versiones, aprobaciones, reglas, usuarios, notificaciones);
  final UUID versionId = UUID.randomUUID();
  final UUID docente = UUID.randomUUID();

  @Test
  void versionPublicadaAvisaSoloCatedraticosConClavePorVersion() {
    var version = mock(VersionFormulario.class);
    when(version.getEstado()).thenReturn(EstadoVersionFormulario.PUBLICADA);
    when(versiones.findById(versionId)).thenReturn(Optional.of(version));
    when(usuarios.findIdsActivosPorRol(RoleName.CATEDRATICO)).thenReturn(List.of(docente));
    servicio.calibracionPendiente(versionId);
    verify(notificaciones)
        .notificar(
            eq(docente),
            eq("CALIBRACION_PENDIENTE"),
            anyString(),
            anyString(),
            eq("version_formulario"),
            eq(versionId),
            eq("calibracion-pendiente:" + versionId));
    verify(usuarios, never()).findIdsActivosPorRol(RoleName.ADMINISTRADOR);
  }

  @Test
  void borradorNoNotifica() {
    var version = mock(VersionFormulario.class);
    when(version.getEstado()).thenReturn(EstadoVersionFormulario.BORRADOR);
    when(versiones.findById(versionId)).thenReturn(Optional.of(version));
    servicio.calibracionPendiente(versionId);
    verifyNoInteractions(notificaciones);
  }

  @Test
  void aprobadaNoProducePendiente() {
    var version = mock(VersionFormulario.class);
    when(version.getEstado()).thenReturn(EstadoVersionFormulario.PUBLICADA);
    when(versiones.findById(versionId)).thenReturn(Optional.of(version));
    when(aprobaciones.existsByVersionFormularioId(versionId)).thenReturn(true);
    servicio.calibracionPendiente(versionId);
    verifyNoInteractions(notificaciones);
  }

  @Test
  void reglasPendientesSeAgrupanPorVersion() {
    when(reglas.tieneReglasPendientes(versionId)).thenReturn(true);
    when(usuarios.findIdsActivosPorRol(RoleName.CATEDRATICO)).thenReturn(List.of(docente));
    servicio.reglasPendientes(versionId);
    verify(notificaciones)
        .notificar(
            eq(docente),
            eq("REGLAS_PENDIENTES_VALIDAR"),
            anyString(),
            anyString(),
            eq("catalogo_reglas"),
            eq(versionId),
            eq("reglas-pendientes-validar:" + versionId));
  }

  @Test
  void sinReglasPendientesNoNotifica() {
    servicio.reglasPendientes(versionId);
    verifyNoInteractions(notificaciones, usuarios);
  }

  @Test
  void rutasApuntanALaVersionExactaSinIncluirDatosDeCalibracion() {
    var aviso =
        new NotificacionResponse(
            UUID.randomUUID(),
            "CALIBRACION_PENDIENTE",
            "Titulo",
            "Mensaje",
            "version_formulario",
            versionId,
            "clave",
            null,
            null,
            false);
    assertThat(aviso.rutaDestino()).isEqualTo("/admin/formularios/" + versionId);
    var catalogo =
        new NotificacionResponse(
            UUID.randomUUID(),
            "REGLAS_PENDIENTES_VALIDAR",
            "Titulo",
            "Mensaje",
            "catalogo_reglas",
            versionId,
            "clave",
            null,
            null,
            false);
    assertThat(catalogo.rutaDestino()).isEqualTo("/mantenimiento/reglas?versionId=" + versionId);
  }
}
