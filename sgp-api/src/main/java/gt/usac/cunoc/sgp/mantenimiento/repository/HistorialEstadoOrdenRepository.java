package gt.usac.cunoc.sgp.mantenimiento.repository;

import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Historial inmutable de estados: solo se agregan filas y se consultan (RN-MTO-04). */
public interface HistorialEstadoOrdenRepository extends Repository<HistorialEstadoOrden, UUID> {

  HistorialEstadoOrden save(HistorialEstadoOrden historial);

  @Query(
      "select coalesce(max(h.secuencia), 0) from HistorialEstadoOrden h"
          + " where h.ordenMantenimientoId = :ordenId")
  int ultimaSecuencia(@Param("ordenId") UUID ordenId);

  List<HistorialEstadoOrden> findByOrdenMantenimientoIdOrderBySecuenciaAsc(UUID ordenId);
}
