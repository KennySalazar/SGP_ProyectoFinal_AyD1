package gt.usac.cunoc.sgp.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.config.OtpProperties;
import gt.usac.cunoc.sgp.usuario.entity.OtpChallenge;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.OtpPurpose;
import gt.usac.cunoc.sgp.usuario.repository.OtpChallengeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class OtpServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-18T21:16:04Z");

  @Test
  void verificaSoloElOtpActivoMasReciente() {
    OtpChallengeRepository challenges = org.mockito.Mockito.mock(OtpChallengeRepository.class);
    PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    UserAccount user = org.mockito.Mockito.mock(UserAccount.class);
    OtpChallenge latest = org.mockito.Mockito.mock(OtpChallenge.class);
    OtpProperties properties = new OtpProperties();
    OtpService service =
        new OtpService(
            challenges,
            passwordEncoder,
            org.mockito.Mockito.mock(OtpEmailService.class),
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC));

    when(challenges.findTopByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            user, OtpPurpose.RECUPERACION_PASSWORD))
        .thenReturn(Optional.of(latest));
    when(latest.isConsumed()).thenReturn(false);
    when(latest.isExpired(NOW)).thenReturn(false);
    when(latest.hasAttemptsRemaining()).thenReturn(true);
    when(latest.getCodeHash()).thenReturn("hash");
    when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
    when(latest.getUser()).thenReturn(user);

    assertThat(service.verifyLatest(user, OtpPurpose.RECUPERACION_PASSWORD, "123456"))
        .isSameAs(user);

    verify(latest).consume(NOW);
  }
}
