package gt.usac.cunoc.sgp.curso.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CursoResponse(
    UUID id,
    String nombre,
    String periodo,
    CatedraticoResponse catedratico,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    String estado) {}
