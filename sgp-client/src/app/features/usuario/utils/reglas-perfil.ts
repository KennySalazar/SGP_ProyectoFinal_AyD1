/** Lo mismo que exige el servidor; allí se vuelve a comprobar. */
export const LIMITE_NOMBRE_COMPLETO = 200;

/**
 * De 3 a 30 caracteres: letras, dígitos, punto, guion y guion bajo, empezando y terminando en letra
 * o dígito. Es lo que se escribe tras la arroba en una mención.
 */
export const PATRON_NOMBRE_USUARIO = /^[A-Za-z0-9](?:[A-Za-z0-9._-]{1,28}[A-Za-z0-9])$/;

/** Nombre completo y nombre de usuario de una cuenta. */
export interface PerfilCuenta {
  nombreCompleto: string;
  nombreUsuario: string;
}
