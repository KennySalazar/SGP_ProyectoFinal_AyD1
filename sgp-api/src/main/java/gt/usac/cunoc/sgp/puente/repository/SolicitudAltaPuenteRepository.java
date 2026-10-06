package gt.usac.cunoc.sgp.puente.repository;

import gt.usac.cunoc.sgp.puente.entity.SolicitudAltaPuente;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudAltaPuenteRepository extends JpaRepository<SolicitudAltaPuente, UUID> {

  @EntityGraph(attributePaths = {"municipio", "municipio.departamento"})
  Page<SolicitudAltaPuente> findBySolicitadoPorId(UUID solicitadoPorId, Pageable pageable);
}
