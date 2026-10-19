package gt.usac.cunoc.sgp.mantenimiento.dto;

import java.util.UUID;

/**
 * Resultado de generar las propuestas de mantenimiento de una inspección publicada (HU-046).
 *
 * @param inspeccionId la inspección evaluada
 * @param reglasEvaluadas reglas activas de la versión del formulario con que se levantó
 * @param creadas propuestas nuevas, siempre en estado PROPUESTA
 * @param yaGeneradas condiciones cumplidas cuya propuesta ya existía de una generación anterior
 * @param omitidasPorOrdenAbierta condiciones cumplidas que ya tienen una orden en curso en el mismo
 *     puente, elemento y regla
 * @param reglasConError reglas que no se pudieron evaluar; no impiden evaluar las demás
 */
public record GeneracionPropuestasResponse(
    UUID inspeccionId,
    int reglasEvaluadas,
    int creadas,
    int yaGeneradas,
    int omitidasPorOrdenAbierta,
    int reglasConError) {}
