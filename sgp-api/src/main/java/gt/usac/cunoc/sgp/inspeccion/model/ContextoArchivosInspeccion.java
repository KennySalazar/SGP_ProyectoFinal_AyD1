package gt.usac.cunoc.sgp.inspeccion.model;

import java.time.Instant;
import java.util.UUID;

public record ContextoArchivosInspeccion(
    UUID inspeccionId, UUID autorId, String estado, Instant eliminadoEn) {}
