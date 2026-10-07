package gt.usac.cunoc.sgp.puente.dto;

import java.time.Instant;
import java.util.UUID;

public record AsignacionPuenteResponse(
    UUID id,
    UUID cursoEstudianteId,
    String estudianteEmail,
    String cursoNombre,
    String periodo,
    UUID puenteId,
    String puenteCodigo,
    String puenteNombre,
    Instant asignadoEn) {}
