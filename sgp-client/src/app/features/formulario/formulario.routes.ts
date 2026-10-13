import { Routes } from '@angular/router';

export const formularioRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./pages/versiones-formulario/versiones-formulario.page').then(
        (m) => m.VersionesFormularioPage,
      ),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./pages/editor-version-formulario/editor-version-formulario.page').then(
        (m) => m.EditorVersionFormularioPage,
      ),
  },
];
