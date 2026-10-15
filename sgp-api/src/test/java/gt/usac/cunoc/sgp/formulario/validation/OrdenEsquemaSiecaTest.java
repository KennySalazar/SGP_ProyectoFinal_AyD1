package gt.usac.cunoc.sgp.formulario.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PostgreSQL guarda el esquema como JSONB, que reordena las claves de cada objeto. El orden en que
 * el inspector ve las secciones y los campos viaja entonces en {@code x-sgp-orden}, una lista
 * (las listas sí conservan el orden) que debe nombrar todas las propiedades del objeto.
 */
class OrdenEsquemaSiecaTest {

  private static JsonNode esquema;

  @BeforeAll
  static void cargarEsquema() throws Exception {
    try (InputStream in =
        OrdenEsquemaSiecaTest.class.getResourceAsStream("/formulario/sieca-v1/esquema.schema.json")) {
      esquema = new ObjectMapper().readTree(in);
    }
  }

  @Test
  void todoObjetoDelFormularioDeclaraElOrdenDeSusPropiedades() {
    List<String> incumplimientos = new ArrayList<>();
    revisar(esquema, "#", false, incumplimientos);

    assertThat(incumplimientos).isEmpty();
  }

  private void revisar(JsonNode nodo, String ruta, boolean condicional, List<String> fallos) {
    if (nodo == null || !nodo.isContainerNode()) {
      return;
    }
    if (nodo.isArray()) {
      for (int i = 0; i < nodo.size(); i++) {
        revisar(nodo.get(i), ruta + "[" + i + "]", condicional, fallos);
      }
      return;
    }

    JsonNode propiedades = nodo.get("properties");
    boolean esObjetoDelFormulario =
        !condicional
            && propiedades != null
            && propiedades.isObject()
            && propiedades.size() > 1
            && "object".equals(nodo.path("type").asText());
    if (esObjetoDelFormulario) {
      comprobarOrden(nodo, propiedades, ruta, fallos);
    }

    nodo.fields()
        .forEachRemaining(
            entrada -> {
              String clave = entrada.getKey();
              boolean esCondicion =
                  List.of("if", "then", "else", "not", "contains").contains(clave);
              if ("properties".equals(clave)) {
                entrada
                    .getValue()
                    .fields()
                    .forEachRemaining(
                        propiedad ->
                            revisar(
                                propiedad.getValue(),
                                ruta + "/" + propiedad.getKey(),
                                condicional,
                                fallos));
              } else {
                revisar(entrada.getValue(), ruta + "/" + clave, condicional || esCondicion, fallos);
              }
            });
  }

  private void comprobarOrden(
      JsonNode nodo, JsonNode propiedades, String ruta, List<String> fallos) {
    JsonNode orden = nodo.get("x-sgp-orden");
    if (orden == null || !orden.isArray()) {
      fallos.add(ruta + ": falta x-sgp-orden");
      return;
    }
    List<String> declaradas = new ArrayList<>();
    orden.forEach(clave -> declaradas.add(clave.asText()));
    List<String> reales = new ArrayList<>();
    propiedades.fieldNames().forEachRemaining(reales::add);

    if (declaradas.size() != reales.size() || !declaradas.containsAll(reales)) {
      fallos.add(ruta + ": x-sgp-orden " + declaradas + " no coincide con las propiedades " + reales);
    }
  }
}
