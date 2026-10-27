package gt.usac.cunoc.sgp.archivo.config;

import gt.usac.cunoc.sgp.archivo.service.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AlmacenamientoConfiguration {
  @Bean
  @ConditionalOnProperty(
      name = "app.archivos.proveedor",
      havingValue = "local",
      matchIfMissing = true)
  AlmacenamientoArchivos local(@Value("${app.archivos.local.directorio}") String directorio)
      throws IOException {
    return new AlmacenamientoLocal(Path.of(directorio));
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "app.archivos.proveedor", havingValue = "s3")
  S3Client cliente(
      @Value("${app.archivos.s3.region:us-east-1}") String region,
      @Value("${app.archivos.s3.endpoint:}") String endpoint,
      @Value("${app.archivos.s3.access-key:}") String accessKey,
      @Value("${app.archivos.s3.secret-key:}") String secretKey) {
    var builder =
        S3Client.builder()
            .overrideConfiguration(
                c ->
                    c.apiCallTimeout(Duration.ofMinutes(2))
                        .apiCallAttemptTimeout(Duration.ofSeconds(90)))
            .region(Region.of(region))
            .httpClientBuilder(
                UrlConnectionHttpClient.builder()
                    .connectionTimeout(Duration.ofSeconds(10))
                    .socketTimeout(Duration.ofSeconds(60)));
    if (!endpoint.isBlank()) {
      if (accessKey.isBlank() != secretKey.isBlank())
        throw new IllegalArgumentException("Las dos credenciales MinIO deben configurarse juntas");
      builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
      if (!accessKey.isBlank() && !secretKey.isBlank())
        builder.credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
    } else if (!accessKey.isBlank() || !secretKey.isBlank())
      throw new IllegalArgumentException(
          "Las credenciales explícitas solo se admiten para endpoint local MinIO");
    // Sin credenciales explícitas, el SDK usa su cadena por defecto, incluido el rol IAM de EC2.
    return builder.build();
  }

  @Bean
  @ConditionalOnProperty(name = "app.archivos.proveedor", havingValue = "s3")
  AlmacenamientoArchivos s3(S3Client cliente, @Value("${app.archivos.s3.bucket}") String bucket) {
    if (bucket.isBlank()) throw new IllegalArgumentException("El bucket privado es obligatorio");
    return new AlmacenamientoS3(cliente, bucket);
  }
}
