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
    boolean mapeoCompleto) {}
