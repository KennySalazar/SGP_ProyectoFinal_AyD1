package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.audit.aspect.Auditable;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambios de estado de las cuentas creadas por invitación. Se separa de {@link InvitacionService}
 * para que cada cambio pase por el proxy y quede auditado con sus valores anterior y posterior.
 */
@Service
public class CuentaInvitadaService {

  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final SecureRandom random = new SecureRandom();

  public CuentaInvitadaService(UserAccountRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Crea la cuenta pendiente de invitación: sin verificar ni activar y con una contraseña aleatoria
   * que nadie conoce, por lo que no puede iniciar sesión hasta aceptar la invitación.
   */
  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.CREAR,
      entidad = "usuario",
      tipo = UserAccount.class,
      emailArgIndex = 0)
  public UserAccount crearPendiente(String email, Role rol) {
    return users.saveAndFlush(
        new UserAccount(email, passwordEncoder.encode(secretoAleatorio()), rol, false, false));
  }

  @Transactional
  @Auditable(
      accion = AccionAuditoria.CAMBIAR_ESTADO,
      entidad = "usuario",
      tipo = UserAccount.class,
      idArg = "usuarioId",
      actorEsEntidad = true)
  public void activar(UUID usuarioId, String password) {
    UserAccount user =
        users
            .findWithRoleById(usuarioId)
            .orElseThrow(() -> new IllegalStateException("La invitacion no tiene cuenta asociada"));
    user.setPasswordHash(passwordEncoder.encode(password));
    user.clearFailedLogins();
    user.verify();
    user.activate();
  }

  private String secretoAleatorio() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
