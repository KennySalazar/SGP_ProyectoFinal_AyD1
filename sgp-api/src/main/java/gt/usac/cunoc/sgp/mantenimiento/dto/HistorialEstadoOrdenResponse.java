package gt.usac.cunoc.sgp.mantenimiento.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HistorialEstadoOrdenResponse(
    int secuencia,
    String estadoAnterior,
    String estadoNuevo,
    UUID realizadoPorId,
    String motivo,
    OffsetDateTime creadoEn) {}
