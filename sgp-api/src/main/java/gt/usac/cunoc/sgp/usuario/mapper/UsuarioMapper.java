package gt.usac.cunoc.sgp.usuario.mapper;

import gt.usac.cunoc.sgp.usuario.dto.UsuarioResponse;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.entity.UsuarioProfesional;
import gt.usac.cunoc.sgp.usuario.model.EstadoUsuario;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UsuarioMapper {

  @Mapping(target = "id", source = "usuario.id")
  @Mapping(target = "email", source = "usuario.email")
  @Mapping(target = "rol", source = "usuario.role.name")
  @Mapping(target = "estado", source = "estado")
  @Mapping(target = "verificado", source = "usuario.verified")
  @Mapping(target = "activado", source = "usuario.activated")
  @Mapping(target = "activo", source = "usuario.active")
  @Mapping(target = "numeroColegiado", source = "profesional.numeroColegiado")
  @Mapping(
      target = "colegiadoVerificado",
      expression = "java(profesional == null ? null : profesional.isColegiadoVerificado())")
  @Mapping(target = "desactivadoEn", source = "usuario.desactivadoEn")
  @Mapping(target = "motivoDesactivacion", source = "usuario.motivoDesactivacion")
  @Mapping(target = "creadoEn", source = "usuario.createdAt")
  UsuarioResponse toResponse(
      UserAccount usuario, EstadoUsuario estado, UsuarioProfesional profesional);

  default OffsetDateTime aHoraGuatemala(Instant instante) {
    return instante == null
        ? null
        : instante.atZone(ZoneId.of("America/Guatemala")).toOffsetDateTime();
  }
}
