package gt.usac.cunoc.sgp.inspeccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalculoIcSiecaTest {

  private final ObjectMapper json = new ObjectMapper();

  private CalculoIndiceCondicionService service;
  private JsonNode datos;
  private JsonNode esquema;
  private JsonNode catalogo;
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

    datos = cargar("/formulario/sieca-v1/ejemplo-inspeccion.json");
    esquema = cargar("/formulario/sieca-v1/esquema.schema.json");
    catalogo = cargar("/formulario/sieca-v1/elementos-ic.json");

    reglas =
        (ObjectNode)
            json.readTree(
                """
                                {
                                  "version_algoritmo": "ic-sieca-prueba-v1",
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
                                    "booleanos": {
                                      "junta.falta_elemento=true": 4,
                                      "junta.falta_elemento=false": 0,
                                      "junta.junta_limpia=true": 0,
                                      "junta.junta_limpia=false": 2,
                                      "senal.existe=true": 0,
                                      "senal.existe=false": 4,
                                      "losa.buen_estado=true": 0,
                                      "losa.buen_estado=false": 0,
                                      "drenaje_aledano.existe=true": 0,
                                      "drenaje_aledano.existe=false": 4
                                    },
                                    "enumerados": {
                                      "drenaje.estado": {
                                        "LIMPIOS": 0,
                                        "OBSTRUIDOS": 4
                                      },
                                      "socavacion.nivel": {
                                        "NO_HAY": 0,
                                        "SIN_EXPOSICION": 1,
                                        "CON_EXPOSICION": 4,
                                        "ASENTAMIENTO_ESTRUCTURA": 4
                                      },
                                      "condicionesCauce": {
                                        "LIMPIO": 0,
                                        "ASOLVADO_SEDIMENTADO": 1,
                                        "EROSIONADO": 2,
                                        "OTRO": 4
                                      },
                                      "estado_bueno_malo": {
                                        "BUENO": 0,
                                        "MALO": 4
                                      }
                                    },
                                    "numericos": {
                                      "golpeado_longitud_m": {
                                        "umbrales_m": {
                                          "LEVE": 1,
                                          "MODERADO": 3,
                                          "SEVERO": 5000
                                        }
                                      },
                                      "golpeados_cantidad": {
                                        "umbrales": {
                                          "LEVE": 1,
                                          "MODERADO": 3,
                                          "SEVERO": 10000
                                        }
                                      }
                                    },
                                    "otros_y_danos": "Severidad declarada"
                                  },
                                  "reglas_anulacion": []
                                }
                                """);

    JsonNode anulaciones =
        catalogo.path("reglas_evaluacion_ic").path("reglas_anulacion").deepCopy();

    for (JsonNode regla : anulaciones) {
      ((ObjectNode) regla).putNull("pendiente");
    }

    reglas.set("reglas_anulacion", anulaciones);

    pesos = new LinkedHashMap<>();

    for (JsonNode elemento : catalogo.get("elementos")) {
      pesos.put(elemento.get("elemento_ref").asText(), BigDecimal.ONE);
    }
  }

  @Test
  void elEjemploCumpleElEsquemaRealDelFormulario() {
    var validador =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(esquema);

    var errores = validador.validate(datos);

    assertTrue(errores.isEmpty(), () -> errores.toString());
  }

  @Test
  void calculaElEjemploRealConDesgloseYAnulacionCritica() {
    var resultado = service.calcular(entrada(reglas, pesos));

    numero("38", resultado.sumaPesos());
    numero("56", resultado.sumaCalificacionesPonderadas());
    numero("63.15789", resultado.indiceCalculado());

    assertEquals(38, resultado.desglose().size());
    assertEquals(EstadoCondicion.REGULAR, resultado.estadoPorRango());
    assertEquals(EstadoCondicion.MALO, resultado.estadoCalculado());

    assertTrue(
        resultado.condicionesAnulacion().stream()
            .anyMatch(item -> item.reglaId().equals("RN-IC-05b")));

    assertTrue(
        resultado.condicionesAnulacion().stream()
            .anyMatch(
                item ->
                    item.elementoRef().equals("/subestructura/pilas/socavacion")
                        && item.instanciaId() != null));
  }

  @Test
  void elEjemploRealEsReproducible() {
    var entrada = entrada(reglas, pesos);

    assertEquals(service.calcular(entrada), service.calcular(entrada));
  }

  @Test
  void elCatalogoOriginalSinCalibrarNoProduceUnResultado() {
    JsonNode reglasOriginales = catalogo.get("reglas_evaluacion_ic");

    EntradaCalculoIc entrada =
        new EntradaCalculoIc(
            UUID.fromString("01900000-0000-7000-8000-000000000001"),
            "sieca-v1",
            reglasOriginales.get("version_algoritmo").asText(),
            datos,
            esquema,
            catalogo,
            reglasOriginales,
            Map.of());

    ApiException exception = assertThrows(ApiException.class, () -> service.calcular(entrada));

    assertEquals("configuracion_ic_incompleta", exception.getCode());
  }

  private EntradaCalculoIc entrada(
      JsonNode configuracion, Map<String, BigDecimal> pesosConfigurados) {

    return new EntradaCalculoIc(
        UUID.fromString("01900000-0000-7000-8000-000000000001"),
        "sieca-v1",
        configuracion.get("version_algoritmo").asText(),
        datos,
        esquema,
        catalogo,
        configuracion,
        pesosConfigurados);
  }

  private JsonNode cargar(String ruta) throws Exception {
    try (var archivo = getClass().getResourceAsStream(ruta)) {
      assertNotNull(archivo, "No se encontro el recurso " + ruta);
      return json.readTree(archivo);
    }
  }

  private void numero(String esperado, BigDecimal actual) {
    assertEquals(0, new BigDecimal(esperado).compareTo(actual));
  }
}
