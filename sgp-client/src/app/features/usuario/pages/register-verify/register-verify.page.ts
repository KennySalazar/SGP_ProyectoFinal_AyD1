import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';
import { TranslocoPipe } from '@jsverse/transloco';
import { UsuarioApiService } from '../../services/usuario-api.service';

@Component({
  selector: 'app-register-verify-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AuthCardComponent,
    ButtonModule,
    InputTextModule,
    TranslocoPipe,
  ],
  templateUrl: './register-verify.page.html',
  styleUrl: './register-verify.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterVerifyPage {
  private readonly fb = inject(FormBuilder);
  private readonly usuarioApi = inject(UsuarioApiService);
  private readonly route = inject(ActivatedRoute);
  readonly done = signal(false);
  readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  submit(): void {
    const email = this.route.snapshot.queryParamMap.get('email');
    const challengeId = this.route.snapshot.queryParamMap.get('challengeId');
    if (!email || !challengeId || this.form.invalid) return;
    this.usuarioApi
      .verifyRegistration(email, challengeId, this.form.controls.code.value)
      .subscribe(() => this.done.set(true));
  }
}
