package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc.CondicionAnulacion;
import gt.usac.cunoc.sgp.inspeccion.service.ResolverElementosIc.ElementoResuelto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EvaluadorCondicionesCriticasIc {

  private final ResolverElementosIc resolver;

  public EvaluadorCondicionesCriticasIc(ResolverElementosIc resolver) {
    this.resolver = resolver;
  }

  public List<CondicionAnulacion> evaluar(JsonNode datos, JsonNode reglas) {
    if (reglas == null || !reglas.isObject()) {
      throw error("Faltan las reglas de evaluacion del IC");
    }

    JsonNode anulaciones = reglas.get("reglas_anulacion");

    if (anulaciones == null || !anulaciones.isArray()) {
      throw error("Falta configurar reglas_anulacion como una lista");
    }

    List<CondicionAnulacion> resultados = new ArrayList<>();

    for (JsonNode regla : anulaciones) {
      String id = texto(regla, "id");
      String descripcion = texto(regla, "descripcion");

      if (!"MALO".equals(texto(regla, "estado_resultante"))) {
        throw error("La regla " + id + " debe tener estado_resultante MALO");
      }

      JsonNode pendiente = regla.get("pendiente");

      if (pendiente != null && !pendiente.isNull()) {
        throw error("La regla " + id + " tiene una decision pendiente de calibracion");
      }

      JsonNode elementos = lista(regla, "elementos");
      JsonNode condicion = regla.get("condicion");

      if (condicion == null || !condicion.isObject() || condicion.isEmpty()) {
        throw error("Falta configurar la condicion de la regla " + id);
      }

      if (condicion.has("campo") || condicion.has("indicador") || condicion.has("indicadores")) {
        evaluarCondicion(datos, elementos, condicion, id, descripcion, resultados);
      } else {
        var grupos = condicion.fields();

        while (grupos.hasNext()) {
          var grupo = grupos.next();
          JsonNode definicion = grupo.getValue();

          if (!definicion.isObject()) {
            throw error("El grupo " + grupo.getKey() + " de la regla " + id + " es invalido");
          }

          JsonNode referencias = lista(definicion, "elementos");

          for (JsonNode referencia : referencias) {
            if (!contiene(elementos, referencia.asText())) {
              throw error(
                  "El grupo de la regla "
                      + id
                      + " contiene un elemento no declarado: "
                      + referencia.asText());
            }
          }

          evaluarCondicion(datos, referencias, definicion, id, descripcion, resultados);
        }
      }
    }

    return List.copyOf(resultados);
  }

  private void evaluarCondicion(
      JsonNode datos,
      JsonNode referencias,
      JsonNode condicion,
      String reglaId,
      String descripcion,
      List<CondicionAnulacion> resultados) {

    List<String> indicadores = new ArrayList<>();
    List<String> valores = new ArrayList<>();

    if (condicion.has("campo")) {
      indicadores.add(texto(condicion, "campo"));
      valores.add(texto(condicion, "igual_a"));
    } else {
      if (condicion.has("indicador")) {
        indicadores.add(texto(condicion, "indicador"));
      } else {
        for (JsonNode indicador : lista(condicion, "indicadores")) {
          indicadores.add(textoValor(indicador, "indicador de " + reglaId));
        }
      }

      for (JsonNode valor : lista(condicion, "algun_valor_en")) {
        valores.add(textoValor(valor, "valor de " + reglaId));
      }
    }

    for (JsonNode referencia : referencias) {
      String elementoRef = textoValor(referencia, "elemento de " + reglaId);

      for (ElementoResuelto elemento : resolver.resolver(datos, elementoRef)) {
        if ("NO_APLICA".equals(elemento.datos().path("existencia").asText())) {
          continue;
        }

        for (String indicador : indicadores) {
          JsonNode valor = elemento.datos().get(indicador);

          if (valor != null && valor.isTextual() && valores.contains(valor.textValue())) {
            resultados.add(
                new CondicionAnulacion(
                    reglaId,
                    descripcion,
                    elementoRef,
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

  private JsonNode lista(JsonNode objeto, String campo) {
    JsonNode valor = objeto.get(campo);

    if (valor == null || !valor.isArray() || valor.isEmpty()) {
      throw error("Falta configurar una lista no vacia en " + campo);
    }

    return valor;
  }

  private String texto(JsonNode objeto, String campo) {
    return textoValor(objeto.get(campo), campo);
  }

  private String textoValor(JsonNode valor, String referencia) {
    if (valor == null || !valor.isTextual() || valor.textValue().isBlank()) {
      throw error("Falta configurar un texto valido en " + referencia);
    }

    return valor.textValue();
  }

  private boolean contiene(JsonNode elementos, String referencia) {
    for (JsonNode elemento : elementos) {
      if (referencia.equals(elemento.asText())) {
        return true;
      }
    }

    return false;
  }

  private ApiException error(String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "reglas_anulacion_ic_invalidas",
        "Reglas de anulacion del IC invalidas",
        detalle);
  }
}
