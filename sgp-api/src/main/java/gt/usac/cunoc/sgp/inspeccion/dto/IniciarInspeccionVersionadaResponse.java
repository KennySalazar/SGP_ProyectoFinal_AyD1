package gt.usac.cunoc.sgp.inspeccion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record IniciarInspeccionVersionadaResponse(
    UUID id,
    UUID puenteId,
    String puenteNombre,
    UUID versionFormularioId,
    String versionFormularioCodigo,
    UUID autorId,
    String rolAutorCreacion,
    String nombreAutorCapturado,
    String tipo,
    String estado,
    LocalDate fechaInspeccion,
    String dispositivoId,
    JsonNode datos,
    Instant creadoEn,
    Instant actualizadoEn) {}
