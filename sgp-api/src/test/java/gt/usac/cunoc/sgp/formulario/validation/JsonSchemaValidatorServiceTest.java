package gt.usac.cunoc.sgp.formulario.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class JsonSchemaValidatorServiceTest {

  private JsonSchemaValidatorService service;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    service = new JsonSchemaValidatorService();
    objectMapper = new ObjectMapper();
  }

  @Test
  void rechazaEsquemaNulo() {
    assertThatThrownBy(() -> service.validarEsquema(null))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("esquema_invalido");
            });
  }

  @Test
  void rechazaEsquemaQueNoEsObjeto() throws Exception {
    JsonNode arrayNode = objectMapper.readTree("[]");
    assertThatThrownBy(() -> service.validarEsquema(arrayNode))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("esquema_invalido");
            });
  }

  @Test
  void aceptaEsquemaValido() throws Exception {
    String schemaJson =
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "nombre": { "type": "string" },
            "edad": { "type": "integer", "minimum": 0 }
          },
          "required": ["nombre"]
        }
        """;
    JsonNode node = objectMapper.readTree(schemaJson);
    service.validarEsquema(node);
  }

  @Test
  void rechazaEsquemaConTipoInvalido() throws Exception {
    String invalidSchemaJson =
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "tipo_inexistente_invalido"
        }
        """;
    JsonNode node = objectMapper.readTree(invalidSchemaJson);
    assertThatThrownBy(() -> service.validarEsquema(node))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("esquema_invalido");
              assertThat(apiEx.getMessage()).contains("$.type");
            });
  }
}
