package gt.usac.cunoc.sgp.usuario.service;

public final class PasswordPolicy {

  public static final int MIN_LENGTH = 10;
  public static final int MAX_LENGTH = 72;

  private PasswordPolicy() {}

  public static boolean isValid(String password) {
    if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH)
      return false;
    boolean letter = false;
    boolean digit = false;
    for (int i = 0; i < password.length(); i++) {
      char current = password.charAt(i);
      letter |= Character.isLetter(current);
      digit |= Character.isDigit(current);
    }
    return letter && digit;
  }
}
