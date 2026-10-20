package gt.usac.cunoc.sgp.sync.http;

import java.util.UUID;

public record ReservaIdempotente(UUID id, RespuestaIdempotente respuesta) {
  public boolean repetida() {
    return respuesta != null;
  }
}
