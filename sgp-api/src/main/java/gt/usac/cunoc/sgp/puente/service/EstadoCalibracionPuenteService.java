package gt.usac.cunoc.sgp.puente.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.dto.AprobacionCalibracionResponse;
import gt.usac.cunoc.sgp.formulario.dto.EstadoCalibracionResponse;
import gt.usac.cunoc.sgp.formulario.repository.AprobacionCalibracionRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstadoCalibracionPuenteService {
  private final PuenteRepository puentes;
  private final AprobacionCalibracionRepository aprobaciones;

  public EstadoCalibracionPuenteService(
      PuenteRepository puentes, AprobacionCalibracionRepository aprobaciones) {
    this.puentes = puentes;
    this.aprobaciones = aprobaciones;
  }

  @Transactional(readOnly = true)
  public EstadoCalibracionResponse consultar(UUID puenteId) {
    var puente =
        puentes
            .findByIdAndActivoTrue(puenteId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "puente_no_encontrado",
                        "Puente no encontrado",
                        "El puente no existe o esta inactivo"));
    UUID versionId = puente.getVersionFormularioIdIcVigente();
    if (versionId == null) return null;
    var aprobacion =
        aprobaciones
            .findByVersionFormularioId(versionId)
            .map(AprobacionCalibracionResponse::de)
            .orElse(null);
    return EstadoCalibracionResponse.de(versionId, aprobacion);
  }
}
