import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
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
  readonly sent = signal(false);
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    if (this.form.invalid) return;
    this.usuarioApi
      .requestPasswordRecovery(this.form.getRawValue())
      .subscribe(() => this.sent.set(true));
  }
}
