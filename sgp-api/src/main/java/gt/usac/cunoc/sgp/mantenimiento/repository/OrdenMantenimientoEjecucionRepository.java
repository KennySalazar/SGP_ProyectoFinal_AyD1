package gt.usac.cunoc.sgp.mantenimiento.repository;

import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimientoEjecucion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Registros de ejecución de órdenes; tampoco expone borrado (RN-MTO-09). */
public interface OrdenMantenimientoEjecucionRepository
    extends Repository<OrdenMantenimientoEjecucion, UUID> {

  OrdenMantenimientoEjecucion save(OrdenMantenimientoEjecucion ejecucion);

  OrdenMantenimientoEjecucion saveAndFlush(OrdenMantenimientoEjecucion ejecucion);

  Optional<OrdenMantenimientoEjecucion> findById(UUID ordenMantenimientoId);
}
