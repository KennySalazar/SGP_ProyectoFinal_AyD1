import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  {
    path: 'bitacora',
    loadComponent: () => import('./pages/bitacora/bitacora.page').then((m) => m.BitacoraPage),
  },
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./pages/admin/admin.placeholder').then((m) => m.AdminPlaceholderPage),
  },
];
