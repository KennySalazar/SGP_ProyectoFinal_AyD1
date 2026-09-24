
package gt.usac.cunoc.sgp.usuario.dto;

import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String role,
    boolean verified,
    boolean activated,
    boolean active,
    boolean twoFactorEnabled
) {}
