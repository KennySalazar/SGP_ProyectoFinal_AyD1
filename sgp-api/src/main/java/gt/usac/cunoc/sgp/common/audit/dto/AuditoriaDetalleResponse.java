package gt.usac.cunoc.sgp.common.audit.dto;

import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AuditoriaDetalleResponse(
    UUID id,
    UUID usuarioId,
    AccionAuditoria accion,
    String entidad,
    UUID entidadId,
    String procesoAutomatico,
    OffsetDateTime creadoEn,
    List<CambioAuditoria> cambios,
    String usuarioEmail) {}
