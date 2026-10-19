package gt.usac.cunoc.sgp.revision.repository;

import gt.usac.cunoc.sgp.revision.entity.ObservacionRevision;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservacionRevisionRepository extends JpaRepository<ObservacionRevision, UUID> {
  List<ObservacionRevision> findByRevisionIdOrderByCreadoEnAscIdAsc(UUID revisionId);

  Optional<ObservacionRevision> findByIdAndRevisionId(UUID id, UUID revisionId);

  long countByRevisionId(UUID revisionId);

  long countByRevisionIdAndEstado(UUID revisionId, EstadoObservacion estado);
}
