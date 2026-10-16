package gt.usac.cunoc.sgp.inspeccion.repository;

import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspeccionRepository extends JpaRepository<Inspeccion, UUID> {

  @EntityGraph(attributePaths = {"puente", "versionFormulario", "acompanantes", "datoTecnico"})
  Optional<Inspeccion> findConDetallesById(UUID id);

  Optional<Inspeccion> findByIdAndEliminadoEnIsNull(UUID id);

  @EntityGraph(attributePaths = {"puente", "puente.municipio.departamento", "versionFormulario"})
  @Query(
      value =
          """
          SELECT i FROM Inspeccion i
          WHERE i.autorId = :autorId
            AND i.eliminadoEn IS NULL
            AND (:estado IS NULL OR i.estado = :estado)
          """,
      countQuery =
          """
          SELECT count(i) FROM Inspeccion i
          WHERE i.autorId = :autorId
            AND i.eliminadoEn IS NULL
            AND (:estado IS NULL OR i.estado = :estado)
          """)
  Page<Inspeccion> findMisInspecciones(
      @Param("autorId") UUID autorId,
      @Param("estado") String estado,
      Pageable pageable);
  List<Inspeccion> findByPuenteIdOrderByFechaInspeccionDesc(UUID puenteId);

  List<Inspeccion> findByAutorIdOrderByFechaInspeccionDesc(UUID autorId);

  @Query("SELECT i FROM Inspeccion i WHERE i.id = :id AND i.eliminadoEn IS NULL")
  Optional<Inspeccion> findActiveById(@Param("id") UUID id);
}
