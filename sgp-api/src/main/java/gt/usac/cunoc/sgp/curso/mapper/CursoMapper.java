package gt.usac.cunoc.sgp.curso.mapper;

import gt.usac.cunoc.sgp.curso.dto.CatedraticoResponse;
import gt.usac.cunoc.sgp.curso.dto.CursoResponse;
import gt.usac.cunoc.sgp.curso.entity.Curso;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CursoMapper {
  @Mapping(target = "nombre", source = "asignatura.nombre")
  @Mapping(target = "estado", expression = "java(curso.isActivo() ? \"VIGENTE\" : \"FINALIZADO\")")
  CursoResponse toResponse(Curso curso);

  CatedraticoResponse toCatedraticoResponse(UserAccount usuario);
}
