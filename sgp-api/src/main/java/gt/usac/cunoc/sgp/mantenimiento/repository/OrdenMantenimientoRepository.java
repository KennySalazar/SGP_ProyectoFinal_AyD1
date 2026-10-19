package gt.usac.cunoc.sgp.mantenimiento.repository;

import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenMantenimientoRepository extends JpaRepository<OrdenMantenimiento, UUID> {}
