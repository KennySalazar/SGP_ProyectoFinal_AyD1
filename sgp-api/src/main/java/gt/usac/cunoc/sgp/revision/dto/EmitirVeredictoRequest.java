package gt.usac.cunoc.sgp.revision.dto;

import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest;
import gt.usac.cunoc.sgp.revision.model.VeredictoRevision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record EmitirVeredictoRequest(
    @NotNull VeredictoRevision veredicto,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal calificacion,
    @Size(max = 2000) String observacionGeneral,
    @Valid ConfirmarEstadoIcRequest confirmacionEstado) {}
