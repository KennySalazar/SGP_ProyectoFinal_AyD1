package gt.usac.cunoc.sgp.inspeccion.mapper;

import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InspeccionMapper {

  @Mapping(target = "puenteId", source = "puente.id")
  @Mapping(target = "puenteNombre", source = "puente.nombre")
  @Mapping(target = "nombrePuente", source = "puente.nombre")
  @Mapping(target = "versionFormularioId", source = "versionFormulario.id")
  @Mapping(target = "versionFormularioCodigo", source = "versionFormulario.codigo")
  InspeccionResumenResponse toResumenResponse(Inspeccion inspeccion);
}
