package gt.usac.cunoc.sgp.usuario.repository;

import gt.usac.cunoc.sgp.usuario.entity.InvitacionUsuario;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitacionUsuarioRepository extends JpaRepository<InvitacionUsuario, UUID> {

  /** Bloquea la fila para que el mismo enlace no pueda aceptarse dos veces en paralelo. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from InvitacionUsuario i join fetch i.rol where i.tokenHash = :tokenHash")
  Optional<InvitacionUsuario> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  @EntityGraph(attributePaths = "rol")
  Optional<InvitacionUsuario> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from InvitacionUsuario i join fetch i.rol where i.id = :id")
  Optional<InvitacionUsuario> findByIdForUpdate(@Param("id") UUID id);

  boolean existsByUsuarioCreadoIdAndAceptadoEnIsNullAndCanceladoEnIsNull(UUID usuarioCreadoId);

  @EntityGraph(attributePaths = "rol")
  @Query(
      """
      SELECT i FROM InvitacionUsuario i
      WHERE :estado IS NULL
         OR (:estado = 'ACEPTADA' AND i.aceptadoEn IS NOT NULL)
         OR (:estado = 'CANCELADA' AND i.canceladoEn IS NOT NULL)
         OR (:estado = 'PENDIENTE' AND i.aceptadoEn IS NULL AND i.canceladoEn IS NULL
             AND i.expiraEn > :ahora)
         OR (:estado = 'VENCIDA' AND i.aceptadoEn IS NULL AND i.canceladoEn IS NULL
             AND i.expiraEn <= :ahora)
      """)
  Page<InvitacionUsuario> findByEstado(
      @Param("estado") String estado, @Param("ahora") Instant ahora, Pageable pageable);
}
