package gt.usac.cunoc.sgp.revision.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import gt.usac.cunoc.sgp.revision.model.TipoDestinoObservacion;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * La existencia del elemento y la pertenencia del UUID se validarán contra el snapshot del ciclo.
 */
public record DestinoObservacion(
    @NotNull TipoDestinoObservacion tipo,
    @Size(max = 300) String elementoRef,
    UUID instanciaElementoId,
    CategoriaFoto categoriaFotografica) {

  @JsonIgnore
  @AssertTrue(
      message = "Indique un elemento con referencia o una categoría fotográfica, sin mezclarlos")
  public boolean isCoherente() {
    if (tipo == null) return false;
    return switch (tipo) {
      case ELEMENTO ->
          elementoRef != null && !elementoRef.isBlank() && categoriaFotografica == null;
      case CATEGORIA_FOTOGRAFICA ->
          categoriaFotografica != null && elementoRef == null && instanciaElementoId == null;
    };
  }
}
