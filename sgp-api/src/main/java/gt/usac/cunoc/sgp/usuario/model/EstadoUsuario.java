package gt.usac.cunoc.sgp.usuario.model;

/**
 * Estado derivado de una cuenta: INACTIVO tras la baja lógica; PENDIENTE mientras no verifica su
 * correo o no ha sido activada; ACTIVO cuando puede iniciar sesión.
 */
public enum EstadoUsuario {
  ACTIVO,
  PENDIENTE,
  INACTIVO
}
