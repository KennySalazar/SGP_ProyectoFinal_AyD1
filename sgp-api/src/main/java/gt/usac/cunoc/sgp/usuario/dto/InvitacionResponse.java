package gt.usac.cunoc.sgp.usuario.dto;

import gt.usac.cunoc.sgp.usuario.model.EstadoInvitacion;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitacionResponse(
    UUID id,
    String email,
    RoleName rol,
    EstadoInvitacion estado,
    UUID usuarioId,
    UUID invitadoPorId,
    OffsetDateTime expiraEn,
    OffsetDateTime aceptadoEn,
    OffsetDateTime canceladoEn,
    OffsetDateTime creadoEn) {}
