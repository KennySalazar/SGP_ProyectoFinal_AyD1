package gt.usac.cunoc.sgp.formulario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.config.BootstrapProperties;
import gt.usac.cunoc.sgp.common.util.EmailNormalizer;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.mantenimiento.service.ReglaMantenimientoService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SemillaFormularioService {
  private final JdbcTemplate jdbc;
  private final VersionFormularioRepository versiones;
  private final VersionFormularioService formularios;
  private final ReglaMantenimientoService mantenimiento;
  private final UserAccountRepository usuarios;
  private final BootstrapProperties bootstrap;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;

  public SemillaFormularioService(
      JdbcTemplate jdbc,
      VersionFormularioRepository versiones,
      VersionFormularioService formularios,
      ReglaMantenimientoService mantenimiento,
      UserAccountRepository usuarios,
      BootstrapProperties bootstrap,
      AuditoriaWriter auditoria,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.versiones = versiones;
    this.formularios = formularios;
    this.mantenimiento = mantenimiento;
    this.usuarios = usuarios;
    this.bootstrap = bootstrap;
    this.auditoria = auditoria;
    this.json = json;
  }

  @Transactional(
      propagation = Propagation.REQUIRES_NEW,
      isolation = Isolation.READ_COMMITTED,
      timeout = 60)
  public Optional<UUID> sembrarSiNoExisteActiva() {
    jdbc.execute("SET LOCAL lock_timeout = '15s'");
    jdbc.execute("LOCK TABLE version_formulario IN SHARE ROW EXCLUSIVE MODE");
    if (versiones.findByActivaTrue().isPresent()) return Optional.empty();
    var administrador =
        usuarios
            .findByEmail(EmailNormalizer.normalize(bootstrap.getEmail()))
            .filter(
                usuario ->
                    usuario.isActive() && usuario.getRole().getName() == RoleName.ADMINISTRADOR)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No esta disponible el administrador inicial para sembrar el formulario"));
    int numero = 1;
    while (versiones.existsByCodigo("SIECA-v" + numero)) numero++;
    var publicada = formularios.sembrarPlantillaInicial("SIECA-v" + numero, administrador.getId());
    int reglas =
        mantenimiento.sembrarCatalogoProvisionalSiNoExiste(publicada.id(), administrador.getId());
    auditoria.registrarEnTransaccion(
        null,
        AccionAuditoria.CREAR,
        "version_formulario",
        publicada.id(),
        null,
        json.createObjectNode()
            .put("codigo", publicada.codigo())
            .put("activa", true)
            .put("calibracionProvisional", true)
            .put("reglasMantenimiento", reglas),
        "HU-051-siembra-formulario");
    return Optional.of(publicada.id());
  }
}
