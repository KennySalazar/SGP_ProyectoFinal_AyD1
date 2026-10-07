package gt.usac.cunoc.sgp.usuario.repository;

import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  @EntityGraph(attributePaths = "role")
  Optional<UserAccount> findByEmail(String email);

  @EntityGraph(attributePaths = "role")
  Optional<UserAccount> findWithRoleById(UUID id);

  /** Bloquea la cuenta para que dos administradores no la modifiquen al mismo tiempo. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u join fetch u.role where u.id = :id")
  Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id);

  @EntityGraph(attributePaths = "role")
  @Query(
      """
      SELECT u FROM UserAccount u
      WHERE (:rol IS NULL OR u.role.name = :rol)
        AND (:estado IS NULL
          OR (:estado = 'INACTIVO' AND u.active = false)
          OR (:estado = 'ACTIVO' AND u.active = true AND u.activated = true AND u.verified = true)
          OR (:estado = 'PENDIENTE' AND u.active = true
              AND (u.activated = false OR u.verified = false)))
      """)
  Page<UserAccount> findByRolYEstado(
      @Param("rol") RoleName rol, @Param("estado") String estado, Pageable pageable);
}
