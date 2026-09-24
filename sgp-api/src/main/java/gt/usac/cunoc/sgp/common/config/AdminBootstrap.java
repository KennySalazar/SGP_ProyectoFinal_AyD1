
package gt.usac.cunoc.sgp.common.config;

import gt.usac.cunoc.sgp.common.util.EmailNormalizer;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.RoleName;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.RoleRepository;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import gt.usac.cunoc.sgp.usuario.service.PasswordPolicy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap {

    private final BootstrapProperties properties;
    private final RoleRepository roles;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(BootstrapProperties properties, RoleRepository roles, UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.roles = roles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void provisionAdmin() {
        if (!PasswordPolicy.isValid(properties.getInitialPassword())) throw new IllegalStateException("INITIAL_ADMIN_PASSWORD no cumple la politica de contraseña");
        String email = EmailNormalizer.normalize(properties.getEmail());
        Role adminRole = roles.findByName(RoleName.ADMINISTRADOR).orElseThrow(() -> new IllegalStateException("Falta el rol ADMINISTRADOR"));
        UserAccount admin = users.findByEmail(email).orElse(null);
        if (admin == null) {
            users.save(new UserAccount(email, passwordEncoder.encode(properties.getInitialPassword()), adminRole, true, true));
            return;
        }
        admin.setRole(adminRole);
        admin.verify();
        admin.activate();
    }
}
