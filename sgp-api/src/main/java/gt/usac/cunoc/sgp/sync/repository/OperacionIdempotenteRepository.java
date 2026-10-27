package gt.usac.cunoc.sgp.sync.repository;

import gt.usac.cunoc.sgp.sync.entity.OperacionIdempotente;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

  /**
   * Elimina las claves vencidas que ya no protegen nada (HU-069): las completadas y las reservas
   * HTTP abandonadas. Conserva las cargas de archivos en proceso, que recupera {@code
   * RecuperacionCargasService}, y las que respaldan un conflicto de sincronización.
   */
  @Modifying
  @Query(
      value =
          "DELETE FROM operacion_idempotente o WHERE o.expira_en <= :ahora"
              + " AND (o.estado = 'COMPLETADA'"
              + " OR (o.estado = 'EN_PROCESO' AND o.entidad = 'http_hu043'))"
              + " AND NOT EXISTS (SELECT 1 FROM conflicto_sincronizacion c"
              + " WHERE c.operacion_idempotente_id = o.id)",
      nativeQuery = true)
  int eliminarVencidas(@Param("ahora") Instant ahora);
}
