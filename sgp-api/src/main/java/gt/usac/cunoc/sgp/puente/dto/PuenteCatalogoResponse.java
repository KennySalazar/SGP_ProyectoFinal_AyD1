package gt.usac.cunoc.sgp.puente.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PuenteCatalogoResponse(
    UUID id,
    String codigo,
    String nombre,
    DepartamentoResponse departamento,
    MunicipioResponse municipio,
    Double latitud,
    Double longitud,
    boolean activo,
    String estadoActual,
    BigDecimal indiceCondicionActual,
    LocalDate fechaUltimaInspeccion,
    UUID versionFormularioIdIcVigente) {

  public PuenteCatalogoResponse(
      UUID id,
      String codigo,
      String nombre,
      DepartamentoResponse departamento,
      MunicipioResponse municipio,
      Double latitud,
      Double longitud,
      boolean activo,
      String estadoActual,
      BigDecimal indiceCondicionActual,
      LocalDate fechaUltimaInspeccion) {
    this(
        id,
        codigo,
        nombre,
        departamento,
        municipio,
        latitud,
        longitud,
        activo,
        estadoActual,
        indiceCondicionActual,
        fechaUltimaInspeccion,
        null);
  }
}
