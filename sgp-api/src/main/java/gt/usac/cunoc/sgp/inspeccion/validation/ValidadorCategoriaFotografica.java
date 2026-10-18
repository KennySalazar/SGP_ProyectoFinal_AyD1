package gt.usac.cunoc.sgp.inspeccion.validation;

import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import gt.usac.cunoc.sgp.formulario.validation.AnclajeInvalidoException;
import org.springframework.stereotype.Component;

/**
 * Valida el catálogo existente sin representar categorías como rutas del formulario. El contrato
 * para identificar este destino en una observación queda pendiente de HU-037.
 */
@Component
public class ValidadorCategoriaFotografica {
  public CategoriaFoto validar(String codigo) {
    if (codigo != null) {
      try {
        return CategoriaFoto.valueOf(codigo);
      } catch (IllegalArgumentException e) {
        // Una categoría debe coincidir exactamente con el código del catálogo existente.
      }
    }
    throw new AnclajeInvalidoException(
        AnclajeInvalidoException.Motivo.CATEGORIA_FOTOGRAFICA_INVALIDA);
  }
}
