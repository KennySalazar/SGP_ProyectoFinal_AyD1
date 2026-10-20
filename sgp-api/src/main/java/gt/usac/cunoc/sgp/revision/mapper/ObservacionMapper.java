package gt.usac.cunoc.sgp.revision.mapper;

import gt.usac.cunoc.sgp.revision.dto.DestinoObservacion;
import gt.usac.cunoc.sgp.revision.dto.ObservacionResponse;
import gt.usac.cunoc.sgp.revision.entity.ObservacionRevision;
import gt.usac.cunoc.sgp.revision.model.TipoDestinoObservacion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ObservacionMapper {
  @Mapping(target = "revisionId", source = "revision.id")
  @Mapping(target = "texto", source = "contenido")
  @Mapping(target = "destino", expression = "java(destino(observacion))")
  ObservacionResponse toResponse(ObservacionRevision observacion);

  default DestinoObservacion destino(ObservacionRevision observacion) {
    if (observacion.getCategoriaFotografica() != null) {
      return new DestinoObservacion(
          TipoDestinoObservacion.CATEGORIA_FOTOGRAFICA,
          null,
          null,
          observacion.getCategoriaFotografica());
    }
    // V9 permitía filas sin destino: se conservan como legado, sin inventar un ancla ni una
    // general.
    if (observacion.getElementoRef() == null) return null;
    return new DestinoObservacion(
        TipoDestinoObservacion.ELEMENTO,
        observacion.getElementoRef(),
        observacion.getInstanciaElementoId(),
        null);
  }
}
