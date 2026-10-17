package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConversorMedicionesIc {

  private final ConversorIndicadoresIc conversor;

  public ConversorMedicionesIc(ConversorIndicadoresIc conversor) {
    this.conversor = conversor;
  }

  public BigDecimal convertir(
      JsonNode valor, JsonNode umbrales, JsonNode reglas, String referencia) {

    if (valor == null || !valor.isNumber()) {
      throw error("medicion_ic_invalida", "Falta una medicion numerica en " + referencia);
    }

    BigDecimal medicion = valor.decimalValue();

    if (medicion.signum() < 0) {
      throw error(
          "medicion_ic_invalida", "La medicion de " + referencia + " no puede ser negativa");
    }

    if (umbrales == null || !umbrales.isObject()) {
      throw error("configuracion_ic_incompleta", "Faltan los umbrales numericos de " + referencia);
    }

    BigDecimal leve = leerUmbral(umbrales, "LEVE", referencia);
    BigDecimal moderado = leerUmbral(umbrales, "MODERADO", referencia);
    BigDecimal severo = leerUmbral(umbrales, "SEVERO", referencia);

    if (leve.signum() <= 0 || moderado.compareTo(leve) <= 0 || severo.compareTo(moderado) <= 0) {
      throw error(
          "umbrales_ic_invalidos",
          "Los umbrales de " + referencia + " deben cumplir 0 < LEVE < MODERADO < SEVERO");
    }

    if (medicion.compareTo(severo) > 0) {
      throw error(
          "medicion_ic_fuera_de_umbrales",
          "La medicion de "
              + referencia
              + " supera el maximo configurado: "
              + severo.toPlainString());
    }

    String nivel;

    if (medicion.signum() == 0) {
      nivel = "NO_PRESENTA";
    } else if (medicion.compareTo(leve) <= 0) {
      nivel = "LEVE";
    } else if (medicion.compareTo(moderado) <= 0) {
      nivel = "MODERADO";
    } else {
      nivel = "SEVERO";
    }

    return conversor.convertirSeveridad(
        com.fasterxml.jackson.databind.node.TextNode.valueOf(nivel), reglas, referencia);
  }

  private BigDecimal leerUmbral(JsonNode umbrales, String nivel, String referencia) {

    JsonNode valor = umbrales.get(nivel);

    if (valor == null || !valor.isNumber()) {
      throw error(
          "configuracion_ic_incompleta",
          "Falta configurar el umbral " + nivel + " de " + referencia);
    }

    return valor.decimalValue();
  }

  private ApiException error(String codigo, String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        codigo,
        "Mediciones del indice de condicion invalidas",
        detalle);
  }
}
