package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.config.InvitacionProperties;
import gt.usac.cunoc.sgp.common.mail.EmailService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
public class InvitacionEmailService {

  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Guatemala"));

  private final EmailService emailService;
  private final InvitacionProperties properties;

  public InvitacionEmailService(EmailService emailService, InvitacionProperties properties) {
    this.emailService = emailService;
    this.properties = properties;
  }

  public void enviarInvitacion(String recipient, RoleName rol, String token, Instant expiraEn) {
    emailService.send(
        recipient,
        "SGP - Invitacion al Sistema de Gestion de Puentes",
        "Fue invitado al Sistema de Gestion de Puentes con el rol "
            + nombreRol(rol)
            + ".\nPara activar su cuenta y definir su contraseña ingrese a:\n"
            + enlaceActivacion(token)
            + "\nEl enlace es de un solo uso y expira: "
            + FORMAT.format(expiraEn)
            + "\nSi el enlace vence, solicite al Administrador que reenvie la invitacion."
            + "\nSi no esperaba esta invitacion, ignore este mensaje.");
  }

  String enlaceActivacion(String token) {
    return properties.getUrlActivacion()
        + "?token="
        + URLEncoder.encode(token, StandardCharsets.UTF_8);
  }

  private String nombreRol(RoleName rol) {
    return switch (rol) {
      case ADMINISTRADOR -> "Administrador";
      case CATEDRATICO -> "Catedratico";
      case PROFESIONAL_EXTERNO -> "Profesional externo";
      case ESTUDIANTE -> "Estudiante";
    };
  }
}
