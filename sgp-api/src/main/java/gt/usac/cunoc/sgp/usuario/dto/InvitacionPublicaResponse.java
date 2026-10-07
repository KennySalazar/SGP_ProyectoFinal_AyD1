package gt.usac.cunoc.sgp.usuario.dto;

import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.OffsetDateTime;

/** Datos mínimos que ve el invitado al abrir su enlace, antes de definir la contraseña. */
public record InvitacionPublicaResponse(String email, RoleName rol, OffsetDateTime expiraEn) {}
