package gt.usac.cunoc.sgp.inspeccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EvaluadorCondicionesCriticasIcTest {

  private final ObjectMapper json = new ObjectMapper();

  private EvaluadorCondicionesCriticasIc service;
  private ObjectNode datos;
  private ObjectNode reglas;

  @BeforeEach
  void preparar() {
    service = new EvaluadorCondicionesCriticasIc(new ResolverElementosIc());
    datos = json.createObjectNode();
    reglas = json.createObjectNode();
    reglas.putArray("reglas_anulacion");
  }

  @ParameterizedTest
  @ValueSource(strings = {"CON_EXPOSICION", "ASENTAMIENTO_ESTRUCTURA"})
  void detectaSocavacionCriticaEnAmbosLados(String valor) {
    datos.putObject("socavacion").put("aguas_arriba", valor).put("aguas_abajo", valor);

    ObjectNode regla = regla("socavacion-critica", "/socavacion");
    ObjectNode condicion = regla.putObject("condicion");
    condicion.putArray("indicadores").add("aguas_arriba").add("aguas_abajo");
    condicion.putArray("algun_valor_en").add(valor);

    var resultado = service.evaluar(datos, reglas);

    assertEquals(2, resultado.size());
    assertEquals(EstadoCondicion.MALO, resultado.getFirst().estadoResultante());
    assertEquals("/socavacion", resultado.getFirst().elementoRef());
    assertEquals(valor, resultado.getFirst().valorDetectado());
    assertEquals("aguas_arriba", resultado.getFirst().indicador());
  }

  @Test
  void detectaLaAusenciaTotalDeUnElementoPortante() {
    datos.putObject("portante").put("existencia", "AUSENTE");

    regla("ausencia-portante", "/portante")
        .putObject("condicion")
        .put("campo", "existencia")
        .put("igual_a", "AUSENTE");

    var resultado = service.evaluar(datos, reglas);

    assertEquals(1, resultado.size());
    assertEquals("ausencia-portante", resultado.getFirst().reglaId());
    assertEquals("AUSENTE", resultado.getFirst().valorDetectado());
  }

  @Test
  void detectaAceroCortadoYLaEquivalenciaDeConcretoConfigurada() {
    datos.putObject("acero").put("elemento_cortado", "LEVE");
    datos.putObject("concreto").put("perdida_petreos_golpe", "SEVERO");

    ObjectNode regla = regla("portante-danado", "/acero");
    ((ArrayNode) regla.get("elementos")).add("/concreto");

    ObjectNode condicion = regla.putObject("condicion");

    ObjectNode acero = condicion.putObject("elementos_acero");
    acero.putArray("elementos").add("/acero");
    acero.put("indicador", "elemento_cortado");
    acero.putArray("algun_valor_en").add("LEVE").add("MODERADO").add("SEVERO");

    ObjectNode concreto = condicion.putObject("elementos_concreto");
    concreto.putArray("elementos").add("/concreto");
    concreto.put("indicador", "perdida_petreos_golpe");
    concreto.putArray("algun_valor_en").add("SEVERO");

    var resultado = service.evaluar(datos, reglas);

    assertEquals(2, resultado.size());
    assertTrue(resultado.stream().anyMatch(item -> item.elementoRef().equals("/acero")));
    assertTrue(resultado.stream().anyMatch(item -> item.elementoRef().equals("/concreto")));
  }

  @Test
  void conservaLaIdentidadDeUnaPilaConCondicionCritica() {
    UUID id = UUID.fromString("01900000-0000-7000-8000-000000000001");

    datos
        .putArray("pilas")
        .addObject()
        .put("instancia_id", id.toString())
        .putObject("socavacion")
        .put("aguas_arriba", "CON_EXPOSICION");

    ObjectNode condicion = regla("socavacion-pila", "/pilas/socavacion").putObject("condicion");

    condicion.put("indicador", "aguas_arriba");
    condicion.putArray("algun_valor_en").add("CON_EXPOSICION");

    var resultado = service.evaluar(datos, reglas);

    assertEquals(1, resultado.size());
    assertEquals(id, resultado.getFirst().instanciaId());
    assertEquals("/pilas/0/socavacion", resultado.getFirst().rutaDatos());
  }

  @Test
  void noAplicaNoActivaUnaCondicionCritica() {
    datos.putObject("portante").put("existencia", "NO_APLICA").put("elemento_cortado", "SEVERO");

    ObjectNode condicion = regla("portante-cortado", "/portante").putObject("condicion");
    condicion.put("indicador", "elemento_cortado");
    condicion.putArray("algun_valor_en").add("SEVERO");

    assertTrue(service.evaluar(datos, reglas).isEmpty());
  }

  @Test
  void ignoraValoresQueNoCumplenLaCondicion() {
    datos.putObject("portante").put("elemento_cortado", "NO_PRESENTA");

    ObjectNode condicion = regla("portante-cortado", "/portante").putObject("condicion");
    condicion.put("indicador", "elemento_cortado");
    condicion.putArray("algun_valor_en").add("SEVERO");

    assertTrue(service.evaluar(datos, reglas).isEmpty());
  }

  @Test
  void unIndicadorAusenteNoActivaUnaCondicion() {
    datos.putObject("portante").put("existencia", "AUSENTE");

    ObjectNode condicion = regla("portante-cortado", "/portante").putObject("condicion");
    condicion.put("indicador", "elemento_cortado");
    condicion.putArray("algun_valor_en").add("SEVERO");

    assertTrue(service.evaluar(datos, reglas).isEmpty());
  }

  @Test
  void laMismaEntradaUsaLasCondicionesDeCadaVersion() {
    datos.putObject("portante").put("elemento_cortado", "LEVE");

    ObjectNode condicion = regla("portante-cortado", "/portante").putObject("condicion");
    condicion.put("indicador", "elemento_cortado");
    condicion.putArray("algun_valor_en").add("SEVERO");

    assertTrue(service.evaluar(datos, reglas).isEmpty());

    condicion.putArray("algun_valor_en").add("LEVE");

    assertEquals(1, service.evaluar(datos, reglas).size());
  }

  @Test
  void permiteUnaListaExplicitaSinReglasDeAnulacion() {
    assertTrue(service.evaluar(datos, reglas).isEmpty());
  }

  @Test
  void rechazaReglasPendientesDeCalibracion() {
    ObjectNode regla = regla("pendiente", "/elemento");
    regla.put("pendiente", "Confirmar equivalencia");
    regla.putObject("condicion").put("campo", "existencia").put("igual_a", "AUSENTE");

    ApiException exception = error();

    assertTrue(exception.getMessage().contains("pendiente"));
  }

  @Test
  void rechazaUnEstadoResultanteDistintoDeMalo() {
    regla("estado-invalido", "/elemento").put("estado_resultante", "REGULAR");

    error();
  }

  @Test
  void rechazaUnaCondicionVacia() {
    regla("condicion-vacia", "/elemento").putObject("condicion");

    error();
  }

  @Test
  void rechazaUnGrupoQueNoEsUnObjeto() {
    regla("grupo-invalido", "/elemento")
        .putObject("condicion")
        .put("elementos_acero", "sin-configurar");

    error();
  }

  @Test
  void rechazaReferenciasDeGrupoNoDeclaradasEnLaRegla() {
    ObjectNode grupo =
        regla("grupo-incongruente", "/elemento")
            .putObject("condicion")
            .putObject("elementos_acero");

    grupo.putArray("elementos").add("/otro");
    grupo.put("indicador", "elemento_cortado");
    grupo.putArray("algun_valor_en").add("SEVERO");

    error();
  }

  @ParameterizedTest
  @ValueSource(strings = {"id", "descripcion", "estado_resultante"})
  void rechazaTextosObligatoriosFaltantes(String campo) {
    ObjectNode regla = regla("incompleta", "/elemento");
    regla.remove(campo);

    error();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void rechazaIdentificadoresVacios(String valor) {
    regla("incompleta", "/elemento").put("id", valor);

    error();
  }

  @Test
  void rechazaUnaListaDeElementosVacia() {
    regla("sin-elementos", "/elemento").putArray("elementos");

    error();
  }

  @Test
  void rechazaUnaListaDeValoresVacia() {
    ObjectNode condicion = regla("sin-valores", "/elemento").putObject("condicion");
    condicion.put("indicador", "elemento_cortado");
    condicion.putArray("algun_valor_en");

    error();
  }

  @Test
  void rechazaUnaListaDeIndicadoresVacia() {
    ObjectNode condicion = regla("sin-indicadores", "/elemento").putObject("condicion");
    condicion.putArray("indicadores");
    condicion.putArray("algun_valor_en").add("SEVERO");

    error();
  }

  @Test
  void rechazaReglasNulas() {
    assertThrows(ApiException.class, () -> service.evaluar(datos, null));
  }

  @Test
  void rechazaReglasQueNoSonUnObjeto() {
    assertThrows(ApiException.class, () -> service.evaluar(datos, json.createArrayNode()));
  }

  @Test
  void rechazaLaAusenciaDeLaListaDeAnulaciones() {
    reglas.remove("reglas_anulacion");

    error();
  }

  private ObjectNode regla(String id, String referencia) {
    ObjectNode regla = ((ArrayNode) reglas.get("reglas_anulacion")).addObject();

    regla.put("id", id);
    regla.put("descripcion", "Condicion critica configurada para pruebas");
    regla.put("estado_resultante", "MALO");
    regla.putNull("pendiente");
    regla.putArray("elementos").add(referencia);

    return regla;
  }

  private ApiException error() {
    ApiException exception = assertThrows(ApiException.class, () -> service.evaluar(datos, reglas));

    assertEquals("reglas_anulacion_ic_invalidas", exception.getCode());

    return exception;
  }

  @Test
  void rechazaUnaListaDeAnulacionesConTipoIncorrecto() {
    reglas.put("reglas_anulacion", "invalida");

    error();
  }

  @Test
  void permiteOmitirElCampoPendienteEnUnaReglaCompleta() {
    datos.putObject("portante").put("existencia", "AUSENTE");

    ObjectNode regla = regla("ausencia", "/portante");
    regla.remove("pendiente");
    regla.putObject("condicion").put("campo", "existencia").put("igual_a", "AUSENTE");

    assertEquals(1, service.evaluar(datos, reglas).size());
  }

  @Test
  void rechazaUnaReglaSinCondicion() {
    regla("sin-condicion", "/elemento");

    error();
  }

  @Test
  void rechazaUnaCondicionQueNoEsObjeto() {
    regla("condicion-invalida", "/elemento").put("condicion", "texto");

    error();
  }

  @Test
  void unValorNoTextualNoActivaLaCondicion() {
    datos.putObject("portante").put("elemento_cortado", true);

    ObjectNode condicion = regla("cortado", "/portante").putObject("condicion");
    condicion.put("indicador", "elemento_cortado");
    condicion.putArray("algun_valor_en").add("SEVERO");

    assertTrue(service.evaluar(datos, reglas).isEmpty());
  }

  @Test
  void rechazaUnaListaDeElementosFaltante() {
    regla("sin-elementos", "/elemento").remove("elementos");

    error();
  }

  @Test
  void rechazaUnaListaDeElementosConTipoIncorrecto() {
    regla("elementos-invalidos", "/elemento").put("elementos", "texto");

    error();
  }

  @Test
  void rechazaUnIdentificadorNoTextual() {
    regla("id-invalido", "/elemento").put("id", 123);

    error();
  }
}
