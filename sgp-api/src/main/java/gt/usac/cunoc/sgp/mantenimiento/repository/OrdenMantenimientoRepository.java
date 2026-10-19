package gt.usac.cunoc.sgp.mantenimiento.repository;

import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a las órdenes de mantenimiento. Extiende {@link Repository} y no {@code JpaRepository}
 * para no exponer ninguna operación de borrado (RN-MTO-09, DT-BD-11).
 */
public interface OrdenMantenimientoRepository extends Repository<OrdenMantenimiento, UUID> {

  OrdenMantenimiento save(OrdenMantenimiento orden);

  OrdenMantenimiento saveAndFlush(OrdenMantenimiento orden);

  Optional<OrdenMantenimiento> findById(UUID id);

  @Query(
      "SELECT o FROM OrdenMantenimiento o"
          + " WHERE (:puenteId IS NULL OR o.puenteId = :puenteId)"
          + " AND (:estado IS NULL OR o.estado = :estado)")
  Page<OrdenMantenimiento> buscar(
      @Param("puenteId") UUID puenteId, @Param("estado") EstadoOrden estado, Pageable pageable);

  /** Evita generar dos veces la misma propuesta para una inspección (HU-046). */
  boolean existsByInspeccionOrigenIdAndClaveGeneracion(
      UUID inspeccionOrigenId, String claveGeneracion);

  /** Órdenes que originó una inspección, de la más antigua a la más reciente. */
  List<OrdenMantenimiento> findByInspeccionOrigenIdOrderByCreadoEnAscIdAsc(UUID inspeccionOrigenId);

  /** Órdenes de un elemento del puente que siguen en curso, para no proponer lo ya propuesto. */
  @Query(
      "SELECT o FROM OrdenMantenimiento o"
          + " WHERE o.puenteId = :puenteId AND o.elementoRef = :elementoRef"
          + " AND o.estado IN :estados")
  List<OrdenMantenimiento> findPorElementoYEstados(
      @Param("puenteId") UUID puenteId,
      @Param("elementoRef") String elementoRef,
      @Param("estados") Collection<EstadoOrden> estados);
}
