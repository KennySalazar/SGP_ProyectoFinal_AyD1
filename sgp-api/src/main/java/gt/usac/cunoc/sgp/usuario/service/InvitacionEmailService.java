package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.config.InvitacionProperties;
import gt.usac.cunoc.sgp.common.mail.EmailService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class InvitacionEmailService {

  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Guatemala"));
  private static final String PLANTILLA = "mail/invitacion.html";

  private final EmailService emailService;
  private final InvitacionProperties properties;
  private final String plantillaHtml;

  public InvitacionEmailService(EmailService emailService, InvitacionProperties properties) {
    this.emailService = emailService;
    this.properties = properties;
    this.plantillaHtml = cargarPlantilla();
  }

  public void enviarInvitacion(String recipient, RoleName rol, String token, Instant expiraEn) {
    String enlace = enlaceActivacion(token);
    String expira = FORMAT.format(expiraEn);

    emailService.sendHtml(
        recipient,
        "Invitación al Sistema de Gestión de Puentes",
        textoPlano(rol, enlace, expira),
        plantillaHtml
            .replace("{{EMAIL}}", escapar(recipient))
            .replace("{{ROL}}", escapar(nombreRol(rol)))
            .replace("{{EXPIRA}}", escapar(expira))
            .replace("{{ENLACE}}", escapar(enlace)));
  }

  String enlaceActivacion(String token) {
    return properties.getUrlActivacion()
        + "?token="
        + URLEncoder.encode(token, StandardCharsets.UTF_8);
  }

  private String textoPlano(RoleName rol, String enlace, String expira) {
    return "Fue invitado al Sistema de Gestión de Puentes con el rol "
        + nombreRol(rol)
        + ".\nPara activar su cuenta y definir su contraseña ingrese a:\n"
        + enlace
        + "\nEl enlace es de un solo uso y expira: "
        + expira
        + "\nSi el enlace vence, solicite al Administrador que reenvíe la invitación."
        + "\nSi no esperaba esta invitación, ignore este mensaje.";
  }

  /**
   * Escapa solo los caracteres especiales de HTML; las tildes se conservan porque el correo es
   * UTF-8.
   */
  private static String escapar(String valor) {
    return HtmlUtils.htmlEscape(valor, StandardCharsets.UTF_8.name());
  }

  private String nombreRol(RoleName rol) {
    return switch (rol) {
      case ADMINISTRADOR -> "Administrador";
      case CATEDRATICO -> "Catedrático";
      case PROFESIONAL_EXTERNO -> "Profesional externo";
      case ESTUDIANTE -> "Estudiante";
    };
  }

  private static String cargarPlantilla() {
    try (InputStream contenido = new ClassPathResource(PLANTILLA).getInputStream()) {
      return new String(contenido.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("No se encontro la plantilla " + PLANTILLA, exception);
    }
  }
}
