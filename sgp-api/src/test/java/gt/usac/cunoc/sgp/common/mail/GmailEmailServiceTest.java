package gt.usac.cunoc.sgp.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class GmailEmailServiceTest {

  private final JavaMailSender sender = mock(JavaMailSender.class);
  private final GmailEmailService service =
      new GmailEmailService(sender, "sgp.cuenta@gmail.com", "SGP CUNOC");

  @BeforeEach
  void prepararMensaje() {
    when(sender.createMimeMessage())
        .thenAnswer(invocacion -> new JavaMailSenderImpl().createMimeMessage());
  }

  @Test
  void enviaHtmlConTextoAlternativoYNombreDelSistemaComoRemitente() throws Exception {
    service.sendHtml(
        "catedratico@usac.edu.gt",
        "Invitación al SGP",
        "Texto plano",
        "<p>Correo <strong>HTML</strong></p>");

    MimeMessage mensaje = mensajeEnviado();
    InternetAddress remitente = (InternetAddress) mensaje.getFrom()[0];
    assertThat(remitente.getAddress()).isEqualTo("sgp.cuenta@gmail.com");
    assertThat(remitente.getPersonal()).isEqualTo("SGP CUNOC");
    assertThat(mensaje.getSubject()).isEqualTo("Invitación al SGP");
    assertThat(mensaje.getAllRecipients()[0].toString()).isEqualTo("catedratico@usac.edu.gt");
    assertThat(mensaje.getContent()).isInstanceOf(MimeMultipart.class);
    String contenido = contenido(mensaje);
    assertThat(contenido).contains("Texto plano").contains("<strong>HTML</strong>");
  }

  @Test
  void elTextoPlanoTambienSaleConElNombreDelSistema() throws Exception {
    service.send("estudiante@usac.edu.gt", "Codigo", "Su codigo es 123456");

    MimeMessage mensaje = mensajeEnviado();
    assertThat(((InternetAddress) mensaje.getFrom()[0]).getPersonal()).isEqualTo("SGP CUNOC");
    assertThat(mensaje.getContent()).isEqualTo("Su codigo es 123456");
  }

  @Test
  void unFalloDelServidorDeCorreoSeTraduceA503() {
    doThrow(new MailSendException("SMTP no disponible")).when(sender).send(any(MimeMessage.class));

    assertThatThrownBy(() -> service.sendHtml("a@b.com", "Asunto", "texto", "<p>html</p>"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(e.getCode()).isEqualTo("email_delivery_failed");
            });
  }

  private MimeMessage mensajeEnviado() {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captor.capture());
    return captor.getValue();
  }

  private String contenido(MimeMessage mensaje) throws Exception {
    mensaje.saveChanges();
    var salida = new ByteArrayOutputStream();
    mensaje.writeTo(salida);
    return salida.toString(StandardCharsets.UTF_8);
  }
}
