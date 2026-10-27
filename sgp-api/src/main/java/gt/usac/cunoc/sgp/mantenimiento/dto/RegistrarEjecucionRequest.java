package gt.usac.cunoc.sgp.mantenimiento.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Fecha de ejecución y responsable de una orden en ejecución (RN-MTO-05)")
public record RegistrarEjecucionRequest(
    @Schema(description = "Día en que se realizó la intervención; no futuro ni anterior al inicio")
        LocalDate fechaEjecucion,
    @Schema(description = "Usuario del sistema responsable de la intervención")
        UUID responsableUsuarioId,
    @Schema(description = "Persona o empresa externa responsable de la intervención")
        String responsableNombre) {}
