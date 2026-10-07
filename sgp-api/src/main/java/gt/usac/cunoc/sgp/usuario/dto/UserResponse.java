package gt.usac.cunoc.sgp.usuario.dto;

import java.util.UUID;

/**
 * Sesión del usuario autenticado. {@code colegiadoVerificado} solo aplica al Profesional Externo y
 * es nulo para los demás roles.
 */
public record UserResponse(
    UUID id,
    String email,
    String role,
    boolean verified,
    boolean activated,
    boolean active,
    boolean twoFactorEnabled,
    Boolean colegiadoVerificado) {}
