package gt.usac.cunoc.sgp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.security.AuthRateLimitFilter;
import gt.usac.cunoc.sgp.common.security.JwtAuthenticationFilter;
import gt.usac.cunoc.sgp.common.security.ProblemAccessDeniedHandler;
import gt.usac.cunoc.sgp.common.security.ProblemAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pruebas de cumplimiento de seguridad del despliegue (DT-SEC-06, DT-SEC-10, DT-SEC-11). */
@WebMvcTest(
    controllers = DeploymentSecurityHeadersTest.TestSecurityController.class,
    properties = {"app.cors.allowed-origins=http://localhost:4200", "app.openapi.public=true"})
@Import({
  SecurityConfiguration.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class DeploymentSecurityHeadersTest {

  @RestController
  static class TestSecurityController {
    @GetMapping("/api/v1/puentes")
    public String publicPuentes() {
      return "ok";
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private JwtAuthenticationFilter jwtFilter;
  @MockBean private AuthRateLimitFilter rateLimitFilter;

  @Test
  @DisplayName("DT-SEC-10: Respuestas HTTPS incluyen Strict-Transport-Security con subdominios")
  void hstsPresenteEnRespuestas() throws Exception {
    mockMvc
        .perform(get("/api/v1/puentes").secure(true))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
  }

  @Test
  @DisplayName("DT-SEC-11: Respuestas incluyen encabezados nosniff, DENY y referrer-policy")
  void encabezadosSeguridadPresentes() throws Exception {
    mockMvc
        .perform(get("/api/v1/puentes"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
  }

  @Test
  @DisplayName("DT-SEC-06: CORS rechaza explícitamente el uso de comodín (*)")
  void corsRechazaComodin() {
    SecurityConfiguration config = new SecurityConfiguration();
    assertThatThrownBy(() -> config.corsConfigurationSource("*"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CORS no permite el comodin '*'");
  }

  @Test
  @DisplayName("DT-SEC-06: CORS permite orígenes explícitos de la lista de despliegue")
  void corsAceptaOrigenesExplicitos() {
    SecurityConfiguration config = new SecurityConfiguration();
    var source =
        config.corsConfigurationSource("https://sgp.cunoc.edu.gt,https://staging.sgp.cunoc.edu.gt");
    assertThat(source).isNotNull();
  }
}
