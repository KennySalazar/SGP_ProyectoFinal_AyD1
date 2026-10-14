package gt.usac.cunoc.sgp.formulario.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ConfiguracionIcElementoResponse(
    UUID id, String elementoRef, BigDecimal peso, String descripcion) {}
