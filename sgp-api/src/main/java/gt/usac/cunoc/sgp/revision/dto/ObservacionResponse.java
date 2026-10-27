package gt.usac.cunoc.sgp.revision.dto;

import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import java.time.Instant;
import java.util.UUID;

public record ObservacionResponse(
    UUID id,
    UUID revisionId,
    DestinoObservacion destino,
    String texto,
    EstadoObservacion estado,
    UUID creadoPorId,
    Instant creadoEn,
    UUID resueltoPorId,
    Instant resueltoEn,
    String respuestaAutor) {
  public ObservacionResponse(
      UUID id,
      UUID revisionId,
      DestinoObservacion destino,
      String texto,
      EstadoObservacion estado,
      UUID creadoPorId,
      Instant creadoEn) {
    this(id, revisionId, destino, texto, estado, creadoPorId, creadoEn, null, null, null);
  }
}
