package gt.usac.cunoc.sgp.inspeccion.dto;

import java.util.UUID;

public record AcompananteResponse(
    UUID id,
    UUID usuarioId,
    String nombre,
    String numeroColegiado) {}
