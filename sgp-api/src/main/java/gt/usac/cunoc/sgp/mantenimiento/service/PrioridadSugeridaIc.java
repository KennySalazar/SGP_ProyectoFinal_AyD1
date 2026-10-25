package gt.usac.cunoc.sgp.mantenimiento.service;

import gt.usac.cunoc.sgp.formulario.entity.ConfiguracionIcElemento;
import gt.usac.cunoc.sgp.inspeccion.entity.ResultadoIc;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** HU-067 deterministic priority calculation for newly generated proposals. */
final class PrioridadSugeridaIc {

  private static final BigDecimal CIEN = BigDecimal.valueOf(100);

  private PrioridadSugeridaIc() {}

  static Calculo calcular(
      PrioridadOrden base,
      ResultadoIc resultado,
      UUID versionFormularioId,
      String elementoRef,
      List<ConfiguracionIcElemento> configuraciones) {
    List<String> motivos = new ArrayList<>();
    BigDecimal ic = resultado == null ? null : resultado.getIndiceCalculado();
    if (resultado == null) {
      motivos.add("No hay un IC publicado disponible.");
    } else {
      if (ic == null) motivos.add("No hay un IC publicado disponible.");
      if (!Objects.equals(resultado.getVersionConfiguracionId(), versionFormularioId)) {
        motivos.add("La versión del resultado IC no coincide con la inspección.");
      }
      if (ic != null && (ic.compareTo(BigDecimal.ZERO) < 0 || ic.compareTo(CIEN) > 0)) {
        motivos.add("El IC publicado está fuera del rango de 0 a 100.");
      }
    }

    List<ConfiguracionIcElemento> pesos = configuraciones == null ? List.of() : configuraciones;
    List<ConfiguracionIcElemento> coincidentes =
        pesos.stream().filter(c -> Objects.equals(c.getElementoRef(), elementoRef)).toList();
    BigDecimal pesoElemento = null;
    if (coincidentes.isEmpty()) {
      motivos.add("No hay un peso configurado para el elemento afectado.");
    } else if (coincidentes.size() != 1) {
      motivos.add("Hay más de un peso configurado para el elemento en esa versión.");
    } else {
      pesoElemento = coincidentes.getFirst().getPeso();
      if (pesoElemento == null || pesoElemento.signum() <= 0) {
        motivos.add("El peso configurado para el elemento no es positivo.");
      }
    }

    BigDecimal pesoMaximo =
        pesos.stream()
            .map(ConfiguracionIcElemento::getPeso)
            .filter(Objects::nonNull)
            .filter(p -> p.signum() > 0)
            .max(BigDecimal::compareTo)
            .orElse(null);
    if (pesoMaximo == null) motivos.add("La versión no tiene un peso máximo positivo disponible.");

    BigDecimal relativo = null;
    if (pesoElemento != null && pesoElemento.signum() > 0 && pesoMaximo != null) {
      relativo = pesoElemento.divide(pesoMaximo, 16, RoundingMode.HALF_UP);
      if (pesoElemento.compareTo(pesoMaximo) > 0
          || relativo.compareTo(BigDecimal.ZERO) < 0
          || relativo.compareTo(BigDecimal.ONE) > 0) {
        motivos.add("El peso relativo no está dentro del rango de 0 a 1.");
      }
    }

    if (!motivos.isEmpty()) {
      return new Calculo(base, ic, pesoElemento, pesoMaximo, relativo, String.join(";", motivos));
    }

    int baseIndex = indice(base);
    BigDecimal incremento =
        BigDecimal.valueOf(3L - baseIndex)
            .multiply(CIEN.subtract(ic))
            .multiply(pesoElemento)
            .divide(CIEN.multiply(pesoMaximo), 0, RoundingMode.HALF_UP);
    int sugerida = baseIndex + incremento.intValueExact();
    return new Calculo(prioridad(sugerida), ic, pesoElemento, pesoMaximo, relativo, null);
  }

  private static int indice(PrioridadOrden prioridad) {
    return switch (prioridad) {
      case BAJA -> 0;
      case MEDIA -> 1;
      case ALTA -> 2;
      case CRITICA -> 3;
    };
  }

  private static PrioridadOrden prioridad(int indice) {
    return switch (indice) {
      case 0 -> PrioridadOrden.BAJA;
      case 1 -> PrioridadOrden.MEDIA;
      case 2 -> PrioridadOrden.ALTA;
      case 3 -> PrioridadOrden.CRITICA;
      default ->
          throw new IllegalArgumentException("Indice de prioridad fuera de rango: " + indice);
    };
  }

  record Calculo(
      PrioridadOrden prioridad,
      BigDecimal ic,
      BigDecimal pesoElemento,
      BigDecimal pesoMaximo,
      BigDecimal pesoRelativo,
      String motivoFallback) {}
}
