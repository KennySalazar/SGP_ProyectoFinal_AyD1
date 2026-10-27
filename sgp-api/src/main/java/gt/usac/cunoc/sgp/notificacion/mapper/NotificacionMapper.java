package gt.usac.cunoc.sgp.notificacion.mapper;

import gt.usac.cunoc.sgp.notificacion.dto.NotificacionResponse;
import gt.usac.cunoc.sgp.notificacion.entity.Notificacion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificacionMapper {

  @Mapping(target = "leido", expression = "java(notificacion.getLeidoEn() != null)")
  NotificacionResponse toResponse(Notificacion notificacion);
}
