import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { UsuarioApiService } from '../../services/usuario-api.service';
import { LIMITE_NOMBRE_COMPLETO, PATRON_NOMBRE_USUARIO } from '../../utils/reglas-perfil';

/**
 * Nombre completo y nombre de usuario de la cuenta. El nombre de usuario es lo que ven los demás en
 * el foro y lo que se escribe tras la arroba para mencionar a alguien.
 */
@Component({
  selector: 'app-perfil-page',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, TranslocoPipe],
  templateUrl: './perfil.page.html',
  styleUrl: './perfil.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PerfilPage {
  private readonly fb = inject(FormBuilder);
  private readonly usuarioApi = inject(UsuarioApiService);
  private readonly apiErrors = inject(ApiErrorService);
  readonly auth = inject(AuthStore);

  readonly limiteNombre = LIMITE_NOMBRE_COMPLETO;
  readonly guardando = signal(false);
  readonly guardado = signal(false);
  readonly errorNombre = signal<string | null>(null);
  readonly errorUsuario = signal<string | null>(null);
  readonly errorGeneral = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    nombreCompleto: [
      this.auth.user()?.nombreCompleto ?? '',
      [Validators.required, Validators.maxLength(LIMITE_NOMBRE_COMPLETO)],
    ],
    nombreUsuario: [
      this.auth.user()?.nombreUsuario ?? '',
      [Validators.required, Validators.pattern(PATRON_NOMBRE_USUARIO)],
    ],
  });

  guardar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.guardando()) return;
    this.guardando.set(true);
    this.guardado.set(false);
    this.errorNombre.set(null);
    this.errorUsuario.set(null);
    this.errorGeneral.set(null);
    const datos = this.form.getRawValue();
    this.usuarioApi
      .actualizarPerfil({
        nombreCompleto: datos.nombreCompleto.trim(),
        nombreUsuario: datos.nombreUsuario.trim(),
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (sesion) => {
          this.auth.setUser(sesion);
          this.form.reset({
            nombreCompleto: sesion.nombreCompleto ?? '',
            nombreUsuario: sesion.nombreUsuario ?? '',
          });
          this.guardado.set(true);
        },
        error: (error: HttpErrorResponse) => this.mostrarError(error),
      });
  }

  /** El motivo se muestra junto al campo; se evita duplicarlo en el aviso global. */
  private mostrarError(error: HttpErrorResponse): void {
    this.apiErrors.clear();
    const problema = error.error as {
      detail?: string;
      code?: string;
      errores?: { campo: string; mensaje: string }[];
    } | null;
    const porCampo = (campo: string) =>
      problema?.errores?.find((item) => item.campo === campo)?.mensaje ?? null;
    const nombre = porCampo('nombreCompleto');
    const usuario =
      porCampo('nombreUsuario') ??
      (problema?.code === 'nombre_usuario_en_uso' ? (problema.detail ?? null) : null);
    this.errorNombre.set(nombre);
    this.errorUsuario.set(usuario);
    if (!nombre && !usuario) this.errorGeneral.set(problema?.detail ?? null);
  }
}
