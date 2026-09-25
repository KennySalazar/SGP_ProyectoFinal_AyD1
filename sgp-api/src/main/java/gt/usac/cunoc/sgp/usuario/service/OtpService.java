package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.config.OtpProperties;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.OtpRateLimitException;
import gt.usac.cunoc.sgp.common.mail.EmailService;
import gt.usac.cunoc.sgp.usuario.entity.OtpChallenge;
import gt.usac.cunoc.sgp.usuario.entity.OtpPurpose;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.OtpChallengeRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {

  private final OtpChallengeRepository challenges;
  private final PasswordEncoder passwordEncoder;
  private final EmailService emailService;
  private final OtpProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public OtpService(
      OtpChallengeRepository challenges,
      PasswordEncoder passwordEncoder,
      EmailService emailService,
      OtpProperties properties,
      Clock clock) {
    this.challenges = challenges;
    this.passwordEncoder = passwordEncoder;
    this.emailService = emailService;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional(noRollbackFor = OtpRateLimitException.class)
  public OtpChallenge issue(UserAccount user, OtpPurpose purpose) {
    Instant now = clock.instant();
    OtpChallenge latest =
        challenges
            .findTopByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(user, purpose)
            .orElse(null);
    if (latest != null
        && !latest.isExpired(now)
        && latest.getCreatedAt().plusSeconds(properties.getResendCooldownSeconds()).isAfter(now)) {
      throw new OtpRateLimitException();
    }
    challenges.invalidateActive(user, purpose, now);
    String code = generateCode();
    OtpChallenge challenge =
        challenges.save(
            new OtpChallenge(
                user,
                purpose,
                passwordEncoder.encode(code),
                now.plusSeconds(properties.getExpirationMinutes() * 60L),
                properties.getMaxAttempts(),
                now));
    emailService.sendOtp(user.getEmail(), purpose, code, challenge.getExpiresAt());
    return challenge;
  }

  @Transactional(noRollbackFor = ApiException.class)
  public UserAccount verify(
      UUID challengeId, UserAccount expectedUser, OtpPurpose purpose, String code) {
    OtpChallenge challenge = challenges.findForUpdate(challengeId).orElse(null);
    if (challenge == null
        || challenge.getPurpose() != purpose
        || !challenge.getUser().getId().equals(expectedUser.getId())) throw invalidOtp();
    verifyCode(challenge, code);
    challenge.consume(clock.instant());
    return challenge.getUser();
  }

  @Transactional(noRollbackFor = ApiException.class)
  public UserAccount verify(UUID challengeId, OtpPurpose purpose, String code) {
    OtpChallenge challenge = challenges.findForUpdate(challengeId).orElse(null);
    if (challenge == null || challenge.getPurpose() != purpose) throw invalidOtp();
    verifyCode(challenge, code);
    challenge.consume(clock.instant());
    return challenge.getUser();
  }

  @Transactional(noRollbackFor = ApiException.class)
  public UserAccount verifyLatest(UserAccount user, OtpPurpose purpose, String code) {
    OtpChallenge challenge = challenges.findLatestActiveForUpdate(user, purpose).orElse(null);
    if (challenge == null) throw invalidOtp();
    verifyCode(challenge, code);
    challenge.consume(clock.instant());
    return challenge.getUser();
  }

  private void verifyCode(OtpChallenge challenge, String code) {
    Instant now = clock.instant();
    if (code == null
        || challenge.isConsumed()
        || challenge.isExpired(now)
        || !challenge.hasAttemptsRemaining()) throw invalidOtp();
    if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
      challenge.registerFailedAttempt(now);
      throw invalidOtp();
    }
  }

  private String generateCode() {
    int length = properties.getLength();
    int bound = (int) Math.pow(10, length);
    return String.format(Locale.ROOT, "%0" + length + "d", random.nextInt(bound));
  }

  private ApiException invalidOtp() {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        "invalid_otp",
        "Codigo OTP invalido",
        "Codigo OTP invalido o expirado");
  }
}
