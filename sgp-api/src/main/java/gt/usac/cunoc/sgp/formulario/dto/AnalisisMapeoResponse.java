package gt.usac.cunoc.sgp.formulario.dto;

import java.util.List;
import java.util.UUID;

public record AnalisisMapeoResponse(
    UUID versionOrigenId,
    String versionOrigenCodigo,
    UUID versionDestinoId,
    String versionDestinoCodigo,
    List<String> camposOrigen,
    List<String> camposDestino,
    List<MapeoCampoResponse> mapeosDeclarados,
    List<String> camposOrigenSinMapeo,
    boolean mapeoCompleto) {
  @com.fasterxml.jackson.annotation.JsonProperty("camposDestinoSinMapeo")
  public List<String> camposDestinoSinMapeo() {
    if (versionOrigenId == null) return List.of();
    var declarados =
        mapeosDeclarados.stream()
            .map(MapeoCampoResponse::elementoRefDestino)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    return camposDestino.stream().filter(campo -> !declarados.contains(campo)).toList();
  }
}
