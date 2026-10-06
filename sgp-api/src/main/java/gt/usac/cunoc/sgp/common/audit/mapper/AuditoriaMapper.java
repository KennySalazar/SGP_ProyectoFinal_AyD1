package gt.usac.cunoc.sgp.common.audit.mapper;

import gt.usac.cunoc.sgp.common.audit.dto.AuditoriaResponse;
import gt.usac.cunoc.sgp.common.audit.entity.Auditoria;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuditoriaMapper {

  @Mapping(
      target = "creadoEn",
      expression =
          "java(auditoria.getCreadoEn().atZone(java.time.ZoneId.of(\"America/Guatemala\")).toOffsetDateTime())")
  AuditoriaResponse toResponse(Auditoria auditoria, String usuarioEmail);
}
