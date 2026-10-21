import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';
import { ContrasenaVisibleComponent } from '../../../../shared/components/contrasena-visible/contrasena-visible.component';
import { TranslocoPipe } from '@jsverse/transloco';
import { UsuarioApiService } from '../../services/usuario-api.service';
import { LIMITE_NOMBRE_COMPLETO, PATRON_NOMBRE_USUARIO } from '../../utils/reglas-perfil';

@Component({
  selector: 'app-register-page',
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
  templateUrl: './register.page.html',
  styleUrl: './register.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly usuarioApi = inject(UsuarioApiService);
  private readonly router = inject(Router);

  readonly limiteNombre = LIMITE_NOMBRE_COMPLETO;
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    nombreCompleto: ['', [Validators.required, Validators.maxLength(LIMITE_NOMBRE_COMPLETO)]],
    nombreUsuario: ['', [Validators.required, Validators.pattern(PATRON_NOMBRE_USUARIO)]],
    password: [
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
    const data = this.form.getRawValue();
    this.usuarioApi
      .register({
        ...data,
        nombreCompleto: data.nombreCompleto.trim(),
        nombreUsuario: data.nombreUsuario.trim(),
      })
      .subscribe(
        (response) =>
          void this.router.navigate(['/registro/verificar'], {
            queryParams: { email: data.email, challengeId: response.challengeId },
          }),
      );
  }
}
