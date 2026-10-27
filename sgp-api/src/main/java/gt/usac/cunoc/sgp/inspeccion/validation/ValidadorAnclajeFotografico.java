package gt.usac.cunoc.sgp.inspeccion.validation;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.validation.AnclajeInvalidoException;
import gt.usac.cunoc.sgp.formulario.validation.ValidadorAnclajeFormulario;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ValidadorAnclajeFotografico {
  private final ValidadorAnclajeFormulario validador = new ValidadorAnclajeFormulario();

  public void validar(Inspeccion inspeccion, String ref, UUID instancia) {
    if (ref == null) {
      if (instancia != null) throw invalido();
      return;
    }
    try {
      validador.validarFotografiaHu028(
          inspeccion.getVersionFormulario().getEsquema(), inspeccion.getDatos(), ref, instancia);
    } catch (AnclajeInvalidoException e) {
      throw invalido();
    }
  }

  private ApiException invalido() {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "anclaje_fotografico_invalido",
        "Referencia inválida",
        "El elemento o la instancia no pertenecen a la versión y datos de esta inspección.");
  }
}
