package gt.usac.cunoc.sgp.formulario.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.config.BootstrapProperties;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResponse;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.mantenimiento.service.ReglaMantenimientoService;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SemillaFormularioServiceTest {
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final VersionFormularioRepository versiones = mock(VersionFormularioRepository.class);
  final VersionFormularioService formularios = mock(VersionFormularioService.class);
  final ReglaMantenimientoService mantenimiento = mock(ReglaMantenimientoService.class);
  final UserAccountRepository usuarios = mock(UserAccountRepository.class);
  final AuditoriaWriter auditoria = mock(AuditoriaWriter.class);
  final BootstrapProperties bootstrap = new BootstrapProperties();
  final UUID usuario = UUID.randomUUID();
  final UUID id = UUID.randomUUID();
  SemillaFormularioService servicio;

  @BeforeEach
  void preparar() {
    bootstrap.setEmail("admin@ejemplo.com");
    servicio =
        new SemillaFormularioService(
            jdbc,
            versiones,
            formularios,
            mantenimiento,
            usuarios,
            bootstrap,
            auditoria,
            new ObjectMapper());
  }

  void administrador() {
    var cuenta = mock(UserAccount.class);
    var rol = mock(Role.class);
    when(cuenta.getId()).thenReturn(usuario);
    when(cuenta.isActive()).thenReturn(true);
    when(cuenta.getRole()).thenReturn(rol);
    when(rol.getName()).thenReturn(RoleName.ADMINISTRADOR);
    when(usuarios.findByEmail("admin@ejemplo.com")).thenReturn(Optional.of(cuenta));
  }

  @Test
  void noTocaUnaVersionActivaNiConsultaAlAdministrador() {
    when(versiones.findByActivaTrue()).thenReturn(Optional.of(mock(VersionFormulario.class)));
    assertThat(servicio.sembrarSiNoExisteActiva()).isEmpty();
    verifyNoInteractions(formularios, mantenimiento, usuarios, auditoria);
    var orden = inOrder(jdbc, versiones);
    orden.verify(jdbc).execute("SET LOCAL lock_timeout = '15s'");
    orden.verify(jdbc).execute("LOCK TABLE version_formulario IN SHARE ROW EXCLUSIVE MODE");
    orden.verify(versiones).findByActivaTrue();
  }

  @Test
  void usaCodigoLibreSiembraReglasYAuditaComoProcesoAutomatico() {
    administrador();
    when(versiones.existsByCodigo("SIECA-v1")).thenReturn(true);
    when(versiones.existsByCodigo("SIECA-v2")).thenReturn(true);
    var publicada = mock(VersionFormularioResponse.class);
    when(publicada.id()).thenReturn(id);
    when(publicada.codigo()).thenReturn("SIECA-v3");
    when(formularios.sembrarPlantillaInicial("SIECA-v3", usuario)).thenReturn(publicada);
    when(mantenimiento.sembrarCatalogoProvisionalSiNoExiste(id, usuario)).thenReturn(18);
    assertThat(servicio.sembrarSiNoExisteActiva()).contains(id);
    verify(auditoria)
        .registrarEnTransaccion(
            isNull(),
            eq(AccionAuditoria.CREAR),
            eq("version_formulario"),
            eq(id),
            isNull(),
            argThat(
                valor ->
                    valor.path("activa").asBoolean()
                        && valor.path("reglasMantenimiento").asInt() == 18),
            eq("HU-051-siembra-formulario"));
  }

  @Test
  void noSiembraSinAdministradorDisponible() {
    assertThatThrownBy(() -> servicio.sembrarSiNoExisteActiva())
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(formularios, mantenimiento, auditoria);
  }

  @Test
  void propagaFalloParaRevertirLaTransaccion() {
    administrador();
    when(formularios.sembrarPlantillaInicial(anyString(), any()))
        .thenThrow(new IllegalStateException("fallo"));
    assertThatThrownBy(() -> servicio.sembrarSiNoExisteActiva()).hasMessage("fallo");
    verifyNoInteractions(mantenimiento, auditoria);
  }
}
