package gt.usac.cunoc.sgp.usuario.exception;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class OtpRateLimitException extends ApiException {
  public OtpRateLimitException() {
    super(
        HttpStatus.TOO_MANY_REQUESTS,
        "otp_rate_limited",
        "Demasiados intentos",
        "Debe esperar antes de solicitar otro codigo OTP");
  }
}
