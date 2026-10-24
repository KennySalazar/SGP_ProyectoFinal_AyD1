package gt.usac.cunoc.sgp.mantenimiento.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record ReglaMantenimientoResponse(
    UUID id,
    String codigo,
    Integer numeroRevision,
    UUID versionFormularioId,
    String versionFormularioCodigo,
    String elementoRef,
    JsonNode condicion,
    TipoOrden tipoMantenimiento,
    String intervencionSugerida,
    JsonNode reglaPrioridad,
    boolean activa,
    boolean provisional,
    String estadoValidacion,
    UUID creadoPorId,
    UUID validadoPorId,
    Instant validadoEn,
    Instant creadoEn,
    String validadoPorNombre) {

  public ReglaMantenimientoResponse(
      UUID id,
      String codigo,
      Integer numeroRevision,
      UUID versionFormularioId,
      String versionFormularioCodigo,
      String elementoRef,
      JsonNode condicion,
      TipoOrden tipoMantenimiento,
      String intervencionSugerida,
      JsonNode reglaPrioridad,
      boolean activa,
      boolean provisional,
      String estadoValidacion,
      UUID creadoPorId,
      UUID validadoPorId,
      Instant validadoEn,
      Instant creadoEn) {
    this(
        id,
        codigo,
        numeroRevision,
        versionFormularioId,
        versionFormularioCodigo,
        elementoRef,
        condicion,
        tipoMantenimiento,
        intervencionSugerida,
        reglaPrioridad,
        activa,
        provisional,
        estadoValidacion,
        creadoPorId,
        validadoPorId,
        validadoEn,
        creadoEn,
        null);
  }

  public ReglaMantenimientoResponse conNombreValidador(String nombre) {
    return new ReglaMantenimientoResponse(
        id,
        codigo,
        numeroRevision,
        versionFormularioId,
        versionFormularioCodigo,
        elementoRef,
        condicion,
        tipoMantenimiento,
        intervencionSugerida,
        reglaPrioridad,
        activa,
        provisional,
        estadoValidacion,
        creadoPorId,
        validadoPorId,
        validadoEn,
        creadoEn,
        nombre);
  }

  @JsonProperty("rotuloValidacion")
  public String rotuloValidacion() {
    if (provisional || validadoEn == null) return "Provisional · sin validar";
    String nombre =
        validadoPorNombre == null || validadoPorNombre.isBlank() ? "usuario" : validadoPorNombre;
    return "Validada por "
        + nombre
        + " el "
        + DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Guatemala"))
            .format(validadoEn);
  }
}
