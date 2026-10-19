package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ValidadorReglaMantenimiento {

  public void validar(JsonNode esquemaRaiz, String elementoRef, JsonNode condicion) {
    if (esquemaRaiz == null || !esquemaRaiz.isObject()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "regla_mantenimiento_invalida",
          "Esquema inválido",
          "El esquema de la versión de formulario no está disponible o es inválido");
    }

    if (elementoRef == null || !elementoRef.startsWith("/") || elementoRef.trim().length() < 2) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "regla_mantenimiento_invalida",
          "Ruta de elemento inválida",
          "El campo elemento_ref debe comenzar con '/' y especificar una ruta válida en el esquema");
    }

    String[] segmentos = elementoRef.substring(1).split("/", -1);
    JsonNode actual = esquemaRaiz;

    for (String segmento : segmentos) {
      if (segmento.isBlank()) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "regla_mantenimiento_invalida",
            "Ruta de elemento inválida",
            "El campo elemento_ref contiene segmentos vacíos: " + elementoRef);
      }

      actual = resolverReferencias(esquemaRaiz, actual);
      if (actual.has("items")) {
        actual = resolverReferencias(esquemaRaiz, actual.path("items"));
      }

      JsonNode props = actual.path("properties");
      if (!props.has(segmento)) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "regla_mantenimiento_invalida",
            "Elemento no encontrado",
            "El elemento '"
                + elementoRef
                + "' no existe en el esquema (segmento no encontrado: '"
                + segmento
                + "')");
      }
      actual = props.get(segmento);
    }

    actual = resolverReferencias(esquemaRaiz, actual);
    if (actual.has("items")) {
      actual = resolverReferencias(esquemaRaiz, actual.path("items"));
    }

    if (condicion == null || !condicion.isObject() || condicion.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "regla_mantenimiento_invalida",
          "Condición requerida",
          "La condición de la regla debe ser un objeto JSON válido");
    }

    if (!condicion.hasNonNull("indicador")) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "regla_mantenimiento_invalida",
          "Indicador requerido",
          "La condición de la regla debe especificar un campo 'indicador'");
    }

    String indicador = condicion.get("indicador").asText().trim();
    if (indicador.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "regla_mantenimiento_invalida",
          "Indicador vacío",
          "El campo 'indicador' de la condición no puede estar vacío");
    }

    JsonNode propiedadesElemento = actual.path("properties");
    if (!propiedadesElemento.has(indicador)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "regla_mantenimiento_invalida",
          "Indicador no encontrado",
          "El indicador '"
              + indicador
              + "' no existe en el elemento '"
              + elementoRef
              + "' del esquema");
    }
  }

  private JsonNode resolverReferencias(JsonNode raiz, JsonNode nodo) {
    if (nodo == null || !nodo.isObject()) {
      return nodo;
    }
    int profundidad = 0;
    while (nodo.hasNonNull("$ref") && profundidad < 32) {
      String ref = nodo.get("$ref").asText();
      if (!ref.startsWith("#/")) {
        break;
      }
      nodo = raiz.at(ref.substring(1));
      profundidad++;
    }
    return nodo;
  }
}
