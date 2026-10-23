package gt.usac.cunoc.sgp.archivo.repository;

import gt.usac.cunoc.sgp.archivo.entity.ArchivoInspeccion;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.model.TipoUsoArchivo;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ArchivoInspeccionRepository extends JpaRepository<ArchivoInspeccion, UUID> {
  long countByInspeccionIdAndTipoUsoAndArchivo_Estado(
      UUID inspeccionId, TipoUsoArchivo tipoUso, EstadoArchivo estado);

  @EntityGraph(attributePaths = {"archivo", "inspeccion"})
  List<ArchivoInspeccion> findByInspeccionIdAndTipoUsoOrderByCreadoEnAsc(
      UUID inspeccionId, TipoUsoArchivo tipoUso);

  @EntityGraph(attributePaths = {"archivo", "inspeccion"})
  Optional<ArchivoInspeccion> findByInspeccionIdAndArchivoIdAndTipoUso(
      UUID inspeccionId, UUID archivoId, TipoUsoArchivo tipoUso);

  /**
   * Archivos todavía no purgados de borradores sin actividad desde el límite (RN-ARC-06): id del
   * archivo e id de la inspección. Nunca incluye inspecciones fuera de BORRADOR.
   */
  @Query(
      value =
          "SELECT a.id, i.id FROM inspeccion i"
              + " JOIN archivo_inspeccion ai ON ai.inspeccion_id = i.id"
              + " JOIN archivo a ON a.id = ai.archivo_id"
              + " WHERE i.estado = 'BORRADOR' AND i.ultima_actividad_en <= :limite"
              + " AND a.estado IN ('DISPONIBLE', 'RETIRADO') ORDER BY i.id",
      nativeQuery = true)
  List<Object[]> findPurgablesDeBorradoresInactivos(@Param("limite") java.time.Instant limite);

  /**
   * Bloquea la inspección y confirma, dentro de la transacción de la purga, que sigue siendo un
   * borrador inactivo desde el límite.
   */
  @Query(
      value =
          "SELECT (i.estado = 'BORRADOR' AND i.ultima_actividad_en <= :limite) FROM inspeccion i"
              + " WHERE i.id = :inspeccionId FOR UPDATE",
      nativeQuery = true)
  Optional<Boolean> bloquearBorradorInactivo(
      @Param("inspeccionId") UUID inspeccionId, @Param("limite") java.time.Instant limite);
}
