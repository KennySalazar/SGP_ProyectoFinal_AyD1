package gt.usac.cunoc.sgp.sync.repository;

import gt.usac.cunoc.sgp.sync.entity.OperacionIdempotente;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperacionIdempotenteRepository extends JpaRepository<OperacionIdempotente, UUID> {

  Optional<OperacionIdempotente> findByUsuarioIdAndClave(UUID usuarioId, String clave);
}
