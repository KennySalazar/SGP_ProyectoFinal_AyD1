package gt.usac.cunoc.sgp.inspeccion.dto;

import java.util.List;

public record ValidacionEvidenciaResponse(
    boolean valida,
    long totalDisponibles,
    int maximoPermitido,
    List<FaltanteEvidenciaResponse> faltantes) {

  public ValidacionEvidenciaResponse {
    faltantes = List.copyOf(faltantes);
  }
}
