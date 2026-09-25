import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthStore } from '../../core/services/auth.store';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  readonly auth = inject(AuthStore);
}
