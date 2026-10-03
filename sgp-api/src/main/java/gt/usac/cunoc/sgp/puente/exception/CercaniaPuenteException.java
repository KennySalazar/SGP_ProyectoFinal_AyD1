package gt.usac.cunoc.sgp.puente.exception;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.puente.dto.PuenteCercanoResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

public class CercaniaPuenteException extends ApiException {

  private final Page<PuenteCercanoResponse> cercanos;

  public CercaniaPuenteException(Page<PuenteCercanoResponse> cercanos) {
    super(
        HttpStatus.CONFLICT,
        "puente_cercano",
        "Confirmacion de cercania requerida",
        "Existen puentes a menos de 100 metros. Puede confirmar y continuar.");
    this.cercanos = cercanos;
  }

  public Page<PuenteCercanoResponse> getCercanos() {
    return cercanos;
  }
}
