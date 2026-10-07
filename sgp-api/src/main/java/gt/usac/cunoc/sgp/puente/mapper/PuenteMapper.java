package gt.usac.cunoc.sgp.puente.mapper;

import gt.usac.cunoc.sgp.puente.dto.CoordenadaUtmResponse;
import gt.usac.cunoc.sgp.puente.dto.DepartamentoResponse;
import gt.usac.cunoc.sgp.puente.dto.MunicipioResponse;
import gt.usac.cunoc.sgp.puente.dto.PuenteCatalogoResponse;
import gt.usac.cunoc.sgp.puente.dto.PuenteCercanoResponse;
import gt.usac.cunoc.sgp.puente.dto.PuenteResponse;
import gt.usac.cunoc.sgp.puente.dto.SolicitudAltaPuenteResponse;
import gt.usac.cunoc.sgp.puente.entity.Departamento;
import gt.usac.cunoc.sgp.puente.entity.Municipio;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.entity.SolicitudAltaPuente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository.CoordenadaUtmProjection;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository.PuenteCercanoProjection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PuenteMapper {

  @Mapping(target = "departamento", source = "municipio.departamento")
  @Mapping(target = "latitud", expression = "java(puente.getUbicacion().getY())")
  @Mapping(target = "longitud", expression = "java(puente.getUbicacion().getX())")
  @Mapping(target = "estadoActual", constant = "Sin evaluar")
  PuenteCatalogoResponse toCatalogoResponse(Puente puente);

  DepartamentoResponse toDepartamentoResponse(Departamento departamento);

  @Mapping(target = "departamentoId", source = "departamento.id")
  MunicipioResponse toMunicipioResponse(Municipio municipio);

  PuenteCercanoResponse toPuenteCercanoResponse(PuenteCercanoProjection cercano);

  @Mapping(target = "hemisferio", constant = "N")
  CoordenadaUtmResponse toCoordenadaUtmResponse(CoordenadaUtmProjection coordenada);

  @Mapping(target = "departamento", source = "puente.municipio.departamento")
  @Mapping(target = "latitud", expression = "java(puente.getUbicacion().getY())")
  @Mapping(target = "longitud", expression = "java(puente.getUbicacion().getX())")
  @Mapping(target = "utm", source = "utm")
  @Mapping(target = "estadoActual", constant = "Sin evaluar")
  @Mapping(target = "indiceCondicionActual", expression = "java((java.math.BigDecimal) null)")
  @Mapping(target = "fechaUltimaInspeccion", expression = "java((java.time.LocalDate) null)")
  @Mapping(
      target = "creadoEn",
      expression =
          "java(puente.getCreadoEn().atZone(java.time.ZoneId.of(\"America/Guatemala\")).toOffsetDateTime())")
  PuenteResponse toAltaResponse(Puente puente, CoordenadaUtmResponse utm);

  @Mapping(target = "nombre", source = "nombrePropuesto")
  @Mapping(target = "departamento", source = "municipio.departamento")
  @Mapping(target = "latitud", expression = "java(solicitud.getUbicacion().getY())")
  @Mapping(target = "longitud", expression = "java(solicitud.getUbicacion().getX())")
  @Mapping(
      target = "puenteCreadoId",
      expression =
          "java(solicitud.getPuenteCreado() == null ? null : solicitud.getPuenteCreado().getId())")
  @Mapping(
      target = "puenteCreadoCodigo",
      expression =
          "java(solicitud.getPuenteCreado() == null ? null : solicitud.getPuenteCreado().getCodigo())")
  SolicitudAltaPuenteResponse toSolicitudResponse(SolicitudAltaPuente solicitud);

  default OffsetDateTime aHoraGuatemala(Instant instante) {
    return instante == null
        ? null
        : instante.atZone(ZoneId.of("America/Guatemala")).toOffsetDateTime();
  }
}
