package gt.usac.cunoc.sgp.formulario.repository;

import gt.usac.cunoc.sgp.formulario.entity.AprobacionCalibracion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AprobacionCalibracionRepository
    extends JpaRepository<AprobacionCalibracion, UUID> {
  Optional<AprobacionCalibracion> findByVersionFormularioId(UUID versionId);

  boolean existsByVersionFormularioId(UUID versionId);
}
