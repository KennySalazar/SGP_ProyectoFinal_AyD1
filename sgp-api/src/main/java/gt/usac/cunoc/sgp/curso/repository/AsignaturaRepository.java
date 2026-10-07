package gt.usac.cunoc.sgp.curso.repository;

import gt.usac.cunoc.sgp.curso.entity.Asignatura;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsignaturaRepository extends JpaRepository<Asignatura, UUID> {
  Optional<Asignatura> findByNombreIgnoreCase(String nombre);
}
