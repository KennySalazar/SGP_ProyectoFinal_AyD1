package gt.usac.cunoc.sgp.mantenimiento.mapper;

import gt.usac.cunoc.sgp.mantenimiento.dto.HistorialEstadoOrdenResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrdenMantenimientoMapper {

  OrdenMantenimientoResponse toResponse(OrdenMantenimiento orden);

  HistorialEstadoOrdenResponse toHistorialResponse(HistorialEstadoOrden historial);

  default OffsetDateTime aHoraGuatemala(Instant instante) {
    return instante == null
        ? null
        : instante.atZone(ZoneId.of("America/Guatemala")).toOffsetDateTime();
  }
}
