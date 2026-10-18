package gt.usac.cunoc.sgp.sync.repository;

import gt.usac.cunoc.sgp.sync.entity.OperacionIdempotente;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperacionIdempotenteRepository extends JpaRepository<OperacionIdempotente, UUID> {

  Optional<OperacionIdempotente> findByUsuarioIdAndClave(UUID usuarioId, String clave);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OperacionIdempotente o where o.id=:id")
  Optional<OperacionIdempotente> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      "select o.id from OperacionIdempotente o where o.estado='EN_PROCESO' and o.expiraEn <= :ahora")
  List<UUID> findVencidas(@Param("ahora") Instant ahora);
}
