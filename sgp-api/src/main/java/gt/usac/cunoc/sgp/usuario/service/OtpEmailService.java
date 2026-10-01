package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.mail.EmailService;
import gt.usac.cunoc.sgp.usuario.model.OtpPurpose;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
public class OtpEmailService {

  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Guatemala"));

  private final EmailService emailService;

  public OtpEmailService(EmailService emailService) {
    this.emailService = emailService;
  }

  public void sendOtp(String recipient, OtpPurpose purpose, String code, Instant expiresAt) {
    emailService.send(
        recipient,
        subjectFor(purpose),
        "Su codigo de verificacion es: "
            + code
            + "\nExpira: "
            + FORMAT.format(expiresAt)
            + "\nSi no solicito esta operacion, ignore este mensaje.");
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
