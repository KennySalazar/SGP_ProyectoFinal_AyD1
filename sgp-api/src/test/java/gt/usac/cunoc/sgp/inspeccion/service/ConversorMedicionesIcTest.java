package gt.usac.cunoc.sgp.inspeccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConversorMedicionesIcTest {

  private final ObjectMapper json = new ObjectMapper();

  private ConversorMedicionesIc service;
  private ObjectNode reglas;
  private ObjectNode umbrales;

  @BeforeEach
  void preparar() throws Exception {
    var agregador = new AgregadorCalificacionesIc();
    var conversor = new ConversorIndicadoresIc(agregador);
    service = new ConversorMedicionesIc(conversor);

    reglas =
        (ObjectNode)
            json.readTree(
                """
                                {
                                  "escala_severidad": {
                                    "NO_PRESENTA": 0,
                                    "LEVE": 1,
                                    "MODERADO": 2,
                                    "SEVERO": 4
                                  }
                                }
                                """);

    umbrales =
        (ObjectNode)
            json.readTree(
                """
                                {
                                  "LEVE": 1,
                                  "MODERADO": 3,
                                  "SEVERO": 5000
                                }
                                """);
  }

  @ParameterizedTest
  @CsvSource({"0, 0", "0.01, 1", "1, 1", "1.01, 2", "3, 2", "3.01, 4", "5000, 4"})
  void convierteMedicionesRespetandoLosLimitesInclusivos(String medicion, String esperado) {

    BigDecimal resultado =
        service.convertir(numero(medicion), umbrales, reglas, "/elemento/golpeado_longitud_m");

    assertEquals(0, new BigDecimal(esperado).compareTo(resultado));
  }

  @ParameterizedTest
  @MethodSource("valoresNoNumericos")
  void rechazaMedicionesNoNumericas(JsonNode valor) {
    verificarError(
        "medicion_ic_invalida",
        () -> service.convertir(valor, umbrales, reglas, "/elemento/medicion"));
  }

  static Stream<Arguments> valoresNoNumericos() {
    ObjectMapper mapper = new ObjectMapper();

    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of(mapper.getNodeFactory().nullNode()),
        Arguments.of(mapper.getNodeFactory().textNode("2")),
        Arguments.of(mapper.getNodeFactory().booleanNode(true)),
        Arguments.of(mapper.createObjectNode()),
        Arguments.of(mapper.createArrayNode()));
  }

  @Test
  void rechazaUnaMedicionNegativa() {
    verificarError(
        "medicion_ic_invalida",
        () -> service.convertir(numero("-0.01"), umbrales, reglas, "/elemento/medicion"));
  }

  @ParameterizedTest
  @MethodSource("umbralesNoValidos")
  void exigeUnObjetoDeUmbrales(JsonNode configuracion) {
    verificarError(
        "configuracion_ic_incompleta",
        () -> service.convertir(numero("1"), configuracion, reglas, "/elemento/medicion"));
  }

  static Stream<Arguments> umbralesNoValidos() {
    ObjectMapper mapper = new ObjectMapper();

    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of(mapper.getNodeFactory().nullNode()),
        Arguments.of(mapper.getNodeFactory().textNode("MAXIMO")),
        Arguments.of(mapper.createArrayNode()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"LEVE", "MODERADO", "SEVERO"})
  void identificaUnUmbralFaltante(String nivel) {
    umbrales.remove(nivel);

    ApiException exception =
        verificarError(
            "configuracion_ic_incompleta",
            () -> service.convertir(numero("1"), umbrales, reglas, "/elemento/medicion"));

    assertTrue(exception.getMessage().contains(nivel));
  }

  @ParameterizedTest
  @ValueSource(strings = {"LEVE", "MODERADO", "SEVERO"})
  void rechazaUnUmbralNulo(String nivel) {
    umbrales.putNull(nivel);

    verificarError(
        "configuracion_ic_incompleta",
        () -> service.convertir(numero("1"), umbrales, reglas, "/elemento/medicion"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"LEVE", "MODERADO", "SEVERO"})
  void rechazaUnUmbralNoNumerico(String nivel) {
    umbrales.put(nivel, "sin-calibrar");

    verificarError(
        "configuracion_ic_incompleta",
        () -> service.convertir(numero("1"), umbrales, reglas, "/elemento/medicion"));
  }

  @ParameterizedTest
  @CsvSource({"0, 3, 5000", "-1, 3, 5000", "1, 1, 5000", "2, 1, 5000", "1, 3, 3", "1, 3, 2"})
  void rechazaUmbralesSinOrdenEstricto(String leve, String moderado, String severo) {

    umbrales.put("LEVE", new BigDecimal(leve));
    umbrales.put("MODERADO", new BigDecimal(moderado));
    umbrales.put("SEVERO", new BigDecimal(severo));

    verificarError(
        "umbrales_ic_invalidos",
        () -> service.convertir(numero("1"), umbrales, reglas, "/elemento/medicion"));
  }

  @Test
  void rechazaUnaMedicionSuperiorAlMaximoConfigurado() {
    verificarError(
        "medicion_ic_fuera_de_umbrales",
        () -> service.convertir(numero("5000.01"), umbrales, reglas, "/elemento/medicion"));
  }

  @Test
  void utilizaLaEscalaDeLaVersionRecibida() {
    ((ObjectNode) reglas.get("escala_severidad")).put("LEVE", new BigDecimal("0.5"));

    BigDecimal resultado = service.convertir(numero("1"), umbrales, reglas, "/elemento/medicion");

    assertEquals(0, new BigDecimal("0.5").compareTo(resultado));
  }

  @Test
  void rechazaUnaSeveridadSinCalibrar() {
    ((ObjectNode) reglas.get("escala_severidad")).putNull("MODERADO");

    ApiException exception =
        verificarError(
            "configuracion_ic_incompleta",
            () -> service.convertir(numero("2"), umbrales, reglas, "/elemento/medicion"));

    assertTrue(exception.getMessage().contains("MODERADO"));
  }

  private JsonNode numero(String valor) {
    return json.getNodeFactory().numberNode(new BigDecimal(valor));
  }

  private ApiException verificarError(
      String codigo, org.junit.jupiter.api.function.Executable operacion) {

    ApiException exception = assertThrows(ApiException.class, operacion);

    assertEquals(codigo, exception.getCode());
    assertTrue(exception.getMessage().contains("/elemento/medicion"));

    return exception;
  }
}
