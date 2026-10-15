package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class HuellaEntradaIc {

  private final MessageDigest sha256;

  public HuellaEntradaIc() throws NoSuchAlgorithmException {
    this.sha256 = MessageDigest.getInstance("SHA-256");
  }

  public synchronized String calcular(EntradaCalculoIc entrada) {
    if (entrada == null) {
      throw error("La entrada del calculo es obligatoria");
    }

    ObjectNode documento = JsonNodeFactory.instance.objectNode();

    documento.put(
        "versionFormularioId",
        entrada.versionFormularioId() == null ? null : entrada.versionFormularioId().toString());

    documento.put("codigoFormulario", entrada.codigoFormulario());
    documento.put("versionAlgoritmo", entrada.versionAlgoritmo());
    documento.set("datos", normalizar(entrada.datos()));
    documento.set("esquema", normalizar(entrada.esquema()));
    documento.set("catalogoElementos", normalizar(entrada.catalogoElementos()));
    documento.set("reglasEvaluacion", normalizar(entrada.reglasEvaluacion()));
    documento.set("pesos", normalizarPesos(entrada.pesos()));

    byte[] contenido = normalizar(documento).toString().getBytes(StandardCharsets.UTF_8);

    return HexFormat.of().formatHex(sha256.digest(contenido));
  }

  private JsonNode normalizarPesos(Map<String, BigDecimal> pesos) {
    if (pesos == null) {
      return JsonNodeFactory.instance.nullNode();
    }

    ObjectNode resultado = JsonNodeFactory.instance.objectNode();

    for (Map.Entry<String, BigDecimal> entrada : pesos.entrySet()) {
      if (entrada.getKey() == null) {
        throw error("Los pesos no pueden contener una referencia nula");
      }

      BigDecimal peso = entrada.getValue();

      if (peso == null) {
        resultado.putNull(entrada.getKey());
      } else {
        resultado.set(
            entrada.getKey(), JsonNodeFactory.instance.numberNode(peso.stripTrailingZeros()));
      }
    }

    return normalizar(resultado);
  }

  private JsonNode normalizar(JsonNode nodo) {
    if (nodo == null || nodo.isNull() || nodo.isMissingNode()) {
      return JsonNodeFactory.instance.nullNode();
    }

    if (nodo.isObject()) {
      ObjectNode resultado = JsonNodeFactory.instance.objectNode();
      List<String> propiedades = new ArrayList<>();
      nodo.fieldNames().forEachRemaining(propiedades::add);
      propiedades.sort(String::compareTo);

      for (String propiedad : propiedades) {
        resultado.set(propiedad, normalizar(nodo.get(propiedad)));
      }

      return resultado;
    }

    if (nodo.isArray()) {
      var resultado = JsonNodeFactory.instance.arrayNode();

      for (JsonNode elemento : nodo) {
        resultado.add(normalizar(elemento));
      }

      return resultado;
    }

    if (nodo.isNumber()) {
      return JsonNodeFactory.instance.numberNode(nodo.decimalValue().stripTrailingZeros());
    }

    return nodo.deepCopy();
  }

  private ApiException error(String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "entrada_ic_invalida",
        "Entrada del indice de condicion invalida",
        detalle);
  }
}
