package gt.usac.cunoc.sgp.formulario.dto;

import java.time.Instant;
import java.util.UUID;

public record ActaCalibracionResponse(
    UUID archivoId,
    String nombreOriginal,
    String mimeReal,
    long tamanoBytes,
    String checksumSha256,
    UUID subidoPorId,
    String subidoPorNombre,
    String subidoPorEmail,
    Instant subidoEn) {}
