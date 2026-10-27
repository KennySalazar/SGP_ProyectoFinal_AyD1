package gt.usac.cunoc.sgp.revision.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Operación separada sobre revision.observacion_general: null permite retirarla. */
public record ObservacionGeneralRequest(
    @Size(max = 2000)
        @Pattern(regexp = "(?s).*\\S.*", message = "El texto no puede contener solo espacios")
        String texto) {}
