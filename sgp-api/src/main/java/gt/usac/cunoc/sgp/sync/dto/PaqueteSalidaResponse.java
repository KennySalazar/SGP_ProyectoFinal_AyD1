package gt.usac.cunoc.sgp.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.inspeccion.dto.DatosPermanentesResponse;
import gt.usac.cunoc.sgp.puente.dto.DepartamentoResponse;
import gt.usac.cunoc.sgp.puente.dto.MunicipioResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaqueteSalidaResponse(
    FormularioSalida formulario,
    List<PuenteSalida> puentes,
    List<InspeccionSalida> inspecciones,
    List<AsignacionSalida> asignaciones) {
  public record FormularioSalida(UUID id, String codigo, String hashContenido, JsonNode esquema) {}

  public record PuenteSalida(
      UUID id,
      String codigo,
      String nombre,
      String ruta,
      BigDecimal kilometraje,
      DepartamentoResponse departamento,
      MunicipioResponse municipio,
      double latitud,
      double longitud,
      boolean activo,
      String estadoActual,
      BigDecimal indiceCondicionActual,
      LocalDate fechaUltimaInspeccion,
      long version) {}

  public record InspeccionSalida(
      UUID id,
      UUID puenteId,
      UUID versionFormularioId,
      String codigoVersionFormulario,
      LocalDate fechaInspeccion,
      Instant publicadoEn,
      JsonNode identificacionCapturada,
      JsonNode datos,
      DatosPermanentesResponse datosPermanentes) {}

  public record AsignacionSalida(UUID id, UUID puenteId, UUID cursoEstudianteId) {}
}
