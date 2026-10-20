package gt.usac.cunoc.sgp.mantenimiento.dto;

import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.UUID;

/** Usuario activo que puede figurar como responsable de una intervención. */
public record ResponsableDisponibleResponse(UUID id, String email, RoleName rol) {}
