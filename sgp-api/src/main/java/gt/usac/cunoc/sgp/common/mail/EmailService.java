package gt.usac.cunoc.sgp.common.mail;

public interface EmailService {
  void send(String recipient, String subject, String body);
}
