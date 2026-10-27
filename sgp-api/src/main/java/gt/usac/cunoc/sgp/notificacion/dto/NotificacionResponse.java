package gt.usac.cunoc.sgp.notificacion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record NotificacionResponse(
    UUID id,
    String tipo,
    String titulo,
    String mensaje,
    String entidad,
    UUID entidadId,
    String claveEvento,
    Instant leidoEn,
    Instant creadoEn,
    boolean leido) {
  @JsonProperty("rutaDestino")
  public String rutaDestino() {
    if (entidadId == null) return null;
    if (("CALIBRACION_PENDIENTE".equals(tipo) || "CALIBRACION_APROBADA".equals(tipo))
        && "version_formulario".equalsIgnoreCase(entidad)) {
      return "/admin/formularios/" + entidadId;
    }
    if ("REGLAS_PENDIENTES_VALIDAR".equals(tipo) && "catalogo_reglas".equalsIgnoreCase(entidad)) {
      return "/mantenimiento/reglas?versionId=" + entidadId;
    }
    return null;
  }
}
