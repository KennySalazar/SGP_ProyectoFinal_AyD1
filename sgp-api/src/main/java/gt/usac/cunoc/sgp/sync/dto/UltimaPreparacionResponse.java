package gt.usac.cunoc.sgp.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record UltimaPreparacionResponse(
    UUID id,
    String dispositivoId,
    UUID versionFormularioId,
    Instant preparadoEn,
    JsonNode contextoAutorizado) {}
