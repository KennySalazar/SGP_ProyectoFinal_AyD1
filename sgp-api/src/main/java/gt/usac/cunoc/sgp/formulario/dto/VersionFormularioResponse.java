package gt.usac.cunoc.sgp.formulario.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VersionFormularioResponse(
    UUID id,
    String codigo,
    EstadoVersionFormulario estado,
    boolean activa,
    JsonNode esquema,
    JsonNode vinculosAlmacenamiento,
    JsonNode reglasEvaluacionIc,
    String hashContenido,
    String versionAlgoritmoIc,
    UUID versionAnteriorId,
    String versionAnteriorCodigo,
    UUID creadoPorId,
    UUID publicadoPorId,
    Instant publicadoEn,
    UUID calibradoPorId,
    Instant calibradoEn,
    Instant creadoEn,
    Instant actualizadoEn,
    long version,
    List<ConfiguracionIcElementoResponse> pesos,
    List<MapeoCampoResponse> mapeos,
    AprobacionCalibracionResponse aprobacionCalibracion) {

  public VersionFormularioResponse(
      UUID id,
      String codigo,
      EstadoVersionFormulario estado,
      boolean activa,
      JsonNode esquema,
      JsonNode vinculosAlmacenamiento,
      JsonNode reglasEvaluacionIc,
      String hashContenido,
      String versionAlgoritmoIc,
      UUID versionAnteriorId,
      String versionAnteriorCodigo,
      UUID creadoPorId,
      UUID publicadoPorId,
      Instant publicadoEn,
      UUID calibradoPorId,
      Instant calibradoEn,
      Instant creadoEn,
      Instant actualizadoEn,
      long version,
      List<ConfiguracionIcElementoResponse> pesos,
      List<MapeoCampoResponse> mapeos) {
    this(
        id,
        codigo,
        estado,
        activa,
        esquema,
        vinculosAlmacenamiento,
        reglasEvaluacionIc,
        hashContenido,
        versionAlgoritmoIc,
        versionAnteriorId,
        versionAnteriorCodigo,
        creadoPorId,
        publicadoPorId,
        publicadoEn,
        calibradoPorId,
        calibradoEn,
        creadoEn,
        actualizadoEn,
        version,
        pesos,
        mapeos,
        null);
  }

  public VersionFormularioResponse conAprobacion(AprobacionCalibracionResponse aprobacion) {
    return new VersionFormularioResponse(
        id,
        codigo,
        estado,
        activa,
        esquema,
        vinculosAlmacenamiento,
        reglasEvaluacionIc,
        hashContenido,
        versionAlgoritmoIc,
        versionAnteriorId,
        versionAnteriorCodigo,
        creadoPorId,
        publicadoPorId,
        publicadoEn,
        calibradoPorId,
        calibradoEn,
        creadoEn,
        actualizadoEn,
        version,
        pesos,
        mapeos,
        aprobacion);
  }

  @com.fasterxml.jackson.annotation.JsonProperty("rotuloCalibracion")
  public String rotuloCalibracion() {
    if (aprobacionCalibracion != null) return aprobacionCalibracion.rotulo();
    return reglasEvaluacionIc != null
            && reglasEvaluacionIc.path("calibracion").path("provisional").asBoolean(false)
        ? "Calibracion provisional · sin aprobar"
        : "Calibracion sin aprobacion registrada";
  }
}
