package gt.usac.cunoc.sgp.inspeccion.dto;

import java.math.BigDecimal;

public record IndiceCondicionPreliminarResponse(
    BigDecimal indice, String estado, String etiqueta) {}
