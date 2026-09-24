
package gt.usac.cunoc.sgp.common.mail;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.usuario.entity.OtpPurpose;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class GmailEmailService implements EmailService {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Guatemala"));

    private final JavaMailSender sender;
    private final String from;

    public GmailEmailService(JavaMailSender sender, @Value("${app.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void sendOtp(String recipient, OtpPurpose purpose, String code, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subjectFor(purpose));
        message.setText("Su codigo de verificacion es: " + code + "\nExpira: " + FORMAT.format(expiresAt) + "\nSi no solicito esta operacion, ignore este mensaje.");
        try {
            sender.send(message);
        } catch (MailException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "email_delivery_failed", "Servicio de correo no disponible", "No fue posible enviar el correo de verificacion");
        }
    }

    private String subjectFor(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTRO -> "SGP - Verificacion de registro";
            case LOGIN_2FA -> "SGP - Codigo de inicio de sesion";
            case RECUPERACION_PASSWORD -> "SGP - Recuperacion de contraseña";
            case ACTIVAR_2FA -> "SGP - Activacion de 2FA";
            case DESACTIVAR_2FA -> "SGP - Desactivacion de 2FA";
        };
    }
}
