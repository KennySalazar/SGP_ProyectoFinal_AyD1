package gt.usac.cunoc.sgp.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

  @Test
  void generatesVersionSevenVariantTwoUuid() {
    UUID id = UuidV7Generator.generate();
    assertEquals(7, id.version());
    assertEquals(2, id.variant());
    assertNotEquals(new UUID(0, 0), id);
  }
}
