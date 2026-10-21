package gt.usac.cunoc.sgp.foro.service;

import gt.usac.cunoc.sgp.formulario.validation.AnclajeInvalidoException;
import gt.usac.cunoc.sgp.formulario.validation.ValidadorAnclajeFormulario;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Comprueba que el campo o elemento al que se ancla un comentario existe en el esquema de la
 * versión del formulario con que se levantó la inspección y, si es repetible, que la instancia está
 * en sus datos (RN-FOR-02). Usa el mismo validador que las observaciones de revisión.
 */
@Component
public class AnclajeComentarioForo {

  private final ValidadorAnclajeFormulario validador;

  public AnclajeComentarioForo(ValidadorAnclajeFormulario validador) {
    this.validador = validador;
  }

  /** Agrega a {@code errores} el motivo por el que el ancla no es válida, si no lo es. */
  public void validar(
      Inspeccion inspeccion,
      String elementoRef,
      UUID instancia,
      List<Map<String, String>> errores) {
    try {
      validador.validar(
          inspeccion.getVersionFormulario().getEsquema(),
          inspeccion.getDatos(),
          elementoRef,
          instancia);
    } catch (AnclajeInvalidoException e) {
      errores.add(
          switch (e.getMotivo()) {
            case INSTANCIA_REQUERIDA ->
                ContenidoForo.error(
                    "instanciaElementoId", "El elemento es repetible: indica cuál instancia.");
            case INSTANCIA_NO_ENCONTRADA ->
                ContenidoForo.error(
                    "instanciaElementoId", "La instancia no existe en esta inspección.");
            case INSTANCIA_DUPLICADA ->
                ContenidoForo.error(
                    "instanciaElementoId",
                    "La instancia aparece repetida en los datos de la inspección.");
            case INSTANCIA_NO_PERMITIDA ->
                ContenidoForo.error(
                    "instanciaElementoId", "El elemento no es repetible: no lleva instancia.");
            case DATOS_REPETIBLE_INVALIDOS ->
                ContenidoForo.error(
                    "instanciaElementoId",
                    "Los datos de la inspección no permiten ubicar la instancia.");
            case ELEMENTO_INEXISTENTE ->
                ContenidoForo.error(
                    "elementoRef",
                    "El elemento no existe en el formulario con que se levantó la inspección.");
            case REPETIBLE_ANIDADO_NO_SOPORTADO ->
                ContenidoForo.error(
                    "elementoRef", "No se admiten elementos repetibles dentro de otros.");
            case RUTA_INVALIDA, REFERENCIA_ESQUEMA_INVALIDA, CATEGORIA_FOTOGRAFICA_INVALIDA ->
                ContenidoForo.error(
                    "elementoRef", "La referencia al elemento no tiene un formato válido.");
          });
    }
  }
}
