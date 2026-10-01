import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/admin/admin.placeholder').then((m) => m.AdminPlaceholderPage),
  },
];
