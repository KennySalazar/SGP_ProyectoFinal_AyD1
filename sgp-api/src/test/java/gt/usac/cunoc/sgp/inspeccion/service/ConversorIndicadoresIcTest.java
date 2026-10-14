package gt.usac.cunoc.sgp.inspeccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class ConversorIndicadoresIcTest {

  private final ObjectMapper json = new ObjectMapper();

  private ConversorIndicadoresIc service;

  @BeforeEach
  void preparar() {
    service = new ConversorIndicadoresIc(new AgregadorCalificacionesIc());
  }

  @ParameterizedTest
  @CsvSource({"NO_PRESENTA, 0", "LEVE, 1", "MODERADO, 2", "SEVERO, 4"})
  void convierteLosNivelesDeSeveridad(String nivel, String esperado) throws Exception {

    JsonNode reglas =
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

    BigDecimal resultado =
        service.convertirSeveridad(json.getNodeFactory().textNode(nivel), reglas, "/elemento/dano");

    numero(esperado, resultado);
  }

  @Test
  void utilizaLaEscalaDeLaVersionRecibida() throws Exception {
    JsonNode reglas =
        json.readTree(
            """
                        {
                          "escala_severidad": {
                            "LEVE": 0.75
                          }
                        }
                        """);

    BigDecimal resultado =
        service.convertirSeveridad(
            json.getNodeFactory().textNode("LEVE"), reglas, "/elemento/dano");

    numero("0.75", resultado);
  }

  @ParameterizedTest
  @MethodSource("objetosInvalidos")
  void rechazaReglasAusentesONoObjetos(JsonNode reglas) {
    error(
        "configuracion_ic_incompleta",
        () ->
            service.convertirSeveridad(
                json.getNodeFactory().textNode("LEVE"), reglas, "/elemento/dano"));
  }

  @ParameterizedTest
  @MethodSource("valoresAusentes")
  void rechazaIndicadoresSinValor(JsonNode valor) {
    error(
        "datos_ic_incompletos",
        () -> service.convertirCategoria(valor, json.createObjectNode(), "/elemento/dano"));
  }

  @ParameterizedTest
  @MethodSource("categoriasInvalidas")
  void rechazaTiposQueNoSonCategoriasNiBooleanos(JsonNode valor) {
    error(
        "indicador_ic_invalido",
        () -> service.convertirCategoria(valor, json.createObjectNode(), "/elemento/dano"));
  }

  @ParameterizedTest
  @MethodSource("objetosInvalidos")
  void rechazaEquivalenciasAusentesONoObjetos(JsonNode equivalencias) {
    error(
        "configuracion_ic_incompleta",
        () ->
            service.convertirCategoria(
                json.getNodeFactory().textNode("LEVE"), equivalencias, "/elemento/dano"));
  }

  @Test
  void identificaUnaCategoriaSinEquivalencia() {
    ApiException exception =
        error(
            "configuracion_ic_incompleta",
            () ->
                service.convertirCategoria(
                    json.getNodeFactory().textNode("SIN_CONFIGURAR"),
                    json.createObjectNode(),
                    "/elemento/dano"));

    assertTrue(exception.getMessage().contains("SIN_CONFIGURAR"));
  }

  @ParameterizedTest
  @CsvSource({"true, 4", "false, 0"})
  void convierteBooleanosConEquivalenciasExplicitas(boolean valor, String esperado)
      throws Exception {

    JsonNode equivalencias = json.readTree("{\"true\":4,\"false\":0}");

    BigDecimal resultado =
        service.convertirCategoria(
            json.getNodeFactory().booleanNode(valor), equivalencias, "/elemento/dano");

    numero(esperado, resultado);
  }

  @ParameterizedTest
  @MethodSource("valoresAusentes")
  void rechazaCalificacionesSinConfigurar(JsonNode valor) {
    error("configuracion_ic_incompleta", () -> service.leerCalificacion(valor, "/elemento/dano"));
  }

  @ParameterizedTest
  @MethodSource("calificacionesNoNumericas")
  void rechazaCalificacionesConfiguradasConTipoIncorrecto(JsonNode valor) {
    error("configuracion_ic_invalida", () -> service.leerCalificacion(valor, "/elemento/dano"));
  }

  @ParameterizedTest
  @CsvSource({"0", "0.5", "2", "4"})
  void aceptaCalificacionesNumericasDentroDelRango(String valor) {
    BigDecimal resultado =
        service.leerCalificacion(
            json.getNodeFactory().numberNode(new BigDecimal(valor)), "/elemento/dano");

    numero(valor, resultado);
  }

  @ParameterizedTest
  @CsvSource({"-0.01", "4.01"})
  void rechazaCalificacionesFueraDelRango(String valor) {
    error(
        "calificacion_ic_fuera_de_rango",
        () ->
            service.leerCalificacion(
                json.getNodeFactory().numberNode(new BigDecimal(valor)), "/elemento/dano"));
  }

  static Stream<Arguments> valoresAusentes() {
    ObjectMapper mapper = new ObjectMapper();

    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of(mapper.getNodeFactory().nullNode()),
        Arguments.of(MissingNode.getInstance()));
  }

  static Stream<Arguments> objetosInvalidos() {
    ObjectMapper mapper = new ObjectMapper();

    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of(mapper.getNodeFactory().nullNode()),
        Arguments.of(mapper.getNodeFactory().textNode("sin-configurar")),
        Arguments.of(mapper.createArrayNode()));
  }

  static Stream<Arguments> categoriasInvalidas() {
    ObjectMapper mapper = new ObjectMapper();

    return Stream.of(
        Arguments.of(mapper.getNodeFactory().numberNode(1)),
        Arguments.of(mapper.createObjectNode()),
        Arguments.of(mapper.createArrayNode()));
  }

  static Stream<Arguments> calificacionesNoNumericas() {
    ObjectMapper mapper = new ObjectMapper();

    return Stream.of(
        Arguments.of(mapper.getNodeFactory().textNode("1")),
        Arguments.of(mapper.getNodeFactory().booleanNode(true)),
        Arguments.of(mapper.createObjectNode()),
        Arguments.of(mapper.createArrayNode()));
  }

  private ApiException error(String codigo, org.junit.jupiter.api.function.Executable operacion) {

    ApiException exception = assertThrows(ApiException.class, operacion);

    assertEquals(codigo, exception.getCode());
    assertTrue(exception.getMessage().contains("/elemento/dano"));

    return exception;
  }

  private void numero(String esperado, BigDecimal actual) {
    assertEquals(0, new BigDecimal(esperado).compareTo(actual));
  }
}
