package gt.usac.cunoc.sgp.inspeccion.config;

import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Política del servidor; las cantidades siguen pendientes de ratificación académica. */
@Component
@ConfigurationProperties(prefix = "app.evidencia-fotografica")
public class EvidenciaFotograficaProperties {

  private Map<CategoriaFoto, Integer> minimos = new EnumMap<>(CategoriaFoto.class);

  public Map<CategoriaFoto, Integer> getMinimos() {
    return minimos;
  }

  public void setMinimos(Map<CategoriaFoto, Integer> minimos) {
    this.minimos = new EnumMap<>(CategoriaFoto.class);
    if (minimos != null) this.minimos.putAll(minimos);
  }

  @PostConstruct
  void validar() {
    var obligatorias =
        EnumSet.of(
            CategoriaFoto.ACCESO,
            CategoriaFoto.SUPERESTRUCTURA,
            CategoriaFoto.SUBESTRUCTURA,
            CategoriaFoto.CAUCE);
    if (!minimos.keySet().containsAll(obligatorias)
        || minimos.values().stream().anyMatch(valor -> valor == null || valor <= 0)) {
      throw new IllegalArgumentException(
          "La evidencia requiere mínimos positivos en ACCESO, SUPERESTRUCTURA, SUBESTRUCTURA y CAUCE.");
    }
    minimos = Map.copyOf(minimos);
  }

  public Map<String, Integer> minimosPorCategoria() {
    return minimos.entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(entrada -> entrada.getKey().name(), Map.Entry::getValue));
  }
}
