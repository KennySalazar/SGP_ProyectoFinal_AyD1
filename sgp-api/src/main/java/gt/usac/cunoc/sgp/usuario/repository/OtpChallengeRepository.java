package gt.usac.cunoc.sgp.usuario.repository;

import gt.usac.cunoc.sgp.usuario.entity.OtpChallenge;
import gt.usac.cunoc.sgp.usuario.entity.OtpPurpose;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

  @Modifying
  @Query(
      "update OtpChallenge c set c.consumedAt = :now where c.user = :user and c.purpose = :purpose and c.consumedAt is null")
  int invalidateActive(
      @Param("user") UserAccount user,
      @Param("purpose") OtpPurpose purpose,
      @Param("now") Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from OtpChallenge c join fetch c.user u join fetch u.role where c.id = :id")
  Optional<OtpChallenge> findForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select c from OtpChallenge c join fetch c.user u join fetch u.role where c.user = :user and c.purpose = :purpose and c.consumedAt is null order by c.createdAt desc")
  Optional<OtpChallenge> findLatestActiveForUpdate(
      @Param("user") UserAccount user, @Param("purpose") OtpPurpose purpose);

  Optional<OtpChallenge> findTopByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
      UserAccount user, OtpPurpose purpose);
}
