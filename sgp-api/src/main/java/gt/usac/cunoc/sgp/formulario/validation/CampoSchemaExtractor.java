package gt.usac.cunoc.sgp.formulario.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CampoSchemaExtractor {

  public List<String> extraerCampos(JsonNode esquema) {
    if (esquema == null || !esquema.isObject()) {
      return Collections.emptyList();
    }
    Set<String> campos = new LinkedHashSet<>();
    recorrerNodo(esquema, "", campos);
    return new ArrayList<>(campos);
  }

  private void recorrerNodo(JsonNode nodo, String rutaActual, Set<String> campos) {
    if (nodo == null || !nodo.isObject()) {
      return;
    }

    if (nodo.has("properties") && nodo.get("properties").isObject()) {
      JsonNode properties = nodo.get("properties");
      Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        String nuevaRuta = rutaActual + "/" + field.getKey();
        JsonNode childNode = field.getValue();

        if (childNode.has("properties") && childNode.get("properties").isObject()) {
          recorrerNodo(childNode, nuevaRuta, campos);
        } else {
          campos.add(nuevaRuta);
        }
      }
    } else if (!rutaActual.isBlank()) {
      campos.add(rutaActual);
    }
  }
}
