package gt.usac.cunoc.sgp.puente.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AsignarPuenteRequest(@NotNull UUID cursoEstudianteId, @NotNull UUID puenteId) {}
