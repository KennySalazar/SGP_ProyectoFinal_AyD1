package gt.usac.cunoc.sgp.inspeccion.repository;

import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspeccionRepository extends JpaRepository<Inspeccion, UUID> {

  @EntityGraph(attributePaths = {"puente", "versionFormulario", "acompanantes", "datoTecnico"})
  Optional<Inspeccion> findConDetallesById(UUID id);

  Optional<Inspeccion> findByIdAndEliminadoEnIsNull(UUID id);
}
