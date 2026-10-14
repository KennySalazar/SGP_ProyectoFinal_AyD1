package gt.usac.cunoc.sgp.inspeccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AgregadorCalificacionesIcTest {

  private final AgregadorCalificacionesIc service = new AgregadorCalificacionesIc();

  @Test
  void maximoConservaLaCalificacionMasAlta() {
    BigDecimal resultado =
        service.agregar(
            List.of(BigDecimal.ZERO, new BigDecimal("2"), new BigDecimal("4")),
            "MAXIMO",
            "/elemento");

    numero("4", resultado);
  }

  @Test
  void promedioUsaTodasLasCalificaciones() {
    BigDecimal resultado =
        service.agregar(
            List.of(BigDecimal.ZERO, new BigDecimal("2"), new BigDecimal("4")),
            "PROMEDIO",
            "/elemento");

    numero("2", resultado);
  }

  @Test
  void promedioReproducibleConDivisionNoExacta() {
    List<BigDecimal> valores = List.of(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);

    BigDecimal esperado = BigDecimal.ONE.divide(new BigDecimal("3"), MathContext.DECIMAL128);

    assertEquals(esperado, service.agregar(valores, "PROMEDIO", "/elemento"));
    assertEquals(
        service.agregar(valores, "PROMEDIO", "/elemento"),
        service.agregar(valores, "PROMEDIO", "/elemento"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"MAXIMO", "PROMEDIO"})
  void unSoloValorConservaSuCalificacion(String regla) {
    BigDecimal resultado = service.agregar(List.of(new BigDecimal("1.25")), regla, "/elemento");

    numero("1.25", resultado);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void exigeUnaReglaConfigurada(String regla) {
    error(
        "configuracion_ic_incompleta",
        () -> service.agregar(List.of(BigDecimal.ONE), regla, "/elemento"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUMA", "maximo", "PROMEDIO_PONDERADO"})
  void rechazaReglasDesconocidas(String regla) {
    error(
        "regla_agregacion_ic_invalida",
        () -> service.agregar(List.of(BigDecimal.ONE), regla, "/elemento"));
  }

  @ParameterizedTest
  @MethodSource("listasSinCalificaciones")
  void noInterpretaUnaListaVaciaComoSinDanos(List<BigDecimal> valores) {
    error("sin_calificaciones_ic", () -> service.agregar(valores, "MAXIMO", "/elemento"));
  }

  static Stream<Arguments> listasSinCalificaciones() {
    return Stream.of(Arguments.of((Object) null), Arguments.of(List.<BigDecimal>of()));
  }

  @Test
  void rechazaUnaCalificacionNulaDentroDeLaLista() {
    error(
        "configuracion_ic_incompleta",
        () -> service.agregar(Arrays.asList(BigDecimal.ONE, null), "MAXIMO", "/elemento"));
  }

  @Test
  void rechazaUnaCalificacionNulaDirectamente() {
    error("configuracion_ic_incompleta", () -> service.validarCalificacion(null, "/elemento"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "-0.01", "4.01", "5"})
  void rechazaCalificacionesFueraDeRango(String valor) {
    error(
        "calificacion_ic_fuera_de_rango",
        () -> service.agregar(List.of(new BigDecimal(valor)), "PROMEDIO", "/elemento"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.5", "2", "4"})
  void aceptaLosExtremosYValoresIntermedios(String valor) {
    BigDecimal resultado = service.agregar(List.of(new BigDecimal(valor)), "MAXIMO", "/elemento");

    numero(valor, resultado);
  }

  private void error(String codigo, org.junit.jupiter.api.function.Executable operacion) {

    ApiException exception = assertThrows(ApiException.class, operacion);

    assertEquals(codigo, exception.getCode());
    assertTrue(exception.getMessage().contains("/elemento"));
  }

  private void numero(String esperado, BigDecimal actual) {
    assertEquals(0, new BigDecimal(esperado).compareTo(actual));
  }
}
