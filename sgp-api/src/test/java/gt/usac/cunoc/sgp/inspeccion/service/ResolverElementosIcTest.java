package gt.usac.cunoc.sgp.inspeccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ResolverElementosIcTest {

  private static final String ID = "01900000-0000-7000-8000-000000000001";

  private final ObjectMapper json = new ObjectMapper();
  private final ResolverElementosIc service = new ResolverElementosIc();

  @Test
  void resuelveUnElementoSimple() throws Exception {
    var datos = json.readTree("{\"grupo\":{\"elemento\":{\"existencia\":\"PRESENTE\"}}}");

    var resultado = service.resolver(datos, "/grupo/elemento");

    assertEquals(1, resultado.size());
    assertEquals("/grupo/elemento", resultado.getFirst().rutaDatos());
    assertNull(resultado.getFirst().instanciaId());
    assertEquals("PRESENTE", resultado.getFirst().datos().get("existencia").asText());
  }

  @Test
  void resuelveCaracteresEscapadosDeJsonPointer() {
    var datos = json.createObjectNode();
    datos.putObject("grupo/especial").putObject("elemento~uno");

    var resultado = service.resolver(datos, "/grupo~1especial/elemento~0uno");

    assertEquals(1, resultado.size());
    assertEquals("/grupo~1especial/elemento~0uno", resultado.getFirst().rutaDatos());
  }

  @Test
  void conservaElIdentificadorYLaRutaDeLaInstancia() {
    var datos = json.createObjectNode();
    datos.putArray("pilas").addObject().put("instancia_id", ID).putObject("columnas");

    var resultado = service.resolver(datos, "/pilas/columnas");

    assertEquals(1, resultado.size());
    assertEquals(UUID.fromString(ID), resultado.getFirst().instanciaId());
    assertEquals("/pilas/0/columnas", resultado.getFirst().rutaDatos());
  }

  @Test
  void tambienResuelveUnaReferenciaQueTerminaEnLaLista() {
    var datos = json.createObjectNode();
    datos.putArray("pilas").addObject().put("instancia_id", ID);

    var resultado = service.resolver(datos, "/pilas");

    assertEquals(1, resultado.size());
    assertEquals("/pilas/0", resultado.getFirst().rutaDatos());
  }

  @Test
  void unaListaVaciaDevuelveUnaColeccionVacia() {
    var datos = json.createObjectNode();
    datos.putArray("pilas");

    assertTrue(service.resolver(datos, "/pilas/columnas").isEmpty());
  }

  @Test
  void unaPropiedadAusenteDevuelveUnaColeccionVacia() {
    assertTrue(service.resolver(json.createObjectNode(), "/elemento").isEmpty());
  }

  @Test
  void unaPropiedadNulaDevuelveUnaColeccionVacia() {
    var datos = json.createObjectNode();
    datos.putNull("elemento");

    assertTrue(service.resolver(datos, "/elemento").isEmpty());
  }

  @Test
  void unaPropiedadMissingDevuelveUnaColeccionVacia() {
    var datos = json.createObjectNode();
    datos.set("elemento", MissingNode.getInstance());

    assertTrue(service.resolver(datos, "/elemento").isEmpty());
  }

  @Test
  void rechazaDatosNulos() {
    error(() -> service.resolver(null, "/elemento"));
  }

  @Test
  void rechazaDatosQueNoSonUnObjeto() {
    error(() -> service.resolver(json.createArrayNode(), "/elemento"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"/", "elemento", " "})
  void rechazaReferenciasInvalidas(String referencia) {
    error(() -> service.resolver(json.createObjectNode(), referencia));
  }

  @Test
  void rechazaUnElementoFinalQueNoEsObjeto() {
    var datos = json.createObjectNode().put("elemento", 1);

    error(() -> service.resolver(datos, "/elemento"));
  }

  @Test
  void rechazaRecorrerUnaPropiedadEscalar() {
    var datos = json.createObjectNode().put("grupo", "texto");

    error(() -> service.resolver(datos, "/grupo/elemento"));
  }

  @Test
  void rechazaUnaInstanciaQueNoEsObjeto() {
    var datos = json.createObjectNode();
    datos.putArray("pilas").add("texto");

    error(() -> service.resolver(datos, "/pilas/columnas"));
  }

  @Test
  void rechazaUnaInstanciaSinIdentificador() {
    var datos = json.createObjectNode();
    datos.putArray("pilas").addObject().putObject("columnas");

    error(() -> service.resolver(datos, "/pilas/columnas"));
  }

  @Test
  void rechazaUnIdentificadorConTipoIncorrecto() {
    var datos = json.createObjectNode();
    datos.putArray("pilas").addObject().put("instancia_id", 123);

    error(() -> service.resolver(datos, "/pilas/columnas"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"no-es-uuid", "", "1-1-1-1-1"})
  void rechazaIdentificadoresInvalidosONoCanonicos(String id) {
    var datos = json.createObjectNode();
    datos.putArray("pilas").addObject().put("instancia_id", id);

    error(() -> service.resolver(datos, "/pilas/columnas"));
  }

  @Test
  void rechazaIdentificadoresDuplicadosEnLaMismaLista() {
    var datos = json.createObjectNode();
    var pilas = datos.putArray("pilas");

    pilas.addObject().put("instancia_id", ID).putObject("columnas");
    pilas.addObject().put("instancia_id", ID).putObject("columnas");

    ApiException exception = error(() -> service.resolver(datos, "/pilas/columnas"));

    assertTrue(exception.getMessage().contains("duplicada"));
  }

  private ApiException error(org.junit.jupiter.api.function.Executable operacion) {

    ApiException exception = assertThrows(ApiException.class, operacion);
    assertEquals("datos_ic_invalidos", exception.getCode());

    return exception;
  }
}
