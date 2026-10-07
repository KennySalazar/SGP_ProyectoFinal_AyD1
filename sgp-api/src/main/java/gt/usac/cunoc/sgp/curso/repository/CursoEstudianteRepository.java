package gt.usac.cunoc.sgp.curso.repository;

import gt.usac.cunoc.sgp.curso.entity.CursoEstudiante;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CursoEstudianteRepository extends JpaRepository<CursoEstudiante, UUID> {
  boolean existsByEstudiante_IdAndEstado(UUID estudianteId, String estado);
}
