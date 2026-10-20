package gt.usac.cunoc.sgp.usuario.dto;

import java.util.UUID;

/**
 * Sesión del usuario autenticado. {@code colegiadoVerificado} solo aplica al Profesional Externo y
 * es nulo para los demás roles. Los nombres son nulos en las cuentas creadas antes de que el perfil
 * fuera obligatorio.
 */
public record UserResponse(
    UUID id,
    String email,
    String nombreCompleto,
    String nombreUsuario,
    String role,
    boolean verified,
    boolean activated,
    boolean active,
    boolean twoFactorEnabled,
    Boolean colegiadoVerificado) {}
