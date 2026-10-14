package gt.usac.cunoc.sgp.inspeccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HuellaEntradaIcTest {

  private static final UUID VERSION = UUID.fromString("01900000-0000-7000-8000-000000000001");

  private final ObjectMapper json = new ObjectMapper();

  private HuellaEntradaIc service;

  @BeforeEach
  void preparar() throws Exception {
    service = new HuellaEntradaIc();
  }

  @Test
  void generaUnHashSha256Hexadecimal() {
    String resultado = service.calcular(entrada(json.createObjectNode(), Map.of()));

    assertTrue(resultado.matches("[0-9a-f]{64}"));
  }

  @Test
  void elOrdenDePropiedadesNoCambiaElHash() throws Exception {
    JsonNode primero = json.readTree("{\"b\":2,\"a\":{\"y\":true,\"x\":\"texto\"}}");
    JsonNode segundo = json.readTree("{\"a\":{\"x\":\"texto\",\"y\":true},\"b\":2}");

    assertEquals(
        service.calcular(entrada(primero, Map.of())), service.calcular(entrada(segundo, Map.of())));
  }

  @Test
  void elOrdenDelMapaDePesosNoCambiaElHash() {
    Map<String, BigDecimal> primero = new LinkedHashMap<>();
    primero.put("/b", new BigDecimal("2"));
    primero.put("/a", BigDecimal.ONE);

    Map<String, BigDecimal> segundo = new LinkedHashMap<>();
    segundo.put("/a", BigDecimal.ONE);
    segundo.put("/b", new BigDecimal("2"));

    assertEquals(
        service.calcular(entrada(json.createObjectNode(), primero)),
        service.calcular(entrada(json.createObjectNode(), segundo)));
  }

  @Test
  void valoresNumericamenteEquivalentesConservanElHash() throws Exception {
    JsonNode primero = json.readTree("{\"valor\":1}");
    JsonNode segundo = json.readTree("{\"valor\":1.00}");

    assertEquals(
        service.calcular(entrada(primero, Map.of("/a", new BigDecimal("2")))),
        service.calcular(entrada(segundo, Map.of("/a", new BigDecimal("2.000")))));
  }

  @Test
  void conservaElOrdenDeLosArreglos() throws Exception {
    JsonNode primero = json.readTree("{\"valores\":[1,2]}");
    JsonNode segundo = json.readTree("{\"valores\":[2,1]}");

    assertNotEquals(
        service.calcular(entrada(primero, Map.of())), service.calcular(entrada(segundo, Map.of())));
  }

  @Test
  void cambiarLosPesosCambiaElHash() {
    JsonNode datos = json.createObjectNode();

    assertNotEquals(
        service.calcular(entrada(datos, Map.of("/a", BigDecimal.ONE))),
        service.calcular(entrada(datos, Map.of("/a", new BigDecimal("2")))));
  }

  @Test
  void cambiarLaVersionCambiaElHash() {
    EntradaCalculoIc original = entrada(json.createObjectNode(), Map.of());

    EntradaCalculoIc otraVersion =
        new EntradaCalculoIc(
            UUID.fromString("01900000-0000-7000-8000-000000000002"),
            original.codigoFormulario(),
            original.versionAlgoritmo(),
            original.datos(),
            original.esquema(),
            original.catalogoElementos(),
            original.reglasEvaluacion(),
            original.pesos());

    assertNotEquals(service.calcular(original), service.calcular(otraVersion));
  }

  @Test
  void cambiarLasReglasCambiaElHash() throws Exception {
    EntradaCalculoIc original = entrada(json.createObjectNode(), Map.of());

    EntradaCalculoIc otrasReglas =
        new EntradaCalculoIc(
            original.versionFormularioId(),
            original.codigoFormulario(),
            original.versionAlgoritmo(),
            original.datos(),
            original.esquema(),
            original.catalogoElementos(),
            json.readTree("{\"regla\":\"otra\"}"),
            original.pesos());

    assertNotEquals(service.calcular(original), service.calcular(otrasReglas));
  }

  @Test
  void normalizaNodosNulosYMissingSinModificarLosDatos() {
    var datos = json.createObjectNode();
    datos.putNull("nulo");
    datos.set("missing", MissingNode.getInstance());
    datos.putArray("lista").add(true).add("texto").addNull();

    JsonNode copia = datos.deepCopy();

    String primero = service.calcular(entrada(datos, Map.of()));
    String segundo = service.calcular(entrada(datos, Map.of()));

    assertEquals(primero, segundo);
    assertEquals(copia, datos);
  }

  @Test
  void permiteRepresentarPesosNulosEnLaHuella() {
    Map<String, BigDecimal> pesos = new LinkedHashMap<>();
    pesos.put("/a", null);

    String resultado = service.calcular(entrada(json.createObjectNode(), pesos));

    assertTrue(resultado.matches("[0-9a-f]{64}"));
  }

  @Test
  void permiteRepresentarCamposAusentesEnLaHuella() {
    EntradaCalculoIc entrada = new EntradaCalculoIc(null, null, null, null, null, null, null, null);

    assertTrue(service.calcular(entrada).matches("[0-9a-f]{64}"));
  }

  @Test
  void rechazaReferenciasNulasEnLosPesos() {
    Map<String, BigDecimal> pesos = new LinkedHashMap<>();
    pesos.put(null, BigDecimal.ONE);

    ApiException exception =
        assertThrows(
            ApiException.class, () -> service.calcular(entrada(json.createObjectNode(), pesos)));

    assertEquals("entrada_ic_invalida", exception.getCode());
  }

  @Test
  void rechazaUnaEntradaNula() {
    ApiException exception = assertThrows(ApiException.class, () -> service.calcular(null));

    assertEquals("entrada_ic_invalida", exception.getCode());
  }

  private EntradaCalculoIc entrada(JsonNode datos, Map<String, BigDecimal> pesos) {
    return new EntradaCalculoIc(
        VERSION,
        "sieca-v1",
        "ic-prueba-v1",
        datos,
        json.createObjectNode(),
        json.createObjectNode(),
        json.createObjectNode(),
        pesos);
  }
}
