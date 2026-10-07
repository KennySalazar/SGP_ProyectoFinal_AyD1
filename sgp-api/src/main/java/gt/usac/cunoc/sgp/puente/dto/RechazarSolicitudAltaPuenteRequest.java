package gt.usac.cunoc.sgp.puente.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazarSolicitudAltaPuenteRequest(
    @NotBlank(message = "El motivo del rechazo es obligatorio")
        @Size(max = 1000, message = "El motivo no debe superar 1000 caracteres")
        String motivo) {}
