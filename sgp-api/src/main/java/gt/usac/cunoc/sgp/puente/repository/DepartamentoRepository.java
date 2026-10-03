package gt.usac.cunoc.sgp.puente.repository;

import gt.usac.cunoc.sgp.puente.entity.Departamento;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartamentoRepository extends JpaRepository<Departamento, UUID> {

  Optional<Departamento> findByIdAndActivoTrue(UUID id);

  Page<Departamento> findByActivoTrue(Pageable pageable);
}
