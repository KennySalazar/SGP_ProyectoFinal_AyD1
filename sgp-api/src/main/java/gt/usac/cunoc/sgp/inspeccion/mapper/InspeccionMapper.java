package gt.usac.cunoc.sgp.inspeccion.mapper;

import gt.usac.cunoc.sgp.inspeccion.dto.DatosPermanentesResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionPublicadaDetalleResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.RelacionVersionActivaResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ResultadoIcPublicadoResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.entity.ResultadoIc;
import java.util.UUID;
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

  @Mapping(target = "id", source = "inspeccion.id")
  @Mapping(target = "puenteId", source = "inspeccion.puente.id")
  @Mapping(target = "puenteNombre", source = "inspeccion.puente.nombre")
  @Mapping(target = "versionFormularioId", source = "inspeccion.versionFormulario.id")
  @Mapping(target = "versionFormularioCodigo", source = "inspeccion.versionFormulario.codigo")
  @Mapping(target = "esquemaFormulario", source = "inspeccion.versionFormulario.esquema")
  @Mapping(target = "nombreAutor", source = "inspeccion.nombreAutorCapturado")
  @Mapping(target = "rolAutor", source = "inspeccion.rolAutorCreacion")
  @Mapping(target = "creadoEn", source = "inspeccion.creadoEn")
  @Mapping(target = "enviadoEn", source = "inspeccion.enviadoDispositivoEn")
  @Mapping(target = "ic", source = "resultado")
  @Mapping(target = "datosPermanentes", source = "datosPermanentes")
  @Mapping(target = "superadaPorId", source = "correccionId")
  @Mapping(
      target = "vigencia",
      expression = "java(correccionId == null ? \"VIGENTE\" : \"SUPERADA\")")
  @Mapping(target = "relacionVersionActiva", source = "relacionVersionActiva")
  InspeccionPublicadaDetalleResponse toPublicadaDetalle(
      Inspeccion inspeccion,
      ResultadoIc resultado,
      DatosPermanentesResponse datosPermanentes,
      UUID correccionId,
      RelacionVersionActivaResponse relacionVersionActiva);

  ResultadoIcPublicadoResponse toResultadoPublicado(ResultadoIc resultado);
}
