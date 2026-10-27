package gt.usac.cunoc.sgp.mantenimiento.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Filtros combinados de la bandeja de mantenimiento (HU-049). Las fechas son días de creación en
 * hora de Guatemala, ambos incluidos; cualquier filtro puede faltar.
 */
public record FiltroOrdenesRequest(
    UUID puenteId,
    List<String> estados,
    String tipo,
    String prioridad,
    LocalDate desde,
    LocalDate hasta) {}
