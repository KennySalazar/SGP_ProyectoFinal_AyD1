package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.util.EmailNormalizer;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.RoleRepository;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminProvisioningService {

  private final RoleRepository roles;
  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;

  public AdminProvisioningService(
      RoleRepository roles, UserAccountRepository users, PasswordEncoder passwordEncoder) {
    this.roles = roles;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public void provisionAdmin(String rawEmail, String initialPassword) {
    if (!PasswordPolicy.isValid(initialPassword))
      throw new IllegalStateException("INITIAL_ADMIN_PASSWORD no cumple la politica de contraseña");
    String email = EmailNormalizer.normalize(rawEmail);
    Role adminRole =
        roles
            .findByName(RoleName.ADMINISTRADOR)
            .orElseThrow(() -> new IllegalStateException("Falta el rol ADMINISTRADOR"));
    UserAccount admin = users.findByEmail(email).orElse(null);
    if (admin == null) {
      users.save(
          new UserAccount(email, passwordEncoder.encode(initialPassword), adminRole, true, true));
      return;
    }
    admin.setRole(adminRole);
    admin.verify();
    admin.activate();
  }
}
