package gt.usac.cunoc.sgp.inspeccion.dto;

import java.time.Instant;
import java.util.UUID;

/** Documento PDF disponible de una inspección; nunca expone la clave del almacenamiento. */
public record DocumentoInspeccionResponse(
    UUID id,
    UUID inspeccionId,
    String nombreOriginal,
    long tamanoBytes,
    UUID subidoPorId,
    Instant subidoEn) {}
