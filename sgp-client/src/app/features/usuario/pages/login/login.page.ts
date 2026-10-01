import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { AuthStore } from '../../../../core/services/auth.store';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';
import { TranslocoPipe } from '@jsverse/transloco';
import { UsuarioApiService } from '../../services/usuario-api.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AuthCardComponent,
    ButtonModule,
    InputTextModule,
    TranslocoPipe,
  ],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly usuarioApi = inject(UsuarioApiService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStore);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.usuarioApi.login(this.form.getRawValue()).subscribe((response) => {
      if (response.requiresTwoFactor && response.challengeId) {
        void this.router.navigate(['/login/verificar'], {
          queryParams: { challengeId: response.challengeId },
        });
        return;
      }
      if (!response.accessToken) return;
      this.auth.setAccessToken(response.accessToken);
      this.auth.loadMe().subscribe(() => void this.router.navigate(['/']));
    });
  }
}
