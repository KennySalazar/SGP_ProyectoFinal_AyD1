package gt.usac.cunoc.sgp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.logging.MaskingJsonLayout;
import gt.usac.cunoc.sgp.usuario.dto.AceptarInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.ChangePasswordRequest;
import gt.usac.cunoc.sgp.usuario.dto.LoginRequest;
import gt.usac.cunoc.sgp.usuario.dto.RegisterRequest;
import gt.usac.cunoc.sgp.usuario.dto.VerifyRecoveryRequest;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas de bitácoras estructuradas y ocultamiento de datos sensibles (DT-DEP-09, DT-SEC-12). */
class BitacoraSeguridadTest {

  private MaskingJsonLayout layout;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    layout = new MaskingJsonLayout();
    layout.start();
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("DT-DEP-09: Registros salen en formato estructurado JSON con campos clave")
  void bitacoraEstructuradaEnJson() throws Exception {
    ILoggingEvent event = mock(ILoggingEvent.class);
    when(event.getTimeStamp()).thenReturn(System.currentTimeMillis());
    when(event.getLevel()).thenReturn(Level.INFO);
    when(event.getThreadName()).thenReturn("main");
    when(event.getLoggerName()).thenReturn("gt.usac.cunoc.sgp.usuario.controller.AuthController");
    when(event.getFormattedMessage()).thenReturn("Usuario autenticado exitosamente");
    when(event.getMDCPropertyMap()).thenReturn(Collections.emptyMap());

    String output = layout.doLayout(event);
    JsonNode node = objectMapper.readTree(output);

    assertThat(node.has("timestamp")).isTrue();
    assertThat(node.get("level").asText()).isEqualTo("INFO");
    assertThat(node.get("thread").asText()).isEqualTo("main");
    assertThat(node.get("logger").asText())
        .isEqualTo("gt.usac.cunoc.sgp.usuario.controller.AuthController");
    assertThat(node.get("message").asText()).isEqualTo("Usuario autenticado exitosamente");
  }

  @Test
  @DisplayName("DT-SEC-12: Enmascara contraseñas en mensajes de log")
  void bitacoraEnmascaraContrasenaEnMensaje() {
    String original =
        "Fallo de login para usuario juan@cunoc.edu.gt con password=\"MiSuperClave123!\" y confirmPassword=\"MiSuperClave123!\"";
    String masked = layout.mask(original);

    assertThat(masked).doesNotContain("MiSuperClave123!");
    assertThat(masked).contains("password=\"[PROTEGIDO]\"");
    assertThat(masked).contains("confirmPassword=\"[PROTEGIDO]\"");
  }

  @Test
  @DisplayName("DT-SEC-12: Enmascara encabezados Bearer y tokens de autenticación")
  void bitacoraEnmascaraBearerToken() {
    String original =
        "Peticion entrante con Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.doNotLeakThisToken";
    String masked = layout.mask(original);

    assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
    assertThat(masked).contains("Bearer [PROTEGIDO]");
  }

  @Test
  @DisplayName("DT-SEC-12: DTOs de autenticación y credenciales ocultan contraseñas en toString()")
  void loginRequestNoImprimeContrasenaEnToString() {
    LoginRequest login = new LoginRequest("usuario@cunoc.edu.gt", "ContraseñaSecreta#1");
    assertThat(login.toString()).doesNotContain("ContraseñaSecreta#1");
    assertThat(login.toString()).contains("[PROTEGIDO]");

    RegisterRequest register =
        new RegisterRequest(
            "estudiante@cunoc.edu.gt",
            "ClaveEstudiante#123",
            "Estudiante Prueba",
            "estudianteprueba");
    assertThat(register.toString()).doesNotContain("ClaveEstudiante#123");
    assertThat(register.toString()).contains("[PROTEGIDO]");

    ChangePasswordRequest change = new ChangePasswordRequest("ClaveVieja#123", "ClaveNueva#123");
    assertThat(change.toString()).doesNotContain("ClaveVieja#123");
    assertThat(change.toString()).doesNotContain("ClaveNueva#123");
    assertThat(change.toString()).contains("[PROTEGIDO]");

    VerifyRecoveryRequest recovery =
        new VerifyRecoveryRequest("rec@cunoc.edu.gt", "123456", "NuevaClave#123");
    assertThat(recovery.toString()).doesNotContain("123456");
    assertThat(recovery.toString()).doesNotContain("NuevaClave#123");

    AceptarInvitacionRequest invitacion =
        new AceptarInvitacionRequest(
            "token-secreto-abc-123", "ClaveDocente#123", "Docente Prueba", "docenteprueba");
    assertThat(invitacion.toString()).doesNotContain("token-secreto-abc-123");
    assertThat(invitacion.toString()).doesNotContain("ClaveDocente#123");
  }
}
