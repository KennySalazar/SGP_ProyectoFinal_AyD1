package gt.usac.cunoc.sgp.formulario.validation;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class ValidadorCalibracionIc {
  private ValidadorCalibracionIc() {}

  public static void validar(JsonNode catalogo) {
    var errores = new ArrayList<Map<String, String>>();
    if (!catalogo.isObject()) {
      agregar(errores, "reglasEvaluacionIc", "Debe ser un objeto.");
    } else if (catalogo.has("reglas_evaluacion_ic")) {
      var reglas = catalogo.path("reglas_evaluacion_ic");
      if (!reglas.isObject()) {
        agregar(errores, "reglasEvaluacionIc.reglas_evaluacion_ic", "Debe ser un objeto.");
      } else {
        var escala = reglas.path("escala_severidad");
        if (!escala.isMissingNode()) {
          for (String nivel : List.of("NO_PRESENTA", "LEVE", "MODERADO", "SEVERO")) {
            calificacion(escala.path(nivel), "escala_severidad." + nivel, errores);
          }
          if (escala.path("NO_PRESENTA").isNumber()
              && escala.path("NO_PRESENTA").decimalValue().signum() != 0) {
            agregar(errores, "escala_severidad.NO_PRESENTA", "Debe ser cero.");
          }
        }
        var rangos = reglas.path("rangos_estado");
        if (!rangos.isMissingNode()) {
          if (!rangos.path("MALO").isNumber()
              || !rangos.path("REGULAR").isNumber()
              || !rangos.path("BUENO").isNumber()
              || rangos.path("MALO").decimalValue().signum() != 0
              || rangos.path("REGULAR").decimalValue().signum() <= 0
              || rangos
                      .path("BUENO")
                      .decimalValue()
                      .compareTo(rangos.path("REGULAR").decimalValue())
                  <= 0
              || rangos.path("BUENO").decimalValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            agregar(errores, "rangos_estado", "Debe cumplir MALO = 0 < REGULAR < BUENO <= 100.");
          }
        }
        var agregacion = reglas.path("agregacion");
        for (String campo : List.of("indicadores_de_un_elemento", "instancias_repetibles")) {
          if (agregacion.has(campo)
              && (!agregacion.path(campo).isTextual()
                  || !Set.of("MAXIMO", "PROMEDIO").contains(agregacion.path(campo).asText()))) {
            agregar(errores, "agregacion." + campo, "Debe ser MAXIMO o PROMEDIO.");
          }
        }
        for (String campo :
            List.of("elementos_ausentes_portantes", "elementos_ausentes_no_portantes")) {
          if (agregacion.has(campo))
            calificacion(agregacion.path(campo), "agregacion." + campo, errores);
        }
        var conversiones = reglas.path("conversion_indicadores");
        conversiones
            .path("booleanos")
            .fields()
            .forEachRemaining(
                e ->
                    calificacion(
                        e.getValue(), "conversion_indicadores.booleanos." + e.getKey(), errores));
        conversiones
            .path("enumerados")
            .fields()
            .forEachRemaining(
                familia ->
                    familia
                        .getValue()
                        .fields()
                        .forEachRemaining(
                            e ->
                                calificacion(
                                    e.getValue(),
                                    "conversion_indicadores.enumerados."
                                        + familia.getKey()
                                        + "."
                                        + e.getKey(),
                                    errores)));
        conversiones
            .path("numericos")
            .fields()
            .forEachRemaining(
                e -> {
                  String campo = e.getValue().has("umbrales_m") ? "umbrales_m" : "umbrales";
                  JsonNode valores = e.getValue().path(campo);
                  var leve = valores.path("LEVE");
                  var moderado = valores.path("MODERADO");
                  var severo = valores.path("SEVERO");
                  if (!leve.isNumber()
                      || !moderado.isNumber()
                      || !severo.isNumber()
                      || leve.decimalValue().signum() <= 0
                      || moderado.decimalValue().compareTo(leve.decimalValue()) <= 0
                      || severo.decimalValue().compareTo(moderado.decimalValue()) <= 0) {
                    agregar(
                        errores,
                        "conversion_indicadores.numericos." + e.getKey() + "." + campo,
                        "Debe cumplir 0 < LEVE < MODERADO < SEVERO.");
                  }
                });
        if (reglas.has("reglas_anulacion")) {
          try {
            ValidadorReglasCriticasIc.validarCatalogo(reglas, catalogo);
          } catch (ApiException e) {
            agregar(errores, "reglas_anulacion", e.getMessage());
          }
        }
      }
    }
    if (!errores.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "calibracion_ic_invalida",
          "Calibracion invalida",
          "Revise los valores de la calibracion.",
          errores);
    }
  }

  private static void calificacion(
      JsonNode valor, String campo, List<Map<String, String>> errores) {
    if (!valor.isNumber()
        || valor.decimalValue().signum() < 0
        || valor.decimalValue().compareTo(BigDecimal.valueOf(4)) > 0) {
      agregar(errores, campo, "Requiere un numero entre 0 y 4.");
    }
  }

  private static void agregar(List<Map<String, String>> errores, String campo, String mensaje) {
    errores.add(Map.of("campo", campo, "mensaje", mensaje));
  }
}
