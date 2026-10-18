package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.formulario.validation.ValidadorReglasCriticasIc;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc.CondicionAnulacion;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvaluadorCondicionesCriticasIc {
  private final ResolverElementosIc resolver;

  public EvaluadorCondicionesCriticasIc(ResolverElementosIc resolver) {
    this.resolver = resolver;
  }

  public List<CondicionAnulacion> evaluar(JsonNode datos, JsonNode reglas) {
    var condiciones = ValidadorReglasCriticasIc.validar(reglas);
    List<CondicionAnulacion> resultados = new ArrayList<>();
    for (var condicion : condiciones) {
      for (String referencia : condicion.elementos()) {
        for (var elemento : resolver.resolver(datos, referencia)) {
          if ("NO_APLICA".equals(elemento.datos().path("existencia").asText())) {
            continue;
          }
          for (String indicador : condicion.indicadores()) {
            JsonNode valor = elemento.datos().get(indicador);
            if (valor != null
                && valor.isTextual()
                && condicion.valores().contains(valor.textValue())) {
              resultados.add(
                  new CondicionAnulacion(
                      condicion.id(),
                      condicion.descripcion(),
                      referencia,
                      elemento.instanciaId(),
                      elemento.rutaDatos(),
                      indicador,
                      valor.textValue(),
                      EstadoCondicion.MALO));
            }
          }
        }
      }
    }
    return List.copyOf(resultados);
  }
}
