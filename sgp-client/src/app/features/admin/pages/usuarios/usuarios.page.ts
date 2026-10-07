import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
  WritableSignal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { Observable, finalize } from 'rxjs';
import { RoleName } from '../../../../core/models/auth.models';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { EstadoUsuario, PaginaUsuarios, UsuarioAdmin } from '../../models/usuario.models';
import { GestionUsuariosApiService } from '../../services/gestion-usuarios-api.service';

const TAMANIO_PAGINA = 10;
const ROLES: RoleName[] = ['ADMINISTRADOR', 'CATEDRATICO', 'PROFESIONAL_EXTERNO', 'ESTUDIANTE'];

type Severidad = 'success' | 'warn' | 'danger';

/** Mensaje de confirmación de la última acción realizada sobre una cuenta. */
interface Confirmacion {
  clave: string;
  email: string;
}

@Component({
  selector: 'app-usuarios-page',
  standalone: true,
  imports: [
    TranslocoPipe,
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    DialogModule,
    InputTextModule,
    PaginatorModule,
    SelectModule,
    TableModule,
    TagModule,
    TextareaModule,
  ],
  templateUrl: './usuarios.page.html',
  styleUrl: './usuarios.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsuariosPage {
  private readonly api = inject(GestionUsuariosApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly auth = inject(AuthStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private readonly transloco = inject(TranslocoService);

  readonly opcionesRol: { valor: RoleName | null; etiqueta: string }[] = [
    { valor: null, etiqueta: 'usuarios.todosLosRoles' },
    ...ROLES.map((rol) => ({ valor: rol, etiqueta: `invitaciones.roles.${rol}` })),
  ];
  readonly estados: (EstadoUsuario | null)[] = [null, 'ACTIVO', 'PENDIENTE', 'INACTIVO'];
  private readonly severidades: Record<EstadoUsuario, Severidad> = {
    ACTIVO: 'success',
    PENDIENTE: 'warn',
    INACTIVO: 'danger',
  };

  readonly filtroRol = new FormControl<RoleName | null>(null);
  readonly estado = signal<EstadoUsuario | null>(null);
  readonly resultado = signal<PaginaUsuarios | null>(null);
  readonly cargando = signal(false);
  readonly errorLista = signal(false);
  readonly confirmacion = signal<Confirmacion | null>(null);
  readonly reactivando = signal<string | null>(null);

  // Desactivación
  readonly porDesactivar = signal<UsuarioAdmin | null>(null);
  readonly motivo = new FormControl('', {
    nonNullable: true,
    validators: Validators.maxLength(1000),
  });
  readonly desactivando = signal(false);
  readonly errorDesactivacion = signal<string | null>(null);

  // Cambio de rol
  readonly porCambiarRol = signal<UsuarioAdmin | null>(null);
  readonly cambioRol = this.fb.nonNullable.group({
    rol: this.fb.control<RoleName | null>(null, Validators.required),
    numeroColegiado: this.fb.nonNullable.control({ value: '', disabled: true }, [
      Validators.required,
      Validators.maxLength(50),
      Validators.pattern(/^\s*[A-Za-z0-9-]+\s*$/),
    ]),
  });
  readonly nuevoRolEsProfesional = signal(false);
  readonly rolesDisponibles = computed(() => {
    const actual = this.porCambiarRol()?.rol;
    return ROLES.filter((rol) => rol !== actual).map((rol) => ({
      valor: rol,
      etiqueta: `invitaciones.roles.${rol}`,
    }));
  });
  readonly cambiandoRol = signal(false);
  readonly errorCambioRol = signal<string | null>(null);

  constructor() {
    this.filtroRol.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.recargarDesdeInicio());
    this.cambioRol.controls.rol.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rol) => this.ajustarColegiado(rol));
    this.cargar(0);
  }

  /** El administrador no puede desactivarse ni cambiar su propio rol. */
  esUsuarioActual(usuario: UsuarioAdmin): boolean {
    return usuario.id === this.auth.user()?.id;
  }

  cargar(pagina: number): void {
    this.cargando.set(true);
    this.errorLista.set(false);
    this.api
      .listar({ rol: this.filtroRol.value, estado: this.estado() }, pagina, TAMANIO_PAGINA)
      .pipe(
        finalize(() => this.cargando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (resultado) => this.resultado.set(resultado),
        error: () => this.errorLista.set(true),
      });
  }

  filtrarEstado(estado: EstadoUsuario | null): void {
    if (estado === this.estado()) return;
    this.estado.set(estado);
    this.recargarDesdeInicio();
  }

  cambiarPagina(evento: PaginatorState): void {
    this.cargar(evento.page ?? 0);
  }

  // --- Desactivar ---

  abrirDesactivacion(usuario: UsuarioAdmin): void {
    this.confirmacion.set(null);
    this.errorDesactivacion.set(null);
    this.motivo.reset('');
    this.porDesactivar.set(usuario);
  }

  cerrarDesactivacion(): void {
    if (this.desactivando()) return;
    this.porDesactivar.set(null);
  }

  desactivar(): void {
    const usuario = this.porDesactivar();
    if (!usuario || this.motivo.invalid || this.desactivando()) return;
    const motivo = this.motivo.value.trim() || null;
    this.ejecutar(
      this.api.desactivar(usuario.id, motivo),
      this.desactivando,
      this.errorDesactivacion,
      () => {
        this.porDesactivar.set(null);
        this.confirmacion.set({ clave: 'usuarios.desactivado', email: usuario.email });
      },
    );
  }

  // --- Reactivar ---

  reactivar(usuario: UsuarioAdmin): void {
    if (this.reactivando()) return;
    this.confirmacion.set(null);
    this.apiErrors.clear();
    this.reactivando.set(usuario.id);
    this.api
      .reactivar(usuario.id)
      .pipe(
        finalize(() => this.reactivando.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.confirmacion.set({ clave: 'usuarios.reactivado', email: usuario.email });
          this.cargar(this.resultado()?.number ?? 0);
        },
        // El aviso global explica el motivo (por ejemplo, otro administrador ya la reactivó).
        error: () => this.cargar(this.resultado()?.number ?? 0),
      });
  }

  // --- Cambiar rol ---

  abrirCambioRol(usuario: UsuarioAdmin): void {
    this.confirmacion.set(null);
    this.errorCambioRol.set(null);
    this.cambioRol.reset({ rol: null, numeroColegiado: '' });
    this.porCambiarRol.set(usuario);
  }

  cerrarCambioRol(): void {
    if (this.cambiandoRol()) return;
    this.porCambiarRol.set(null);
  }

  cambiarRol(): void {
    const usuario = this.porCambiarRol();
    this.cambioRol.markAllAsTouched();
    if (!usuario || this.cambioRol.invalid || this.cambiandoRol()) return;
    const { rol, numeroColegiado } = this.cambioRol.getRawValue();
    if (!rol) return;
    this.ejecutar(
      this.api.cambiarRol(usuario.id, {
        rol,
        ...(rol === 'PROFESIONAL_EXTERNO' ? { numeroColegiado: numeroColegiado.trim() } : {}),
      }),
      this.cambiandoRol,
      this.errorCambioRol,
      () => {
        this.porCambiarRol.set(null);
        this.confirmacion.set({ clave: 'usuarios.rolCambiado', email: usuario.email });
      },
    );
  }

  severidad(estado: EstadoUsuario): Severidad {
    return this.severidades[estado];
  }

  claveEstado(estado: EstadoUsuario | null): string {
    return `usuarios.estados.${estado ?? 'TODOS'}`;
  }

  private ajustarColegiado(rol: RoleName | null): void {
    const colegiado = this.cambioRol.controls.numeroColegiado;
    const esProfesional = rol === 'PROFESIONAL_EXTERNO';
    this.nuevoRolEsProfesional.set(esProfesional);
    if (esProfesional) {
      colegiado.enable();
    } else {
      colegiado.reset('');
      colegiado.disable();
    }
  }

  private recargarDesdeInicio(): void {
    this.resultado.set(null);
    this.cargar(0);
  }

  /** Envía una acción confirmada en un diálogo; los errores se explican dentro del diálogo. */
  private ejecutar(
    peticion: Observable<UsuarioAdmin>,
    enCurso: WritableSignal<boolean>,
    error: WritableSignal<string | null>,
    alTerminar: () => void,
  ): void {
    enCurso.set(true);
    error.set(null);
    this.apiErrors.clear();
    peticion
      .pipe(
        finalize(() => enCurso.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          alTerminar();
          this.cargar(this.resultado()?.number ?? 0);
        },
        error: (respuesta: HttpErrorResponse) => {
          const problem = respuesta.error as { detail?: string } | null;
          error.set(problem?.detail ?? this.transloco.translate('common.operationFailed'));
          this.apiErrors.clear();
        },
      });
  }
}
