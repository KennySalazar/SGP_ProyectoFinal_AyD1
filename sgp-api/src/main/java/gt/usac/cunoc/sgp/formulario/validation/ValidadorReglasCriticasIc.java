package gt.usac.cunoc.sgp.formulario.validation;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class ValidadorReglasCriticasIc {
  private ValidadorReglasCriticasIc() {}

  public record CondicionCritica(
      String id,
      String descripcion,
      List<String> elementos,
      List<String> indicadores,
      List<String> valores) {}

  public static List<CondicionCritica> validar(JsonNode reglas) {
    if (reglas == null || !reglas.isObject()) {
      throw error("Faltan las reglas de evaluacion del IC");
    }
    JsonNode anulaciones = reglas.get("reglas_anulacion");
    if (anulaciones == null || !anulaciones.isArray()) {
      throw error("Falta configurar reglas_anulacion como una lista");
    }
    List<CondicionCritica> condiciones = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode regla : anulaciones) {
      if (!regla.isObject()) {
        throw error("Cada regla de anulacion debe ser un objeto");
      }
      String id = texto(regla.get("id"), "id");
      if (!ids.add(id)) {
        throw error("Identificador de regla duplicado: " + id);
      }
      String descripcion = texto(regla.get("descripcion"), "descripcion de " + id);
      if (!"MALO".equals(texto(regla.get("estado_resultante"), "estado_resultante de " + id))) {
        throw error("La regla " + id + " debe tener estado_resultante MALO");
      }
      if (regla.hasNonNull("pendiente")
          && !(reglas.path("calibracion_provisional").isBoolean()
              && reglas.path("calibracion_provisional").booleanValue()
              && Set.of("RN-IC-05a", "RN-IC-05c").contains(id)
              && regla.path("pendiente").isTextual()
              && !regla.path("pendiente").asText().isBlank())) {
        throw error("La regla " + id + " tiene una decision pendiente de calibracion");
      }
      List<String> elementos = lista(regla.get("elementos"), "elementos de " + id);
      elementos.forEach(ValidadorReglasCriticasIc::validarReferencia);
      JsonNode condicion = regla.get("condicion");
      if (condicion == null || !condicion.isObject() || condicion.isEmpty()) {
        throw error("Falta configurar la condicion de la regla " + id);
      }
      if (directa(condicion)) {
        condiciones.add(condicion(id, descripcion, elementos, condicion));
      } else {
        Set<String> cubiertos = new HashSet<>();
        var grupos = condicion.fields();
        while (grupos.hasNext()) {
          var grupo = grupos.next();
          JsonNode definicion = grupo.getValue();
          if (!definicion.isObject() || !directa(definicion)) {
            throw error("El grupo " + grupo.getKey() + " de la regla " + id + " es invalido");
          }
          List<String> referencias =
              lista(definicion.get("elementos"), "elementos del grupo " + id);
          if (!elementos.containsAll(referencias)) {
            throw error("El grupo de la regla " + id + " contiene un elemento no declarado");
          }
          cubiertos.addAll(referencias);
          condiciones.add(condicion(id, descripcion, referencias, definicion));
        }
        if (!cubiertos.containsAll(elementos)) {
          throw error("La regla " + id + " contiene elementos sin grupo de condicion");
        }
      }
    }
    return List.copyOf(condiciones);
  }

  public static void validarCatalogo(JsonNode reglas, JsonNode catalogo) {
    List<CondicionCritica> condiciones = validar(reglas);
    if ("sieca-v1".equals(catalogo.path("formulario").asText())) {
      Set<String> presentes = new HashSet<>();
      condiciones.forEach(condicion -> presentes.add(condicion.id()));
      if (!presentes.containsAll(Set.of("RN-IC-05a", "RN-IC-05b", "RN-IC-05c", "RN-IC-05d"))) {
        throw error(
            "La plantilla SIECA requiere las reglas RN-IC-05a, RN-IC-05b, RN-IC-05c y RN-IC-05d");
      }
    }
    for (CondicionCritica condicion : condiciones) {
      for (String referencia : condicion.elementos()) {
        JsonNode encontrado = null;
        for (JsonNode elemento : catalogo.path("elementos")) {
          if (referencia.equals(elemento.path("elemento_ref").asText())) {
            encontrado = elemento;
            break;
          }
        }
        if (encontrado == null) {
          throw error(
              "La regla " + condicion.id() + " usa un elemento fuera del catalogo: " + referencia);
        }
        for (String indicador : condicion.indicadores()) {
          if ("existencia".equals(indicador)) {
            continue;
          }
          boolean existe = false;
          for (JsonNode configurado : encontrado.path("indicadores")) {
            if (indicador.equals(configurado.asText())) {
              existe = true;
              break;
            }
          }
          if (!existe) {
            throw error(
                "La regla "
                    + condicion.id()
                    + " usa un indicador fuera del catalogo: "
                    + referencia
                    + "/"
                    + indicador);
          }
        }
      }
    }
  }

  private static CondicionCritica condicion(
      String id, String descripcion, List<String> elementos, JsonNode nodo) {
    int alternativas =
        (nodo.has("campo") ? 1 : 0)
            + (nodo.has("indicador") ? 1 : 0)
            + (nodo.has("indicadores") ? 1 : 0);
    if (alternativas != 1) {
      throw error("La regla " + id + " requiere exactamente campo, indicador o indicadores");
    }
    List<String> indicadores;
    List<String> valores;
    if (nodo.has("campo")) {
      if (nodo.has("algun_valor_en")) {
        throw error("La condicion de campo de " + id + " solo admite igual_a");
      }
      indicadores = List.of(texto(nodo.get("campo"), "campo de " + id));
      valores = List.of(texto(nodo.get("igual_a"), "igual_a de " + id));
    } else {
      if (nodo.has("igual_a")) {
        throw error("La condicion de indicadores de " + id + " solo admite algun_valor_en");
      }
      indicadores =
          nodo.has("indicador")
              ? List.of(texto(nodo.get("indicador"), "indicador de " + id))
              : lista(nodo.get("indicadores"), "indicadores de " + id);
      valores = lista(nodo.get("algun_valor_en"), "algun_valor_en de " + id);
    }
    return new CondicionCritica(id, descripcion, List.copyOf(elementos), indicadores, valores);
  }

  private static boolean directa(JsonNode nodo) {
    return nodo.has("campo") || nodo.has("indicador") || nodo.has("indicadores");
  }

  private static List<String> lista(JsonNode nodo, String referencia) {
    if (nodo == null || !nodo.isArray() || nodo.isEmpty()) {
      throw error("Falta configurar una lista no vacia en " + referencia);
    }
    List<String> valores = new ArrayList<>();
    Set<String> unicos = new HashSet<>();
    for (JsonNode item : nodo) {
      String valor = texto(item, referencia);
      if (!unicos.add(valor)) {
        throw error("Valor duplicado en " + referencia + ": " + valor);
      }
      valores.add(valor);
    }
    return List.copyOf(valores);
  }

  private static void validarReferencia(String referencia) {
    if (!referencia.startsWith("/")
        || referencia.length() < 2
        || referencia.length() > 300
        || referencia.contains("//")
        || referencia.endsWith("/")
        || referencia.matches(".*~(?![01]).*")) {
      throw error("Referencia de elemento invalida: " + referencia);
    }
  }

  private static String texto(JsonNode valor, String referencia) {
    if (valor == null
        || !valor.isTextual()
        || valor.textValue().isBlank()
        || !valor.textValue().equals(valor.textValue().strip())) {
      throw error("Falta configurar un texto valido en " + referencia);
    }
    return valor.textValue();
  }

  private static ApiException error(String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "reglas_anulacion_ic_invalidas",
        "Reglas de anulacion del IC invalidas",
        detalle);
  }
}
