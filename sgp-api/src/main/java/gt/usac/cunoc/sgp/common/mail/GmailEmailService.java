package gt.usac.cunoc.sgp.common.mail;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class GmailEmailService implements EmailService {

  private final JavaMailSender sender;
  private final String from;

  public GmailEmailService(JavaMailSender sender, @Value("${app.mail.from}") String from) {
    this.sender = sender;
    this.from = from;
  }

  @Override
  public void send(String recipient, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(recipient);
    message.setSubject(subject);
    message.setText(body);
    try {
      sender.send(message);
    } catch (MailException exception) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "email_delivery_failed",
          "Servicio de correo no disponible",
          "No fue posible enviar el correo de verificacion");
    }
  }

}
