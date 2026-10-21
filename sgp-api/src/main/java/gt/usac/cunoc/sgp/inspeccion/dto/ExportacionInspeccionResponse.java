package gt.usac.cunoc.sgp.inspeccion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Datos estructurados de la inspección publicada para exportación en formato JSON sanitizado,
 * preservando la estructura histórica del formulario y excluyendo referencias internas sensibles
 * (RN-FRM-05).
 */
public record ExportacionInspeccionResponse(
    UUID id,
    LocalDate fechaInspeccion,
    PuenteResumenExportacion puente,
    VersionFormularioExportacion versionFormulario,
    RelacionVersionActivaResponse relacionVersionActiva,
    String estado,
    String vigencia,
    JsonNode identificacionCapturada,
    JsonNode datos,
    DatosPermanentesResponse datosPermanentes,
    ResultadoIcPublicadoResponse ic,
    List<FotografiaExportacionResponse> fotografias,
    List<PropuestaMantenimientoExportacionResponse> propuestasMantenimiento,
    Instant exportadoEn) {

  public record PuenteResumenExportacion(
      UUID id, String codigo, String nombre, String departamento, String municipio) {}

  public record VersionFormularioExportacion(UUID id, String codigo, JsonNode esquema) {}

  public record FotografiaExportacionResponse(
      UUID id, String categoria, String elementoRef, Instant capturadoEn) {}

  public record PropuestaMantenimientoExportacionResponse(
      UUID id, String tipo, String estado, String descripcion, String prioridadSugerida) {}
}
