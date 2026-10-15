package gt.usac.cunoc.sgp.formulario.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Un elemento cuya existencia es «NO_APLICA» o «AUSENTE» no tiene qué medir ni calificar: en el
 * formulario en papel su fila queda en blanco. El esquema lo expresa con {@code else:
 * {maxProperties: 1}} en cada condición «si está presente», de modo que solo quede la existencia.
 */
class ReglaExistenciaEsquemaSiecaTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static JsonNode esquema;

  @BeforeAll
  static void cargarEsquema() throws Exception {
    try (InputStream in =
        ReglaExistenciaEsquemaSiecaTest.class.getResourceAsStream(
            "/formulario/sieca-v1/esquema.schema.json")) {
      esquema = JSON.readTree(in);
    }
  }

  @Test
  void todaCondicionDeExistenciaProhibeTodoLoDemasSiElElementoNoEstaPresente() {
    List<String> incumplimientos = new ArrayList<>();
    int condiciones = revisar(esquema, "#", incumplimientos);

    assertThat(condiciones).as("condiciones «si está presente» del esquema").isEqualTo(14);
    assertThat(incumplimientos).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "elementoConcreto",
        "muroGravedad",
        "aleton",
        "apoyos",
        "losaRodamiento",
        "elementoSuperConcreto",
        "elementoSuperAcero",
        "junta",
        "losaAcceso",
        "obraProteccion"
      })
  void unElementoNoAplicaOAusenteSoloAceptaSuExistencia(String definicion) throws Exception {
    JsonSchema validador = validadorDe(definicion);
    JsonNode propiedades = esquema.get("$defs").get(definicion).get("properties");

    for (String existencia : List.of("NO_APLICA", "AUSENTE")) {
      assertThat(validador.validate(JSON.readTree("{\"existencia\":\"" + existencia + "\"}")))
          .as("%s solo con existencia %s", definicion, existencia)
          .isEmpty();

      Iterator<String> campos = propiedades.fieldNames();
      while (campos.hasNext()) {
        String campo = campos.next();
        if (campo.equals("existencia")) {
          continue;
        }
        ObjectNode elemento = JSON.createObjectNode().put("existencia", existencia);
        elemento.put(campo, 1);

        Set<ValidationMessage> errores = validador.validate(elemento);
        assertThat(errores)
            .as("%s con existencia %s no admite «%s»", definicion, existencia, campo)
            .anyMatch(error -> "maxProperties".equals(error.getType()));
      }
    }
  }

  /** Recorre el esquema y devuelve cuántas condiciones «si está presente» encontró. */
  private int revisar(JsonNode nodo, String ruta, List<String> fallos) {
    int condiciones = 0;
    if (nodo == null || !nodo.isContainerNode()) {
      return 0;
    }
    if (nodo.isArray()) {
      for (int i = 0; i < nodo.size(); i++) {
        condiciones += revisar(nodo.get(i), ruta + "[" + i + "]", fallos);
      }
      return condiciones;
    }

    if (esCondicionDeExistencia(nodo.get("if"))) {
      condiciones++;
      JsonNode sino = nodo.get("else");
      if (sino == null || !sino.equals(JSON.createObjectNode().put("maxProperties", 1))) {
        fallos.add(ruta + ": «si está presente» sin else {maxProperties: 1}");
      }
    }

    Iterator<java.util.Map.Entry<String, JsonNode>> campos = nodo.fields();
    while (campos.hasNext()) {
      var campo = campos.next();
      condiciones += revisar(campo.getValue(), ruta + "/" + campo.getKey(), fallos);
    }
    return condiciones;
  }

  private static boolean esCondicionDeExistencia(JsonNode condicion) {
    return condicion != null
        && condicion.size() == 1
        && "#/$defs/siPresente".equals(condicion.path("$ref").asText());
  }

  private static JsonSchema validadorDe(String definicion) {
    ObjectNode envoltorio = JSON.createObjectNode();
    envoltorio.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    envoltorio.put("$ref", "#/$defs/" + definicion);
    envoltorio.set("$defs", esquema.get("$defs"));
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(envoltorio);
  }
}
