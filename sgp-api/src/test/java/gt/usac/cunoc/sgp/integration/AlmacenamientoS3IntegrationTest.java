package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import gt.usac.cunoc.sgp.archivo.model.ClavesImagen;
import gt.usac.cunoc.sgp.archivo.model.ImagenProcesada;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoS3;
import gt.usac.cunoc.sgp.archivo.service.ObjetosImagenService;
import java.io.*;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Testcontainers
class AlmacenamientoS3IntegrationTest {
  @Container
  static final GenericContainer<?> minio =
      new GenericContainer<>(
              new ImageFromDockerfile()
                  .withDockerfile(Path.of("..", "infra", "minio", "Dockerfile")))
          .withEnv("MINIO_ROOT_USER", "prueba-local")
          .withEnv("MINIO_ROOT_PASSWORD", "prueba-solo-test")
          .withCommand("server", "/data")
          .withExposedPorts(9000)
          .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

  private static S3Client cliente(String accessKey) {
    return S3Client.builder()
        .endpointOverride(URI.create("http://" + minio.getHost() + ":" + minio.getMappedPort(9000)))
        .forcePathStyle(true)
        .region(Region.US_EAST_1)
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, "prueba-solo-test")))
        .build();
  }

  @Test
  void contratoPrivadoSinSobrescriturasYErroresDistintosDeAusencia() throws Exception {
    assertThat(minio.execInContainer("minio", "--version").getStdout())
        .contains("RELEASE.2025-07-23T15-54-02Z");
    try (var cliente = cliente("prueba-local")) {
      cliente.createBucket(CreateBucketRequest.builder().bucket("prueba-archivos").build());
      var s = new AlmacenamientoS3(cliente, "prueba-archivos");
      assertThat(s.existe("test.jpg")).isFalse();
      s.guardar("test.jpg", new ByteArrayInputStream(new byte[] {1, 2}), 2, "image/jpeg");
      assertThat(s.existe("test.jpg")).isTrue();
      assertThat(cliente.headObject(b -> b.bucket("prueba-archivos").key("test.jpg")).contentType())
          .isEqualTo("image/jpeg");
      assertThatThrownBy(
              () ->
                  s.guardar("test.jpg", new ByteArrayInputStream(new byte[] {3}), 1, "image/jpeg"))
          .isInstanceOf(IOException.class)
          .hasCauseInstanceOf(S3Exception.class)
          .satisfies(e -> assertThat(((S3Exception) e.getCause()).statusCode()).isEqualTo(412));
      try (var in = s.abrir("test.jpg")) {
        assertThat(in.readAllBytes()).containsExactly(1, 2);
      }
      var url =
          URI.create(
              "http://"
                  + minio.getHost()
                  + ":"
                  + minio.getMappedPort(9000)
                  + "/prueba-archivos/test.jpg");
      var respuesta =
          java.net.http.HttpClient.newHttpClient()
              .send(
                  java.net.http.HttpRequest.newBuilder(url).build(),
                  java.net.http.HttpResponse.BodyHandlers.discarding());
      assertThat(respuesta.statusCode()).isEqualTo(403);
      s.eliminar("test.jpg");
      s.eliminar("test.jpg");
      assertThat(s.existe("test.jpg")).isFalse();
      assertThatThrownBy(() -> s.abrir("test.jpg")).isInstanceOf(NoSuchFileException.class);
    }
  }

  @Test
  void credencialesInvalidasNoSeConfundenConObjetoAusenteYBucketDebeExistir() throws Exception {
    try (var autorizado = cliente("prueba-local");
        var invalido = cliente("usuario-invalido")) {
      autorizado.createBucket(CreateBucketRequest.builder().bucket("prueba-permisos").build());
      var s = new AlmacenamientoS3(invalido, "prueba-permisos");
      for (var accion :
          java.util.List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
              () -> s.existe("test.jpg"),
              () -> s.abrir("test.jpg"),
              () ->
                  s.guardar("test.jpg", new ByteArrayInputStream(new byte[] {1}), 1, "image/jpeg"),
              () -> s.eliminar("test.jpg"))) {
        assertThatThrownBy(accion)
            .isInstanceOf(IOException.class)
            .hasCauseInstanceOf(S3Exception.class)
            .satisfies(e -> assertThat(((S3Exception) e.getCause()).statusCode()).isEqualTo(403));
      }
      var sinBucket = new AlmacenamientoS3(autorizado, "bucket-inexistente");
      assertThatThrownBy(
              () ->
                  sinBucket.guardar(
                      "test.jpg", new ByteArrayInputStream(new byte[] {1}), 1, "image/jpeg"))
          .isInstanceOf(IOException.class)
          .hasCauseInstanceOf(S3Exception.class)
          .satisfies(e -> assertThat(((S3Exception) e.getCause()).statusCode()).isEqualTo(404));
    }
  }

  @Test
  void falloParcialCompensaOriginalYPermiteReintentarSinBorrarOtrosObjetos() throws Exception {
    try (var cliente = cliente("prueba-local")) {
      cliente.createBucket(CreateBucketRequest.builder().bucket("prueba-parciales").build());
      var real = new AlmacenamientoS3(cliente, "prueba-parciales");
      real.guardar("ajeno.jpg", new ByteArrayInputStream(new byte[] {9}), 1, "image/jpeg");
      var conFallo = spy(real);
      var claves = new ClavesImagen("original.jpg", "miniatura.jpg");
      var imagen = new ImagenProcesada(new byte[] {1, 2}, new byte[] {3}, "image/jpeg", 2, 1, "");
      doThrow(new IOException("Fallo simulado al guardar miniatura"))
          .doCallRealMethod()
          .when(conFallo)
          .guardar(eq(claves.miniatura()), any(InputStream.class), anyLong(), anyString());
      var objetos = new ObjetosImagenService(conFallo);
      assertThatThrownBy(() -> objetos.guardar(claves, imagen)).isInstanceOf(IOException.class);
      assertThat(real.existe(claves.original())).isFalse();
      assertThat(real.existe(claves.miniatura())).isFalse();
      assertThat(real.existe("ajeno.jpg")).isTrue();
      objetos.guardar(claves, imagen);
      try (var original = real.abrir(claves.original());
          var miniatura = real.abrir(claves.miniatura())) {
        assertThat(original.readAllBytes()).containsExactly(1, 2);
        assertThat(miniatura.readAllBytes()).containsExactly(3);
      }
      doThrow(new IOException("Fallo simulado al eliminar original"))
          .doCallRealMethod()
          .when(conFallo)
          .eliminar(claves.original());
      assertThatThrownBy(() -> objetos.eliminar(claves)).isInstanceOf(IOException.class);
      assertThat(real.existe(claves.original())).isTrue();
      assertThat(real.existe(claves.miniatura())).isFalse();
      objetos.eliminar(claves);
      assertThat(real.existe(claves.original())).isFalse();
      assertThat(real.existe("ajeno.jpg")).isTrue();
    }
  }
}
