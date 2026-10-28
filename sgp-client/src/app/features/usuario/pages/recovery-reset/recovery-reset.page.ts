import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';
import { ContrasenaVisibleComponent } from '../../../../shared/components/contrasena-visible/contrasena-visible.component';
import { TranslocoPipe } from '@jsverse/transloco';
import { UsuarioApiService } from '../../services/usuario-api.service';

@Component({
  selector: 'app-recovery-reset-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    ContrasenaVisibleComponent,
    RouterLink,
    AuthCardComponent,
    ButtonModule,
    InputTextModule,
    TranslocoPipe,
  ],
  templateUrl: './recovery-reset.page.html',
  styleUrl: './recovery-reset.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecoveryResetPage {
  private readonly fb = inject(FormBuilder);
  private readonly usuarioApi = inject(UsuarioApiService);
  private readonly route = inject(ActivatedRoute);
  readonly done = signal(false);
  /** Llega desde la solicitud del código: se avisa que ya se envió al correo. */
  readonly codigoEnviado = this.route.snapshot.queryParamMap.get('enviado') === '1';
  readonly form = this.fb.nonNullable.group({
    email: [
      this.route.snapshot.queryParamMap.get('email') ?? '',
      [Validators.required, Validators.email],
    ],
    otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    newPassword: [
      '',
      [
        Validators.required,
        Validators.minLength(10),
        Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/),
      ],
    ],
  });

  submit(): void {
    if (this.form.invalid) return;
    this.usuarioApi.resetPassword(this.form.getRawValue()).subscribe(() => this.done.set(true));
  }
}
