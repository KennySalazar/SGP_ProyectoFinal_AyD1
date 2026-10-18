package gt.usac.cunoc.sgp.sync.service;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class HuellaSolicitud {
  private HuellaSolicitud() {}

  /**
   * Ordena objetos recursivamente, conserva el orden de listas; incluir destino y checksum en el
   * valor.
   */
  public static String calcular(JsonNode valor) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(ordenar(valor).toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static JsonNode ordenar(JsonNode n) {
    if (n.isObject()) {
      var resultado = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
      var nombres = new TreeSet<String>();
      n.fieldNames().forEachRemaining(nombres::add);
      for (String k : nombres) resultado.set(k, ordenar(n.get(k)));
      return resultado;
    }
    if (n.isArray()) {
      var resultado = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
      n.forEach(v -> resultado.add(ordenar(v)));
      return resultado;
    }
    return n.deepCopy();
  }
}
