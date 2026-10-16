package gt.usac.cunoc.sgp.inspeccion.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record IniciarInspeccionRequest(
    UUID id,
    @NotNull(message = "El puente es obligatorio") UUID puenteId,
    @NotNull(message = "La fecha de inspección es obligatoria") LocalDate fechaInspeccion,
    @NotNull(message = "El identificador de dispositivo es obligatorio") String dispositivoId,
    String nombreAutor,
    Double latitud,
    Double longitud,
    BigDecimal precisionGpsM,
    boolean confirmarDatosPermanentes,
    List<AcompananteRequest> acompanantes) {}
