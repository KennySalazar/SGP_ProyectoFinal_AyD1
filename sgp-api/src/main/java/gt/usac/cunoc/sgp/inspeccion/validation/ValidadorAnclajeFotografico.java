package gt.usac.cunoc.sgp.inspeccion.validation;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ValidadorAnclajeFotografico {
  public void validar(Inspeccion inspeccion, String ref, UUID instancia) {
    if (ref == null) {
      if (instancia != null) throw invalido();
      return;
    }
    if (!ref.startsWith("/")
        || ref.length() < 2
        || ref.length() > 300
        || ref.matches(".*~(?![01]).*")) throw invalido();
    JsonNode raiz = inspeccion.getVersionFormulario().getEsquema(),
        esquema = raiz,
        datos = inspeccion.getDatos();
    boolean repetible = false;
    for (String segmento : ref.substring(1).split("/", -1)) {
      esquema = resolver(raiz, esquema);
      if (esquema.has("items")) {
        if (repetible) throw invalido();
        repetible = true;
        datos = buscarInstancia(datos, instancia);
        esquema = resolver(raiz, esquema.path("items"));
      }
      String propiedad = segmento.replace("~1", "/").replace("~0", "~");
      esquema = esquema.path("properties").path(propiedad);
      datos = datos.path(propiedad);
      if (esquema.isMissingNode()) throw invalido();
    }
    esquema = resolver(raiz, esquema);
    if (esquema.has("items")) {
      if (repetible) throw invalido();
      repetible = true;
      buscarInstancia(datos, instancia);
    }
    if (!repetible && instancia != null) throw invalido();
  }

  private JsonNode buscarInstancia(JsonNode datos, UUID id) {
    if (id == null || !datos.isArray()) throw invalido();
    JsonNode encontrada = null;
    for (JsonNode d : datos) {
      if (id.toString().equals(d.path("instancia_id").asText())) {
        if (encontrada != null) throw invalido();
        encontrada = d;
      }
    }
    if (encontrada == null) throw invalido();
    return encontrada;
  }

  private JsonNode resolver(JsonNode raiz, JsonNode nodo) {
    for (int profundidad = 0; nodo.has("$ref"); profundidad++) {
      String ref = nodo.path("$ref").asText();
      if (profundidad >= 32 || !ref.startsWith("#/")) throw invalido();
      try {
        nodo = raiz.at(ref.substring(1));
      } catch (IllegalArgumentException e) {
        throw invalido();
      }
      if (nodo.isMissingNode()) throw invalido();
    }
    return nodo;
  }

  private ApiException invalido() {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "anclaje_fotografico_invalido",
        "Referencia inválida",
        "El elemento o la instancia no pertenecen a la versión y datos de esta inspección.");
  }
}
