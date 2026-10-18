package gt.usac.cunoc.sgp.archivo.mapper;

import gt.usac.cunoc.sgp.archivo.entity.ArchivoInspeccion;
import gt.usac.cunoc.sgp.archivo.model.FotografiaRegistrada;
import gt.usac.cunoc.sgp.inspeccion.dto.FotografiaResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface FotografiaMapper {
  @Mapping(target = "id", source = "archivoId")
  @Mapping(target = "inspeccionId", source = "inspeccion.id")
  @Mapping(target = "categoria", source = "archivo.categoriaFoto")
  @Mapping(target = "estado", source = "archivo.estado")
  FotografiaRegistrada toRegistrada(ArchivoInspeccion vinculo);

  @Mapping(target = "id", source = "archivoId")
  @Mapping(target = "inspeccionId", source = "inspeccion.id")
  @Mapping(target = "categoria", source = "archivo.categoriaFoto")
  @Mapping(target = "mimeReal", source = "archivo.mimeReal")
  @Mapping(target = "tamanoBytes", source = "archivo.tamanoBytes")
  @Mapping(target = "anchoPx", source = "archivo.anchoPx")
  @Mapping(target = "altoPx", source = "archivo.altoPx")
  @Mapping(target = "capturadoEn", source = "archivo.capturadoEn")
  @Mapping(
      target = "latitud",
      expression = "java(vinculo.getArchivo().getUbicacionCaptura().getY())")
  @Mapping(
      target = "longitud",
      expression = "java(vinculo.getArchivo().getUbicacionCaptura().getX())")
  FotografiaResponse toResponse(ArchivoInspeccion vinculo);
}
