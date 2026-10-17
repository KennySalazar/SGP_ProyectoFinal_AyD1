package gt.usac.cunoc.sgp.inspeccion.repository;

import gt.usac.cunoc.sgp.inspeccion.entity.HistorialEstadoInspeccion;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistorialEstadoInspeccionRepository
    extends JpaRepository<HistorialEstadoInspeccion, UUID> {

  @Query(
      "select coalesce(max(h.secuencia), 0) from HistorialEstadoInspeccion h"
          + " where h.inspeccionId = :inspeccionId")
  int ultimaSecuencia(@Param("inspeccionId") UUID inspeccionId);
}
