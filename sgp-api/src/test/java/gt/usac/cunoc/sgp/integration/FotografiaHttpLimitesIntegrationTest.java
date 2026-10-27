package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.controller.FotografiaController;
import gt.usac.cunoc.sgp.inspeccion.service.FotografiaService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import jakarta.servlet.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(
    classes = FotografiaHttpLimitesIntegrationTest.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.location=optional:classpath:/fotografia-http-test.properties",
      "spring.servlet.multipart.max-file-size=20MB",
      "spring.servlet.multipart.max-request-size=25MB"
    })
class FotografiaHttpLimitesIntegrationTest {
  @Configuration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        org.springframework.boot.actuate.autoconfigure.security.servlet
            .ManagementWebSecurityAutoConfiguration.class
      })
  @Import({FotografiaController.class, ApiExceptionHandler.class})
  static class App {
    @Bean
    Filter autenticacionDePrueba() {
      return (request, response, chain) -> {
        var auth = new UsernamePasswordAuthenticationToken("test", null, List.of());
        auth.setDetails(
            new JwtData("test@example.com", UUID.randomUUID(), RoleName.CATEDRATICO, 0));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
          chain.doFilter(
              new jakarta.servlet.http.HttpServletRequestWrapper(
                  (jakarta.servlet.http.HttpServletRequest) request) {
                @Override
                public java.security.Principal getUserPrincipal() {
                  return auth;
                }
              },
              response);
        } finally {
          SecurityContextHolder.clearContext();
        }
      };
    }
  }

  @LocalServerPort int puerto;
  @MockBean FotografiaService servicio;

  private int enviar(int archivoBytes, int totalBytes) throws Exception {
    String antes =
        "--sgp\r\nContent-Disposition: form-data; name=\"metadatos\"\r\nContent-Type: application/json\r\n\r\n{}\r\n--sgp\r\nContent-Disposition: form-data; name=\"relleno\"; filename=\"relleno.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n";
    String entre =
        "\r\n--sgp\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"f.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n";
    String despues = "\r\n--sgp--\r\n";
    int fijo = antes.length() + entre.length() + despues.length() + archivoBytes;
    byte[] relleno = new byte[totalBytes == 0 ? 0 : totalBytes - fijo];
    Arrays.fill(relleno, (byte) ' ');
    var datos =
        List.of(
            antes.getBytes(StandardCharsets.US_ASCII),
            relleno,
            entre.getBytes(StandardCharsets.US_ASCII),
            new byte[archivoBytes],
            despues.getBytes(StandardCharsets.US_ASCII));
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + puerto
                        + "/api/v1/inspecciones/"
                        + UUID.randomUUID()
                        + "/fotografias"))
            .header("Content-Type", "multipart/form-data; boundary=sgp")
            .header("Idempotency-Key", "limites")
            .expectContinue(true)
            .POST(HttpRequest.BodyPublishers.ofByteArrays(datos))
            .build();
    try (var cliente = HttpClient.newHttpClient()) {
      return cliente.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
  }

  @Test
  void aplica20MbPorArchivoY25MbPorPeticionEnServidorReal() throws Exception {
    assertThat(enviar(20 * 1024 * 1024, 0)).isEqualTo(201);
    assertThat(enviar(20 * 1024 * 1024 + 1, 0)).isEqualTo(413);
    assertThat(enviar(16 * 1024 * 1024, 25 * 1024 * 1024)).isEqualTo(201);
    assertThat(enviar(16 * 1024 * 1024, 25 * 1024 * 1024 + 1)).isEqualTo(413);
  }
}
