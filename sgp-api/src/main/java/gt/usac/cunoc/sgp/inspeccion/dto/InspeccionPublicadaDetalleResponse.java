package gt.usac.cunoc.sgp.inspeccion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InspeccionPublicadaDetalleResponse(
    UUID id,
    UUID puenteId,
    String puenteNombre,
    UUID versionFormularioId,
    String versionFormularioCodigo,
    JsonNode esquemaFormulario,
    UUID autorId,
    String nombreAutor,
    String rolAutor,
    List<AcompananteResponse> acompanantes,
    String estado,
    LocalDate fechaInspeccion,
    String dispositivoId,
    JsonNode identificacionCapturada,
    JsonNode datos,
    DatosPermanentesResponse datosPermanentes,
    Instant creadoEn,
    Instant enviadoEn,
    Instant publicadoEn,
    ResultadoIcPublicadoResponse ic,
    UUID supersedeAId,
    String motivoCorreccion,
    UUID superadaPorId,
    String vigencia,
    RelacionVersionActivaResponse relacionVersionActiva) {
  @com.fasterxml.jackson.annotation.JsonProperty(
      value = "calibracionEndpoint",
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  public String calibracionEndpoint() {
    return "/api/v1/formularios/" + versionFormularioId + "/estado-calibracion";
  }

  public InspeccionPublicadaDetalleResponse(
      UUID id,
      UUID puenteId,
      String puenteNombre,
      UUID versionFormularioId,
      String versionFormularioCodigo,
      JsonNode esquemaFormulario,
      UUID autorId,
      String nombreAutor,
      String rolAutor,
      List<AcompananteResponse> acompanantes,
      String estado,
      LocalDate fechaInspeccion,
      String dispositivoId,
      JsonNode identificacionCapturada,
      JsonNode datos,
      DatosPermanentesResponse datosPermanentes,
      Instant creadoEn,
      Instant enviadoEn,
      Instant publicadoEn,
      ResultadoIcPublicadoResponse ic,
      UUID supersedeAId,
      String motivoCorreccion,
      UUID superadaPorId,
      String vigencia) {
    this(
        id,
        puenteId,
        puenteNombre,
        versionFormularioId,
        versionFormularioCodigo,
        esquemaFormulario,
        autorId,
        nombreAutor,
        rolAutor,
        acompanantes,
        estado,
        fechaInspeccion,
        dispositivoId,
        identificacionCapturada,
        datos,
        datosPermanentes,
        creadoEn,
        enviadoEn,
        publicadoEn,
        ic,
        supersedeAId,
        motivoCorreccion,
        superadaPorId,
        vigencia,
        null);
  }
}
