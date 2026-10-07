package gt.usac.cunoc.sgp.puente.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CrearPuenteRequest(
    @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "El nombre no debe superar 200 caracteres")
        String nombre,
    @NotNull(message = "El departamento es obligatorio") UUID departamentoId,
    @NotNull(message = "El municipio es obligatorio") UUID municipioId,
    @NotBlank(message = "La ruta es obligatoria")
        @Size(max = 100, message = "La ruta no debe superar 100 caracteres")
        String ruta,
    @DecimalMin(value = "0", message = "El kilometraje no puede ser negativo")
        @Digits(
            integer = 7,
            fraction = 3,
            message = "El kilometraje admite hasta 7 enteros y 3 decimales")
        BigDecimal kilometraje,
    @NotNull(message = "La latitud es obligatoria")
        @DecimalMin(value = "-90", message = "La latitud debe ser mayor o igual a -90")
        @DecimalMax(value = "90", message = "La latitud debe ser menor o igual a 90")
        BigDecimal latitud,
    @NotNull(message = "La longitud es obligatoria")
        @DecimalMin(value = "-180", message = "La longitud debe ser mayor o igual a -180")
        @DecimalMax(value = "180", message = "La longitud debe ser menor o igual a 180")
        BigDecimal longitud,
    boolean confirmarCercania) {}
