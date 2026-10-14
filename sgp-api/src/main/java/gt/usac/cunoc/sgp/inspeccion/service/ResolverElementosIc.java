package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ResolverElementosIc {

  public List<ElementoResuelto> resolver(JsonNode datos, String elementoRef) {
    if (datos == null || !datos.isObject()) {
      throw error("Los datos de inspeccion deben ser un objeto JSON.");
    }

    if (elementoRef == null || !elementoRef.startsWith("/") || elementoRef.length() < 2) {
      throw error("La referencia del elemento debe ser una ruta JSON valida.");
    }

    JsonPointer puntero = JsonPointer.compile(elementoRef);

    List<ElementoResuelto> encontrados = new ArrayList<>();
    recorrer(datos, puntero, "", null, encontrados);

    return List.copyOf(encontrados);
  }

  private void recorrer(
      JsonNode nodo,
      JsonPointer pendiente,
      String ruta,
      UUID instanciaId,
      List<ElementoResuelto> encontrados) {

    if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
      return;
    }

    if (nodo.isArray()) {
      Set<UUID> identificadores = new HashSet<>();

      for (int indice = 0; indice < nodo.size(); indice++) {
        JsonNode instancia = nodo.get(indice);
        String rutaInstancia = ruta + "/" + indice;

        if (!instancia.isObject()) {
          throw error("La instancia repetible debe ser un objeto: " + rutaInstancia);
        }

        UUID identificador = leerInstanciaId(instancia, rutaInstancia);

        if (!identificadores.add(identificador)) {
          throw error("La instancia repetible esta duplicada en " + ruta + ": " + identificador);
        }

        recorrer(instancia, pendiente, rutaInstancia, identificador, encontrados);
      }

      return;
    }

    if (pendiente.matches()) {
      if (!nodo.isObject()) {
        throw error("El elemento evaluable debe ser un objeto: " + ruta);
      }

      encontrados.add(new ElementoResuelto(instanciaId, ruta, nodo));
      return;
    }

    if (!nodo.isObject()) {
      throw error("No se puede recorrer la referencia a traves de: " + ruta);
    }

    String propiedad = pendiente.getMatchingProperty();

    recorrer(
        nodo.get(propiedad),
        pendiente.tail(),
        ruta + "/" + escapar(propiedad),
        instanciaId,
        encontrados);
  }

  private UUID leerInstanciaId(JsonNode instancia, String ruta) {
    JsonNode identificador = instancia.get("instancia_id");

    if (identificador == null || !identificador.isTextual()) {
      throw error("Falta instancia_id en el elemento repetible: " + ruta);
    }

    String valor = identificador.textValue();

    try {
      UUID uuid = UUID.fromString(valor);

      if (!uuid.toString().equalsIgnoreCase(valor)) {
        throw error("instancia_id no tiene el formato UUID esperado: " + ruta);
      }

      return uuid;
    } catch (IllegalArgumentException exception) {
      throw error("instancia_id no es un UUID valido: " + ruta);
    }
  }

  private String escapar(String propiedad) {
    return propiedad.replace("~", "~0").replace("/", "~1");
  }

  private ApiException error(String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "datos_ic_invalidos",
        "Datos del indice de condicion invalidos",
        detalle);
  }

  public record ElementoResuelto(UUID instanciaId, String rutaDatos, JsonNode datos) {}
}
