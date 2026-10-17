package gt.usac.cunoc.sgp.formulario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ConfiguracionIcElementoRequest(
    @NotBlank(message = "La referencia del elemento es obligatoria")
        @Size(max = 300, message = "La referencia no debe superar 300 caracteres")
        String elementoRef,
    @NotNull(message = "El peso es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "El peso debe ser mayor a cero")
        @Digits(
            integer = 6,
            fraction = 6,
            message = "El peso admite hasta 6 digitos enteros y 6 decimales")
        BigDecimal peso,
    @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 500, message = "La descripcion no debe superar 500 caracteres")
        String descripcion) {}
