package gt.usac.cunoc.sgp.inspeccion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record ConflictoSincronizacionResponse(
    UUID id,
    long versionServidor,
    Long versionCliente,
    JsonNode copiaServidor,
    Instant detectadoEn) {}
