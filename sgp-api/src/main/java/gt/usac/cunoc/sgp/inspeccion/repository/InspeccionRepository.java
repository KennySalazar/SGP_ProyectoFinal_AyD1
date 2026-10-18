package gt.usac.cunoc.sgp.inspeccion.repository;

import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspeccionRepository extends JpaRepository<Inspeccion, UUID> {

  @EntityGraph(attributePaths = {"puente", "versionFormulario", "acompanantes", "datoTecnico"})
  Optional<Inspeccion> findConDetallesById(UUID id);

  Optional<Inspeccion> findByIdAndEliminadoEnIsNull(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inspeccion i where i.id = :id and i.eliminadoEn is null")
  Optional<Inspeccion> findByIdAndEliminadoEnIsNullForUpdate(@Param("id") UUID id);

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
      @Param("autorId") UUID autorId, @Param("estado") String estado, Pageable pageable);

  List<Inspeccion> findByPuenteIdOrderByFechaInspeccionDesc(UUID puenteId);

  List<Inspeccion> findByAutorIdOrderByFechaInspeccionDesc(UUID autorId);

  Optional<Inspeccion> findTopByAsignacionPuenteIdAndEliminadoEnIsNullOrderByCreadoEnDesc(
      UUID asignacionPuenteId);

  @Query("SELECT i FROM Inspeccion i WHERE i.id = :id AND i.eliminadoEn IS NULL")
  Optional<Inspeccion> findActiveById(@Param("id") UUID id);

  /**
   * Bloquea la inspección durante una transición de estado. En la publicación se invoca después de
   * bloquear su puente, para respetar siempre el mismo orden de bloqueos.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT i FROM Inspeccion i JOIN FETCH i.versionFormulario"
          + " WHERE i.id = :id AND i.eliminadoEn IS NULL")
  Optional<Inspeccion> findActivaByIdForUpdate(@Param("id") UUID id);

  @Query("SELECT i.puente.id FROM Inspeccion i WHERE i.id = :id AND i.eliminadoEn IS NULL")
  Optional<UUID> findPuenteIdByInspeccionId(@Param("id") UUID id);

  /** Categorías con al menos una fotografía disponible de la inspección (RN-INS-06). */
  @Query(
      value =
          """
          SELECT DISTINCT a.categoria_foto
          FROM archivo_inspeccion ai
          JOIN archivo a ON a.id = ai.archivo_id
          WHERE ai.inspeccion_id = :id
            AND a.tipo_uso = 'FOTO_INSPECCION'
            AND a.estado = 'DISPONIBLE'
          """,
      nativeQuery = true)
  List<String> findCategoriasFotoDisponibles(@Param("id") UUID id);

  /** Corrección publicada que supera a la inspección indicada, si existe (RN-INS-08). */
  @Query(
      "SELECT i.id FROM Inspeccion i WHERE i.supersedeAId = :id AND i.estado = 'PUBLICADA'"
          + " AND i.eliminadoEn IS NULL")
  Optional<UUID> findCorreccionPublicadaId(@Param("id") UUID id);

  @Query(
      "SELECT i.supersedeAId, i.id FROM Inspeccion i WHERE i.supersedeAId IN :ids"
          + " AND i.estado = 'PUBLICADA' AND i.eliminadoEn IS NULL")
  List<Object[]> findCorreccionesPublicadas(@Param("ids") Collection<UUID> ids);
}
