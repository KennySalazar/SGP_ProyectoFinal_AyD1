package gt.usac.cunoc.sgp.mantenimiento.repository;

import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
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

  /** Serializa la ejecución de una orden: evidencia, responsable y cierre (HU-048). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT o FROM OrdenMantenimiento o WHERE o.id = :id")
  Optional<OrdenMantenimiento> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Bandeja de mantenimiento (HU-049): todos los filtros se combinan. Los estados nunca van vacíos
   * y el rango de creación es semiabierto [desde, hasta).
   */
  @Query(
      "SELECT o FROM OrdenMantenimiento o"
          + " WHERE o.estado IN :estados"
          + " AND (:puenteId IS NULL OR o.puenteId = :puenteId)"
          + " AND (:tipo IS NULL OR o.tipo = :tipo)"
          + " AND (:prioridad IS NULL OR o.prioridadConfirmada = :prioridad)"
          + " AND o.creadoEn >= :desde AND o.creadoEn < :hasta")
  Page<OrdenMantenimiento> filtrar(
      @Param("estados") Collection<EstadoOrden> estados,
      @Param("puenteId") UUID puenteId,
      @Param("tipo") TipoOrden tipo,
      @Param("prioridad") PrioridadOrden prioridad,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta,
      Pageable pageable);

  /** Total de órdenes por estado con los mismos filtros de la bandeja, para sus pestañas. */
  @Query(
      "SELECT o.estado, COUNT(o) FROM OrdenMantenimiento o"
          + " WHERE (:puenteId IS NULL OR o.puenteId = :puenteId)"
          + " AND (:tipo IS NULL OR o.tipo = :tipo)"
          + " AND (:prioridad IS NULL OR o.prioridadConfirmada = :prioridad)"
          + " AND o.creadoEn >= :desde AND o.creadoEn < :hasta"
          + " GROUP BY o.estado")
  List<Object[]> contarPorEstado(
      @Param("puenteId") UUID puenteId,
      @Param("tipo") TipoOrden tipo,
      @Param("prioridad") PrioridadOrden prioridad,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta);

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
