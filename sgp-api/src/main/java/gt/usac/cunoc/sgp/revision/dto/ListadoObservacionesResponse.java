package gt.usac.cunoc.sgp.revision.dto;

import java.util.List;
import java.util.UUID;

/** Total y pendientes cuentan observaciones puntuales, sin incluir la general del ciclo. */
public record ListadoObservacionesResponse(
    UUID revisionId,
    List<ObservacionResponse> observaciones,
    long total,
    long pendientes,
    ObservacionGeneralResponse general) {
  public ListadoObservacionesResponse {
    observaciones = List.copyOf(observaciones);
  }
}
