import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthStore } from '../../core/services/auth.store';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { TranslocoPipe } from '@jsverse/transloco';

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
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  logout(): void {
    this.http
      .post('/api/v1/auth/logout', {}, { withCredentials: true })
      .subscribe({ complete: () => this.finishLogout(), error: () => this.finishLogout() });
  }
  private finishLogout(): void {
    this.auth.clear();
    void this.router.navigate(['/login']);
  }
}
