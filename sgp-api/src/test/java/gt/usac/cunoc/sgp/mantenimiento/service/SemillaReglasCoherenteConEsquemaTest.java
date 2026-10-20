package gt.usac.cunoc.sgp.mantenimiento.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Cada regla del catálogo provisional debe poder evaluarse: su umbral tiene que ser del mismo tipo
 * que el indicador que mira en el esquema SIECA. Una regla incomparable nunca se cumpliría y nadie
 * lo notaría (HU-045, HU-046).
 */
class SemillaReglasCoherenteConEsquemaTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void elUmbralDeCadaReglaEsDelTipoDelIndicador() throws Exception {
    JsonNode esquema = leer("formulario/sieca-v1/esquema.schema.json");
    JsonNode reglas = leer("mantenimiento/reglas-sieca-v1-semilla.json");

    List<String> incoherentes = new ArrayList<>();
    for (JsonNode regla : reglas) {
      String problema = revisar(esquema, regla);
      if (problema != null) {
        incoherentes.add(regla.path("codigo").asText() + ": " + problema);
      }
    }

    assertThat(reglas).isNotEmpty();
    assertThat(incoherentes).isEmpty();
  }

  private static String revisar(JsonNode esquema, JsonNode regla) {
    JsonNode condicion = regla.path("condicion");
    JsonNode indicador = indicadorDelEsquema(esquema, regla);
    if (indicador == null) {
      return "el indicador no existe en el esquema";
    }
    String operador = condicion.path("operador").asText();
    JsonNode umbral = condicion.get("umbral");
    if (umbral == null || umbral.isNull()) {
      return "no tiene umbral";
    }

    if (indicador.has("enum")) {
      if (!umbral.isTextual() || !contiene(indicador.get("enum"), umbral.textValue())) {
        return "el umbral " + umbral + " no es uno de los valores " + indicador.get("enum");
      }
      boolean comparaEscala = operador.equals("MAYOR_O_IGUAL");
      if (comparaEscala && !EvaluadorCondicionRegla.ESCALA_SEVERIDAD.contains(umbral.textValue())) {
        return "MAYOR_O_IGUAL solo aplica a la escala de severidad";
      }
      return operador.equals("IGUAL") || comparaEscala ? null : "operador " + operador;
    }
    return switch (indicador.path("type").asText()) {
      case "boolean" ->
          umbral.isBoolean() && operador.equals("IGUAL")
              ? null
              : "un booleano se compara con IGUAL y un true o false";
      case "number", "integer" ->
          umbral.isNumber() && !operador.equals("CONTIENE")
              ? null
              : "un número se compara con un umbral numérico, no con " + umbral;
      case "array" -> {
        JsonNode valores = indicador.path("items").path("enum");
        yield operador.equals("CONTIENE")
                && umbral.isTextual()
                && contiene(valores, umbral.textValue())
            ? null
            : "una lista se consulta con CONTIENE y un valor de " + valores;
      }
      default -> "tipo de indicador no soportado: " + indicador.path("type").asText();
    };
  }

  /** Recorre el esquema siguiendo el {@code elemento_ref} y devuelve el indicador de la regla. */
  private static JsonNode indicadorDelEsquema(JsonNode esquema, JsonNode regla) {
    JsonNode actual = esquema;
    for (String segmento : regla.path("elemento_ref").asText().substring(1).split("/")) {
      actual = entrarEnItems(esquema, actual);
      actual = resolver(esquema, actual.path("properties").get(segmento));
      if (actual == null) {
        return null;
      }
    }
    actual = entrarEnItems(esquema, actual);
    return resolver(
        esquema, actual.path("properties").get(regla.path("condicion").path("indicador").asText()));
  }

  private static JsonNode entrarEnItems(JsonNode raiz, JsonNode nodo) {
    return nodo.has("items") ? resolver(raiz, nodo.get("items")) : nodo;
  }

  private static JsonNode resolver(JsonNode raiz, JsonNode nodo) {
    JsonNode actual = nodo;
    int vueltas = 0;
    while (actual != null && actual.hasNonNull("$ref") && vueltas++ < 32) {
      String ref = actual.get("$ref").asText();
      actual = ref.startsWith("#/") ? raiz.at(ref.substring(1)) : null;
    }
    return actual == null || actual.isMissingNode() ? null : actual;
  }

  private static boolean contiene(JsonNode valores, String valor) {
    for (JsonNode candidato : valores) {
      if (candidato.isTextual() && candidato.textValue().equals(valor)) {
        return true;
      }
    }
    return false;
  }

  private static JsonNode leer(String recurso) throws Exception {
    try (InputStream is = new ClassPathResource(recurso).getInputStream()) {
      return MAPPER.readTree(is);
    }
  }
}
