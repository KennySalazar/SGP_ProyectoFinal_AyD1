package gt.usac.cunoc.sgp.inspeccion.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ResultadoCalculoIc(
    UUID versionFormularioId,
    String codigoFormulario,
    String versionAlgoritmo,
    String hashEntrada,
    BigDecimal indiceCalculado,
    EstadoCondicion estadoPorRango,
    EstadoCondicion estadoCalculado,
    BigDecimal sumaPesos,
    BigDecimal sumaCalificacionesPonderadas,
    List<DesgloseElemento> desglose,
    List<CondicionAnulacion> condicionesAnulacion) {
  @com.fasterxml.jackson.annotation.JsonProperty(
      value = "calibracionEndpoint",
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  public String calibracionEndpoint() {
    return "/api/v1/formularios/" + versionFormularioId + "/estado-calibracion";
  }

  public ResultadoCalculoIc {
    desglose = List.copyOf(desglose);
    condicionesAnulacion = List.copyOf(condicionesAnulacion);
  }

  public record DesgloseElemento(
      String elementoRef,
      BigDecimal peso,
      BigDecimal calificacionAgregada,
      BigDecimal aportePonderado,
      String reglaAgregacion,
      List<DesgloseInstancia> instancias) {

    public DesgloseElemento {
      instancias = List.copyOf(instancias);
    }
  }

  public record DesgloseInstancia(
      UUID instanciaId,
      String rutaDatos,
      String existencia,
      boolean incluida,
      BigDecimal calificacion,
      List<DesgloseIndicador> indicadores) {

    public DesgloseInstancia {
      indicadores = List.copyOf(indicadores);
    }
  }

  public record DesgloseIndicador(
      String indicador, String valorOriginal, BigDecimal calificacion) {}

  public record CondicionAnulacion(
      String reglaId,
      String descripcion,
      String elementoRef,
      UUID instanciaId,
      String rutaDatos,
      String indicador,
      String valorDetectado,
      EstadoCondicion estadoResultante) {}
}
