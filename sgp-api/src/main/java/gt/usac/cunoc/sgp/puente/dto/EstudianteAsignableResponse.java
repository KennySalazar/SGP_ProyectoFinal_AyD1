package gt.usac.cunoc.sgp.puente.dto;

import java.util.UUID;

public record EstudianteAsignableResponse(
    UUID cursoEstudianteId,
    UUID estudianteId,
    String estudianteEmail,
    UUID cursoId,
    String cursoNombre,
    String periodo) {}
