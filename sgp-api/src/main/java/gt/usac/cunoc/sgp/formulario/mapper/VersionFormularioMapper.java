package gt.usac.cunoc.sgp.formulario.mapper;

import gt.usac.cunoc.sgp.formulario.dto.ConfiguracionIcElementoResponse;
import gt.usac.cunoc.sgp.formulario.dto.FormularioActivoResponse;
import gt.usac.cunoc.sgp.formulario.dto.MapeoCampoResponse;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResponse;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResumenResponse;
import gt.usac.cunoc.sgp.formulario.entity.ConfiguracionIcElemento;
import gt.usac.cunoc.sgp.formulario.entity.MapeoCampoFormulario;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VersionFormularioMapper {

  @Mapping(target = "versionAnteriorId", source = "versionAnterior.id")
  @Mapping(target = "versionAnteriorCodigo", source = "versionAnterior.codigo")
  VersionFormularioResumenResponse toResumenResponse(VersionFormulario version);

  @Mapping(target = "versionAnteriorId", source = "version.versionAnterior.id")
  @Mapping(target = "versionAnteriorCodigo", source = "version.versionAnterior.codigo")
  @Mapping(target = "id", source = "version.id")
  @Mapping(target = "codigo", source = "version.codigo")
  @Mapping(target = "estado", source = "version.estado")
  @Mapping(target = "activa", source = "version.activa")
  @Mapping(target = "esquema", source = "version.esquema")
  @Mapping(target = "vinculosAlmacenamiento", source = "version.vinculosAlmacenamiento")
  @Mapping(target = "reglasEvaluacionIc", source = "version.reglasEvaluacionIc")
  @Mapping(target = "hashContenido", source = "version.hashContenido")
  @Mapping(target = "versionAlgoritmoIc", source = "version.versionAlgoritmoIc")
  @Mapping(target = "creadoPorId", source = "version.creadoPorId")
  @Mapping(target = "publicadoPorId", source = "version.publicadoPorId")
  @Mapping(target = "publicadoEn", source = "version.publicadoEn")
  @Mapping(target = "calibradoPorId", source = "version.calibradoPorId")
  @Mapping(target = "calibradoPorNombre", source = "version.calibradoPorNombre")
  @Mapping(target = "calibradoPorEmail", source = "version.calibradoPorEmail")
  @Mapping(target = "calibradoEn", source = "version.calibradoEn")
  @Mapping(target = "creadoEn", source = "version.creadoEn")
  @Mapping(target = "actualizadoEn", source = "version.actualizadoEn")
  @Mapping(target = "version", source = "version.version")
  @Mapping(target = "pesos", source = "pesos")
  @Mapping(target = "mapeos", source = "mapeos")
  @Mapping(target = "aprobacionCalibracion", ignore = true)
  VersionFormularioResponse toResponse(
      VersionFormulario version,
      List<ConfiguracionIcElementoResponse> pesos,
      List<MapeoCampoResponse> mapeos);

  default VersionFormularioResponse toResponse(
      VersionFormulario version,
      List<ConfiguracionIcElementoResponse> pesos,
      List<MapeoCampoResponse> mapeos,
      gt.usac.cunoc.sgp.formulario.dto.AprobacionCalibracionResponse aprobacion) {
    return toResponse(version, pesos, mapeos).conAprobacion(aprobacion);
  }

  FormularioActivoResponse toActivoResponse(VersionFormulario version);

  @Mapping(target = "versionOrigenId", source = "versionOrigen.id")
  @Mapping(target = "versionDestinoId", source = "versionDestino.id")
  MapeoCampoResponse toMapeoResponse(MapeoCampoFormulario mapeo);

  ConfiguracionIcElementoResponse toIcElementoResponse(ConfiguracionIcElemento elemento);
}
