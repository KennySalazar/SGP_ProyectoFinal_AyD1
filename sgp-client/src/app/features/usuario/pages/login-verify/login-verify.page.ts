import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { LoginResponse } from '../../../../core/models/auth.models';
import { AuthStore } from '../../../../core/services/auth.store';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';

@Component({
  selector: 'app-login-verify-page',
  standalone: true,
  imports: [ReactiveFormsModule, AuthCardComponent, ButtonModule, InputTextModule],
  templateUrl: './login-verify.page.html',
  styleUrl: './login-verify.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginVerifyPage {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStore);

  readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
  });

  submit(): void {
    const challengeId = this.route.snapshot.queryParamMap.get('challengeId');
    if (!challengeId || this.form.invalid) return;
    this.http.post<LoginResponse>('/api/v1/auth/login/verify', { challengeId, otp: this.form.controls.code.value }, { withCredentials: true }).subscribe((response) => {
      if (!response.accessToken) return;
      this.auth.setAccessToken(response.accessToken);
      this.auth.loadMe().subscribe(() => void this.router.navigate(['/']));
    });
  }
}
