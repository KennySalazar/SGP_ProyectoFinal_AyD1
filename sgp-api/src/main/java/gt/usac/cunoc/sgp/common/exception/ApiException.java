package gt.usac.cunoc.sgp.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final String title;
  private final java.util.List<java.util.Map<String, String>> errores;

  public ApiException(HttpStatus status, String code, String title, String detail) {
    this(status, code, title, detail, java.util.List.of());
  }

  public ApiException(
      HttpStatus status,
      String code,
      String title,
      String detail,
      java.util.List<java.util.Map<String, String>> errores) {
    super(detail);
    this.status = status;
    this.code = code;
    this.title = title;
    this.errores = errores == null ? java.util.List.of() : java.util.List.copyOf(errores);
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public String getTitle() {
    return title;
  }

  public java.util.List<java.util.Map<String, String>> getErrores() {
    return errores;
  }
}
