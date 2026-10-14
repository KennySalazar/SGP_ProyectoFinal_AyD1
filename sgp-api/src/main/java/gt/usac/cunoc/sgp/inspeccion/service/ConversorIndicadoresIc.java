package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConversorIndicadoresIc {

  private final AgregadorCalificacionesIc agregador;

  public ConversorIndicadoresIc(AgregadorCalificacionesIc agregador) {
    this.agregador = agregador;
  }

  public BigDecimal convertirSeveridad(JsonNode valor, JsonNode reglas, String referencia) {

    if (reglas == null || !reglas.isObject()) {
      throw error(
          "configuracion_ic_incompleta", "Faltan las reglas de evaluacion de " + referencia);
    }

    return convertirCategoria(valor, reglas.get("escala_severidad"), referencia);
  }

  public BigDecimal convertirCategoria(JsonNode valor, JsonNode equivalencias, String referencia) {

    if (valor == null || valor.isNull() || valor.isMissingNode()) {
      throw error("datos_ic_incompletos", "Falta el valor del indicador " + referencia);
    }

    if (!valor.isTextual() && !valor.isBoolean()) {
      throw error(
          "indicador_ic_invalido",
          "El indicador " + referencia + " debe contener una categoria o un booleano");
    }

    String categoria = valor.asText();

    if (equivalencias == null || !equivalencias.isObject()) {
      throw error(
          "configuracion_ic_incompleta", "Faltan las equivalencias del indicador " + referencia);
    }

    return leerCalificacion(equivalencias.get(categoria), referencia + " [" + categoria + "]");
  }

  public BigDecimal leerCalificacion(JsonNode configuracion, String referencia) {

    if (configuracion == null || configuracion.isNull() || configuracion.isMissingNode()) {
      throw error(
          "configuracion_ic_incompleta", "Falta configurar la calificacion de " + referencia);
    }

    if (!configuracion.isNumber()) {
      throw error(
          "configuracion_ic_invalida",
          "La calificacion configurada de " + referencia + " debe ser numerica");
    }

    BigDecimal calificacion = configuracion.decimalValue();
    agregador.validarCalificacion(calificacion, referencia);

    return calificacion;
  }

  private ApiException error(String codigo, String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        codigo,
        "Indicadores del indice de condicion invalidos",
        detalle);
  }
}
