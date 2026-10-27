package gt.usac.cunoc.sgp.mantenimiento.repository;

import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface OrdenCicloPlanRepository extends Repository<OrdenMantenimiento, UUID> {

  boolean existsByPlanMantenimientoRutinarioIdAndCicloPlan(UUID planId, int cicloPlan);
}
