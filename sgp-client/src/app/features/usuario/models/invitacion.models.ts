import { RoleName } from '../../../core/models/auth.models';

/** Datos que el invitado ve al abrir su enlace, antes de definir la contraseña. */
export interface InvitacionPublica {
  email: string;
  rol: RoleName;
  expiraEn: string;
}
