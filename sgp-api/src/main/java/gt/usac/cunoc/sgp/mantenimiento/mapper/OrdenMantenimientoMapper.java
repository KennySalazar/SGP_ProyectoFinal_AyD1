package gt.usac.cunoc.sgp.mantenimiento.mapper;

import gt.usac.cunoc.sgp.archivo.entity.ArchivoEvidenciaMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.dto.EjecucionOrdenResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.EvidenciaMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.HistorialEstadoOrdenResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimientoEjecucion;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrdenMantenimientoMapper {

  OrdenMantenimientoResponse toResponse(OrdenMantenimiento orden);

  HistorialEstadoOrdenResponse toHistorialResponse(HistorialEstadoOrden historial);

  @Mapping(target = "ordenId", source = "ejecucion.ordenMantenimientoId")
  @Mapping(target = "responsableUsuarioEmail", source = "responsableUsuarioEmail")
  EjecucionOrdenResponse toEjecucionResponse(
      OrdenMantenimientoEjecucion ejecucion, String responsableUsuarioEmail);

  @Mapping(target = "id", source = "archivoId")
  @Mapping(target = "ordenId", source = "ordenMantenimientoId")
  @Mapping(target = "mimeReal", source = "archivo.mimeReal")
  @Mapping(target = "tamanoBytes", source = "archivo.tamanoBytes")
  @Mapping(target = "anchoPx", source = "archivo.anchoPx")
  @Mapping(target = "altoPx", source = "archivo.altoPx")
  @Mapping(target = "subidoPorId", source = "archivo.subidoPorId")
  @Mapping(target = "subidoEn", source = "creadoEn")
  EvidenciaMantenimientoResponse toEvidenciaResponse(ArchivoEvidenciaMantenimiento evidencia);

  default OffsetDateTime aHoraGuatemala(Instant instante) {
    return instante == null
        ? null
        : instante.atZone(ZoneId.of("America/Guatemala")).toOffsetDateTime();
  }
}
