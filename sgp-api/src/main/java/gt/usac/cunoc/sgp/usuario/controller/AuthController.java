
package gt.usac.cunoc.sgp.usuario.controller;

import gt.usac.cunoc.sgp.common.config.JwtProperties;
import gt.usac.cunoc.sgp.common.config.RefreshCookieProperties;
import gt.usac.cunoc.sgp.usuario.dto.AccessTokenResponse;
import gt.usac.cunoc.sgp.usuario.dto.ChallengeResponse;
import gt.usac.cunoc.sgp.usuario.dto.ChangePasswordRequest;
import gt.usac.cunoc.sgp.usuario.dto.LoginRequest;
import gt.usac.cunoc.sgp.usuario.dto.LoginResponse;
import gt.usac.cunoc.sgp.usuario.dto.MessageResponse;
import gt.usac.cunoc.sgp.usuario.dto.PasswordChangeResponse;
import gt.usac.cunoc.sgp.usuario.dto.RecoveryRequest;
import gt.usac.cunoc.sgp.usuario.dto.RegisterRequest;
import gt.usac.cunoc.sgp.usuario.dto.TwoFactorRequest;
import gt.usac.cunoc.sgp.usuario.dto.UserResponse;
import gt.usac.cunoc.sgp.usuario.dto.VerifyChallengeRequest;
import gt.usac.cunoc.sgp.usuario.dto.VerifyLoginRequest;
import gt.usac.cunoc.sgp.usuario.dto.VerifyRecoveryRequest;
import gt.usac.cunoc.sgp.usuario.dto.VerifyRegistrationRequest;
import gt.usac.cunoc.sgp.usuario.entity.OtpPurpose;
import gt.usac.cunoc.sgp.usuario.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, RefreshCookieProperties cookieProperties,
            JwtProperties jwtProperties) {
        this.authService = authService;
        this.cookieProperties = cookieProperties;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar estudiante")
    public ResponseEntity<ChallengeResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/register/resend")
    public ChallengeResponse resendRegistration(@Valid @RequestBody RecoveryRequest request) {
        return authService.resendRegistration(request.email());
    }

    @PostMapping("/register/verify")
    public MessageResponse verifyRegistration(@Valid @RequestBody VerifyRegistrationRequest request) {
        return authService.verifyRegistration(request.email(), request.challengeId(), request.otp());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = authService.login(request);
        return withRefreshCookie(result.response(), result.refreshToken());
    }

    @PostMapping("/login/verify")
    public ResponseEntity<LoginResponse> verifyLogin(@Valid @RequestBody VerifyLoginRequest request) {
        var result = authService.verifyLogin(request.challengeId(), request.otp());
        return withRefreshCookie(result.response(), result.refreshToken());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = "SGP_REFRESH_TOKEN", required = false) String refreshToken) {
        var result = authService.refresh(refreshToken);
        return withRefreshCookie(result.response(), result.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "SGP_REFRESH_TOKEN", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    @PostMapping("/password-recovery")
    public ResponseEntity<MessageResponse> requestRecovery(@Valid @RequestBody RecoveryRequest request) {
        return ResponseEntity.accepted().body(authService.requestRecovery(request));
    }

    @PostMapping("/password-recovery/verify")
    public MessageResponse verifyRecovery(@Valid @RequestBody VerifyRecoveryRequest request) {
        return authService.verifyRecovery(request.email(), request.otp(), request.newPassword());
    }

    @PostMapping("/password/change")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PasswordChangeResponse> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        var result = authService.changePassword(authentication.getName(), request);
        return withRefreshCookie(result.response(), result.refreshToken());
    }

    @PostMapping("/2fa/enable")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ChallengeResponse requestTwoFactorEnable(
            Authentication authentication,
            @Valid @RequestBody TwoFactorRequest request) {
        return authService.requestTwoFactorChange(
                authentication.getName(),
                request.currentPassword(),
                OtpPurpose.ACTIVAR_2FA);
    }

    @PostMapping("/2fa/enable/verify")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public MessageResponse confirmTwoFactorEnable(
            Authentication authentication,
            @Valid @RequestBody VerifyChallengeRequest request) {
        return authService.confirmTwoFactorChange(
                authentication.getName(),
                request.challengeId(),
                request.otp(),
                OtpPurpose.ACTIVAR_2FA);
    }

    @PostMapping("/2fa/disable")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ChallengeResponse requestTwoFactorDisable(
            Authentication authentication,
            @Valid @RequestBody TwoFactorRequest request) {
        return authService.requestTwoFactorChange(
                authentication.getName(),
                request.currentPassword(),
                OtpPurpose.DESACTIVAR_2FA);
    }

    @PostMapping("/2fa/disable/verify")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public MessageResponse confirmTwoFactorDisable(
            Authentication authentication,
            @Valid @RequestBody VerifyChallengeRequest request) {
        return authService.confirmTwoFactorChange(
                authentication.getName(),
                request.challengeId(),
                request.otp(),
                OtpPurpose.DESACTIVAR_2FA);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse currentUser(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }

    private <T> ResponseEntity<T> withRefreshCookie(T body, String rawRefreshToken) {
        if (rawRefreshToken == null)
            return ResponseEntity.ok(body);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(rawRefreshToken).toString()).body(body);
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(cookieProperties.getName(), value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/api/v1/auth")
                .maxAge(jwtProperties.getRefreshExpirationMs() / 1000)
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(cookieProperties.getName(), "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}
