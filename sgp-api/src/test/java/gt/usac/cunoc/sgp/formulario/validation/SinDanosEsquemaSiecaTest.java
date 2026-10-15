package gt.usac.cunoc.sgp.formulario.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * El formulario ofrece «Sin daños en este elemento» (RN-INS-11). Qué significa «sano» depende del
 * indicador (la severidad es NO_PRESENTA; «falta elemento» es false; «junta limpia» es true), así
 * que el esquema lo declara con {@code x-sgp-sin-danos} en cada indicador de daño. El cliente no
 * conoce ningún campo en particular: marca como sano lo que el esquema declara.
 */
class SinDanosEsquemaSiecaTest {

  private static final String ANOTACION = "x-sgp-sin-danos";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static JsonNode esquema;

  @BeforeAll
  static void cargarEsquema() throws Exception {
    try (InputStream in =
        SinDanosEsquemaSiecaTest.class.getResourceAsStream(
            "/formulario/sieca-v1/esquema.schema.json")) {
      esquema = JSON.readTree(in);
    }
  }

  @Test
  void elValorSanoDeCadaIndicadorEsValidoParaSuPropioCampo() {
    List<String> incumplimientos = new ArrayList<>();
    int anotaciones = revisarValoresSanos(esquema, "#", incumplimientos);

    assertThat(anotaciones).as("indicadores con x-sgp-sin-danos").isEqualTo(12);
    assertThat(incumplimientos).isEmpty();
  }

  @Test
  void todoElementoEvaluableTieneAlMenosUnIndicadorDeDano() {
    List<String> elementos = new ArrayList<>();
    List<String> sinIndicadores = new ArrayList<>();
    recorrerElementos(esquema, "", elementos, sinIndicadores);

    assertThat(elementos).as("elementos evaluables").hasSize(45);
    assertThat(sinIndicadores).isEmpty();
  }

  private int revisarValoresSanos(JsonNode nodo, String ruta, List<String> fallos) {
    int anotaciones = 0;
    if (nodo == null || !nodo.isContainerNode()) {
      return 0;
    }
    if (nodo.isArray()) {
      for (int i = 0; i < nodo.size(); i++) {
        anotaciones += revisarValoresSanos(nodo.get(i), ruta + "[" + i + "]", fallos);
      }
      return anotaciones;
    }

    if (nodo.has(ANOTACION)) {
      anotaciones++;
      var errores = validadorDe(nodo).validate(nodo.get(ANOTACION));
      if (!errores.isEmpty()) {
        fallos.add(ruta + ": " + nodo.get(ANOTACION) + " no es válido para su campo: " + errores);
      }
    }

    Iterator<Map.Entry<String, JsonNode>> campos = nodo.fields();
    while (campos.hasNext()) {
      var campo = campos.next();
      anotaciones += revisarValoresSanos(campo.getValue(), ruta + "/" + campo.getKey(), fallos);
    }
    return anotaciones;
  }

  /** Recorre los objetos del formulario y revisa los marcados como elementos evaluables. */
  private void recorrerElementos(
      JsonNode entrada, String ruta, List<String> elementos, List<String> sinIndicadores) {
    JsonNode nodo = resolver(entrada);
    if (nodo.path("x-sgp-evaluable").asBoolean(false)) {
      elementos.add(ruta);
      if (!tieneIndicadores(nodo)) {
        sinIndicadores.add(ruta);
      }
    }

    JsonNode propiedades = nodo.get("properties");
    if (propiedades != null && "object".equals(nodo.path("type").asText())) {
      Iterator<Map.Entry<String, JsonNode>> campos = propiedades.fields();
      while (campos.hasNext()) {
        var campo = campos.next();
        recorrerElementos(campo.getValue(), ruta + "/" + campo.getKey(), elementos, sinIndicadores);
      }
    } else if ("array".equals(nodo.path("type").asText()) && nodo.has("items")) {
      recorrerElementos(nodo.get("items"), ruta + "[]", elementos, sinIndicadores);
    }
  }

  private boolean tieneIndicadores(JsonNode elemento) {
    JsonNode propiedades = elemento.get("properties");
    if (propiedades == null) {
      return false;
    }
    Iterator<JsonNode> hijos = propiedades.elements();
    while (hijos.hasNext()) {
      if (resolver(hijos.next()).has(ANOTACION)) {
        return true;
      }
    }
    return false;
  }

  /** Expande un {@code $ref} local a {@code $defs}; las claves vecinas prevalecen. */
  private JsonNode resolver(JsonNode nodo) {
    if (!nodo.has("$ref")) {
      return nodo;
    }
    String definicion = nodo.get("$ref").asText().substring("#/$defs/".length());
    ObjectNode resultado = ((ObjectNode) esquema.get("$defs").get(definicion)).deepCopy();
    nodo.fields()
        .forEachRemaining(
            campo -> {
              if (!"$ref".equals(campo.getKey())) {
                resultado.set(campo.getKey(), campo.getValue());
              }
            });
    return resultado;
  }

  private static JsonSchema validadorDe(JsonNode campo) {
    ObjectNode envoltorio = ((ObjectNode) campo).deepCopy();
    envoltorio.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    envoltorio.set("$defs", esquema.get("$defs"));
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(envoltorio);
  }
}
