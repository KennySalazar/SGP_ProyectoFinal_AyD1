package gt.usac.cunoc.sgp.usuario.repository;

import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  @EntityGraph(attributePaths = "role")
  Optional<UserAccount> findByEmail(String email);

  @EntityGraph(attributePaths = "role")
  Optional<UserAccount> findWithRoleById(UUID id);
}
