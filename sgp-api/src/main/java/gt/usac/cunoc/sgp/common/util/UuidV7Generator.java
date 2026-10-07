package gt.usac.cunoc.sgp.common.util;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7Generator {

  private static final SecureRandom RANDOM = new SecureRandom();

  private UuidV7Generator() {}

  public static UUID generate() {
    long unixMillis = System.currentTimeMillis();
    long randA = RANDOM.nextLong() & 0x0FFFL;
    long most = ((unixMillis & 0xFFFFFFFFFFFFL) << 16) | 0x7000L | randA;
    long least = RANDOM.nextLong();
    least = (least & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(most, least);
  }
}
