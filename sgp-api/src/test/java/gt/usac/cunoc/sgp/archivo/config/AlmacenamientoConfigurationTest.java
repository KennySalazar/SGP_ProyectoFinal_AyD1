package gt.usac.cunoc.sgp.archivo.config;

import static org.assertj.core.api.Assertions.*;

import gt.usac.cunoc.sgp.archivo.service.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AlmacenamientoConfigurationTest {
  @TempDir Path directorio;
  final ApplicationContextRunner contexto =
      new ApplicationContextRunner().withUserConfiguration(AlmacenamientoConfiguration.class);

  @Test
  void seleccionaLocalExplicitoSinClienteCloud() {
    contexto
        .withPropertyValues(
            "app.archivos.proveedor=local", "app.archivos.local.directorio=" + directorio)
        .run(
            c ->
                assertThat(c.getBean(AlmacenamientoArchivos.class))
                    .isInstanceOf(AlmacenamientoLocal.class));
  }

  @Test
  void seleccionaS3ConBucketYRegionSinCredencialesEstaticas() {
    contexto
        .withPropertyValues(
            "app.archivos.proveedor=s3",
            "app.archivos.s3.bucket=privado",
            "app.archivos.s3.region=us-east-1")
        .run(
            c -> {
              assertThat(c).hasNotFailed();
              assertThat(c.getBean(AlmacenamientoArchivos.class))
                  .isInstanceOf(AlmacenamientoS3.class);
            });
  }

  @Test
  void s3SinBucketNoPuedeUsarDiscoComoFallback() {
    contexto
        .withPropertyValues("app.archivos.proveedor=s3", "app.archivos.s3.bucket=")
        .run(c -> assertThat(c).hasFailed());
  }

  @Test
  void credencialesEstaticasSoloSePermitenConEndpointMinio() {
    contexto
        .withPropertyValues(
            "app.archivos.proveedor=s3",
            "app.archivos.s3.bucket=privado",
            "app.archivos.s3.access-key=prueba",
            "app.archivos.s3.secret-key=prueba")
        .run(c -> assertThat(c).hasFailed());
    contexto
        .withPropertyValues(
            "app.archivos.proveedor=s3",
            "app.archivos.s3.bucket=privado",
            "app.archivos.s3.endpoint=http://localhost:19000",
            "app.archivos.s3.access-key=prueba")
        .run(c -> assertThat(c).hasFailed());
    contexto
        .withPropertyValues(
            "app.archivos.proveedor=s3",
            "app.archivos.s3.bucket=privado",
            "app.archivos.s3.endpoint=http://localhost:19000",
            "app.archivos.s3.access-key=prueba",
            "app.archivos.s3.secret-key=prueba")
        .run(c -> assertThat(c).hasNotFailed());
  }
}
