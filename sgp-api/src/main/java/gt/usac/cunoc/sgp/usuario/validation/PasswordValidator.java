package gt.usac.cunoc.sgp.usuario.validation;

import gt.usac.cunoc.sgp.usuario.service.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return PasswordPolicy.isValid(value);
  }
}
