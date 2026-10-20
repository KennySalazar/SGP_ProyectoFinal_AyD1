import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthStore } from '../../core/services/auth.store';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { SyncQueueItem } from '../../offline/db/sgp-db';
import { TranslocoPipe } from '@jsverse/transloco';
import { RevisionApiService } from '../../features/revision/services/revision-api.service';
import { CampanaAvisosComponent } from '../../features/notificaciones/components/campana-avisos/campana-avisos.component';
import { SincronizacionService } from '../../offline/services/sincronizacion.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    AsyncPipe,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    TranslocoPipe,
    CampanaAvisosComponent,
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  readonly auth = inject(AuthStore);
  readonly connectivity = inject(ConnectivityService);
  readonly sincronizacion = inject(SincronizacionService);
  readonly revisiones = inject(RevisionApiService);
  private readonly router = inject(Router);

  constructor() {
    this.sincronizacion.iniciar();
  }

  cantidadPendiente(items: SyncQueueItem[], tipo: 'INSPECCION' | 'FOTO'): number {
    const propietarioId = this.auth.user()?.id;
    return items.filter((item) => item.propietarioId === propietarioId && item.tipo === tipo)
      .length;
  }

  logout(): void {
    this.auth
      .logout()
      .subscribe({ complete: () => this.finishLogout(), error: () => this.finishLogout() });
  }
  private finishLogout(): void {
    this.auth.clear();
    void this.router.navigate(['/login']);
  }
}
