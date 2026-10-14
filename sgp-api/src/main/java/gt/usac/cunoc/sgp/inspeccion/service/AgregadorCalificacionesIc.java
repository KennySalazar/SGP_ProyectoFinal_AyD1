package gt.usac.cunoc.sgp.inspeccion.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgregadorCalificacionesIc {

  private static final BigDecimal CALIFICACION_MAXIMA = new BigDecimal("4");

  public BigDecimal agregar(List<BigDecimal> calificaciones, String regla, String referencia) {

    if (regla == null || regla.isBlank()) {
      throw error(
          "configuracion_ic_incompleta",
          "Falta configurar la regla de agregacion de " + referencia);
    }

    if (!regla.equals("MAXIMO") && !regla.equals("PROMEDIO")) {
      throw error(
          "regla_agregacion_ic_invalida",
          "La regla de agregacion de " + referencia + " no es compatible: " + regla);
    }

    if (calificaciones == null || calificaciones.isEmpty()) {
      throw error("sin_calificaciones_ic", "No hay calificaciones para agregar en " + referencia);
    }

    BigDecimal suma = BigDecimal.ZERO;
    BigDecimal maximo = BigDecimal.ZERO;

    for (BigDecimal calificacion : calificaciones) {
      validarCalificacion(calificacion, referencia);

      suma = suma.add(calificacion);
      maximo = maximo.max(calificacion);
    }

    if (regla.equals("MAXIMO")) {
      return maximo;
    }

    return suma.divide(BigDecimal.valueOf(calificaciones.size()), MathContext.DECIMAL128);
  }

  public void validarCalificacion(BigDecimal calificacion, String referencia) {

    if (calificacion == null) {
      throw error(
          "configuracion_ic_incompleta", "Falta configurar la calificacion de " + referencia);
    }

    if (calificacion.signum() < 0 || calificacion.compareTo(CALIFICACION_MAXIMA) > 0) {
      throw error(
          "calificacion_ic_fuera_de_rango",
          "La calificacion de "
              + referencia
              + " debe estar entre 0 y 4: "
              + calificacion.toPlainString());
    }
  }

  private ApiException error(String codigo, String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        codigo,
        "Calificaciones del indice de condicion invalidas",
        detalle);
  }
}
