package gt.usac.cunoc.sgp.puente.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.puente.dto.CandidatoTerritorialResponse;
import gt.usac.cunoc.sgp.puente.dto.DepartamentoResponse;
import gt.usac.cunoc.sgp.puente.dto.MunicipioResponse;
import gt.usac.cunoc.sgp.puente.dto.UbicacionTerritorialResponse;
import gt.usac.cunoc.sgp.puente.repository.MunicipioRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
public class UbicacionTerritorialService {

  private final MunicipioRepository municipios;
  private final PuenteRepository puentes;

  public UbicacionTerritorialService(MunicipioRepository municipios, PuenteRepository puentes) {
    this.municipios = municipios;
    this.puentes = puentes;
  }

  public UbicacionTerritorialResponse resolver(double latitud, double longitud) {
    if (!Double.isFinite(latitud)
        || !Double.isFinite(longitud)
        || latitud < -90
        || latitud > 90
        || longitud < -180
        || longitud > 180) {
      throw validacion(
          "coordenadas_invalidas",
          "La latitud debe estar entre -90 y 90 y la longitud entre -180 y 180.");
    }

    Boolean dentroDeGuatemala = puentes.estaDentroDeGuatemala(latitud, longitud);

    if (dentroDeGuatemala == null) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "limite_territorial_no_disponible",
          "Validación territorial no disponible",
          "No está cargado el límite territorial de Guatemala.");
    }

    if (!dentroDeGuatemala) {
      throw validacion(
          "ubicacion_fuera_de_guatemala",
          "Las coordenadas deben estar dentro del territorio de Guatemala.");
    }

    var candidatos =
        municipios.findMunicipiosPorUbicacion(latitud, longitud).stream()
            .map(
                candidato ->
                    new CandidatoTerritorialResponse(
                        new DepartamentoResponse(
                            candidato.getDepartamentoId(),
                            candidato.getDepartamentoCodigoIne(),
                            candidato.getDepartamentoNombre()),
                        new MunicipioResponse(
                            candidato.getMunicipioId(),
                            candidato.getDepartamentoId(),
                            candidato.getMunicipioCodigoIne(),
                            candidato.getMunicipioNombre())))
            .toList();

    if (candidatos.isEmpty()) {
      throw validacion(
          "ubicacion_sin_municipio_activo",
          "No se encontró un municipio activo que cubra las coordenadas "
              + "en los límites municipales cargados.");
    }

    return new UbicacionTerritorialResponse(
        latitud, longitud, longitud < -90 ? "15N" : "16N", candidatos.size() > 1, candidatos);
  }

  private ApiException validacion(String codigo, String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY, codigo, "Ubicación territorial inválida", detalle);
  }
}
