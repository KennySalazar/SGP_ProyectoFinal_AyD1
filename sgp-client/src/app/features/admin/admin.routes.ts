import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  {
    path: 'invitaciones',
    loadComponent: () =>
      import('./pages/invitaciones/invitaciones.page').then((m) => m.InvitacionesPage),
  },
  {
    path: 'profesionales',
    loadComponent: () =>
      import('./pages/profesionales/profesionales.page').then((m) => m.ProfesionalesPage),
  },
  {
    path: 'bitacora',
    loadComponent: () => import('./pages/bitacora/bitacora.page').then((m) => m.BitacoraPage),
  },
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./pages/usuarios/usuarios.page').then((m) => m.UsuariosPage),
  },
];
