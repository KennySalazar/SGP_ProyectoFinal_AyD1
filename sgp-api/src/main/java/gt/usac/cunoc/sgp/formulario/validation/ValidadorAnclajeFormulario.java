package gt.usac.cunoc.sgp.formulario.validation;

import static gt.usac.cunoc.sgp.formulario.validation.AnclajeInvalidoException.Motivo.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Valida exclusivamente contra el esquema histórico y los datos proporcionados, sin mutarlos. */
@Component
public class ValidadorAnclajeFormulario {
  /**
   * La ruta canónica comienza por {@code /}, no contiene índices ni segmentos vacíos. El UUID es
   * obligatorio incluso si el destino es el propio arreglo de objetos; un arreglo escalar es un
   * campo y no exige UUID. Solo se inspeccionan los identificadores del repetible recorrido.
   */
  public void validar(JsonNode esquemaHistorico, JsonNode datos, String ref, UUID instancia) {
    validar(esquemaHistorico, datos, ref, instancia, false);
  }

  /**
   * Compatibilidad explícita con HU-028: conserva su sintaxis y el tratamiento de cualquier {@code
   * items} como repetible, incluso los arreglos escalares. El adaptador conserva su error HTTP.
   */
  public void validarFotografiaHu028(
      JsonNode esquemaHistorico, JsonNode datos, String ref, UUID instancia) {
    validar(esquemaHistorico, datos, ref, instancia, true);
  }

  private void validar(JsonNode raiz, JsonNode datos, String ref, UUID instancia, boolean hu028) {
    if (ref == null) {
      if (hu028 && instancia == null) return;
      throw error(RUTA_INVALIDA);
    }
    if (!ref.startsWith("/") || ref.length() < 2 || ref.length() > 300) {
      throw error(RUTA_INVALIDA);
    }
    if (hu028 ? ref.matches(".*~(?![01]).*") : !escapesValidos(ref)) {
      throw error(RUTA_INVALIDA);
    }
    JsonNode esquema = raiz;
    if (!hu028 && datos == null) datos = MissingNode.getInstance();
    boolean repetible = false;
    for (String segmento : ref.substring(1).split("/", -1)) {
      if (!hu028 && segmento.isEmpty()) throw error(RUTA_INVALIDA);
      esquema = resolver(raiz, esquema, hu028);
      if (esRepetible(raiz, esquema, hu028)) {
        if (repetible) throw error(REPETIBLE_ANIDADO_NO_SOPORTADO);
        repetible = true;
        datos = buscarInstancia(datos, instancia, hu028);
        esquema = resolver(raiz, esquema.path("items"), hu028);
      }
      String propiedad = segmento.replace("~1", "/").replace("~0", "~");
      esquema = esquema.path("properties").path(propiedad);
      datos = datos.path(propiedad);
      if (esquema.isMissingNode()) throw error(ELEMENTO_INEXISTENTE);
    }
    esquema = resolver(raiz, esquema, hu028);
    if (esRepetible(raiz, esquema, hu028)) {
      if (repetible) throw error(REPETIBLE_ANIDADO_NO_SOPORTADO);
      repetible = true;
      buscarInstancia(datos, instancia, hu028);
    }
    if (!repetible && instancia != null) throw error(INSTANCIA_NO_PERMITIDA);
  }

  private boolean esRepetible(JsonNode raiz, JsonNode esquema, boolean hu028) {
    if (!esquema.has("items")) return false;
    if (hu028) return true;
    JsonNode item = resolver(raiz, esquema.path("items"), false);
    if (item.has("items")) throw error(REPETIBLE_ANIDADO_NO_SOPORTADO);
    return "object".equals(item.path("type").asText()) || item.has("properties");
  }

  private JsonNode buscarInstancia(JsonNode datos, UUID id, boolean hu028) {
    if (id == null) throw error(INSTANCIA_REQUERIDA);
    if (datos == null || !datos.isArray()) throw error(DATOS_REPETIBLE_INVALIDOS);
    JsonNode encontrada = null;
    Set<UUID> identificadores = new HashSet<>();
    for (JsonNode dato : datos) {
      boolean coincide;
      if (hu028) {
        coincide = id.toString().equals(dato.path("instancia_id").asText());
      } else {
        String texto = dato.path("instancia_id").asText();
        UUID identificador;
        try {
          identificador = UUID.fromString(texto);
          if (!identificador.toString().equalsIgnoreCase(texto)) {
            throw error(DATOS_REPETIBLE_INVALIDOS);
          }
        } catch (IllegalArgumentException e) {
          throw error(DATOS_REPETIBLE_INVALIDOS);
        }
        if (!identificadores.add(identificador)) throw error(INSTANCIA_DUPLICADA);
        coincide = id.equals(identificador);
      }
      if (coincide) {
        if (encontrada != null) throw error(INSTANCIA_DUPLICADA);
        encontrada = dato;
      }
    }
    if (encontrada == null) throw error(INSTANCIA_NO_ENCONTRADA);
    return encontrada;
  }

  private JsonNode resolver(JsonNode raiz, JsonNode nodo, boolean hu028) {
    if (!hu028 && (nodo == null || !nodo.isObject())) {
      throw error(REFERENCIA_ESQUEMA_INVALIDA);
    }
    for (int profundidad = 0; nodo.has("$ref"); profundidad++) {
      String ref = nodo.path("$ref").asText();
      if (profundidad >= 32 || !ref.startsWith("#/") || (!hu028 && !escapesValidos(ref))) {
        throw error(REFERENCIA_ESQUEMA_INVALIDA);
      }
      try {
        nodo = raiz.at(ref.substring(1));
      } catch (IllegalArgumentException e) {
        throw error(REFERENCIA_ESQUEMA_INVALIDA);
      }
      if (nodo.isMissingNode() || (!hu028 && !nodo.isObject())) {
        throw error(REFERENCIA_ESQUEMA_INVALIDA);
      }
    }
    return nodo;
  }

  private boolean escapesValidos(String ref) {
    for (int i = 0; i < ref.length(); i++) {
      if (ref.charAt(i) == '~') {
        if (++i == ref.length() || (ref.charAt(i) != '0' && ref.charAt(i) != '1')) return false;
      }
    }
    return true;
  }

  private AnclajeInvalidoException error(AnclajeInvalidoException.Motivo motivo) {
    return new AnclajeInvalidoException(motivo);
  }
}
