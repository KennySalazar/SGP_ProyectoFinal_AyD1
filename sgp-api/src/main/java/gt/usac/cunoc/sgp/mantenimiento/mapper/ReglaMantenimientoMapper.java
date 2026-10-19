package gt.usac.cunoc.sgp.mantenimiento.mapper;

import gt.usac.cunoc.sgp.mantenimiento.dto.ReglaMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.entity.ReglaMantenimiento;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReglaMantenimientoMapper {

  @Mapping(target = "versionFormularioId", source = "versionFormulario.id")
  @Mapping(target = "versionFormularioCodigo", source = "versionFormulario.codigo")
  @Mapping(target = "provisional", expression = "java(regla.isProvisional())")
  @Mapping(
      target = "estadoValidacion",
      expression = "java(regla.isProvisional() ? \"Provisional · sin validar\" : \"Validada\")")
  ReglaMantenimientoResponse toResponse(ReglaMantenimiento regla);
}
