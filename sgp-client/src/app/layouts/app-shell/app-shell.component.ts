import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthStore } from '../../core/services/auth.store';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { RevisionApiService } from '../../features/revision/services/revision-api.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslocoPipe],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  readonly auth = inject(AuthStore);
  readonly connectivity = inject(ConnectivityService);
  readonly revisiones = inject(RevisionApiService);
  private readonly router = inject(Router);

  constructor() {
    if (this.auth.user()?.role === 'CATEDRATICO') {
      this.revisiones.cargarContador().subscribe({ error: () => undefined });
    }
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
