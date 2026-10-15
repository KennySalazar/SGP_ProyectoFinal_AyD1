package gt.usac.cunoc.sgp.inspeccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CalculoIndiceCondicionServiceTest {

  private static final UUID VERSION = UUID.fromString("01900000-0000-7000-8000-000000000001");

  private static final UUID PILA_UNO = UUID.fromString("01900000-0000-7000-8000-000000000002");

  private static final UUID PILA_DOS = UUID.fromString("01900000-0000-7000-8000-000000000003");

  private final ObjectMapper json = new ObjectMapper();

  private CalculoIndiceCondicionService service;
  private ObjectNode datos;
  private ObjectNode catalogo;
  private ObjectNode reglas;
  private Map<String, BigDecimal> pesos;

  @BeforeEach
  void preparar() throws Exception {
    var resolver = new ResolverElementosIc();
    var agregador = new AgregadorCalificacionesIc();
    var conversor = new ConversorIndicadoresIc(agregador);

    service =
        new CalculoIndiceCondicionService(
            resolver,
            conversor,
            new ConversorMedicionesIc(conversor),
            agregador,
            new EvaluadorCondicionesCriticasIc(resolver),
            new HuellaEntradaIc(),
            new MotorPonderacionIc(agregador));

    datos = json.createObjectNode();
    catalogo = json.createObjectNode();
    catalogo.put("formulario", "sieca-v1");
    catalogo.putArray("elementos");

    reglas =
        (ObjectNode)
            json.readTree(
                """
                                {
                                  "version_algoritmo": "ic-hu018-prueba-v1",
                                  "escala_severidad": {
                                    "NO_PRESENTA": 0,
                                    "LEVE": 1,
                                    "MODERADO": 2,
                                    "SEVERO": 4
                                  },
                                  "agregacion": {
                                    "indicadores_de_un_elemento": "MAXIMO",
                                    "instancias_repetibles": "MAXIMO",
                                    "elementos_ausentes_no_portantes": 4,
                                    "elementos_ausentes_portantes": 4
                                  },
                                  "rangos_estado": {
                                    "BUENO": 75,
                                    "REGULAR": 50,
                                    "MALO": 0
                                  },
                                  "conversion_indicadores": {
                                    "booleanos": {},
                                    "enumerados": {},
                                    "numericos": {}
                                  },
                                  "reglas_anulacion": []
                                }
                                """);

    pesos = new LinkedHashMap<>();
  }

  @Test
  void sinDanosProduceCienYBueno() {
    agregarElemento("uno", "NO_PRESENTA", "2");
    agregarElemento("dos", "NO_PRESENTA", "3");

    ResultadoCalculoIc resultado = service.calcular(entrada());

    numero("100", resultado.indiceCalculado());
    assertEquals(EstadoCondicion.BUENO, resultado.estadoPorRango());
    assertEquals(EstadoCondicion.BUENO, resultado.estadoCalculado());
    numero("5", resultado.sumaPesos());
  }

  @Test
  void danoMaximoProduceCeroYMalo() {
    agregarElemento("uno", "SEVERO", "2");
    agregarElemento("dos", "SEVERO", "3");

    ResultadoCalculoIc resultado = service.calcular(entrada());

    numero("0", resultado.indiceCalculado());
    assertEquals(EstadoCondicion.MALO, resultado.estadoCalculado());
  }

  @Test
  void aplicaLaFormulaPonderada() {
    agregarElemento("uno", "LEVE", "1");
    agregarElemento("dos", "SEVERO", "3");

    ResultadoCalculoIc resultado = service.calcular(entrada());

    numero("18.75", resultado.indiceCalculado());
    numero("4", resultado.sumaPesos());
    numero("13", resultado.sumaCalificacionesPonderadas());
    assertEquals(EstadoCondicion.MALO, resultado.estadoPorRango());
    assertEquals(2, resultado.desglose().size());
  }

  @ParameterizedTest
  @CsvSource({
    "0, 100, BUENO",
    "1, 75, BUENO",
    "1.0004, 74.99, REGULAR",
    "2, 50, REGULAR",
    "2.0004, 49.99, MALO",
    "4, 0, MALO"
  })
  void clasificaLosLimites(String calificacion, String indice, EstadoCondicion estado) {

    agregarElemento("uno", "LEVE", "1");
    ((ObjectNode) reglas.get("escala_severidad")).put("LEVE", new BigDecimal(calificacion));

    ResultadoCalculoIc resultado = service.calcular(entrada());

    numero(indice, resultado.indiceCalculado());
    assertEquals(estado, resultado.estadoPorRango());
  }

  @Test
  void noAplicaNoParticipaNiRequierePeso() {
    agregarElemento("uno", "NO_PRESENTA", "2");
    agregarElemento("dos", "SEVERO", "100");

    ((ObjectNode) datos.get("dos")).put("existencia", "NO_APLICA");
    pesos.remove("/dos");

    ResultadoCalculoIc resultado = service.calcular(entrada());

    numero("100", resultado.indiceCalculado());
    numero("2", resultado.sumaPesos());
    assertEquals(1, resultado.desglose().size());
  }

  @Test
  void todosNoAplicaRechazanElCalculo() {
    agregarElemento("uno", "NO_PRESENTA", "1");
    ((ObjectNode) datos.get("uno")).put("existencia", "NO_APLICA");

    error("sin_elementos_evaluados_ic");
  }

  @Test
  void catalogoSinEvaluacionesRechazaElCalculo() {
    error("sin_elementos_evaluados_ic");
  }

  @Test
  void elementoSinPesoIdentificaLaReferencia() {
    agregarElemento("uno", "LEVE", "1");
    pesos.remove("/uno");

    ApiException exception = error("configuracion_ic_incompleta");

    assertTrue(exception.getMessage().contains("/uno"));
  }

  @Test
  void severidadSinCalibrarIdentificaElNivel() {
    agregarElemento("uno", "LEVE", "1");
    ((ObjectNode) reglas.get("escala_severidad")).putNull("LEVE");

    ApiException exception = error("configuracion_ic_incompleta");

    assertTrue(exception.getMessage().contains("LEVE"));
  }

  @ParameterizedTest
  @CsvSource({"-1", "4.01"})
  void rechazaCalificacionesFueraDeRango(String calificacion) {
    agregarElemento("uno", "LEVE", "1");
    ((ObjectNode) reglas.get("escala_severidad")).put("LEVE", new BigDecimal(calificacion));

    error("calificacion_ic_fuera_de_rango");
  }

  @Test
  void rechazaLaRutaDeUnElementoFaltante() {
    agregarElemento("uno", "LEVE", "1");
    datos.remove("uno");

    ApiException exception = error("datos_ic_incompletos");

    assertTrue(exception.getMessage().contains("/uno"));
  }

  @Test
  void repetiblesUsanLaReglaDeSuVersion() {
    agregarPilas();

    ResultadoCalculoIc maximo = service.calcular(entrada());

    numero("0", maximo.indiceCalculado());
    assertEquals(2, maximo.desglose().getFirst().instancias().size());
    assertEquals(PILA_UNO, maximo.desglose().getFirst().instancias().getFirst().instanciaId());

    ((ObjectNode) reglas.get("agregacion")).put("instancias_repetibles", "PROMEDIO");

    ResultadoCalculoIc promedio = service.calcular(entrada());

    numero("50", promedio.indiceCalculado());
    assertEquals(EstadoCondicion.REGULAR, promedio.estadoCalculado());
    assertNotEquals(maximo.hashEntrada(), promedio.hashEntrada());
  }

  @Test
  void listaRepetibleVaciaSeExcluyePeroUnaFaltanteSeRechaza() {
    agregarPilas();
    agregarElemento("uno", "NO_PRESENTA", "1");

    datos.putArray("pilas");

    ResultadoCalculoIc resultado = service.calcular(entrada());

    numero("100", resultado.indiceCalculado());
    numero("1", resultado.sumaPesos());

    datos.remove("pilas");

    error("datos_ic_incompletos");
  }

  @Test
  void rechazaInstanciasRepetiblesDuplicadas() {
    agregarPilas();

    ((ObjectNode) datos.get("pilas").get(1)).put("instancia_id", PILA_UNO.toString());

    error("datos_ic_invalidos");
  }

  @Test
  void condicionCriticaFuerzaMaloSinCambiarElIndice() {
    agregarElemento("uno", "LEVE", "1");

    ObjectNode regla =
        ((com.fasterxml.jackson.databind.node.ArrayNode) reglas.get("reglas_anulacion"))
            .addObject();

    regla.put("id", "critica-prueba");
    regla.put("descripcion", "Condicion critica de prueba");
    regla.put("estado_resultante", "MALO");
    regla.putNull("pendiente");
    regla.putArray("elementos").add("/uno");

    ObjectNode condicion = regla.putObject("condicion");
    condicion.putArray("indicadores").add("dano");
    condicion.putArray("algun_valor_en").add("LEVE");

    ResultadoCalculoIc resultado = service.calcular(entrada());

    numero("75", resultado.indiceCalculado());
    assertEquals(EstadoCondicion.BUENO, resultado.estadoPorRango());
    assertEquals(EstadoCondicion.MALO, resultado.estadoCalculado());
    assertEquals(1, resultado.condicionesAnulacion().size());
    assertEquals("critica-prueba", resultado.condicionesAnulacion().getFirst().reglaId());
    assertEquals("/uno", resultado.condicionesAnulacion().getFirst().elementoRef());
  }

  @Test
  void ausenciaPortanteActivaLaReglaDeLaVersion() {
    agregarElemento("uno", "NO_PRESENTA", "1");
    ((ObjectNode) datos.get("uno")).put("existencia", "AUSENTE");
    ((ObjectNode) catalogo.get("elementos").get(0)).put("portante", true);

    ObjectNode regla =
        ((com.fasterxml.jackson.databind.node.ArrayNode) reglas.get("reglas_anulacion"))
            .addObject();

    regla.put("id", "ausencia-portante");
    regla.put("descripcion", "Ausencia total de elemento portante");
    regla.put("estado_resultante", "MALO");
    regla.putNull("pendiente");
    regla.putArray("elementos").add("/uno");
    regla.putObject("condicion").put("campo", "existencia").put("igual_a", "AUSENTE");

    ResultadoCalculoIc resultado = service.calcular(entrada());

    assertEquals(EstadoCondicion.MALO, resultado.estadoCalculado());
    assertEquals("AUSENTE", resultado.condicionesAnulacion().getFirst().valorDetectado());
  }

  @Test
  void mismaEntradaProduceElMismoResultado() {
    agregarElemento("uno", "MODERADO", "3");

    ResultadoCalculoIc primero = service.calcular(entrada());
    ResultadoCalculoIc segundo = service.calcular(entrada());

    assertEquals(primero, segundo);
    assertEquals(VERSION, primero.versionFormularioId());
    assertEquals("ic-hu018-prueba-v1", primero.versionAlgoritmo());
    assertTrue(primero.hashEntrada().matches("[0-9a-f]{64}"));
    assertFalse(primero.desglose().isEmpty());
  }

  @Test
  void cambiarLosDatosCambiaLaHuella() {
    agregarElemento("uno", "LEVE", "1");
    ResultadoCalculoIc primero = service.calcular(entrada());

    ((ObjectNode) datos.get("uno")).put("dano", "SEVERO");
    ResultadoCalculoIc segundo = service.calcular(entrada());

    assertNotEquals(primero.hashEntrada(), segundo.hashEntrada());
    assertNotEquals(primero.indiceCalculado(), segundo.indiceCalculado());
  }

  @Test
  void rechazaUnaVersionDeAlgoritmoIncongruente() {
    agregarElemento("uno", "LEVE", "1");
    reglas.put("version_algoritmo", "otro-algoritmo");

    error("version_ic_incongruente");
  }

  private EntradaCalculoIc entrada() {
    return new EntradaCalculoIc(
        VERSION,
        "sieca-v1",
        "ic-hu018-prueba-v1",
        datos,
        json.createObjectNode(),
        catalogo,
        reglas,
        pesos);
  }

  private void agregarElemento(String nombre, String severidad, String peso) {
    datos.putObject(nombre).put("existencia", "PRESENTE").put("dano", severidad);

    ObjectNode definicion =
        ((com.fasterxml.jackson.databind.node.ArrayNode) catalogo.get("elementos")).addObject();

    definicion.put("elemento_ref", "/" + nombre);
    definicion.put("repetible", false);
    definicion.put("portante", false);
    definicion.putArray("indicadores").add("dano");

    pesos.put("/" + nombre, new BigDecimal(peso));
  }

  private void agregarPilas() {
    var pilas = datos.putArray("pilas");

    pilas
        .addObject()
        .put("instancia_id", PILA_UNO.toString())
        .putObject("elemento")
        .put("existencia", "PRESENTE")
        .put("dano", "NO_PRESENTA");

    pilas
        .addObject()
        .put("instancia_id", PILA_DOS.toString())
        .putObject("elemento")
        .put("existencia", "PRESENTE")
        .put("dano", "SEVERO");

    ObjectNode definicion =
        ((com.fasterxml.jackson.databind.node.ArrayNode) catalogo.get("elementos")).addObject();

    definicion.put("elemento_ref", "/pilas/elemento");
    definicion.put("repetible", true);
    definicion.put("portante", true);
    definicion.putArray("indicadores").add("dano");

    pesos.put("/pilas/elemento", BigDecimal.ONE);
  }

  private ApiException error(String codigo) {
    ApiException exception = assertThrows(ApiException.class, () -> service.calcular(entrada()));

    assertEquals(codigo, exception.getCode());
    return exception;
  }

  private void numero(String esperado, BigDecimal actual) {
    assertEquals(0, new BigDecimal(esperado).compareTo(actual));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "entrada", "datosNulos", "datosLista", "esquemaNulo", "esquemaLista",
        "catalogoNulo", "catalogoLista", "reglasNulas", "reglasLista", "pesosNulos"
      })
  void rechazaComponentesIncompletosDeEntrada(String caso) {
    EntradaCalculoIc base = entrada();
    JsonNode datosEntrada = base.datos();
    JsonNode esquemaEntrada = base.esquema();
    JsonNode catalogoEntrada = base.catalogoElementos();
    JsonNode reglasEntrada = base.reglasEvaluacion();
    Map<String, BigDecimal> pesosEntrada = base.pesos();
    switch (caso) {
      case "datosNulos" -> datosEntrada = null;
      case "datosLista" -> datosEntrada = json.createArrayNode();
      case "esquemaNulo" -> esquemaEntrada = null;
      case "esquemaLista" -> esquemaEntrada = json.createArrayNode();
      case "catalogoNulo" -> catalogoEntrada = null;
      case "catalogoLista" -> catalogoEntrada = json.createArrayNode();
      case "reglasNulas" -> reglasEntrada = null;
      case "reglasLista" -> reglasEntrada = json.createArrayNode();
      case "pesosNulos" -> pesosEntrada = null;
      default -> {}
    }
    EntradaCalculoIc invalida =
        caso.equals("entrada")
            ? null
            : new EntradaCalculoIc(
                VERSION,
                base.codigoFormulario(),
                base.versionAlgoritmo(),
                datosEntrada,
                esquemaEntrada,
                catalogoEntrada,
                reglasEntrada,
                pesosEntrada);
    ApiException exception = assertThrows(ApiException.class, () -> service.calcular(invalida));
    assertEquals("entrada_ic_invalida", exception.getCode());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rechazaCatalogoSinListaValida(boolean ausente) {
    if (ausente) catalogo.remove("elementos");
    else catalogo.put("elementos", "texto");
    error("configuracion_ic_incompleta");
  }

  @Test
  void rechazaReferenciasDuplicadasEnElCatalogo() {
    agregarElemento("uno", "NO_PRESENTA", "1");
    ((ArrayNode) catalogo.get("elementos")).add(catalogo.get("elementos").get(0).deepCopy());
    error("configuracion_ic_invalida");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ausente", "numero", "vacio"})
  void rechazaReferenciaDeElementoIncompleta(String caso) {
    agregarElemento("uno", "NO_PRESENTA", "1");
    ObjectNode definicion = (ObjectNode) catalogo.get("elementos").get(0);
    switch (caso) {
      case "ausente" -> definicion.remove("elemento_ref");
      case "numero" -> definicion.put("elemento_ref", 123);
      default -> definicion.put("elemento_ref", " ");
    }
    error("configuracion_ic_incompleta");
  }

  @Test
  void rechazaCatalogoDeOtroFormulario() {
    catalogo.put("formulario", "otro-formulario");
    error("version_ic_incongruente");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void rechazaPesosNoPositivos(String peso) {
    agregarElemento("uno", "LEVE", peso);
    error("configuracion_ic_incompleta");
  }

  @Test
  void rechazaExistenciaDesconocida() {
    agregarElemento("uno", "LEVE", "1");
    ((ObjectNode) datos.get("uno")).put("existencia", "DESCONOCIDA");
    error("datos_ic_invalidos");
  }

  @Test
  void ausenciaNoPortanteUsaSuCalificacionConfigurada() {
    agregarElemento("uno", "NO_PRESENTA", "1");
    ((ObjectNode) datos.get("uno")).put("existencia", "AUSENTE");
    ResultadoCalculoIc resultado = service.calcular(entrada());
    numero("0", resultado.indiceCalculado());
    numero("4", resultado.desglose().getFirst().calificacionAgregada());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ausentes", "texto", "vacios"})
  void rechazaConfiguracionDeIndicadoresIncompleta(String caso) {
    agregarElemento("uno", "LEVE", "1");
    ObjectNode definicion = (ObjectNode) catalogo.get("elementos").get(0);
    switch (caso) {
      case "ausentes" -> definicion.remove("indicadores");
      case "texto" -> definicion.put("indicadores", "dano");
      default -> definicion.putArray("indicadores");
    }
    error("configuracion_ic_incompleta");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ausente", "nulo", "listaVacia"})
  void rechazaIndicadorSinEvaluacion(String caso) {
    agregarElemento("uno", "LEVE", "1");
    ObjectNode elemento = (ObjectNode) datos.get("uno");
    switch (caso) {
      case "ausente" -> elemento.remove("dano");
      case "nulo" -> elemento.putNull("dano");
      default -> elemento.putArray("dano");
    }
    error("datos_ic_incompletos");
  }

  @ParameterizedTest
  @ValueSource(strings = {"otros", "danos"})
  void omiteIndicadoresOpcionalesAusentesONulos(String nombre) {
    agregarElemento("uno", "NO_PRESENTA", "1");
    ((ArrayNode) catalogo.get("elementos").get(0).get("indicadores")).add(nombre);
    numero("100", service.calcular(entrada()).indiceCalculado());
    ((ObjectNode) datos.get("uno")).putNull(nombre);
    numero("100", service.calcular(entrada()).indiceCalculado());
  }

  @ParameterizedTest
  @ValueSource(strings = {"booleano", "texto", "objeto", "existe"})
  void rechazaConversionDeIndicadorNoConfigurada(String caso) {
    agregarElemento("uno", "LEVE", "1");
    ObjectNode elemento = (ObjectNode) datos.get("uno");
    if (caso.equals("existe")) {
      ((ObjectNode) catalogo.get("elementos").get(0)).putArray("indicadores").add("existe");
      elemento.put("existe", true);
    } else if (caso.equals("booleano")) elemento.put("dano", true);
    else if (caso.equals("texto")) elemento.put("dano", "DESCONOCIDO");
    else elemento.putObject("dano");
    error("configuracion_ic_invalida");
  }

  @Test
  void excluyeSoloLasReferenciasConfiguradas() {
    agregarElemento("uno", "SEVERO", "100");
    agregarElemento("dos", "NO_PRESENTA", "1");
    reglas.putArray("elementos_excluidos_ic").add("/uno");
    ResultadoCalculoIc resultado = service.calcular(entrada());
    numero("100", resultado.indiceCalculado());
    numero("1", resultado.sumaPesos());
    assertEquals("/dos", resultado.desglose().getFirst().elementoRef());
  }

  @Test
  void listaDeExclusionesVaciaNoExcluyeElementos() {
    agregarElemento("uno", "LEVE", "1");
    reglas.putArray("elementos_excluidos_ic");
    numero("75", service.calcular(entrada()).indiceCalculado());
  }

  @Test
  void rechazaExclusionesQueNoSonUnaLista() {
    agregarElemento("uno", "LEVE", "1");
    reglas.put("elementos_excluidos_ic", "texto");
    error("configuracion_ic_invalida");
  }

  @Test
  void omiteElementosDelCauceCuandoNoAplica() {
    agregarElemento("uno", "NO_PRESENTA", "1");
    agregarDefinicionCauce();
    datos.putObject("caracteristicas_cauce").put("aplica", false);
    numero("100", service.calcular(entrada()).indiceCalculado());
  }

  @Test
  void textoFalseNoEquivaleAUnCauceNoAplicable() {
    agregarElemento("uno", "NO_PRESENTA", "1");
    agregarDefinicionCauce();
    datos.putObject("caracteristicas_cauce").put("aplica", "false");
    error("datos_ic_incompletos");
  }

  @ParameterizedTest
  @ValueSource(strings = {"nulo", "missing", "escalar", "intermedio"})
  void rechazaRutasSinUnObjetoEvaluado(String caso) {
    agregarElemento("uno", "LEVE", "1");
    switch (caso) {
      case "nulo" -> datos.putNull("uno");
      case "missing" -> datos.set("uno", MissingNode.getInstance());
      case "escalar" -> datos.put("uno", 123);
      default -> {
        ((ObjectNode) catalogo.get("elementos").get(0)).put("elemento_ref", "/uno/hijo");
        datos.put("uno", 123);
      }
    }
    error(
        caso.equals("nulo") || caso.equals("missing")
            ? "datos_ic_incompletos"
            : "datos_ic_invalidos");
  }

  @Test
  void listaVaciaDeUnElementoNoRepetibleEsIncompleta() {
    agregarPilas();
    ((ObjectNode) catalogo.get("elementos").get(0)).put("repetible", false);
    datos.putArray("pilas");
    error("datos_ic_incompletos");
  }

  private void agregarDefinicionCauce() {
    ObjectNode definicion = ((ArrayNode) catalogo.get("elementos")).addObject();
    definicion.put("elemento_ref", "/caracteristicas_cauce/condicion");
    definicion.put("repetible", false);
    definicion.put("portante", false);
    definicion.putArray("indicadores").add("condiciones");
  }
}
