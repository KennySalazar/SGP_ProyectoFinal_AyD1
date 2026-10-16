package gt.usac.cunoc.sgp.sync.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** intentoId actúa como token de exclusión para impedir confirmaciones de intentos vencidos. */
public record ReservaOperacion(
    UUID operacionId, UUID intentoId, Instant venceEn, boolean completada, JsonNode respuesta) {
  public ReservaOperacion {
    respuesta = respuesta == null ? null : respuesta.deepCopy();
  }

  @Override
  public JsonNode respuesta() {
    return respuesta == null ? null : respuesta.deepCopy();
  }
}
