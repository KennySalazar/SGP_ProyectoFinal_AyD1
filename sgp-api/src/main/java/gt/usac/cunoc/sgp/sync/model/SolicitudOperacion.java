package gt.usac.cunoc.sgp.sync.model;

import java.util.UUID;

public record SolicitudOperacion(
    UUID usuarioId,
    String clave,
    String metodo,
    String ruta,
    String hashSolicitud,
    String entidad,
    UUID entidadId) {
  public SolicitudOperacion {
    if (usuarioId == null
        || clave == null
        || clave.isBlank()
        || clave.length() > 100
        || metodo == null
        || metodo.isBlank()
        || metodo.length() > 10
        || ruta == null
        || !ruta.startsWith("/api/v1/")
        || ruta.length() > 300
        || hashSolicitud == null
        || !hashSolicitud.matches("[a-f0-9]{64}")
        || entidad == null
        || entidad.isBlank()
        || entidad.length() > 100)
      throw new gt.usac.cunoc.sgp.common.exception.ApiException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "operacion_identidad_invalida",
          "Solicitud inválida",
          "Identidad de operación inválida");
  }
}
