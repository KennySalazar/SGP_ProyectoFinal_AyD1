
package gt.usac.cunoc.sgp.common.mail;

import gt.usac.cunoc.sgp.usuario.entity.OtpPurpose;
import java.time.Instant;

public interface EmailService {
    void sendOtp(String recipient, OtpPurpose purpose, String code, Instant expiresAt);
}
