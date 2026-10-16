package gt.usac.cunoc.sgp.inspeccion.repository;

import gt.usac.cunoc.sgp.inspeccion.entity.InspeccionDatoTecnico;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspeccionDatoTecnicoRepository
    extends JpaRepository<InspeccionDatoTecnico, UUID> {

  Optional<InspeccionDatoTecnico> findByInspeccionId(UUID inspeccionId);
}
