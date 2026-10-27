package gt.usac.cunoc.sgp.inspeccion.dto;

import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import java.time.Instant;
import java.util.UUID;

public record FotografiaResponse(
    UUID id,
    UUID inspeccionId,
    CategoriaFoto categoria,
    String elementoRef,
    UUID instanciaElementoId,
    String mimeReal,
    long tamanoBytes,
    int anchoPx,
    int altoPx,
    double latitud,
    double longitud,
    Instant capturadoEn) {}
