import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { finalize } from 'rxjs';
import { ProblemDetails } from '../../../../core/models/problem-details';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';
import { InvitacionPublica } from '../../models/invitacion.models';
import { UsuarioApiService } from '../../services/usuario-api.service';

export type EstadoActivacion =
  'validando' | 'lista' | 'vencida' | 'invalida' | 'error' | 'activada';

function contrasenasIguales(control: AbstractControl): ValidationErrors | null {
  const { password, confirmacion } = control.value as { password: string; confirmacion: string };
  return confirmacion && password !== confirmacion ? { noCoinciden: true } : null;
}

@Component({
  selector: 'app-activar-cuenta-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AuthCardComponent,
    ButtonModule,
    InputTextModule,
    TranslocoPipe,
  ],
  templateUrl: './activar-cuenta.page.html',
  styleUrl: './activar-cuenta.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivarCuentaPage {
  private readonly fb = inject(FormBuilder);
  private readonly usuarioApi = inject(UsuarioApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';

  readonly estado = signal<EstadoActivacion>('validando');
  readonly invitacion = signal<InvitacionPublica | null>(null);
  readonly enviando = signal(false);
  readonly errorContrasena = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(10),
          Validators.maxLength(72),
          Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/),
        ],
      ],
      confirmacion: ['', [Validators.required]],
    },
    { validators: contrasenasIguales },
  );

  constructor() {
    this.validar();
  }

  validar(): void {
    if (!this.token) {
      this.estado.set('invalida');
      return;
    }
    this.estado.set('validando');
    this.usuarioApi
      .validarInvitacion(this.token)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (invitacion) => {
          this.invitacion.set(invitacion);
          this.estado.set('lista');
        },
        error: (error: HttpErrorResponse) => this.estado.set(this.estadoPorError(error)),
      });
  }

  activar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.enviando()) return;

    this.enviando.set(true);
    this.errorContrasena.set(null);
    this.apiErrors.clear();

    this.usuarioApi
      .aceptarInvitacion(this.token, this.form.getRawValue().password)
      .pipe(
        finalize(() => this.enviando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.form.reset();
          this.estado.set('activada');
        },
        error: (error: HttpErrorResponse) => {
          const problem = error.error as (ProblemDetails & { code?: string }) | null;
          const errorDeContrasena =
            problem?.code === 'contrasena_invalida' ||
            (problem?.errores ?? []).some((item) => item.campo === 'password');
          if (error.status === 400 && errorDeContrasena) {
            this.errorContrasena.set(problem?.detail ?? null);
            this.apiErrors.clear();
            return;
          }
          this.estado.set(this.estadoPorError(error));
        },
      });
  }

  private estadoPorError(error: HttpErrorResponse): EstadoActivacion {
    const code = (error.error as { code?: string } | null)?.code;
    // Vencido o inválido se explican en la tarjeta; el aviso global queda para fallos inesperados.
    if (error.status === 410 || code === 'invitacion_vencida') {
      this.apiErrors.clear();
      return 'vencida';
    }
    if (error.status === 400 && code === 'invitacion_invalida') {
      this.apiErrors.clear();
      return 'invalida';
    }
    return 'error';
  }
}
