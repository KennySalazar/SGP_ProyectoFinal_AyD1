package gt.usac.cunoc.sgp.mantenimiento.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Solicitud para aceptar y programar una orden propuesta")
public record ProgramarOrdenRequest(
    @Schema(description = "Fecha programada; no puede ser anterior a hoy", example = "2026-11-02")
        LocalDate fechaProgramada,
    @Schema(description = "Versión de la orden leída por el cliente (control optimista)")
        Long version) {}
