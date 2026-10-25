package gt.usac.cunoc.sgp.foro.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Inspección publicada sin revisión previa, para moderarla a posteriori (RN-REV-02). {@code hiloId}
 * es nulo si su hilo todavía no se abrió; entonces no tiene comentarios.
 */
public record InspeccionSinRevisionResponse(
    UUID inspeccionId,
    UUID puenteId,
    String puenteCodigo,
    String puenteNombre,
    LocalDate fechaInspeccion,
    OffsetDateTime publicadoEn,
    String autorUsuario,
    String autorRol,
    UUID hiloId,
    long totalComentarios) {}
