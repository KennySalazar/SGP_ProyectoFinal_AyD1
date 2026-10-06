import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { finalize } from 'rxjs';
import {
  ConsultaAuditoria,
  DetalleAuditoria,
  PaginaAuditoria,
  RegistroAuditoria,
} from '../../models/auditoria.models';
import { AuditoriaApiService } from '../../services/auditoria-api.service';

function rangoFechasValido(control: AbstractControl): ValidationErrors | null {
  const { desde, hasta } = control.value as { desde: string; hasta: string };
  return desde && hasta && desde > hasta ? { rangoFechas: true } : null;
}

@Component({
  selector: 'app-bitacora-page',
  standalone: true,
  imports: [
    TranslocoPipe,
    ReactiveFormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    PaginatorModule,
    SelectModule,
    TableModule,
  ],
  templateUrl: './bitacora.page.html',
  styleUrl: './bitacora.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BitacoraPage {
  private readonly api = inject(AuditoriaApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private readonly transloco = inject(TranslocoService);
  private consultaAplicada: ConsultaAuditoria = { pagina: 0, tamanio: 20 };
  private registroSeleccionado: string | null = null;
  private readonly formatoFecha = new Intl.DateTimeFormat('es-GT', {
    timeZone: 'America/Guatemala',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  });

  readonly filtros = this.fb.nonNullable.group(
    {
      usuarioEmail: ['', [Validators.email, Validators.maxLength(320)]],
      desde: [''],
      hasta: [''],
    },
    { validators: rangoFechasValido },
  );
  readonly tamanios = [10, 20, 50, 100];
  readonly tamanio = this.fb.nonNullable.control(20);
  readonly pagina = signal<PaginaAuditoria | null>(null);
  readonly cargando = signal(false);
  readonly errorConsulta = signal<'consulta' | 'prohibido' | null>(null);
  readonly mostrarDetalle = signal(false);
  readonly cargandoDetalle = signal(false);
  readonly detalle = signal<DetalleAuditoria | null>(null);
  readonly errorDetalle = signal<'consulta' | 'prohibido' | null>(null);

  constructor() {
    this.cargarPagina();
  }

  aplicarFiltros(): void {
    this.filtros.markAllAsTouched();
    if (this.filtros.invalid || this.cargando()) return;
    const { usuarioEmail, desde, hasta } = this.filtros.getRawValue();
    this.consultaAplicada = {
      pagina: 0,
      tamanio: this.tamanio.value,
      ...(usuarioEmail.trim() ? { usuarioEmail: usuarioEmail.trim() } : {}),
      ...(desde ? { desde: `${desde}T00:00:00-06:00` } : {}),
      ...(hasta ? { hasta: `${hasta}T23:59:59.999999-06:00` } : {}),
    };
    this.cargarPagina();
  }

  limpiarFiltros(): void {
    if (this.cargando()) return;
    this.filtros.reset();
    this.consultaAplicada = { pagina: 0, tamanio: this.tamanio.value };
    this.cargarPagina();
  }

  cambiarPagina(evento: PaginatorState): void {
    if (this.cargando()) return;
    this.consultaAplicada = { ...this.consultaAplicada, pagina: evento.page ?? 0 };
    this.cargarPagina();
  }

  cambiarTamanio(): void {
    if (this.cargando()) return;
    this.consultaAplicada = { ...this.consultaAplicada, pagina: 0, tamanio: this.tamanio.value };
    this.cargarPagina();
  }

  cargarPagina(): void {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.errorConsulta.set(null);
    this.api
      .consultar(this.consultaAplicada)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (pagina) => this.pagina.set(pagina),
        error: (error: HttpErrorResponse) => {
          this.pagina.set(null);
          this.errorConsulta.set(error.status === 403 ? 'prohibido' : 'consulta');
        },
      });
  }

  abrirDetalle(registro: RegistroAuditoria): void {
    if (this.cargandoDetalle()) return;
    this.registroSeleccionado = registro.id;
    this.detalle.set(null);
    this.mostrarDetalle.set(true);
    this.cargarDetalle();
  }

  cargarDetalle(): void {
    const id = this.registroSeleccionado;
    if (!id || this.cargandoDetalle()) return;
    this.cargandoDetalle.set(true);
    this.errorDetalle.set(null);
    this.api
      .detalle(id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargandoDetalle.set(false)),
      )
      .subscribe({
        next: (detalle) => {
          if (this.registroSeleccionado === id) this.detalle.set(detalle);
        },
        error: (error: HttpErrorResponse) => {
          if (this.registroSeleccionado === id) {
            this.errorDetalle.set(error.status === 403 ? 'prohibido' : 'consulta');
          }
        },
      });
  }

  cerrarDetalle(): void {
    this.mostrarDetalle.set(false);
    this.registroSeleccionado = null;
    this.detalle.set(null);
    this.errorDetalle.set(null);
  }

  readonly gruposDetalle = computed(() => {
    const camposOcultos = ['municipio.id', 'municipio.departamentoId', 'departamento.id'];
    const cambios = (this.detalle()?.cambios ?? []).filter(
      (cambio) => !camposOcultos.includes(cambio.campo),
    );
    const grupos = ['generales', 'ubicacion', 'estado', 'otros', 'tecnicos'] as const;
    return grupos
      .map((id) => ({
        id,
        cambios: cambios.filter((cambio) => this.grupoCampo(cambio.campo) === id),
      }))
      .filter((grupo) => grupo.cambios.length > 0);
  });

  private grupoCampo(campo: string): string {
    const partes = campo.split('.');
    const raiz = partes[0];
    const hoja = partes.at(-1) ?? campo;
    if (/^id$|Id$/.test(hoja) || ['creadoEn', 'versionToken', 'tokenVersion'].includes(raiz)) {
      return 'tecnicos';
    }
    if (['departamento', 'municipio', 'latitud', 'longitud', 'utm'].includes(raiz)) {
      return 'ubicacion';
    }
    if (
      [
        'activo',
        'activado',
        'verificado',
        'rol',
        'estadoActual',
        'indiceCondicionActual',
        'fechaUltimaInspeccion',
        'twoFactorEnabled',
        'dosFactoresHabilitado',
      ].includes(raiz)
    ) {
      return 'estado';
    }
    return ['nombre', 'codigo', 'email', 'ruta', 'kilometraje'].includes(raiz)
      ? 'generales'
      : 'otros';
  }

  nombreCampo(campo: string): string {
    const conocidos = [
      'id',
      'nombre',
      'codigo',
      'codigoIne',
      'email',
      'activo',
      'activado',
      'verificado',
      'rol',
      'latitud',
      'longitud',
      'ruta',
      'kilometraje',
      'estadoActual',
      'twoFactorEnabled',
      'dosFactoresHabilitado',
      'indiceCondicionActual',
      'fechaUltimaInspeccion',
      'creadoEn',
      'versionToken',
      'tokenVersion',
      'departamento',
      'municipio',
      'utm',
      'zona',
      'epsg',
      'este',
      'norte',
    ];
    return campo
      .split('.')
      .map((parte) =>
        conocidos.includes(parte)
          ? this.transloco.translate<string>(`auditoria.campos.${parte}`)
          : parte.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' '),
      )
      .join(' · ');
  }

  fechaLegible(fecha: string): string {
    return this.formatoFecha.format(new Date(fecha));
  }
}
