package gt.usac.cunoc.sgp.inspeccion.dto;

import java.util.List;
import java.util.UUID;

public record ComparacionIcResponse(
    UUID inspeccionId,
    ResultadoIcHistorialResponse original,
    List<ResultadoIcHistorialResponse> recalculos) {
  public ComparacionIcResponse {
    recalculos = List.copyOf(recalculos);
  }
}
