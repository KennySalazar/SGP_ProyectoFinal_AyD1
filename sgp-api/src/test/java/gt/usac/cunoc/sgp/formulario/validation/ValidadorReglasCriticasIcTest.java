package gt.usac.cunoc.sgp.formulario.validation;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidadorReglasCriticasIcTest {
  private final ObjectMapper json = new ObjectMapper();

  private ObjectNode reglas() throws Exception {
    return (ObjectNode)
        json.readTree(
            """
                            {"reglas_anulacion":[{"id":"RN-IC-05a","descripcion":"Socavacion critica",
                            "estado_resultante":"MALO","pendiente":null,"elementos":["/socavacion"],
                            "condicion":{"indicador":"aguas_arriba","algun_valor_en":["CON_EXPOSICION"]}}]}
                            """);
  }

  @Test
  void validaSinNecesitarDatosDeUnaInspeccion() throws Exception {
    var condiciones = ValidadorReglasCriticasIc.validar(reglas());
    assertThat(condiciones).hasSize(1);
    assertThat(condiciones.getFirst().indicadores()).containsExactly("aguas_arriba");
  }

  @ParameterizedTest
  @ValueSource(strings = {"RN-IC-05a", "RN-IC-05c"})
  void rechazaDecisionesPendientes(String id) throws Exception {
    ObjectNode reglas = reglas();
    ObjectNode regla = (ObjectNode) reglas.path("reglas_anulacion").get(0);
    regla.put("id", id).put("pendiente", "Falta confirmacion academica");
    assertThatThrownBy(() -> ValidadorReglasCriticasIc.validar(reglas))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining(id);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RN-IC-05a", "RN-IC-05c"})
  void aceptaNotasPendientesSoloConCalibracionProvisional(String id) throws Exception {
    var reglas = reglas();
    reglas.put("calibracion_provisional", true);
    ((ObjectNode) reglas.path("reglas_anulacion").get(0))
        .put("id", id)
        .put("pendiente", "Revisar con el Catedratico");
    assertThat(ValidadorReglasCriticasIc.validar(reglas)).hasSize(1);
    ((ObjectNode) reglas.path("reglas_anulacion").get(0)).put("id", "RN-IC-05b");
    assertThatThrownBy(() -> ValidadorReglasCriticasIc.validar(reglas))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void rechazaReglasNulasEIdentificadoresDuplicados() throws Exception {
    ObjectNode reglas = reglas();
    ((com.fasterxml.jackson.databind.node.ArrayNode) reglas.get("reglas_anulacion")).addNull();
    assertThatThrownBy(() -> ValidadorReglasCriticasIc.validar(reglas))
        .isInstanceOf(ApiException.class);
    ObjectNode duplicadas = reglas();
    ((com.fasterxml.jackson.databind.node.ArrayNode) duplicadas.get("reglas_anulacion"))
        .add(duplicadas.path("reglas_anulacion").get(0).deepCopy());
    assertThatThrownBy(() -> ValidadorReglasCriticasIc.validar(duplicadas))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("duplicado");
  }

  @ParameterizedTest
  @ValueSource(strings = {"sin-barra", "/", "/a//b", "/a/", "/a~2"})
  void rechazaReferenciasInvalidas(String referencia) throws Exception {
    ObjectNode reglas = reglas();
    ((ObjectNode) reglas.path("reglas_anulacion").get(0)).putArray("elementos").add(referencia);
    assertThatThrownBy(() -> ValidadorReglasCriticasIc.validar(reglas))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void rechazaCondicionesAmbiguas() throws Exception {
    ObjectNode reglas = reglas();
    ((ObjectNode) reglas.at("/reglas_anulacion/0/condicion")).put("campo", "existencia");
    assertThatThrownBy(() -> ValidadorReglasCriticasIc.validar(reglas))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("exactamente");
  }

  @Test
  void rechazaElementosEIndicadoresFueraDelCatalogo() throws Exception {
    ObjectNode reglas = reglas();
    JsonNode catalogo =
        json.readTree(
            """
                            {"elementos":[{"elemento_ref":"/otro","indicadores":["aguas_arriba"]}]}
                            """);
    assertThatThrownBy(() -> ValidadorReglasCriticasIc.validarCatalogo(reglas, catalogo))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("fuera del catalogo");
    JsonNode sinIndicador =
        json.readTree(
            """
                            {"elementos":[{"elemento_ref":"/socavacion","indicadores":["aguas_abajo"]}]}
                            """);
    assertThatThrownBy(() -> ValidadorReglasCriticasIc.validarCatalogo(reglas, sinIndicador))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("indicador fuera del catalogo");
  }

  @Test
  void validaElCatalogoRealConDecisionesResueltasSoloParaPruebas() throws Exception {
    try (var entrada = getClass().getResourceAsStream("/formulario/sieca-v1/elementos-ic.json")) {
      ObjectNode catalogo = (ObjectNode) json.readTree(entrada);
      ObjectNode reglas = (ObjectNode) catalogo.get("reglas_evaluacion_ic");
      ValidadorReglasCriticasIc.validarCatalogo(reglas, catalogo);
      reglas.put("calibracion_provisional", false);
      assertThatThrownBy(() -> ValidadorReglasCriticasIc.validarCatalogo(reglas, catalogo))
          .isInstanceOf(ApiException.class)
          .hasMessageContaining("pendiente");
      for (JsonNode regla : reglas.path("reglas_anulacion")) {
        ((ObjectNode) regla).putNull("pendiente");
      }
      ValidadorReglasCriticasIc.validarCatalogo(reglas, catalogo);
      reglas.putArray("reglas_anulacion");
      assertThatThrownBy(() -> ValidadorReglasCriticasIc.validarCatalogo(reglas, catalogo))
          .isInstanceOf(ApiException.class)
          .hasMessageContaining("RN-IC-05");
    }
  }
}
