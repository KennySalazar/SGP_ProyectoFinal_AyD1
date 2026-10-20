package gt.usac.cunoc.sgp.mantenimiento.dto;

import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import java.util.Map;

/** Totales de la bandeja por estado, con todos los estados presentes aunque valgan cero. */
public record TotalesOrdenesResponse(long total, Map<EstadoOrden, Long> porEstado) {}
