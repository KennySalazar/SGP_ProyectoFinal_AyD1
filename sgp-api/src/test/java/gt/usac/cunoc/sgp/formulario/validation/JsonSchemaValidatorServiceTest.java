package gt.usac.cunoc.sgp.formulario.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class JsonSchemaValidatorServiceTest {

  private JsonSchemaValidatorService service;
  private ObjectMapper objectMapper;

  private static final String ESQUEMA_INSPECCION =
      """
      {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "additionalProperties": false,
        "required": ["identificacion", "subestructura"],
        "properties": {
          "identificacion": {
            "type": "object",
            "additionalProperties": false,
            "required": ["nombre_puente", "fecha_inspeccion"],
            "properties": {
              "nombre_puente": { "type": "string" },
              "fecha_inspeccion": { "type": "string", "format": "date" }
            }
          },
          "subestructura": {
            "type": "object",
            "additionalProperties": false,
            "required": ["estribo_entrada"],
            "properties": {
              "estribo_entrada": {
                "type": "object",
                "additionalProperties": false,
                "required": ["material"],
                "properties": {
                  "material": {
                    "type": "string",
                    "enum": ["CONCRETO", "MAMPOSTERIA", "ACERO"]
                  },
                  "grietas_mm": {
                    "type": "number",
                    "minimum": 0,
                    "maximum": 100
                  }
                }
              }
            }
          }
        }
      }
      """;

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

  @Test
  void aceptaDatosCompletosYValidosEnModoEstricto() throws Exception {
    JsonNode esquema = objectMapper.readTree(ESQUEMA_INSPECCION);
    JsonNode datos =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente Los Esclavos",
                "fecha_inspeccion": "2026-10-15"
              },
              "subestructura": {
                "estribo_entrada": {
                  "material": "CONCRETO",
                  "grietas_mm": 5.2
                }
              }
            }
            """);

    assertThatCode(() -> service.validarRespuestas(esquema, datos, false))
        .doesNotThrowAnyException();
  }

  @Test
  void rechazaTipoOValorIncorrectoConFormatoRfc7807YListaErrores() throws Exception {
    JsonNode esquema = objectMapper.readTree(ESQUEMA_INSPECCION);
    // material con entero en vez de string, o enum inválido
    JsonNode datos =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente Los Esclavos",
                "fecha_inspeccion": "2026-10-15"
              },
              "subestructura": {
                "estribo_entrada": {
                  "material": "PLASTICO_INVALIDO",
                  "grietas_mm": -10
                }
              }
            }
            """);

    assertThatThrownBy(() -> service.validarRespuestas(esquema, datos, false))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("datos_formulario_invalidos");
              assertThat(apiEx.getErrores()).isNotEmpty();

              // Debe indicar el campo exacto como subestructura.estribo_entrada.material
              boolean enumEncontrado =
                  apiEx.getErrores().stream()
                      .anyMatch(
                          e ->
                              "subestructura.estribo_entrada.material".equals(e.get("campo"))
                                  && e.get("mensaje").contains("Valor no permitido"));
              boolean rangoEncontrado =
                  apiEx.getErrores().stream()
                      .anyMatch(
                          e ->
                              "subestructura.estribo_entrada.grietas_mm".equals(e.get("campo"))
                                  && e.get("mensaje").contains("menor que el mínimo"));

              assertThat(enumEncontrado).as("error de enum en material").isTrue();
              assertThat(rangoEncontrado).as("error de rango en grietas_mm").isTrue();
            });
  }

  @Test
  void rechazaCamposDesconocidos() throws Exception {
    JsonNode esquema = objectMapper.readTree(ESQUEMA_INSPECCION);
    JsonNode datos =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente Los Esclavos",
                "fecha_inspeccion": "2026-10-15",
                "campo_inexistente": "valor_fantasma"
              },
              "subestructura": {
                "estribo_entrada": {
                  "material": "CONCRETO"
                }
              }
            }
            """);

    assertThatThrownBy(() -> service.validarRespuestas(esquema, datos, false))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getErrores()).isNotEmpty();
              boolean campoDesconocido =
                  apiEx.getErrores().stream()
                      .anyMatch(
                          e ->
                              "identificacion.campo_inexistente".equals(e.get("campo"))
                                  && e.get("mensaje").contains("no está permitido"));
              assertThat(campoDesconocido).isTrue();
            });
  }

  @Test
  void borradorAceptaDatosIncompletosSiempreQueSeanValidos() throws Exception {
    JsonNode esquema = objectMapper.readTree(ESQUEMA_INSPECCION);
    // Solo se envía una sección y un campo, omitiendo subestructura y fecha_inspeccion
    JsonNode datosBorrador =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente Los Esclavos"
              }
            }
            """);

    // En modo borrador (esBorrador = true) no debe fallar por campos obligatorios faltantes
    assertThatCode(() -> service.validarRespuestas(esquema, datosBorrador, true))
        .doesNotThrowAnyException();
  }

  @Test
  void borradorRechazaDatosInvalidosOCamposDesconocidos() throws Exception {
    JsonNode esquema = objectMapper.readTree(ESQUEMA_INSPECCION);
    JsonNode datosBorradorInvalido =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente",
                "campo_desconocido": 123
              }
            }
            """);

    assertThatThrownBy(() -> service.validarRespuestas(esquema, datosBorradorInvalido, true))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getErrores()).isNotEmpty();
              assertThat(
                      apiEx.getErrores().stream()
                          .anyMatch(e -> "identificacion.campo_desconocido".equals(e.get("campo"))))
                  .isTrue();
            });
  }

  @Test
  void enviarExigeCamposObligatorios() throws Exception {
    JsonNode esquema = objectMapper.readTree(ESQUEMA_INSPECCION);
    JsonNode datosIncompletos =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente Los Esclavos"
              }
            }
            """);

    // Al enviar (esBorrador = false), se exigen todos los campos obligatorios
    assertThatThrownBy(() -> service.validarRespuestas(esquema, datosIncompletos, false))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getErrores()).isNotEmpty();
              // Debe exigir fecha_inspeccion y subestructura
              boolean fechaFaltante =
                  apiEx.getErrores().stream()
                      .anyMatch(e -> "identificacion.fecha_inspeccion".equals(e.get("campo")));
              boolean subestructuraFaltante =
                  apiEx.getErrores().stream()
                      .anyMatch(e -> "subestructura".equals(e.get("campo")));

              assertThat(fechaFaltante).as("falta identificacion.fecha_inspeccion").isTrue();
              assertThat(subestructuraFaltante).as("falta subestructura").isTrue();
            });
  }

  @Test
  void validacionDeInspeccionCompletaTardaMenosDe500Ms() throws Exception {
    JsonNode esquemaSieca;
    JsonNode ejemploInspeccion;
    try (InputStream in = getClass().getResourceAsStream("/formulario/sieca-v1/esquema.schema.json")) {
      esquemaSieca = objectMapper.readTree(in);
    }
    try (InputStream in = getClass().getResourceAsStream("/formulario/sieca-v1/ejemplo-inspeccion.json")) {
      ejemploInspeccion = objectMapper.readTree(in);
    }

    // Calentamiento
    service.validarRespuestas(esquemaSieca, ejemploInspeccion, false);

    // Medición de 50 iteraciones
    long inicio = System.currentTimeMillis();
    for (int i = 0; i < 50; i++) {
      service.validarRespuestas(esquemaSieca, ejemploInspeccion, false);
    }
    long totalMs = System.currentTimeMillis() - inicio;
    double promedioMs = (double) totalMs / 50.0;

    // RNF-01: percentil 95 < 500 ms; con caché precompilada el promedio suele ser < 10 ms
    assertThat(promedioMs).isLessThan(500.0);
  }
}
