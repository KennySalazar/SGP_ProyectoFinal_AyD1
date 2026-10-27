import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';

export const inspeccionesRoutes: Routes = [
  {
    path: ':id/publicada',
    loadComponent: () =>
      import('./pages/detalle-publicado/detalle-publicado.page').then(
        (m) => m.DetallePublicadoPage,
      ),
  },
  {
    path: '',
    canActivate: [roleGuard('CATEDRATICO', 'PROFESIONAL_EXTERNO', 'ESTUDIANTE')],
    pathMatch: 'full',
    loadComponent: () =>
      import('./pages/mis-inspecciones/mis-inspecciones.page').then((m) => m.MisInspeccionesPage),
  },
  {
    path: ':id/borrador',
    canActivate: [roleGuard('CATEDRATICO', 'PROFESIONAL_EXTERNO', 'ESTUDIANTE')],
    loadComponent: () =>
      import('./pages/completar-inspeccion/completar-inspeccion.page').then(
        (m) => m.CompletarInspeccionPage,
      ),
  },
  {
    path: ':id',
    canActivate: [roleGuard('CATEDRATICO', 'PROFESIONAL_EXTERNO', 'ESTUDIANTE')],
    loadComponent: () =>
      import('./pages/completar-inspeccion/completar-inspeccion.page').then(
        (m) => m.CompletarInspeccionPage,
      ),
  },
];
