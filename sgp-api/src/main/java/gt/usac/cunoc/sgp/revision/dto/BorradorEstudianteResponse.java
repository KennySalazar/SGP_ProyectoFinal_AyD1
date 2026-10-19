package gt.usac.cunoc.sgp.revision.dto;

import java.time.Instant;
import java.util.UUID;

public record BorradorEstudianteResponse(
    UUID inspeccionId,
    UUID puenteId,
    String puenteCodigo,
    String puenteNombre,
    UUID estudianteId,
    String estudianteEmail,
    String estudianteNombre,
    UUID cursoId,
    String cursoNombre,
    String periodo,
    String seccion,
    Instant ultimaActividadEn) {}
