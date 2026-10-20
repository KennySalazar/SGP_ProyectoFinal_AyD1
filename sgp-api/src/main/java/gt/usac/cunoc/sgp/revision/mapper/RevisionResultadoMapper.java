package gt.usac.cunoc.sgp.revision.mapper;

import gt.usac.cunoc.sgp.revision.dto.RevisionResultadoResponse;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RevisionResultadoMapper {
  @Mapping(target = "revisionId", source = "id")
  @Mapping(target = "inspeccionId", source = "inspeccion.id")
  @Mapping(target = "autorId", source = "inspeccion.autorId")
  @Mapping(target = "estadoRevision", source = "estado")
  @Mapping(target = "estadoInspeccion", source = "inspeccion.estado")
  RevisionResultadoResponse toResponse(Revision revision);
}
