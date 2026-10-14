package gt.usac.cunoc.sgp.inspeccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc.CondicionAnulacion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc.DesgloseElemento;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MotorPonderacionIcTest {

  private static final UUID VERSION = UUID.fromString("01900000-0000-7000-8000-000000000001");

  private static final String HASH = "a".repeat(64);

  private final ObjectMapper json = new ObjectMapper();
  private final MotorPonderacionIc service =
      new MotorPonderacionIc(new AgregadorCalificacionesIc());

  private ObjectNode reglas;

  @BeforeEach
  void preparar() {
    reglas = json.createObjectNode();
    reglas.putObject("rangos_estado").put("BUENO", 75).put("REGULAR", 50).put("MALO", 0);
  }

  @ParameterizedTest
  @CsvSource({"0, 100, BUENO", "1, 75, BUENO", "2, 50, REGULAR", "3, 25, MALO", "4, 0, MALO"})
  void calculaYClasifica(String calificacion, String esperado, EstadoCondicion estado) {

    var resultado =
        service.calcular(entrada(), List.of(elemento("2", calificacion)), List.of(), HASH);

    assertEquals(0, new BigDecimal(esperado).compareTo(resultado.indiceCalculado()));

    assertEquals(estado, resultado.estadoPorRango());
    assertEquals(estado, resultado.estadoCalculado());
    assertEquals(HASH, resultado.hashEntrada());
    assertEquals(VERSION, resultado.versionFormularioId());
  }

  @Test
  void usaLosRangosDeLaVersionRecibida() {
    ((ObjectNode) reglas.get("rangos_estado")).put("BUENO", 80);

    var resultado = service.calcular(entrada(), List.of(elemento("1", "1")), List.of(), HASH);

    assertEquals(EstadoCondicion.REGULAR, resultado.estadoPorRango());
  }

  @Test
  void unaCondicionCriticaConservaElValorNumerico() {
    var resultado =
        service.calcular(
            entrada(),
            List.of(elemento("1", "0.6")),
            List.of(condicion(EstadoCondicion.MALO)),
            HASH);

    assertEquals(0, new BigDecimal("85").compareTo(resultado.indiceCalculado()));
    assertEquals(EstadoCondicion.BUENO, resultado.estadoPorRango());
    assertEquals(EstadoCondicion.MALO, resultado.estadoCalculado());
  }

  @Test
  void rechazaEntradaNula() {
    error(
        "configuracion_ic_incompleta",
        () -> service.calcular(null, List.of(elemento("1", "0")), List.of(), HASH));
  }

  @Test
  void exigeIdentificarLaVersion() {
    error(
        "configuracion_ic_incompleta",
        () ->
            service.calcular(
                entrada(null, "sieca-v1", "ic-v1", reglas),
                List.of(elemento("1", "0")),
                List.of(),
                HASH));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void exigeCodigoDelFormulario(String codigo) {
    error(
        "configuracion_ic_incompleta",
        () ->
            service.calcular(
                entrada(VERSION, codigo, "ic-v1", reglas),
                List.of(elemento("1", "0")),
                List.of(),
                HASH));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void exigeVersionDelAlgoritmo(String algoritmo) {
    error(
        "configuracion_ic_incompleta",
        () ->
            service.calcular(
                entrada(VERSION, "sieca-v1", algoritmo, reglas),
                List.of(elemento("1", "0")),
                List.of(),
                HASH));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"abc", "ZZZZ", "A"})
  void rechazaHashesInvalidos(String hash) {
    error(
        "hash_entrada_ic_invalido",
        () -> service.calcular(entrada(), List.of(elemento("1", "0")), List.of(), hash));
  }

  @Test
  void rechazaDesgloseNulo() {
    error("sin_elementos_evaluados_ic", () -> service.calcular(entrada(), null, List.of(), HASH));
  }

  @Test
  void rechazaDesgloseVacio() {
    error(
        "sin_elementos_evaluados_ic",
        () -> service.calcular(entrada(), List.of(), List.of(), HASH));
  }

  @Test
  void rechazaUnElementoNuloEnElDesglose() {
    error(
        "datos_ic_invalidos",
        () -> service.calcular(entrada(), Arrays.asList((DesgloseElemento) null), List.of(), HASH));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void rechazaPesosNoPositivos(String peso) {
    error(
        "configuracion_ic_incompleta",
        () -> service.calcular(entrada(), List.of(elemento(peso, "1")), List.of(), HASH));
  }

  @Test
  void rechazaUnPesoNulo() {
    var detalle =
        new DesgloseElemento(
            "/elemento", null, BigDecimal.ONE, BigDecimal.ONE, "MAXIMO", List.of());

    error(
        "configuracion_ic_incompleta",
        () -> service.calcular(entrada(), List.of(detalle), List.of(), HASH));
  }

  @Test
  void rechazaUnAportePonderadoIncongruente() {
    var detalle =
        new DesgloseElemento(
            "/elemento",
            new BigDecimal("2"),
            BigDecimal.ONE,
            new BigDecimal("3"),
            "MAXIMO",
            List.of());

    error(
        "desglose_ic_incongruente",
        () -> service.calcular(entrada(), List.of(detalle), List.of(), HASH));
  }

  @Test
  void rechazaUnAportePonderadoNulo() {
    var detalle =
        new DesgloseElemento(
            "/elemento", BigDecimal.ONE, BigDecimal.ONE, null, "MAXIMO", List.of());

    error(
        "desglose_ic_incongruente",
        () -> service.calcular(entrada(), List.of(detalle), List.of(), HASH));
  }

  @Test
  void rechazaUnaListaNulaDeCondiciones() {
    error(
        "datos_ic_invalidos",
        () -> service.calcular(entrada(), List.of(elemento("1", "0")), null, HASH));
  }

  @Test
  void rechazaUnaCondicionNula() {
    error(
        "regla_anulacion_ic_invalida",
        () ->
            service.calcular(
                entrada(),
                List.of(elemento("1", "0")),
                Arrays.asList((CondicionAnulacion) null),
                HASH));
  }

  @Test
  void unaCondicionCriticaDebeForzarMalo() {
    error(
        "regla_anulacion_ic_invalida",
        () ->
            service.calcular(
                entrada(),
                List.of(elemento("1", "0")),
                List.of(condicion(EstadoCondicion.REGULAR)),
                HASH));
  }

  @Test
  void exigeReglasDeEvaluacion() {
    error(
        "configuracion_ic_incompleta",
        () ->
            service.calcular(
                entrada(VERSION, "sieca-v1", "ic-v1", null),
                List.of(elemento("1", "0")),
                List.of(),
                HASH));
  }

  @Test
  void rechazaReglasQueNoSonUnObjeto() {
    error(
        "configuracion_ic_incompleta",
        () ->
            service.calcular(
                entrada(VERSION, "sieca-v1", "ic-v1", json.createArrayNode()),
                List.of(elemento("1", "0")),
                List.of(),
                HASH));
  }

  @Test
  void exigeElObjetoDeRangos() {
    reglas.remove("rangos_estado");

    error(
        "configuracion_ic_incompleta",
        () -> service.calcular(entrada(), List.of(elemento("1", "0")), List.of(), HASH));
  }

  @Test
  void rechazaRangosConTipoIncorrecto() {
    reglas.put("rangos_estado", "sin-calibrar");

    error(
        "configuracion_ic_incompleta",
        () -> service.calcular(entrada(), List.of(elemento("1", "0")), List.of(), HASH));
  }

  @ParameterizedTest
  @ValueSource(strings = {"BUENO", "REGULAR", "MALO"})
  void exigeTodosLosLimites(String estado) {
    ((ObjectNode) reglas.get("rangos_estado")).remove(estado);

    error(
        "configuracion_ic_incompleta",
        () -> service.calcular(entrada(), List.of(elemento("1", "0")), List.of(), HASH));
  }

  @ParameterizedTest
  @ValueSource(strings = {"BUENO", "REGULAR", "MALO"})
  void rechazaLimitesNoNumericos(String estado) {
    ((ObjectNode) reglas.get("rangos_estado")).put(estado, "pendiente");

    error(
        "configuracion_ic_incompleta",
        () -> service.calcular(entrada(), List.of(elemento("1", "0")), List.of(), HASH));
  }

  @ParameterizedTest
  @CsvSource({"75, 50, 1", "75, 0, 0", "50, 50, 0", "49, 50, 0", "101, 50, 0"})
  void rechazaRangosIncoherentes(int bueno, int regular, int malo) {
    reglas.putObject("rangos_estado").put("BUENO", bueno).put("REGULAR", regular).put("MALO", malo);

    error(
        "rangos_ic_invalidos",
        () -> service.calcular(entrada(), List.of(elemento("1", "0")), List.of(), HASH));
  }

  private DesgloseElemento elemento(String peso, String calificacion) {
    BigDecimal pesoNumerico = new BigDecimal(peso);
    BigDecimal calificacionNumerica = new BigDecimal(calificacion);

    return new DesgloseElemento(
        "/elemento",
        pesoNumerico,
        calificacionNumerica,
        pesoNumerico.multiply(calificacionNumerica),
        "MAXIMO",
        List.of());
  }

  private CondicionAnulacion condicion(EstadoCondicion estado) {
    return new CondicionAnulacion(
        "critica",
        "Condicion critica de prueba",
        "/elemento",
        null,
        "/elemento",
        "existencia",
        "AUSENTE",
        estado);
  }

  private EntradaCalculoIc entrada() {
    return entrada(VERSION, "sieca-v1", "ic-v1", reglas);
  }

  private EntradaCalculoIc entrada(
      UUID version, String codigo, String algoritmo, JsonNode configuracion) {

    return new EntradaCalculoIc(
        version,
        codigo,
        algoritmo,
        json.createObjectNode(),
        json.createObjectNode(),
        json.createObjectNode(),
        configuracion,
        java.util.Map.of());
  }

  private void error(String codigo, org.junit.jupiter.api.function.Executable operacion) {

    ApiException exception = assertThrows(ApiException.class, operacion);
    assertEquals(codigo, exception.getCode());
  }
}
