package gt.usac.cunoc.sgp.inspeccion.config;

import static org.assertj.core.api.Assertions.*;

import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

class EvidenciaFotograficaPropertiesTest {
  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(EvidenciaFotograficaProperties.class);

  private ApplicationContextRunner propuesta() {
    return contexto.withPropertyValues(
        "app.evidencia-fotografica.minimos.ACCESO=1",
        "app.evidencia-fotografica.minimos.SUPERESTRUCTURA=1",
        "app.evidencia-fotografica.minimos.SUBESTRUCTURA=1",
        "app.evidencia-fotografica.minimos.CAUCE=1");
  }

  @Test
  void propiedadesRealesIncluyenLaPropuestaSinExigirOtra() {
    contexto
        .withInitializer(
            c -> {
              try {
                c.getEnvironment()
                    .getPropertySources()
                    .addLast(
                        new ResourcePropertySource(
                            new ClassPathResource("application.properties")));
              } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
              }
            })
        .run(
            c -> {
              assertThat(c).hasNotFailed();
              var politica = c.getBean(EvidenciaFotograficaProperties.class);
              assertThat(politica.minimosPorCategoria())
                  .isEqualTo(
                      Map.of("ACCESO", 1, "SUPERESTRUCTURA", 1, "SUBESTRUCTURA", 1, "CAUCE", 1));
              assertThatThrownBy(() -> politica.getMinimos().put(CategoriaFoto.OTRA, 1))
                  .isInstanceOf(UnsupportedOperationException.class);
              assertThatThrownBy(() -> politica.minimosPorCategoria().put("OTRA", 1))
                  .isInstanceOf(UnsupportedOperationException.class);
            });
  }

  @Test
  void cantidadesSonParametrizadas() {
    propuesta()
        .withPropertyValues("app.evidencia-fotografica.minimos.ACCESO=2")
        .run(
            c ->
                assertThat(c.getBean(EvidenciaFotograficaProperties.class).minimosPorCategoria())
                    .containsEntry("ACCESO", 2));
  }

  @Test
  void configuracionAusenteImpideArrancar() {
    contexto.run(c -> assertThat(c).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACCESO", "SUPERESTRUCTURA", "SUBESTRUCTURA", "CAUCE"})
  void categoriaObligatoriaAusenteImpideArrancar(String ausente) {
    String[] propiedades =
        Map.of("ACCESO", 1, "SUPERESTRUCTURA", 1, "SUBESTRUCTURA", 1, "CAUCE", 1).keySet().stream()
            .filter(c -> !c.equals(ausente))
            .map(c -> "app.evidencia-fotografica.minimos." + c + "=1")
            .toArray(String[]::new);
    contexto.withPropertyValues(propiedades).run(c -> assertThat(c).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "", "abc"})
  void minimoInvalidoImpideArrancar(String valor) {
    propuesta()
        .withPropertyValues("app.evidencia-fotografica.minimos.ACCESO=" + valor)
        .run(c -> assertThat(c).hasFailed());
  }

  @Test
  void categoriaAjenaAlEnumCompartidoImpideArrancar() {
    propuesta()
        .withPropertyValues("app.evidencia-fotografica.minimos.DESCONOCIDA=1")
        .run(c -> assertThat(c).hasFailed());
  }
}
