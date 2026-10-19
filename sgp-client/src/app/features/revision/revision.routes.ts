import { Routes } from '@angular/router';

export const revisionRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./pages/bandeja-revision/bandeja-revision.page').then((m) => m.BandejaRevisionPage),
  },
];
