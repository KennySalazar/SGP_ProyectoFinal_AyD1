package gt.usac.cunoc.sgp.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenInvitacionRequest(
    @NotBlank(message = "El token de invitacion es obligatorio")
        @Size(max = 200, message = "El token de invitacion no es valido")
        String token) {}
