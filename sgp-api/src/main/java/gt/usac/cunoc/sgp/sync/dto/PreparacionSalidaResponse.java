package gt.usac.cunoc.sgp.sync.dto;

import java.time.Instant;
import java.util.UUID;

public record PreparacionSalidaResponse(
    UUID id,
    UUID usuarioId,
    String dispositivoId,
    Instant preparadoEn,
    int numeroPuentes,
    long tamanoEstimadoBytes,
    PaqueteSalidaResponse paquete) {}
