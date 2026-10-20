package gt.usac.cunoc.sgp.mantenimiento.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EvaluadorCondicionReglaTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final EvaluadorCondicionRegla evaluador = new EvaluadorCondicionRegla();

  private Optional<JsonNode> evaluar(String condicion, String elemento) throws Exception {
    return evaluador.evaluar(MAPPER.readTree(condicion), MAPPER.readTree(elemento));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        // MAYOR_O_IGUAL sobre la escala de severidad
        "SEVERO|true",
        "MODERADO|true",
        "LEVE|false",
        "NO_PRESENTA|false"
      })
  void mayorOIgualUsaLaEscalaDeSeveridad(String severidad, boolean cumple) throws Exception {
    String condicion =
        "{\"indicador\":\"exposicion_acero\",\"operador\":\"MAYOR_O_IGUAL\",\"umbral\":\"MODERADO\"}";
    String elemento = "{\"exposicion_acero\":\"" + severidad + "\"}";

    assertThat(evaluar(condicion, elemento).isPresent()).isEqualTo(cumple);
  }

  @Test
  void devuelveElValorEncontradoCuandoSeCumple() throws Exception {
    String condicion =
        "{\"indicador\":\"exposicion_acero\",\"operador\":\"MAYOR_O_IGUAL\",\"umbral\":\"MODERADO\"}";

    assertThat(evaluar(condicion, "{\"exposicion_acero\":\"SEVERO\"}"))
        .hasValueSatisfying(valor -> assertThat(valor.textValue()).isEqualTo("SEVERO"));
  }

  @ParameterizedTest(name = "junta_limpia={0} -> {1}")
  @CsvSource({"false,true", "true,false"})
  void igualCompararBooleanos(boolean valor, boolean cumple) throws Exception {
    String condicion = "{\"indicador\":\"junta_limpia\",\"operador\":\"IGUAL\",\"umbral\":false}";

    assertThat(evaluar(condicion, "{\"junta_limpia\":" + valor + "}").isPresent())
        .isEqualTo(cumple);
  }

  @Test
  void unIndicadorSinResponderNoDisparaLaReglaNiSiSeBuscaFalse() throws Exception {
    String condicion = "{\"indicador\":\"junta_limpia\",\"operador\":\"IGUAL\",\"umbral\":false}";

    assertThat(evaluar(condicion, "{}")).isEmpty();
    assertThat(evaluar(condicion, "{\"junta_limpia\":null}")).isEmpty();
  }

  @Test
  void igualCompararTextosYNumeros() throws Exception {
    String texto =
        "{\"indicador\":\"aguas_arriba\",\"operador\":\"IGUAL\",\"umbral\":\"CON_EXPOSICION\"}";
    assertThat(evaluar(texto, "{\"aguas_arriba\":\"CON_EXPOSICION\"}")).isPresent();
    assertThat(evaluar(texto, "{\"aguas_arriba\":\"NO_HAY\"}")).isEmpty();

    String numero = "{\"indicador\":\"golpeados\",\"operador\":\"IGUAL\",\"umbral\":2}";
    assertThat(evaluar(numero, "{\"golpeados\":2.0}")).isPresent();
    assertThat(evaluar(numero, "{\"golpeados\":3}")).isEmpty();
  }

  @ParameterizedTest(name = "golpeado_longitud_m={0} -> {1}")
  @CsvSource({"0,false", "0.9,false", "1,true", "12.5,true"})
  void mayorOIgualCompararNumeros(String metros, boolean cumple) throws Exception {
    String condicion =
        "{\"indicador\":\"golpeado_longitud_m\",\"operador\":\"MAYOR_O_IGUAL\",\"umbral\":1}";

    assertThat(evaluar(condicion, "{\"golpeado_longitud_m\":" + metros + "}").isPresent())
        .isEqualTo(cumple);
  }

  @ParameterizedTest(name = "pasamanos={0} -> {1}")
  @CsvSource({"SEVERO,false", "MODERADO,true", "LEVE,true", "NO_PRESENTA,true"})
  void menorOIgualUsaLaEscalaDeSeveridad(String severidad, boolean cumple) throws Exception {
    String condicion =
        "{\"indicador\":\"pasamanos\",\"operador\":\"MENOR_O_IGUAL\",\"umbral\":\"MODERADO\"}";

    assertThat(evaluar(condicion, "{\"pasamanos\":\"" + severidad + "\"}").isPresent())
        .isEqualTo(cumple);
  }

  @ParameterizedTest(name = "golpeados={0} -> {1}")
  @CsvSource({"0,true", "2,true", "2.5,false", "7,false"})
  void menorOIgualCompararNumeros(String cantidad, boolean cumple) throws Exception {
    String condicion = "{\"indicador\":\"golpeados\",\"operador\":\"MENOR_O_IGUAL\",\"umbral\":2}";

    assertThat(evaluar(condicion, "{\"golpeados\":" + cantidad + "}").isPresent())
        .isEqualTo(cumple);
  }

  @Test
  void distintoEsLaNegacionDeIgualSoloCuandoSePuedeComparar() throws Exception {
    String texto =
        "{\"indicador\":\"aguas_arriba\",\"operador\":\"DISTINTO\",\"umbral\":\"NO_HAY\"}";
    assertThat(evaluar(texto, "{\"aguas_arriba\":\"CON_EXPOSICION\"}"))
        .hasValueSatisfying(valor -> assertThat(valor.textValue()).isEqualTo("CON_EXPOSICION"));
    assertThat(evaluar(texto, "{\"aguas_arriba\":\"NO_HAY\"}")).isEmpty();

    String booleano = "{\"indicador\":\"junta_limpia\",\"operador\":\"DISTINTO\",\"umbral\":true}";
    assertThat(evaluar(booleano, "{\"junta_limpia\":false}")).isPresent();
    assertThat(evaluar(booleano, "{\"junta_limpia\":true}")).isEmpty();

    // Un indicador sin responder o de otro tipo no es «distinto»: la regla no se dispara.
    assertThat(evaluar(texto, "{}")).isEmpty();
    assertThat(evaluar(texto, "{\"aguas_arriba\":3}")).isEmpty();
  }

  @Test
  void contieneBuscaEnUnaListaDeValores() throws Exception {
    String condicion =
        "{\"indicador\":\"condiciones\",\"operador\":\"CONTIENE\",\"umbral\":\"ASOLVADO_SEDIMENTADO\"}";

    assertThat(evaluar(condicion, "{\"condiciones\":[\"LIMPIO\",\"ASOLVADO_SEDIMENTADO\"]}"))
        .isPresent();
    assertThat(evaluar(condicion, "{\"condiciones\":[\"LIMPIO\"]}")).isEmpty();
    assertThat(evaluar(condicion, "{\"condiciones\":[]}")).isEmpty();
  }

  @Test
  void unaCondicionIncomparableSeTrataComoQueNoSeCumple() throws Exception {
    // Un umbral de severidad contra un número: la regla defectuosa de la semilla original.
    assertThat(
            evaluar(
                "{\"indicador\":\"golpeado_longitud_m\",\"operador\":\"MAYOR_O_IGUAL\",\"umbral\":\"MODERADO\"}",
                "{\"golpeado_longitud_m\":8}"))
        .isEmpty();
    // IGUAL entre tipos distintos.
    assertThat(
            evaluar(
                "{\"indicador\":\"x\",\"operador\":\"IGUAL\",\"umbral\":\"true\"}", "{\"x\":true}"))
        .isEmpty();
    // CONTIENE sobre algo que no es una lista, o con un umbral que no es texto.
    assertThat(
            evaluar(
                "{\"indicador\":\"x\",\"operador\":\"CONTIENE\",\"umbral\":\"A\"}",
                "{\"x\":\"A\"}"))
        .isEmpty();
    assertThat(
            evaluar("{\"indicador\":\"x\",\"operador\":\"CONTIENE\",\"umbral\":1}", "{\"x\":[1]}"))
        .isEmpty();
    // MENOR_O_IGUAL con un umbral de otro tipo.
    assertThat(
            evaluar(
                "{\"indicador\":\"x\",\"operador\":\"MENOR_O_IGUAL\",\"umbral\":\"LEVE\"}",
                "{\"x\":3}"))
        .isEmpty();
    // MAYOR_O_IGUAL sobre un texto fuera de la escala.
    assertThat(
            evaluar(
                "{\"indicador\":\"x\",\"operador\":\"MAYOR_O_IGUAL\",\"umbral\":\"LEVE\"}",
                "{\"x\":\"OTRO\"}"))
        .isEmpty();
  }

  @Test
  void unaCondicionIncompletaODesconocidaNoSeCumpleNiFalla() throws Exception {
    assertThat(evaluar("{\"operador\":\"IGUAL\",\"umbral\":1}", "{\"x\":1}")).isEmpty();
    assertThat(evaluar("{\"indicador\":\"x\",\"operador\":\"IGUAL\"}", "{\"x\":1}")).isEmpty();
    assertThat(evaluar("{\"indicador\":\"x\",\"operador\":\"IGUAL\",\"umbral\":null}", "{\"x\":1}"))
        .isEmpty();
    assertThat(
            evaluar("{\"indicador\":\"x\",\"operador\":\"PARECIDO_A\",\"umbral\":1}", "{\"x\":1}"))
        .isEmpty();
    assertThat(evaluador.evaluar(null, MAPPER.readTree("{}"))).isEmpty();
    assertThat(evaluador.evaluar(MAPPER.readTree("{}"), null)).isEmpty();
    assertThat(evaluador.evaluar(MAPPER.readTree("[]"), MAPPER.readTree("{}"))).isEmpty();
    assertThat(evaluador.evaluar(MAPPER.readTree("{}"), MAPPER.readTree("[]"))).isEmpty();
  }
}
