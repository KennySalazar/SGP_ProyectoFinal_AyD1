package gt.usac.cunoc.sgp.formulario.validation;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class VersionCalibracionHttp {
  private VersionCalibracionHttp() {}

  public static long exigir(String valor) {
    if (valor == null || valor.isBlank()) {
      throw new ApiException(
          HttpStatus.PRECONDITION_REQUIRED,
          "version_calibracion_requerida",
          "Version requerida",
          "Envie If-Match con la version consultada antes de guardar.");
    }
    String version = valor.strip();
    if (version.matches("\"[0-9]+\"")) version = version.substring(1, version.length() - 1);
    try {
      if (!version.matches("[0-9]+")) throw new NumberFormatException();
      return Long.parseLong(version);
    } catch (NumberFormatException e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "version_calibracion_invalida",
          "Version invalida",
          "If-Match debe contener una unica version numerica no negativa.");
    }
  }
}
