package gt.usac.cunoc.sgp.formulario.validation;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.service.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlantillaSiecaProvisionalTest {
  private final ObjectMapper json = new ObjectMapper();

  private JsonNode cargar(String nombre) throws Exception {
    try (var entrada = getClass().getResourceAsStream("/formulario/sieca-v1/" + nombre)) {
      return json.readTree(entrada);
    }
  }

  @Test
  void plantillaCompletaSinNulosConPesosPortantesMayores() throws Exception {
    var catalogo = cargar("elementos-ic.json");
    sinNulos(catalogo);
    assertThat(catalogo.path("elementos")).hasSize(45);
    BigDecimal menorPortante = BigDecimal.valueOf(999999);
    BigDecimal mayorNoPortante = BigDecimal.ZERO;
    for (var elemento : catalogo.path("elementos")) {
      var peso = elemento.path("peso").decimalValue();
      assertThat(peso).isPositive();
      assertThat(peso.scale()).isLessThanOrEqualTo(6);
      assertThat(peso.precision() - peso.scale()).isLessThanOrEqualTo(6);
      if (elemento.path("portante").asBoolean()) menorPortante = menorPortante.min(peso);
      else mayorNoPortante = mayorNoPortante.max(peso);
    }
    assertThat(menorPortante).isGreaterThan(mayorNoPortante);
    var reglas = catalogo.path("reglas_evaluacion_ic");
    assertThat(reglas.path("escala_severidad"))
        .isEqualTo(json.readTree("{\"NO_PRESENTA\":0,\"LEVE\":1,\"MODERADO\":2,\"SEVERO\":4}"));
    assertThat(reglas.path("rangos_estado"))
        .isEqualTo(json.readTree("{\"BUENO\":75,\"REGULAR\":50,\"MALO\":0}"));
    ValidadorReglasCriticasIc.validarCatalogo(reglas, catalogo);
    assertThat(reglas.at("/reglas_anulacion/0/pendiente").asText()).isNotBlank();
    assertThat(reglas.at("/reglas_anulacion/2/pendiente").asText()).isNotBlank();
  }

  @Test
  void calculaConLaPlantillaSinReemplazarNingunValor() throws Exception {
    var catalogo = cargar("elementos-ic.json");
    var reglas = catalogo.path("reglas_evaluacion_ic");
    var pesos = new LinkedHashMap<String, BigDecimal>();
    catalogo
        .path("elementos")
        .forEach(e -> pesos.put(e.path("elemento_ref").asText(), e.path("peso").decimalValue()));
    var agregador = new AgregadorCalificacionesIc();
    var resolver = new ResolverElementosIc();
    var conversor = new ConversorIndicadoresIc(agregador);
    var calculo =
        new CalculoIndiceCondicionService(
            resolver,
            conversor,
            new ConversorMedicionesIc(conversor),
            agregador,
            new EvaluadorCondicionesCriticasIc(resolver),
            new HuellaEntradaIc(),
            new MotorPonderacionIc(agregador));
    var datos = cargar("ejemplo-inspeccion.json");
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            datos.at("/subestructura/estribo_entrada/socavacion"))
        .put("aguas_arriba", "CON_EXPOSICION");
    var resultado =
        calculo.calcular(
            new EntradaCalculoIc(
                UUID.randomUUID(),
                "sieca-v1",
                reglas.path("version_algoritmo").asText(),
                datos,
                cargar("esquema.schema.json"),
                catalogo,
                reglas,
                pesos));
    assertThat(resultado.indiceCalculado()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
    assertThat(resultado.estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
    assertThat(resultado.condicionesAnulacion()).anyMatch(c -> "RN-IC-05a".equals(c.reglaId()));
  }

  private void sinNulos(JsonNode nodo) {
    assertThat(nodo.isNull()).isFalse();
    if (nodo.isContainerNode()) nodo.forEach(this::sinNulos);
  }
}
