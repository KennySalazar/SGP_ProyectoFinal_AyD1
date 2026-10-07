package gt.usac.cunoc.sgp.common.mail;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class GmailEmailService implements EmailService {

  private final JavaMailSender sender;
  private final String from;
  private final String fromName;

  public GmailEmailService(
      JavaMailSender sender,
      @Value("${app.mail.from}") String from,
      @Value("${app.mail.from-name:SGP CUNOC}") String fromName) {
    this.sender = sender;
    this.from = from;
    this.fromName = fromName;
  }

  @Override
  public void send(String recipient, String subject, String body) {
    enviar(recipient, subject, body, null);
  }

  @Override
  public void sendHtml(String recipient, String subject, String text, String html) {
    enviar(recipient, subject, text, html);
  }

  private void enviar(String recipient, String subject, String text, String html) {
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, html != null, StandardCharsets.UTF_8.name());
      // El nombre visible identifica al sistema aunque la cuenta SMTP sea personal.
      helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
      helper.setTo(recipient);
      helper.setSubject(subject);
      if (html == null) {
        helper.setText(text);
      } else {
        helper.setText(text, html);
      }
      sender.send(message);
    } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "email_delivery_failed",
          "Servicio de correo no disponible",
          "No fue posible enviar el correo de verificacion");
    }
  }
}
