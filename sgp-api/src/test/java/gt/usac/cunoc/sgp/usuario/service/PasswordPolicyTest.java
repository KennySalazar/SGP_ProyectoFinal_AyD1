
package gt.usac.cunoc.sgp.usuario.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsRequiredPolicy() {
        assertTrue(PasswordPolicy.isValid("Admin2026Local"));
    }

    @Test
    void rejectsShortOrWithoutDigits() {
        assertFalse(PasswordPolicy.isValid("corta1"));
        assertFalse(PasswordPolicy.isValid("sololetraslargas"));
    }
}
