package gt.usac.cunoc.sgp.formulario.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record FormularioActivoResponse(
    UUID id,
    String codigo,
    long version,
    JsonNode esquema,
    String hashContenido,
    Instant publicadoEn) {}
