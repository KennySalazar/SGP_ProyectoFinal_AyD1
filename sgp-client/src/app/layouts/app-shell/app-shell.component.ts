import { AvisoSalidaComponent } from '../../features/salida/components/aviso-salida.component';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthStore } from '../../core/services/auth.store';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { RevisionApiService } from '../../features/revision/services/revision-api.service';
import { CampanaAvisosComponent } from '../../features/notificaciones/components/campana-avisos/campana-avisos.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    AvisoSalidaComponent,
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
  readonly puedePrepararSalida = () => {
    const usuario = this.auth.user();
    return (
      !!usuario &&
      usuario.active &&
      usuario.activated &&
      usuario.verified &&
      ['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO'].includes(usuario.role) &&
      (usuario.role !== 'PROFESIONAL_EXTERNO' || usuario.colegiadoVerificado === true)
    );
  };
  readonly connectivity = inject(ConnectivityService);
  readonly revisiones = inject(RevisionApiService);
  private readonly router = inject(Router);

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
