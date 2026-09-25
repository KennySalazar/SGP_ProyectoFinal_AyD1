package gt.usac.cunoc.sgp.usuario.entity;

import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "desafio_otp")
public class OtpChallenge {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "usuario_id", nullable = false)
  private UserAccount user;

  @Enumerated(EnumType.STRING)
  @Column(name = "proposito", nullable = false, length = 40)
  private OtpPurpose purpose;

  @Column(name = "codigo_hash", nullable = false, length = 100)
  private String codeHash;

  @Column(name = "expira_en", nullable = false)
  private Instant expiresAt;

  @Column(name = "intentos", nullable = false)
  private int attempts;

  @Column(name = "max_intentos", nullable = false)
  private int maxAttempts;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "consumido_en")
  private Instant consumedAt;

  protected OtpChallenge() {}

  public OtpChallenge(
      UserAccount user,
      OtpPurpose purpose,
      String codeHash,
      Instant expiresAt,
      int maxAttempts,
      Instant createdAt) {
    this.id = UuidV7Generator.generate();
    this.user = user;
    this.purpose = purpose;
    this.codeHash = codeHash;
    this.expiresAt = expiresAt;
    this.maxAttempts = maxAttempts;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UserAccount getUser() {
    return user;
  }

  public OtpPurpose getPurpose() {
    return purpose;
  }

  public String getCodeHash() {
    return codeHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public int getAttempts() {
    return attempts;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getConsumedAt() {
    return consumedAt;
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public boolean hasAttemptsRemaining() {
    return attempts < maxAttempts;
  }

  public void consume(Instant now) {
    consumedAt = now;
  }

  public void registerFailedAttempt(Instant now) {
    attempts++;
    if (!hasAttemptsRemaining()) consumedAt = now;
  }
}
