package gt.usac.cunoc.sgp.revision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** La edición conserva el destino y la identidad de la observación. */
public record EditarObservacionRequest(@NotBlank @Size(max = 2000) String texto) {}
