package gt.usac.cunoc.sgp.common.mail;

public interface EmailService {
  void send(String recipient, String subject, String body);

  /**
   * Envía un correo con versión HTML y versión de texto plano para clientes que no muestran HTML.
   * Las implementaciones sin soporte HTML envían solo el texto.
   */
  default void sendHtml(String recipient, String subject, String text, String html) {
    send(recipient, subject, text);
  }
}
