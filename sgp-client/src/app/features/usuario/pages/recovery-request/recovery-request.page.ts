import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';
import { TranslocoPipe } from '@jsverse/transloco';
import { UsuarioApiService } from '../../services/usuario-api.service';

@Component({
  selector: 'app-recovery-request-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AuthCardComponent,
    ButtonModule,
    InputTextModule,
    TranslocoPipe,
  ],
  templateUrl: './recovery-request.page.html',
  styleUrl: './recovery-request.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecoveryRequestPage {
  private readonly fb = inject(FormBuilder);
  private readonly usuarioApi = inject(UsuarioApiService);
  private readonly router = inject(Router);
  readonly enviando = signal(false);
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  /** Tras enviar el código pasa directo a escribirlo, con el correo ya cargado. */
  submit(): void {
    if (this.form.invalid || this.enviando()) return;
    const { email } = this.form.getRawValue();
    this.enviando.set(true);
    this.usuarioApi
      .requestPasswordRecovery({ email })
      .pipe(finalize(() => this.enviando.set(false)))
      .subscribe(() =>
        this.router.navigate(['/recuperar/restablecer'], {
          queryParams: { email, enviado: 1 },
        }),
      );
  }
}
