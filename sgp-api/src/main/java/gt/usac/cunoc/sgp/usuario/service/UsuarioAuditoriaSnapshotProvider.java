package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.common.util.EmailNormalizer;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Campos de usuario permitidos en auditoria aca  nunca incluye credenciales ni tokens jsjs */
@Component
public class UsuarioAuditoriaSnapshotProvider implements AuditoriaSnapshotProvider {
  private final UserAccountRepository usuarios;

  public UsuarioAuditoriaSnapshotProvider(UserAccountRepository usuarios) {
    this.usuarios = usuarios;
  }

  @Override
  public Class<?> tipo() {
    return UserAccount.class;
  }

  @Override
  public Object cargar(UUID id) {
    return usuarios.findById(id).map(this::proyectar).orElse(null);
  }

  @Override
  public Object cargarPorEmail(String email) {
    return usuarios.findByEmail(EmailNormalizer.normalize(email)).map(this::proyectar).orElse(null);
  }

  private UsuarioSnapshot proyectar(UserAccount usuario) {
    return new UsuarioSnapshot(
        usuario.getId(),
        usuario.getEmail(),
        usuario.getRole().getName().name(),
        usuario.isVerified(),
        usuario.isActivated(),
        usuario.isActive(),
        usuario.isTwoFactorEnabled(),
        usuario.getTokenVersion());
  }

  public record UsuarioSnapshot(
      UUID id,
      String email,
      String rol,
      boolean verificado,
      boolean activado,
      boolean activo,
      boolean dosFactoresHabilitado,
      int versionToken) {}
}
