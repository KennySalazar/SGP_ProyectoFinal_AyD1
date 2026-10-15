package gt.usac.cunoc.sgp.inspeccion.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record EntradaCalculoIc(
    UUID versionFormularioId,
    String codigoFormulario,
    String versionAlgoritmo,
    JsonNode datos,
    JsonNode esquema,
    JsonNode catalogoElementos,
    JsonNode reglasEvaluacion,
    Map<String, BigDecimal> pesos) {}
