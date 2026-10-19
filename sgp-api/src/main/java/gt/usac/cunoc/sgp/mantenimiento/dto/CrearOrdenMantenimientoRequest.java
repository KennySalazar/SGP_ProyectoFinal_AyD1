package gt.usac.cunoc.sgp.mantenimiento.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Datos de una orden manual (RN-MTO-01). Los campos se validan en el servicio para responder 422
 * con el detalle de cada error, incluida una tipología no reconocida (RN-MTO-03).
 */
@Schema(description = "Solicitud para crear una orden de mantenimiento manual")
public record CrearOrdenMantenimientoRequest(
    @Schema(description = "Puente activo al que pertenece la orden") UUID puenteId,
    @Schema(
            description = "Tipología de la orden",
            allowableValues = {"RUTINARIO", "PREVENTIVO", "CORRECTIVO", "EMERGENCIA"})
        String tipo,
    @Schema(description = "Intervención a realizar", example = "Limpiar drenajes obstruidos")
        String descripcion,
    @Schema(
            description = "Prioridad inicial",
            allowableValues = {"BAJA", "MEDIA", "ALTA", "CRITICA"})
        String prioridad,
    @Schema(
            description = "Elemento del formulario al que se refiere, si aplica",
            example = "/superestructura/drenaje")
        String elementoRef,
    @Schema(description = "Instancia del elemento repetible, si aplica") UUID instanciaElementoId,
    @Schema(description = "Inspección del mismo puente que originó la orden, si aplica")
        UUID inspeccionOrigenId) {}
