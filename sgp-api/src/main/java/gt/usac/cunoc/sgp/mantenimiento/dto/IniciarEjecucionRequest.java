package gt.usac.cunoc.sgp.mantenimiento.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Solicitud para iniciar la ejecución de una orden programada")
public record IniciarEjecucionRequest(
    @Schema(description = "Versión de la orden leída por el cliente (control optimista)")
        Long version) {}
