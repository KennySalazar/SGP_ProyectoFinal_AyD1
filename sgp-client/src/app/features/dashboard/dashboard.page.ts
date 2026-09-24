import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthStore } from '../../core/services/auth.store';
@Component({selector:'app-dashboard-page',standalone:true,templateUrl:'./dashboard.page.html',styleUrl:'./dashboard.page.scss',changeDetection:ChangeDetectionStrategy.OnPush})
export class DashboardPage{readonly auth=inject(AuthStore);}
