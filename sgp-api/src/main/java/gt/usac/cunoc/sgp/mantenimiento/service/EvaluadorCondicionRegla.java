package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Evalúa la {@code condicion} de una regla de mantenimiento contra un elemento de la inspección
 * (HU-046, RN-MTO-02). La condición es un dato, no código: {@code {"indicador", "operador",
 * "umbral"}}, con los operadores {@code IGUAL}, {@code DISTINTO}, {@code MAYOR_O_IGUAL}, {@code
 * MENOR_O_IGUAL} y {@code CONTIENE}.
 *
 * <p>Una condición que no se puede evaluar —indicador sin responder, tipos incompatibles u operador
 * desconocido— se trata como «no se cumple», nunca como error: una regla defectuosa no debe impedir
 * que se evalúen las demás.
 */
@Component
public class EvaluadorCondicionRegla {

  private static final Logger log = LoggerFactory.getLogger(EvaluadorCondicionRegla.class);

  /** Escala de severidad del esquema SIECA, de menor a mayor gravedad. */
  static final List<String> ESCALA_SEVERIDAD = List.of("NO_PRESENTA", "LEVE", "MODERADO", "SEVERO");

  /**
   * @param condicion la condición de la regla
   * @param elemento el elemento de la inspección donde se busca el indicador
   * @return el valor encontrado si la condición se cumple; vacío si no se cumple o no se puede
   *     evaluar
   */
  public Optional<JsonNode> evaluar(JsonNode condicion, JsonNode elemento) {
    if (condicion == null || !condicion.isObject() || elemento == null || !elemento.isObject()) {
      return Optional.empty();
    }
    String indicador = condicion.path("indicador").asText("");
    String operador = condicion.path("operador").asText("");
    JsonNode umbral = condicion.get("umbral");
    if (indicador.isBlank() || umbral == null || umbral.isNull()) {
      log.warn("Condición de regla incompleta: {}", condicion);
      return Optional.empty();
    }

    JsonNode valor = elemento.get(indicador);
    if (valor == null || valor.isNull()) {
      // Sin responder no es lo mismo que false ni que cero: no dispara la regla.
      return Optional.empty();
    }

    Boolean cumple =
        switch (operador) {
          case "IGUAL" -> esIgual(valor, umbral);
          case "DISTINTO" -> aplicar(esIgual(valor, umbral), igual -> !igual);
          case "MAYOR_O_IGUAL" -> aplicar(comparar(valor, umbral), orden -> orden >= 0);
          case "MENOR_O_IGUAL" -> aplicar(comparar(valor, umbral), orden -> orden <= 0);
          case "CONTIENE" -> contiene(valor, umbral);
          default -> {
            log.warn("Operador de regla desconocido '{}' en {}", operador, condicion);
            yield null;
          }
        };
    if (cumple == null) {
      log.warn(
          "La condición {} no es comparable con el valor {} del indicador '{}'",
          condicion,
          valor,
          indicador);
      return Optional.empty();
    }
    return cumple ? Optional.of(valor) : Optional.empty();
  }

  private static Boolean esIgual(JsonNode valor, JsonNode umbral) {
    if (valor.isBoolean() && umbral.isBoolean()) {
      return valor.booleanValue() == umbral.booleanValue();
    }
    if (valor.isNumber() && umbral.isNumber()) {
      return valor.decimalValue().compareTo(umbral.decimalValue()) == 0;
    }
    if (valor.isTextual() && umbral.isTextual()) {
      return valor.textValue().equals(umbral.textValue());
    }
    return null;
  }

  /** Orden del valor respecto al umbral: números, o textos de la escala de severidad. */
  private static Integer comparar(JsonNode valor, JsonNode umbral) {
    if (valor.isNumber() && umbral.isNumber()) {
      return valor.decimalValue().compareTo(umbral.decimalValue());
    }
    if (valor.isTextual() && umbral.isTextual()) {
      int posicionValor = ESCALA_SEVERIDAD.indexOf(valor.textValue());
      int posicionUmbral = ESCALA_SEVERIDAD.indexOf(umbral.textValue());
      if (posicionValor >= 0 && posicionUmbral >= 0) {
        return Integer.compare(posicionValor, posicionUmbral);
      }
    }
    return null;
  }

  /** Aplica la prueba al resultado, conservando «no comparable» como nulo. */
  private static <T> Boolean aplicar(T resultado, Predicate<T> prueba) {
    return resultado == null ? null : prueba.test(resultado);
  }

  private static Boolean contiene(JsonNode valor, JsonNode umbral) {
    if (!umbral.isTextual()) {
      return null;
    }
    if (valor.isArray()) {
      for (JsonNode elemento : valor) {
        if (elemento.isTextual() && elemento.textValue().equals(umbral.textValue())) {
          return true;
        }
      }
      return false;
    }
    return null;
  }
}
