package gt.usac.cunoc.sgp.notificacion.repository;

import gt.usac.cunoc.sgp.notificacion.entity.Notificacion;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

  boolean existsByUsuarioIdAndClaveEvento(UUID usuarioId, String claveEvento);
}
