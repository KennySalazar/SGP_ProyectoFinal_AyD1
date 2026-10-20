package gt.usac.cunoc.sgp.mantenimiento.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cierre de una orden en ejecución como ejecutada (RN-MTO-05)")
public record CerrarComoEjecutadaRequest(
    @Schema(description = "Versión de la orden leída por el cliente (control optimista)")
        Long version) {}
