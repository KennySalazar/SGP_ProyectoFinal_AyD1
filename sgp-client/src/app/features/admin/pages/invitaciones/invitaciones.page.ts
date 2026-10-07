import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { RoleName } from '../../../../core/models/auth.models';
import { ProblemDetails } from '../../../../core/models/problem-details';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import {
  EstadoInvitacion,
  InvitacionResponse,
  PaginaInvitaciones,
} from '../../models/invitacion.models';
import { InvitacionApiService } from '../../services/invitacion-api.service';

const TAMANIO_PAGINA = 10;

/** Códigos que se explican junto al formulario en lugar del aviso global. */
const CODIGOS_CORREO_EN_USO = ['email_already_registered', 'invitacion_pendiente'];

type Severidad = 'success' | 'warn' | 'danger' | 'secondary';

@Component({
  selector: 'app-invitaciones-page',
  standalone: true,
  imports: [
    TranslocoPipe,
    ReactiveFormsModule,
    ButtonModule,
    InputTextModule,
    PaginatorModule,
    SelectModule,
    TableModule,
    TagModule,
  ],
  templateUrl: './invitaciones.page.html',
  styleUrl: './invitaciones.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvitacionesPage {
  private readonly api = inject(InvitacionApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private readonly transloco = inject(TranslocoService);
  private readonly formatoFecha = new Intl.DateTimeFormat('es-GT', {
    timeZone: 'America/Guatemala',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  });

  /** Roles que el backend acepta para invitación; HU-002 y HU-004 amplían esta lista. */
  readonly roles: { valor: RoleName; etiqueta: string }[] = [
    { valor: 'CATEDRATICO', etiqueta: 'invitaciones.roles.CATEDRATICO' },
  ];
  readonly estados: (EstadoInvitacion | null)[] = [
    null,
    'PENDIENTE',
    'VENCIDA',
    'ACEPTADA',
    'CANCELADA',
  ];

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    rol: this.fb.nonNullable.control<RoleName>('CATEDRATICO', Validators.required),
  });

  readonly enviando = signal(false);
  readonly errorEnvio = signal<string | null>(null);
  readonly invitacionEnviada = signal<InvitacionResponse | null>(null);

  readonly estado = signal<EstadoInvitacion | null>(null);
  readonly resultado = signal<PaginaInvitaciones | null>(null);
  readonly cargando = signal(false);
  readonly errorLista = signal(false);
  readonly reenviando = signal<string | null>(null);
  readonly reenvioExitoso = signal<InvitacionResponse | null>(null);

  private readonly severidades: Record<EstadoInvitacion, Severidad> = {
    PENDIENTE: 'warn',
    VENCIDA: 'danger',
    ACEPTADA: 'success',
    CANCELADA: 'secondary',
  };

  constructor() {
    this.cargar(0);
  }

  invitar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.enviando()) return;

    this.enviando.set(true);
    this.errorEnvio.set(null);
    this.invitacionEnviada.set(null);
    this.reenvioExitoso.set(null);
    this.apiErrors.clear();
    const { email, rol } = this.form.getRawValue();

    this.api
      .invitar({ email: email.trim(), rol })
      .pipe(
        finalize(() => this.enviando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (invitacion) => {
          this.invitacionEnviada.set(invitacion);
          this.form.reset({ email: '', rol });
          this.cargar(0);
        },
        error: (error: HttpErrorResponse) => {
          const problem = error.error as (ProblemDetails & { code?: string }) | null;
          if (error.status === 409 && CODIGOS_CORREO_EN_USO.includes(problem?.code ?? '')) {
            this.errorEnvio.set(this.transloco.translate(`invitaciones.errores.${problem?.code}`));
            this.apiErrors.clear();
            return;
          }
          this.errorEnvio.set(
            problem?.detail ?? this.transloco.translate('invitaciones.errores.envio'),
          );
        },
      });
  }

  cargar(pagina: number): void {
    this.cargando.set(true);
    this.errorLista.set(false);

    this.api
      .listar(this.estado(), pagina, TAMANIO_PAGINA)
      .pipe(
        finalize(() => this.cargando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (resultado) => this.resultado.set(resultado),
        error: () => this.errorLista.set(true),
      });
  }

  filtrar(estado: EstadoInvitacion | null): void {
    if (estado === this.estado()) return;
    this.estado.set(estado);
    this.resultado.set(null);
    this.cargar(0);
  }

  cambiarPagina(evento: PaginatorState): void {
    this.cargar(evento.page ?? 0);
  }

  reenviar(invitacion: InvitacionResponse): void {
    if (this.reenviando()) return;
    this.reenviando.set(invitacion.id);
    this.reenvioExitoso.set(null);
    this.invitacionEnviada.set(null);
    this.apiErrors.clear();

    this.api
      .reenviar(invitacion.id)
      .pipe(
        finalize(() => this.reenviando.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (nueva) => {
          this.reenvioExitoso.set(nueva);
          this.cargar(this.resultado()?.number ?? 0);
        },
        // El aviso global muestra el detalle del problema (por ejemplo, ya aceptada).
        error: () => this.cargar(this.resultado()?.number ?? 0),
      });
  }

  puedeReenviar(invitacion: InvitacionResponse): boolean {
    return invitacion.estado === 'PENDIENTE' || invitacion.estado === 'VENCIDA';
  }

  severidad(estado: EstadoInvitacion): Severidad {
    return this.severidades[estado];
  }

  claveEstado(estado: EstadoInvitacion | null): string {
    return `invitaciones.estados.${estado ?? 'TODAS'}`;
  }

  fechaGuatemala(fecha: string): string {
    return this.formatoFecha.format(new Date(fecha));
  }
}
