
package gt.usac.cunoc.sgp.usuario.entity;

import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class UserAccount {

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "verificado", nullable = false)
    private boolean verified;

    @Column(name = "activado", nullable = false)
    private boolean activated;

    @Column(name = "activo", nullable = false)
    private boolean active;

    @Column(name = "two_factor_habilitado", nullable = false)
    private boolean twoFactorEnabled;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "intentos_login_fallidos", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "ventana_intentos_iniciada_en")
    private Instant failedLoginWindowStartedAt;

    @Column(name = "bloqueado_hasta")
    private Instant lockedUntil;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private Role role;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "actualizado_en", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UserAccount() {}

    public UserAccount(String email, String passwordHash, Role role, boolean verified, boolean activated) {
        this.id = UuidV7Generator.generate();
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.verified = verified;
        this.activated = activated;
        this.active = true;
        this.twoFactorEnabled = false;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UuidV7Generator.generate();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isVerified() { return verified; }
    public boolean isActivated() { return activated; }
    public boolean isActive() { return active; }
    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public int getTokenVersion() { return tokenVersion; }
    public Role getRole() { return role; }
    public Instant getLockedUntil() { return lockedUntil; }

    public void verify() { verified = true; }
    public void activate() { activated = true; active = true; }
    public void deactivate() { active = false; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setTwoFactorEnabled(boolean value) { twoFactorEnabled = value; }
    public void incrementTokenVersion() { tokenVersion++; }
    public void setRole(Role role) { this.role = role; }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedLogin(Instant now) {
        if (failedLoginWindowStartedAt == null || failedLoginWindowStartedAt.plus(LOGIN_WINDOW).isBefore(now)) {
            failedLoginWindowStartedAt = now;
            failedLoginAttempts = 1;
        } else {
            failedLoginAttempts++;
        }
        if (failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    public void clearFailedLogins() {
        failedLoginAttempts = 0;
        failedLoginWindowStartedAt = null;
        lockedUntil = null;
    }
}
