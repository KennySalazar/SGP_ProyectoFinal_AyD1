import { salidaGuard } from './features/salida/salida.guard';
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { usuarioProtectedRoutes, usuarioPublicRoutes } from './features/usuario/usuario.routes';

export const routes: Routes = [
  ...usuarioPublicRoutes,
  {
    path: 'puentes',
    loadChildren: () => import('./features/puentes/puentes.routes').then((m) => m.puentesRoutes),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      ...usuarioProtectedRoutes,
      {
        path: 'avisos',
        loadComponent: () =>
          import('./features/notificaciones/pages/avisos/avisos.page').then((m) => m.AvisosPage),
      },
      {
        path: 'preparar-salida',
        canActivate: [salidaGuard],
        loadComponent: () =>
          import('./features/salida/pages/preparar-salida/preparar-salida.page').then(
            (m) => m.PrepararSalidaPage,
          ),
      },
      {
        path: 'offline',
        loadComponent: () =>
          import('./offline/pages/diagnostics/diagnostics.page').then((m) => m.DiagnosticsPage),
      },
      {
        path: 'inspecciones',
        loadChildren: () =>
          import('./features/inspecciones/inspecciones.routes').then((m) => m.inspeccionesRoutes),
      },
      {
        path: 'observaciones',
        canActivate: [roleGuard('ESTUDIANTE')],
        loadChildren: () =>
          import('./features/revision/observaciones-autor.routes').then(
            (m) => m.observacionesAutorRoutes,
          ),
      },
      {
        path: 'revisiones',
        canActivate: [roleGuard('CATEDRATICO')],
        loadChildren: () =>
          import('./features/revision/revision.routes').then((m) => m.revisionRoutes),
      },

      {
        path: 'formulario/vista-previa',
        loadComponent: () =>
          import('./features/formulario/pages/vista-previa-formulario/vista-previa-formulario.page').then(
            (m) => m.VistaPreviaFormularioPage,
          ),
      },
      {
        path: 'admin/formularios',
        canActivate: [roleGuard('ADMINISTRADOR', 'CATEDRATICO')],
        loadChildren: () =>
          import('./features/formulario/formulario.routes').then((m) => m.formularioRoutes),
      },
      {
        path: 'admin',
        canActivate: [roleGuard('ADMINISTRADOR')],
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.adminRoutes),
      },
      {
        path: 'foro',
        canActivate: [roleGuard('ADMINISTRADOR', 'CATEDRATICO')],
        loadChildren: () => import('./features/foro/foro.routes').then((m) => m.foroRoutes),
      },
      {
        path: 'mantenimiento',
        canActivate: [roleGuard('ADMINISTRADOR', 'CATEDRATICO')],
        loadChildren: () =>
          import('./features/mantenimiento/mantenimiento.routes').then(
            (m) => m.mantenimientoRoutes,
          ),
      },
      {
        path: '',
        pathMatch: 'full',
        loadChildren: () =>
          import('./features/dashboard/dashboard.routes').then((m) => m.dashboardRoutes),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
