
package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.OtpRateLimitException;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.util.EmailNormalizer;
import gt.usac.cunoc.sgp.usuario.dto.AccessTokenResponse;
import gt.usac.cunoc.sgp.usuario.dto.ChallengeResponse;
import gt.usac.cunoc.sgp.usuario.dto.ChangePasswordRequest;
import gt.usac.cunoc.sgp.usuario.dto.LoginRequest;
import gt.usac.cunoc.sgp.usuario.dto.LoginResponse;
import gt.usac.cunoc.sgp.usuario.dto.MessageResponse;
import gt.usac.cunoc.sgp.usuario.dto.PasswordChangeResponse;
import gt.usac.cunoc.sgp.usuario.dto.RecoveryRequest;
import gt.usac.cunoc.sgp.usuario.dto.RegisterRequest;
import gt.usac.cunoc.sgp.usuario.dto.UserResponse;
import gt.usac.cunoc.sgp.usuario.entity.OtpPurpose;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.RoleName;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.RoleRepository;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    public AuthService(UserAccountRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            OtpService otpService, JwtService jwtService, RefreshTokenService refreshTokens, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional
    public ChallengeResponse register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (users.findByEmail(email).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "email_already_registered", "Correo ya registrado",
                    "El correo electronico ya esta registrado");
        Role role = roles.findByName(RoleName.ESTUDIANTE)
                .orElseThrow(() -> new IllegalStateException("Falta el rol ESTUDIANTE"));
        UserAccount user = users
                .save(new UserAccount(email, passwordEncoder.encode(request.password()), role, false, false));
        var challenge = otpService.issue(user, OtpPurpose.REGISTRO);
        return new ChallengeResponse(challenge.getId(), challenge.getExpiresAt(),
                "Se envio un codigo de verificacion al correo indicado");
    }

    @Transactional
    public ChallengeResponse resendRegistration(String rawEmail) {
        UserAccount user = requireUser(rawEmail);
        if (user.isVerified())
            throw new ApiException(HttpStatus.CONFLICT, "account_already_verified", "Cuenta ya verificada",
                    "La cuenta ya fue verificada");
        var challenge = otpService.issue(user, OtpPurpose.REGISTRO);
        return new ChallengeResponse(challenge.getId(), challenge.getExpiresAt(),
                "Se envio un nuevo codigo de verificacion");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse verifyRegistration(String rawEmail, UUID challengeId, String code) {
        UserAccount user = requireUser(rawEmail);
        if (user.isVerified())
            throw new ApiException(HttpStatus.CONFLICT, "account_already_verified", "Cuenta ya verificada",
                    "La cuenta ya fue verificada");
        otpService.verify(challengeId, user, OtpPurpose.REGISTRO, code);
        user.verify();
        return new MessageResponse("Correo verificado. La cuenta queda pendiente de activacion por un Catedratico.");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginFlowResult login(LoginRequest request) {
        UserAccount user = users.findByEmail(EmailNormalizer.normalize(request.email())).orElse(null);
        Instant now = clock.instant();
        if (user == null)
            throw invalidCredentials();
        if (user.isLocked(now))
            throw new ApiException(HttpStatus.LOCKED, "account_temporarily_locked", "Cuenta bloqueada temporalmente",
                    "Espere 15 minutos antes de volver a intentar");
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(now);
            users.save(user);
            throw invalidCredentials();
        }
        user.clearFailedLogins();
        users.save(user);
        ensureLoginEligible(user);
        if (user.isTwoFactorEnabled()) {
            var challenge = otpService.issue(user, OtpPurpose.LOGIN_2FA);
            return new LoginFlowResult(LoginResponse.challenge(challenge.getId()), null);
        }
        return issueLogin(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginFlowResult verifyLogin(UUID challengeId, String code) {
        UserAccount user = otpService.verify(challengeId, OtpPurpose.LOGIN_2FA, code);
        ensureLoginEligible(user);
        return issueLogin(user);
    }

    @Transactional
    public RefreshFlowResult refresh(String rawRefreshToken) {
        var rotated = refreshTokens.rotate(rawRefreshToken);
        String access = jwtService.issueAccessToken(rotated.user());
        return new RefreshFlowResult(new AccessTokenResponse(access, "Bearer", jwtService.accessExpirationMs()),
                rotated.rawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    @Transactional
    public MessageResponse requestRecovery(RecoveryRequest request) {
        UserAccount user = users.findByEmail(EmailNormalizer.normalize(request.email())).orElse(null);
        if (user != null && user.isActive() && user.isVerified()) {
            try {
                otpService.issue(user, OtpPurpose.RECUPERACION_PASSWORD);
            } catch (OtpRateLimitException ignored) {
            }
        }
        return new MessageResponse("Si el correo esta registrado, recibira un codigo de recuperacion");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse verifyRecovery(String email, String code, String newPassword) {
        UserAccount user = users.findByEmail(EmailNormalizer.normalize(email)).orElse(null);
        if (user == null || !user.isActive() || !user.isVerified())
            throw invalidOtp();
        otpService.verifyLatest(user, OtpPurpose.RECUPERACION_PASSWORD, code);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.incrementTokenVersion();
        refreshTokens.revokeAll(user);
        return new MessageResponse("La contraseña fue restablecida correctamente");
    }

    @Transactional
    public PasswordChangeFlowResult changePassword(
            String rawEmail,
            ChangePasswordRequest request) {

        UserAccount user = requireUser(rawEmail);

        ensureLoginEligible(user);

        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPasswordHash())) {
            throw invalidCredentials(
                    "La contraseña actual no es correcta");
        }

        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_password_transition",
                    "Contraseña invalida",
                    "La nueva contraseña debe ser diferente de la actual");
        }

        user.setPasswordHash(
                passwordEncoder.encode(request.newPassword()));

        user.incrementTokenVersion();

        refreshTokens.revokeAll(user);

        String access = jwtService.issueAccessToken(user);
        String refresh = refreshTokens.issue(user);

        PasswordChangeResponse body = new PasswordChangeResponse(
                access,
                "Bearer",
                jwtService.accessExpirationMs(),
                "La contraseña fue cambiada correctamente");

        return new PasswordChangeFlowResult(body, refresh);
    }

    @Transactional
    public ChallengeResponse requestTwoFactorChange(
            String rawEmail,
            String currentPassword,
            OtpPurpose purpose) {

        UserAccount user = requireUser(rawEmail);

        ensureLoginEligible(user);

        if (!passwordEncoder.matches(
                currentPassword,
                user.getPasswordHash())) {
            throw invalidCredentials(
                    "La contraseña actual no es correcta");
        }

        boolean enabling = purpose == OtpPurpose.ACTIVAR_2FA;

        if (user.isTwoFactorEnabled() == enabling) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "two_factor_state_unchanged",
                    "Estado sin cambios",
                    "La autenticacion de dos factores ya tiene ese estado");
        }

        var challenge = otpService.issue(user, purpose);

        return new ChallengeResponse(
                challenge.getId(),
                challenge.getExpiresAt(),
                "Se envio un codigo OTP para confirmar el cambio");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse confirmTwoFactorChange(
            String rawEmail,
            UUID challengeId,
            String code,
            OtpPurpose purpose) {

        UserAccount user = requireUser(rawEmail);

        ensureLoginEligible(user);

        otpService.verify(
                challengeId,
                user,
                purpose,
                code);

        user.setTwoFactorEnabled(
                purpose == OtpPurpose.ACTIVAR_2FA);

        return new MessageResponse(
                purpose == OtpPurpose.ACTIVAR_2FA
                        ? "La autenticacion de dos factores fue activada"
                        : "La autenticacion de dos factores fue desactivada");
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String email) {
        return toResponse(requireUser(email));
    }

    @Transactional(readOnly = true)
    public UserAccount requireUser(String rawEmail) {
        return users.findByEmail(EmailNormalizer.normalize(rawEmail))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "account_not_found", "Cuenta no encontrada",
                        "La cuenta no existe"));
    }

    public UserResponse toResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().getName().name(), user.isVerified(),
                user.isActivated(), user.isActive(), user.isTwoFactorEnabled());
    }

    private LoginFlowResult issueLogin(UserAccount user) {
        String access = jwtService.issueAccessToken(user);
        String refresh = refreshTokens.issue(user);
        return new LoginFlowResult(LoginResponse.token(access, jwtService.accessExpirationMs()), refresh);
    }

    private void ensureLoginEligible(UserAccount user) {
        if (!user.isActive())
            throw new ApiException(HttpStatus.FORBIDDEN, "account_disabled", "Cuenta desactivada",
                    "La cuenta esta desactivada");
        if (!user.isVerified())
            throw new ApiException(HttpStatus.FORBIDDEN, "account_not_verified", "Cuenta no verificada",
                    "La cuenta no ha verificado su correo");
        if (!user.isActivated())
            throw new ApiException(HttpStatus.FORBIDDEN, "account_pending_activation", "Cuenta pendiente de activacion",
                    "Un Catedratico debe activar y vincular la cuenta a un curso");
    }

    private ApiException invalidCredentials() {
        return invalidCredentials("Credenciales invalidas");
    }

    private ApiException invalidCredentials(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Credenciales invalidas", detail);
    }

    private ApiException invalidOtp() {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_otp", "Codigo OTP invalido",
                "Codigo OTP invalido o expirado");
    }

    public record LoginFlowResult(LoginResponse response, String refreshToken) {
    }

    public record RefreshFlowResult(AccessTokenResponse response, String refreshToken) {
    }

    public record PasswordChangeFlowResult(PasswordChangeResponse response, String refreshToken) {
    }
}
