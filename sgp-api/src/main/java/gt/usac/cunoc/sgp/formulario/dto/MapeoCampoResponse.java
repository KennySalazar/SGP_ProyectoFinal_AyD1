package gt.usac.cunoc.sgp.formulario.dto;

import gt.usac.cunoc.sgp.formulario.model.TipoMapeoCampo;
import java.util.UUID;

public record MapeoCampoResponse(
    UUID id,
    UUID versionOrigenId,
    UUID versionDestinoId,
    String elementoRefOrigen,
    String elementoRefDestino,
    TipoMapeoCampo tipo,
    String observacion) {}
