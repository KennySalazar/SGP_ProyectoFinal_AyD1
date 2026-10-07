package gt.usac.cunoc.sgp.usuario.dto;

import jakarta.validation.constraints.Size;

public record DesactivarUsuarioRequest(
    @Size(max = 1000, message = "El motivo no debe superar 1000 caracteres") String motivo) {}
