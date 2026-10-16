package gt.usac.cunoc.sgp.inspeccion.exception;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.ValidacionEvidenciaResponse;
import java.util.Objects;
import org.springframework.http.HttpStatus;

public class EvidenciaFotograficaIncompletaException extends ApiException {
  private final ValidacionEvidenciaResponse validacion;

  public EvidenciaFotograficaIncompletaException(ValidacionEvidenciaResponse validacion) {
    super(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "evidencia_fotografica_incompleta",
        "Evidencia fotográfica incompleta",
        "Falta evidencia en las categorías indicadas.");
    this.validacion = Objects.requireNonNull(validacion);
  }

  public ValidacionEvidenciaResponse getValidacion() {
    return validacion;
  }
}
