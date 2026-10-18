package gt.usac.cunoc.sgp.inspeccion.dto;

import jakarta.validation.constraints.Size;

/** El motivo es obligatorio; el servicio rechaza un motivo vacío con 422 (RN-INS-08). */
public record CrearCorreccionRequest(
    @Size(max = 2000, message = "El motivo no debe superar 2000 caracteres") String motivo) {}
