package gt.usac.cunoc.sgp.usuario.model;

/** Estado derivado de una invitación; VENCIDA se calcula a partir de la fecha de expiración. */
public enum EstadoInvitacion {
  PENDIENTE,
  VENCIDA,
  ACEPTADA,
  CANCELADA
}
