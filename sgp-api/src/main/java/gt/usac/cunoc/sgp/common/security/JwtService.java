package gt.usac.cunoc.sgp.common.security;

import gt.usac.cunoc.sgp.common.config.JwtProperties;
import gt.usac.cunoc.sgp.usuario.entity.RoleName;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtProperties properties;
  private final Clock clock;
  private SecretKey signingKey;

  public JwtService(JwtProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  @PostConstruct
  void validateConfiguration() {
    if (properties.getSecretKey() == null
        || properties.getSecretKey().getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("SECRET_KEY_JWT debe contener al menos 32 caracteres");
    }
    if (properties.getAccessExpirationMs() <= 0 || properties.getRefreshExpirationMs() <= 0) {
      throw new IllegalStateException("La expiracion de los tokens debe ser positiva");
    }
    signingKey = Keys.hmacShaKeyFor(properties.getSecretKey().getBytes(StandardCharsets.UTF_8));
  }

  public String issueAccessToken(UserAccount user) {
    Instant now = clock.instant();
    Instant expiration = now.plusMillis(properties.getAccessExpirationMs());
    return Jwts.builder()
        .subject(user.getEmail())
        .claim("uid", user.getId().toString())
        .claim("role", user.getRole().getName().name())
        .claim("tv", user.getTokenVersion())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiration))
        .signWith(signingKey)
        .compact();
  }

  public Optional<JwtData> parse(String token) {
    try {
      Claims claims =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      String email = claims.getSubject();
      String userId = claims.get("uid", String.class);
      String role = claims.get("role", String.class);
      Number tokenVersion = claims.get("tv", Number.class);
      if (email == null || userId == null || role == null || tokenVersion == null)
        return Optional.empty();
      return Optional.of(
          new JwtData(
              email, UUID.fromString(userId), RoleName.valueOf(role), tokenVersion.intValue()));
    } catch (JwtException | IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  public long accessExpirationMs() {
    return properties.getAccessExpirationMs();
  }

  public long refreshExpirationMs() {
    return properties.getRefreshExpirationMs();
  }
}
