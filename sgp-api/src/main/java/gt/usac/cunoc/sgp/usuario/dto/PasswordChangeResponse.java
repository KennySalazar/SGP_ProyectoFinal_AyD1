package gt.usac.cunoc.sgp.usuario.dto;

public record PasswordChangeResponse(
    String accessToken, String tokenType, long expiresIn, String message) {}
