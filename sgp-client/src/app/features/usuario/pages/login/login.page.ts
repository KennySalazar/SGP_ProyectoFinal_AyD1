import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { LoginResponse } from '../../../../core/models/auth.models';
import { AuthStore } from '../../../../core/services/auth.store';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthCardComponent, ButtonModule, InputTextModule],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStore);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.http.post<LoginResponse>('/api/v1/auth/login', this.form.getRawValue(), { withCredentials: true }).subscribe((response) => {
      if (response.requiresTwoFactor && response.challengeId) {
        void this.router.navigate(['/login/verificar'], { queryParams: { challengeId: response.challengeId } });
        return;
      }
      if (!response.accessToken) return;
      this.auth.setAccessToken(response.accessToken);
      this.auth.loadMe().subscribe(() => void this.router.navigate(['/']));
    });
  }
}
