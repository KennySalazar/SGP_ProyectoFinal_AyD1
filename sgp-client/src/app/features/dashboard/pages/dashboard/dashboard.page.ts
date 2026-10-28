import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthStore } from '../../../../core/services/auth.store';

interface AccesoInicio {
  ruta: string;
  clave: string;
  icono: string;
}

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  readonly auth = inject(AuthStore);
  readonly accesos = computed<AccesoInicio[]>(() => {
    const usuario = this.auth.user();
    if (!usuario?.active || !usuario.activated || !usuario.verified) return [];
    const accesos: AccesoInicio[] = [];
    const agregar = (ruta: string, clave: string, icono: string) =>
      accesos.push({ ruta, clave, icono });
    switch (usuario.role) {
      case 'ADMINISTRADOR':
        agregar('/admin', 'users', 'pi-users');
        agregar('/admin/cursos', 'courses', 'pi-calendar');
        agregar('/admin/formularios', 'forms', 'pi-file-edit');
        agregar('/admin/bitacora', 'audit', 'pi-history');
        agregar('/mantenimiento/ordenes', 'maintenance', 'pi-wrench');
        break;
      case 'CATEDRATICO':
        agregar('/revisiones', 'reviews', 'pi-check-square');
        agregar('/estudiantes/pendientes', 'students', 'pi-users');
        agregar('/puentes/asignaciones', 'assignments', 'pi-link');
        agregar('/inspecciones', 'inspections', 'pi-clipboard');
        agregar('/mantenimiento/ordenes', 'maintenance', 'pi-wrench');
        break;
      case 'ESTUDIANTE':
        agregar('/puentes/mis-asignaciones', 'bridges', 'pi-map-marker');
        agregar('/inspecciones', 'inspections', 'pi-clipboard');
        agregar('/observaciones', 'observations', 'pi-comments');
        break;
      case 'PROFESIONAL_EXTERNO':
        if (usuario.colegiadoVerificado === true) {
          agregar('/inspecciones', 'inspections', 'pi-clipboard');
        }
        break;
    }
    if (
      usuario.role === 'CATEDRATICO' ||
      usuario.role === 'ESTUDIANTE' ||
      (usuario.role === 'PROFESIONAL_EXTERNO' && usuario.colegiadoVerificado === true)
    ) {
      agregar('/preparar-salida', 'prepare', 'pi-cloud-download');
    }
    agregar('/puentes', 'catalog', 'pi-map');
    agregar('/avisos', 'notifications', 'pi-bell');
    agregar('/offline', 'offline', 'pi-sync');
    return accesos;
  });
}
