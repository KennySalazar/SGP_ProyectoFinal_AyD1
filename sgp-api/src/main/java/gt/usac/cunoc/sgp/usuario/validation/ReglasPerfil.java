package gt.usac.cunoc.sgp.usuario.validation;

/**
 * Reglas del perfil de una cuenta: nombre completo y nombre de usuario. El nombre de usuario es la
 * identidad visible en el foro y lo que se escribe tras la arroba en una mención (RN-FOR-10).
 */
public final class ReglasPerfil {

  public static final int NOMBRE_COMPLETO_MINIMO = 2;
  public static final int NOMBRE_COMPLETO_MAXIMO = 200;

  /**
   * De 3 a 30 caracteres: letras, dígitos, punto, guion y guion bajo, empezando y terminando en
   * letra o dígito. Así una mención seguida de un signo de puntuación no lo arrastra consigo.
   */
  public static final String PATRON_USUARIO = "^[A-Za-z0-9](?:[A-Za-z0-9._-]{1,28}[A-Za-z0-9])$";

  public static final String MENSAJE_USUARIO =
      "El nombre de usuario debe tener de 3 a 30 caracteres: letras, dígitos, punto, guion o guion"
          + " bajo, y empezar y terminar con letra o dígito";

  private ReglasPerfil() {}
}
