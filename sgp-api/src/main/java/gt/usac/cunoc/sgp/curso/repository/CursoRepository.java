package gt.usac.cunoc.sgp.curso.repository;

import gt.usac.cunoc.sgp.curso.entity.Curso;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CursoRepository extends JpaRepository<Curso, UUID> {
  boolean existsByAsignatura_NombreIgnoreCaseAndPeriodo(String nombre, String periodo);

  boolean existsByAsignatura_NombreIgnoreCaseAndPeriodoAndIdNot(
      String nombre, String periodo, UUID id);

  List<Curso> findByCatedratico_IdAndActivoTrueOrderByPeriodoDesc(UUID catedraticoId);

  java.util.Optional<Curso> findByIdAndCatedratico_Id(UUID id, UUID catedraticoId);

  @Override
  @EntityGraph(attributePaths = {"asignatura", "catedratico"})
  Page<Curso> findAll(Pageable pageable);
}
