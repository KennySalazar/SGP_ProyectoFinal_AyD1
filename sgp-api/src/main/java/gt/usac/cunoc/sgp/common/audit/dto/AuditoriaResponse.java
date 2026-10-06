package gt.usac.cunoc.sgp.common.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditoriaResponse(
    UUID id,
    UUID usuarioId,
    AccionAuditoria accion,
    String entidad,
    UUID entidadId,
    JsonNode valoresAnteriores,
    JsonNode valoresPosteriores,
    String procesoAutomatico,
    OffsetDateTime creadoEn,
    String usuarioEmail) {}
