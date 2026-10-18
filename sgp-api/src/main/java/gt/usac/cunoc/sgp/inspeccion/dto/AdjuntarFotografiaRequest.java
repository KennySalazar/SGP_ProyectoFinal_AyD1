package gt.usac.cunoc.sgp.inspeccion.dto;

import java.time.Instant;
import java.util.UUID;

public record AdjuntarFotografiaRequest(
    UUID id,
    String categoria,
    Double latitud,
    Double longitud,
    Instant capturadoEn,
    String elementoRef,
    UUID instanciaElementoId) {}
