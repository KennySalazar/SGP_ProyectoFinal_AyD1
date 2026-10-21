package gt.usac.cunoc.sgp.revision.dto;

import jakarta.validation.constraints.Size;

public record ResolverObservacionRequest(@Size(max = 2000) String respuesta) {}
