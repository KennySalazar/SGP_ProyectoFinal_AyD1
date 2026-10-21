package gt.usac.cunoc.sgp.formulario.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SemillaFormularioInitializerTest {
  @Test
  void falloDeSiembraNoImpideContinuarElArranque() {
    var servicio = mock(SemillaFormularioService.class);
    when(servicio.sembrarSiNoExisteActiva())
        .thenThrow(new IllegalStateException("base no disponible"));
    assertThatCode(() -> new SemillaFormularioInitializer(servicio).inicializar())
        .doesNotThrowAnyException();
  }

  @Test
  void ejecutaSiembraYAdmiteElSegundoArranqueSinCambios() {
    var servicio = mock(SemillaFormularioService.class);
    when(servicio.sembrarSiNoExisteActiva())
        .thenReturn(Optional.of(UUID.randomUUID()), Optional.empty());
    var inicializador = new SemillaFormularioInitializer(servicio);
    inicializador.inicializar();
    inicializador.inicializar();
    verify(servicio, times(2)).sembrarSiNoExisteActiva();
  }

  @Test
  void configurableSinCambiarCodigo() {
    var contexto =
        new ApplicationContextRunner()
            .withBean(SemillaFormularioService.class, () -> mock(SemillaFormularioService.class))
            .withUserConfiguration(SemillaFormularioInitializer.class);
    contexto
        .withPropertyValues("app.formulario.siembra.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(SemillaFormularioInitializer.class));
    contexto
        .withPropertyValues("app.formulario.siembra.enabled=true")
        .run(ctx -> assertThat(ctx).hasSingleBean(SemillaFormularioInitializer.class));
  }
}
