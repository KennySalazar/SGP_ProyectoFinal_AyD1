package gt.usac.cunoc.sgp.revision.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** El ciclo se identifica por separado; identidad, estado y fechas los determinará el servidor. */
public record CrearObservacionRequest(
    @NotNull @Valid DestinoObservacion destino, @NotBlank @Size(max = 2000) String texto) {}
