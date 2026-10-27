package gt.usac.cunoc.sgp.foro.dto;

import gt.usac.cunoc.sgp.foro.model.CategoriaHilo;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Hilo del foro. {@code autorUsuario} es nulo si la cuenta aún no tiene nombre de usuario; entonces
 * se muestra su rol. Ni el correo ni el nombre completo se exponen. Un hilo cerrado lleva el motivo
 * del cierre, que es público (RN-FOR-08).
 */
public record HiloResponse(
    UUID id,
    UUID puenteId,
    UUID inspeccionId,
    CategoriaHilo categoria,
    String titulo,
    UUID autorId,
    String autorUsuario,
    String autorRol,
    OffsetDateTime creadoEn,
    OffsetDateTime actualizadoEn,
    boolean cerrado,
    String motivoCierre,
    long totalComentarios) {}
