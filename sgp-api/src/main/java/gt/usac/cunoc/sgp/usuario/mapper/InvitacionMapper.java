package gt.usac.cunoc.sgp.usuario.mapper;

import gt.usac.cunoc.sgp.usuario.dto.InvitacionPublicaResponse;
import gt.usac.cunoc.sgp.usuario.dto.InvitacionResponse;
import gt.usac.cunoc.sgp.usuario.entity.InvitacionUsuario;
import gt.usac.cunoc.sgp.usuario.model.EstadoInvitacion;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface InvitacionMapper {

  @Mapping(target = "id", source = "invitacion.id")
  @Mapping(target = "email", source = "invitacion.email")
  @Mapping(target = "rol", source = "invitacion.rol.name")
  @Mapping(target = "estado", source = "estado")
  @Mapping(target = "usuarioId", source = "invitacion.usuarioCreadoId")
  @Mapping(target = "invitadoPorId", source = "invitacion.invitadoPorId")
  @Mapping(target = "expiraEn", source = "invitacion.expiraEn")
  @Mapping(target = "aceptadoEn", source = "invitacion.aceptadoEn")
  @Mapping(target = "canceladoEn", source = "invitacion.canceladoEn")
  @Mapping(target = "creadoEn", source = "invitacion.creadoEn")
  InvitacionResponse toResponse(InvitacionUsuario invitacion, EstadoInvitacion estado);

  @Mapping(target = "rol", source = "rol.name")
  InvitacionPublicaResponse toPublicaResponse(InvitacionUsuario invitacion);

  default OffsetDateTime aHoraGuatemala(Instant instante) {
    return instante == null
        ? null
        : instante.atZone(ZoneId.of("America/Guatemala")).toOffsetDateTime();
  }
}
