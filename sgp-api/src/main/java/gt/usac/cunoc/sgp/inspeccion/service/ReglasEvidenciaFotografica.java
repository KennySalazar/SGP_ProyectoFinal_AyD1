package gt.usac.cunoc.sgp.inspeccion.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.FaltanteEvidenciaResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ValidacionEvidenciaResponse;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Reglas sin acceso a persistencia, archivos ni sesión. */
@Service
public class ReglasEvidenciaFotografica {

  private static final int MAXIMO_FOTOGRAFIAS = 60;

  /**
   * Evalúa conteos calculados por el servidor sobre una sola inspección. El llamador debe contar
   * únicamente fotografías confirmadas DISPONIBLE, excluyendo documentos, intentos de carga,
   * retiradas y purgadas. Los códigos deben proceder del catálogo compartido de HU-028.
   *
   * <p>Los mínimos son una política del servidor, nunca entrada HTTP. Mientras siga pendiente la
   * decisión académica, el llamador suministrará la propuesta de una foto en ACCESO,
   * SUPERESTRUCTURA, SUBESTRUCTURA y CAUCE. OTRA se cuenta en el total y no requiere mínimo en esa
   * propuesta. Este método conserva los mínimos parametrizados y no presupone su aprobación
   * definitiva.
   *
   * <p>La obtención y el filtrado del inventario pertenecen a la integración pendiente con HU-028;
   * este evaluador no acredita esos pasos ni autoriza una transición de estado.
   */
  public ValidacionEvidenciaResponse evaluar(
      Map<String, Long> disponiblesPorCategoria, Map<String, Integer> minimosPorCategoria) {
    Objects.requireNonNull(disponiblesPorCategoria, "Los conteos son obligatorios.");
    Objects.requireNonNull(minimosPorCategoria, "La política de mínimos es obligatoria.");
    if (minimosPorCategoria.isEmpty()) {
      throw new IllegalArgumentException("Debe definirse la política de mínimos antes de evaluar.");
    }

    long total = 0;
    for (var entrada : disponiblesPorCategoria.entrySet()) {
      validarCodigo(entrada.getKey());
      if (entrada.getValue() == null || entrada.getValue() < 0) {
        throw new IllegalArgumentException("Los conteos deben ser números no negativos.");
      }
      try {
        total = Math.addExact(total, entrada.getValue());
      } catch (ArithmeticException exception) {
        throw new IllegalArgumentException(
            "El total de fotografías excede el rango permitido.", exception);
      }
    }

    for (var entrada : minimosPorCategoria.entrySet()) {
      validarCodigo(entrada.getKey());
      if (entrada.getValue() == null || entrada.getValue() <= 0) {
        throw new IllegalArgumentException(
            "Los mínimos deben ser positivos; omita las categorías sin mínimo obligatorio.");
      }
    }

    var faltantes =
        minimosPorCategoria.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .filter(
                entrada ->
                    disponiblesPorCategoria.getOrDefault(entrada.getKey(), 0L) < entrada.getValue())
            .map(
                entrada ->
                    new FaltanteEvidenciaResponse(
                        entrada.getKey(),
                        entrada.getValue(),
                        disponiblesPorCategoria.getOrDefault(entrada.getKey(), 0L)))
            .toList();

    return new ValidacionEvidenciaResponse(
        faltantes.isEmpty() && total <= MAXIMO_FOTOGRAFIAS, total, MAXIMO_FOTOGRAFIAS, faltantes);
  }

  /**
   * Controla una carga nueva. El llamador debe contar DISPONIBLE bajo el bloqueo de inspección y
   * resolver antes los reintentos ya confirmados; este método no reserva una plaza ni autoriza.
   */
  public void validarCapacidadParaNuevaFotografia(long fotografiasDisponibles) {
    if (fotografiasDisponibles < 0) {
      throw new IllegalArgumentException("El conteo de fotografías no puede ser negativo.");
    }
    if (fotografiasDisponibles >= MAXIMO_FOTOGRAFIAS) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "limite_fotografias_excedido",
          "Límite de fotografías excedido",
          "La inspección admite como máximo 60 fotografías disponibles.");
    }
  }

  private void validarCodigo(String codigo) {
    if (codigo == null || codigo.isBlank()) {
      throw new IllegalArgumentException("El código de categoría es obligatorio.");
    }
  }
}
