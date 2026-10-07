package gt.usac.cunoc.sgp.curso.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CrearCursoRequest(
    @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombre,
    @NotBlank(message = "El periodo es obligatorio") @Size(max = 50) String periodo,
    @NotNull(message = "El catedratico es obligatorio") UUID catedraticoId,
    @NotNull(message = "La fecha de inicio es obligatoria") LocalDate fechaInicio,
    @NotNull(message = "La fecha de fin es obligatoria") LocalDate fechaFin) {
  @AssertTrue(message = "La fecha de fin debe ser posterior o igual a la fecha de inicio")
  public boolean isRangoFechasValido() {
    return fechaInicio == null || fechaFin == null || !fechaFin.isBefore(fechaInicio);
  }
}
