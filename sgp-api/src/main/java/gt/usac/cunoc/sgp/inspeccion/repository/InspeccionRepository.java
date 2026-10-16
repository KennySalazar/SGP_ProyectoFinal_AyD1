package gt.usac.cunoc.sgp.inspeccion.repository;

import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspeccionRepository extends JpaRepository<Inspeccion, UUID> {

  List<Inspeccion> findByPuenteIdOrderByFechaInspeccionDesc(UUID puenteId);

  List<Inspeccion> findByAutorIdOrderByFechaInspeccionDesc(UUID autorId);

  @Query("SELECT i FROM Inspeccion i WHERE i.id = :id AND i.eliminadoEn IS NULL")
  Optional<Inspeccion> findActiveById(@Param("id") UUID id);
}
