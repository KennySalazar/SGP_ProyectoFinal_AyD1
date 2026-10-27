import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';

export const foroRoutes: Routes = [
  {
    path: 'moderacion',
    canActivate: [roleGuard('ADMINISTRADOR', 'CATEDRATICO')],
    loadComponent: () =>
      import('./pages/moderacion/moderacion.page').then((m) => m.ModeracionForoPage),
  },
  {
    // Destino de los avisos del foro (HU-066): llevan al hilo y al comentario.
    path: 'comentarios/:id',
    data: { destino: 'comentario' },
    loadComponent: () => import('./pages/ir-a-foro/ir-a-foro.page').then((m) => m.IrAForoPage),
  },
  {
    path: 'hilos/:id',
    data: { destino: 'hilo' },
    loadComponent: () => import('./pages/ir-a-foro/ir-a-foro.page').then((m) => m.IrAForoPage),
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'moderacion',
  },
];
