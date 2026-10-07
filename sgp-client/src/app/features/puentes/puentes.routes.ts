import { Routes } from '@angular/router';
import { authGuard } from '../../core/guards/auth.guard';
import { roleGuard } from '../../core/guards/role.guard';

export const puentesRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./pages/catalogo-puentes/catalogo-puentes.page').then((m) => m.CatalogoPuentesPage),
  },
  {
    path: '',
    canActivate: [authGuard, roleGuard('ADMINISTRADOR')],
    loadComponent: () =>
      import('../../layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: 'nuevo',
        loadComponent: () =>
          import('./pages/registrar-puente/registrar-puente.page').then(
            (m) => m.RegistrarPuentePage,
          ),
      },
      {
        path: 'solicitudes/revision',
        loadComponent: () =>
          import('./pages/revision-solicitudes/revision-solicitudes.page').then(
            (m) => m.RevisionSolicitudesPage,
          ),
      },
      {
        path: 'solicitudes/revision/:id',
        loadComponent: () =>
          import('./pages/revisar-solicitud/revisar-solicitud.page').then(
            (m) => m.RevisarSolicitudPage,
          ),
      },
      {
        path: ':id/editar',
        loadComponent: () =>
          import('./pages/editar-puente/editar-puente.page').then((m) => m.EditarPuentePage),
      },
    ],
  },
  {
    path: '',
    canActivate: [authGuard, roleGuard('CATEDRATICO')],
    loadComponent: () =>
      import('../../layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: 'asignaciones',
        loadComponent: () =>
          import('./pages/asignaciones-puentes/asignaciones-puentes.page').then(
            (m) => m.AsignacionesPuentesPage,
          ),
      },
      {
        path: 'solicitudes',
        loadComponent: () =>
          import('./pages/mis-solicitudes/mis-solicitudes.page').then((m) => m.MisSolicitudesPage),
      },
      {
        path: 'solicitudes/nueva',
        loadComponent: () =>
          import('./pages/solicitar-alta-puente/solicitar-alta-puente.page').then(
            (m) => m.SolicitarAltaPuentePage,
          ),
      },
      {
        path: 'solicitudes/:id',
        loadComponent: () =>
          import('./pages/detalle-solicitud/detalle-solicitud.page').then(
            (m) => m.DetalleSolicitudPage,
          ),
      },
    ],
  },
  {
    path: '',
    canActivate: [authGuard, roleGuard('ESTUDIANTE')],
    loadComponent: () =>
      import('../../layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: 'mis-asignaciones',
        loadComponent: () =>
          import('./pages/mis-puentes-asignados/mis-puentes-asignados.page').then(
            (m) => m.MisPuentesAsignadosPage,
          ),
      },
    ],
  },
  // Debe ir al final: `:id` coincidiría con cualquier segmento, incluido `solicitudes`.
  {
    path: ':id',
    loadComponent: () =>
      import('./pages/ficha-puente/ficha-puente.page').then((m) => m.FichaPuentePage),
  },
];
