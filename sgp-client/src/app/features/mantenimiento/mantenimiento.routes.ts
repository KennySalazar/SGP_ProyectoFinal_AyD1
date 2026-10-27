import { Routes } from '@angular/router';

export const mantenimientoRoutes: Routes = [
  {
    path: 'reglas',
    loadComponent: () =>
      import('./pages/catalogo-reglas/catalogo-reglas.page').then((m) => m.CatalogoReglasPage),
  },
  {
    path: 'ordenes',
    loadComponent: () =>
      import('./pages/ordenes-mantenimiento/ordenes-mantenimiento.page').then(
        (m) => m.OrdenesMantenimientoPage,
      ),
  },
  {
    path: 'ordenes/:id',
    loadComponent: () =>
      import('./pages/detalle-orden/detalle-orden.page').then((m) => m.DetalleOrdenPage),
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'reglas',
  },
];
