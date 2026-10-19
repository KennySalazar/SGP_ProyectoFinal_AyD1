package gt.usac.cunoc.sgp.mantenimiento.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Solicitud para descartar o cancelar una orden con su motivo (RN-MTO-09)")
public record CerrarSinEjecucionRequest(
    @Schema(
            description = "Motivo obligatorio",
            example = "El daño fue reparado por la municipalidad")
        String motivo,
    @Schema(description = "Versión de la orden leída por el cliente (control optimista)")
        Long version) {}
