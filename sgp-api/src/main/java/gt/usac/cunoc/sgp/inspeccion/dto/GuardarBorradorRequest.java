package gt.usac.cunoc.sgp.inspeccion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;

public record GuardarBorradorRequest(
    Long version,
    JsonNode datos,
    JsonNode identificacionCapturada,
    LocalDate fechaInspeccion,
    List<AcompananteRequest> acompanantes) {}
