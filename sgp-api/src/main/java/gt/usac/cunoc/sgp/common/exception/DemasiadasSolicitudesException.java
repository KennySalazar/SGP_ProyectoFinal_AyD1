package gt.usac.cunoc.sgp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Se superó un límite de tasa (DT-SEC-07): se responde 429 con el encabezado {@code Retry-After} en
 * segundos.
 */
public class DemasiadasSolicitudesException extends ApiException {

  private final long reintentarEnSegundos;

  public DemasiadasSolicitudesException(String code, String detail, long reintentarEnSegundos) {
    super(HttpStatus.TOO_MANY_REQUESTS, code, "Demasiadas solicitudes", detail);
    this.reintentarEnSegundos = Math.max(1, reintentarEnSegundos);
  }

  public long getReintentarEnSegundos() {
    return reintentarEnSegundos;
  }
}
