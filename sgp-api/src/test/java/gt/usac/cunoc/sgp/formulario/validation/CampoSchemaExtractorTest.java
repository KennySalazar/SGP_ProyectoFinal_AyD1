package gt.usac.cunoc.sgp.formulario.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CampoSchemaExtractorTest {

  private CampoSchemaExtractor extractor;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    extractor = new CampoSchemaExtractor();
    objectMapper = new ObjectMapper();
  }

  @Test
  void extraePropiedadesAnidadas() throws Exception {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "datos_generales": {
              "type": "object",
              "properties": {
                "nombre_puente": { "type": "string" },
                "ruta": { "type": "string" }
              }
            },
            "superestructura": {
              "type": "object",
              "properties": {
                "vigas": {
                  "type": "object",
                  "properties": {
                    "estado": { "type": "integer" }
                  }
                }
              }
            }
          }
        }
        """;
    JsonNode node = objectMapper.readTree(json);
    List<String> campos = extractor.extraerCampos(node);

    assertThat(campos)
        .containsExactly(
            "/datos_generales/nombre_puente",
            "/datos_generales/ruta",
            "/superestructura/vigas/estado");
  }

  @Test
  void retornaVacioParaEsquemaSinPropiedades() throws Exception {
    JsonNode node = objectMapper.readTree("{}");
    List<String> campos = extractor.extraerCampos(node);

    assertThat(campos).isEmpty();
  }

  @Test
  void retornaVacioParaNulo() {
    List<String> campos = extractor.extraerCampos(null);
    assertThat(campos).isEmpty();
  }
}
