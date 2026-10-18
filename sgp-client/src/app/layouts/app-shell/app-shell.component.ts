import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthStore } from '../../core/services/auth.store';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { CampanaAvisosComponent } from '../../features/notificaciones/components/campana-avisos/campana-avisos.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslocoPipe, CampanaAvisosComponent],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  readonly auth = inject(AuthStore);
  readonly connectivity = inject(ConnectivityService);
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
