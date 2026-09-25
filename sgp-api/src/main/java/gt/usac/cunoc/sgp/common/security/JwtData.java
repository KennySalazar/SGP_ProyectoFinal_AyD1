package gt.usac.cunoc.sgp.common.security;

import gt.usac.cunoc.sgp.usuario.entity.RoleName;
import java.util.UUID;

public record JwtData(String email, UUID userId, RoleName role, int tokenVersion) {}
