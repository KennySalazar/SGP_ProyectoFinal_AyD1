package gt.usac.cunoc.sgp.formulario.dto;

import gt.usac.cunoc.sgp.formulario.model.TipoMapeoCampo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeclaracionMapeoRequest(
    @Size(max = 300, message = "El elemento origen no debe superar 300 caracteres")
        String elementoRefOrigen,
    @Size(max = 300, message = "El elemento destino no debe superar 300 caracteres")
        String elementoRefDestino,
    @NotNull(message = "El tipo de mapeo es obligatorio") TipoMapeoCampo tipo,
    String observacion) {}
