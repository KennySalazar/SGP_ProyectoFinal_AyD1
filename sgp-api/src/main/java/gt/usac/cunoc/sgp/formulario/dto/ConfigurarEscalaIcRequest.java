package gt.usac.cunoc.sgp.formulario.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

public record ConfigurarEscalaIcRequest(
    @NotNull(message = "La escala de severidad es obligatoria")
        Map<
                String,
                @NotNull(message = "Cada nivel requiere un valor")
                @DecimalMin(value = "0", message = "El valor minimo es 0")
                @DecimalMax(value = "4", message = "El valor maximo es 4") BigDecimal>
            valores) {}
