package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc.CondicionAnulacion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc.DesgloseElemento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MotorPonderacionIc {

  private static final BigDecimal CIEN = new BigDecimal("100");
  private static final BigDecimal CUATRO = new BigDecimal("4");

  private final AgregadorCalificacionesIc agregador;

  public MotorPonderacionIc(AgregadorCalificacionesIc agregador) {
    this.agregador = agregador;
  }

  public ResultadoCalculoIc calcular(
      EntradaCalculoIc entrada,
      List<DesgloseElemento> desglose,
      List<CondicionAnulacion> condiciones,
      String hashEntrada) {

    if (entrada == null || entrada.versionFormularioId() == null) {
      throw error("configuracion_ic_incompleta", "Falta identificar la version del formulario");
    }

    if (entrada.codigoFormulario() == null || entrada.codigoFormulario().isBlank()) {
      throw error("configuracion_ic_incompleta", "Falta el codigo de la version del formulario");
    }

    if (entrada.versionAlgoritmo() == null || entrada.versionAlgoritmo().isBlank()) {
      throw error("configuracion_ic_incompleta", "Falta la version del algoritmo de calculo");
    }

    if (hashEntrada == null || !hashEntrada.matches("[0-9a-f]{64}")) {
      throw error("hash_entrada_ic_invalido", "El hash de entrada debe ser SHA-256 hexadecimal");
    }

    if (desglose == null || desglose.isEmpty()) {
      throw error("sin_elementos_evaluados_ic", "No hay elementos evaluados para calcular el IC");
    }

    if (condiciones == null) {
      throw error("datos_ic_invalidos", "La lista de condiciones de anulacion es obligatoria");
    }

    BigDecimal sumaPesos = BigDecimal.ZERO;
    BigDecimal sumaPonderada = BigDecimal.ZERO;

    for (DesgloseElemento elemento : desglose) {
      if (elemento == null) {
        throw error("datos_ic_invalidos", "El desglose contiene un elemento nulo");
      }

      BigDecimal peso = elemento.peso();

      if (peso == null || peso.signum() <= 0) {
        throw error(
            "configuracion_ic_incompleta",
            "El elemento " + elemento.elementoRef() + " requiere un peso positivo");
      }

      agregador.validarCalificacion(elemento.calificacionAgregada(), elemento.elementoRef());

      BigDecimal aporte = elemento.calificacionAgregada().multiply(peso);

      if (elemento.aportePonderado() == null || elemento.aportePonderado().compareTo(aporte) != 0) {
        throw error(
            "desglose_ic_incongruente",
            "El aporte ponderado no coincide en " + elemento.elementoRef());
      }

      sumaPesos = sumaPesos.add(peso);
      sumaPonderada = sumaPonderada.add(aporte);
    }

    BigDecimal denominador = CUATRO.multiply(sumaPesos);

    BigDecimal indice =
        denominador
            .subtract(sumaPonderada)
            .multiply(CIEN)
            .divide(denominador, 5, RoundingMode.HALF_UP);

    EstadoCondicion estadoPorRango = clasificar(indice, entrada.reglasEvaluacion());

    for (CondicionAnulacion condicion : condiciones) {
      if (condicion == null || condicion.estadoResultante() != EstadoCondicion.MALO) {
        throw error(
            "regla_anulacion_ic_invalida", "Las condiciones criticas deben forzar el estado MALO");
      }
    }

    EstadoCondicion estadoCalculado = condiciones.isEmpty() ? estadoPorRango : EstadoCondicion.MALO;

    return new ResultadoCalculoIc(
        entrada.versionFormularioId(),
        entrada.codigoFormulario(),
        entrada.versionAlgoritmo(),
        hashEntrada,
        indice,
        estadoPorRango,
        estadoCalculado,
        sumaPesos,
        sumaPonderada,
        desglose,
        condiciones);
  }

  private EstadoCondicion clasificar(BigDecimal indice, JsonNode reglas) {
    if (reglas == null || !reglas.isObject()) {
      throw error("configuracion_ic_incompleta", "Faltan las reglas de evaluacion del IC");
    }

    JsonNode rangos = reglas.get("rangos_estado");

    if (rangos == null || !rangos.isObject()) {
      throw error("configuracion_ic_incompleta", "Faltan los rangos del estado del IC");
    }

    BigDecimal bueno = leerLimite(rangos, "BUENO");
    BigDecimal regular = leerLimite(rangos, "REGULAR");
    BigDecimal malo = leerLimite(rangos, "MALO");

    if (malo.signum() != 0
        || regular.compareTo(malo) <= 0
        || bueno.compareTo(regular) <= 0
        || bueno.compareTo(CIEN) > 0) {
      throw error(
          "rangos_ic_invalidos", "Los limites deben cumplir MALO = 0 < REGULAR < BUENO <= 100");
    }

    if (indice.compareTo(bueno) >= 0) {
      return EstadoCondicion.BUENO;
    }

    if (indice.compareTo(regular) >= 0) {
      return EstadoCondicion.REGULAR;
    }

    return EstadoCondicion.MALO;
  }

  private BigDecimal leerLimite(JsonNode rangos, String estado) {
    JsonNode limite = rangos.get(estado);

    if (limite == null || !limite.isNumber()) {
      throw error(
          "configuracion_ic_incompleta",
          "Falta configurar el limite numerico del estado " + estado);
    }

    return limite.decimalValue();
  }

  private ApiException error(String codigo, String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        codigo,
        "Calculo del indice de condicion invalido",
        detalle);
  }
}
