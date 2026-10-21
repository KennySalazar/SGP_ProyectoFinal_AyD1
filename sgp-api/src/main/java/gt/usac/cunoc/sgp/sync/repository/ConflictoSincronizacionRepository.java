package gt.usac.cunoc.sgp.sync.repository;

import gt.usac.cunoc.sgp.sync.entity.ConflictoSincronizacion;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConflictoSincronizacionRepository
    extends JpaRepository<ConflictoSincronizacion, UUID> {
  List<ConflictoSincronizacion> findByInspeccionIdOrderByDetectadoEnDesc(UUID inspeccionId);
}
