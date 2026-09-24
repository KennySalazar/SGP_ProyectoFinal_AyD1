
package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.util.TokenHasher;
import gt.usac.cunoc.sgp.usuario.entity.RefreshToken;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.RefreshTokenRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final JwtService jwtService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, JwtService jwtService, Clock clock) {
        this.tokens = tokens;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public String issue(UserAccount user) {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.save(new RefreshToken(user, TokenHasher.sha256(raw), now.plusMillis(jwtService.refreshExpirationMs()), now));
        return raw;
    }

    @Transactional
    public RotatedRefreshToken rotate(String raw) {
        if (raw == null || raw.isBlank()) throw invalidRefreshToken();
        RefreshToken stored = tokens.findByTokenHashWithUser(TokenHasher.sha256(raw)).orElseThrow(this::invalidRefreshToken);
        Instant now = clock.instant();
        if (!stored.isActive(now)) throw invalidRefreshToken();
        UserAccount user = stored.getUser();
        if (!user.isActive() || !user.isActivated() || !user.isVerified()) throw invalidRefreshToken();
        stored.revoke(now);
        String replacement = issue(user);
        return new RotatedRefreshToken(user, replacement);
    }

    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) return;
        tokens.findByTokenHashWithUser(TokenHasher.sha256(raw)).ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional
    public void revokeAll(UserAccount user) {
        tokens.revokeAllForUser(user, clock.instant());
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "Sesion no valida", "El token de renovacion es invalido o expiro");
    }

    public record RotatedRefreshToken(UserAccount user, String rawToken) {}
}
