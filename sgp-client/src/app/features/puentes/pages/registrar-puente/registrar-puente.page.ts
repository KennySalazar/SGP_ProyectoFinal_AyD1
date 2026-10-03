import { DecimalPipe } from '@angular/common';
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
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { catchError, finalize, of, switchMap, tap } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import {
  CrearPuenteRequest,
  DepartamentoResponse,
  MunicipioResponse,
  PuenteProblemDetails,
  PuenteResponse,
} from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';

function textoObligatorio(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length > 0
    ? null
    : { required: true };
}

function kilometrajeValido(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;

  if (value === null || value === '') return null;

  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 9999999.999) {
    return { kilometraje: true };
  }

  const scaled = value * 1000;

  return Math.abs(scaled - Math.round(scaled)) < 0.000001 ? null : { kilometraje: true };
}

@Component({
  selector: 'app-registrar-puente-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    DecimalPipe,
    ButtonModule,
    InputTextModule,
    SelectModule,
  ],
  templateUrl: './registrar-puente.page.html',
  styleUrl: './registrar-puente.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegistrarPuentePage {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PuenteApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly transloco = inject(TranslocoService);

  private solicitudPendiente: CrearPuenteRequest | null = null;

  readonly departamentos = signal<DepartamentoResponse[]>([]);
  readonly municipios = signal<MunicipioResponse[]>([]);
  readonly cargandoDepartamentos = signal(false);
  readonly cargandoMunicipios = signal(false);
  readonly errorDepartamentos = signal(false);
  readonly errorMunicipios = signal(false);
  readonly guardando = signal(false);
  readonly erroresCampos = signal<Record<string, string>>({});
  readonly errorRegistro = signal<string | null>(null);
  readonly advertencia = signal<PuenteProblemDetails | null>(null);
  readonly puenteRegistrado = signal<PuenteResponse | null>(null);

  readonly form = this.fb.group({
    nombre: this.fb.nonNullable.control('', [textoObligatorio, Validators.maxLength(200)]),
    departamentoId: this.fb.nonNullable.control('', Validators.required),
    municipioId: this.fb.nonNullable.control({ value: '', disabled: true }, Validators.required),
    ruta: this.fb.nonNullable.control('', [textoObligatorio, Validators.maxLength(100)]),
    kilometraje: this.fb.control<number | null>(null, kilometrajeValido),
    latitud: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(-90),
      Validators.max(90),
    ]),
    longitud: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(-180),
      Validators.max(180),
    ]),
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.advertencia.set(null);
      this.solicitudPendiente = null;
      this.erroresCampos.set({});
      this.errorRegistro.set(null);
    });

    this.form.controls.departamentoId.valueChanges
      .pipe(
        tap(() => {
          this.form.controls.municipioId.reset('', { emitEvent: false });
          this.form.controls.municipioId.disable({ emitEvent: false });
          this.municipios.set([]);
          this.errorMunicipios.set(false);
        }),
        switchMap((departamentoId) => {
          if (!departamentoId) {
            this.cargandoMunicipios.set(false);
            return of(null);
          }

          this.cargandoMunicipios.set(true);

          return this.api.listarMunicipios(departamentoId).pipe(
            catchError(() => {
              this.errorMunicipios.set(true);
              return of(null);
            }),
            finalize(() => this.cargandoMunicipios.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina) => {
        if (!pagina) return;

        this.municipios.set(pagina.content);

        if (pagina.content.length > 0) {
          this.form.controls.municipioId.enable({ emitEvent: false });
        }
      });

    this.cargarDepartamentos();
  }

  cargarDepartamentos(): void {
    if (this.cargandoDepartamentos()) return;

    this.cargandoDepartamentos.set(true);
    this.errorDepartamentos.set(false);

    this.api
      .listarDepartamentos()
      .pipe(
        finalize(() => this.cargandoDepartamentos.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (pagina) => this.departamentos.set(pagina.content),
        error: () => this.errorDepartamentos.set(true),
      });
  }

  reintentarMunicipios(): void {
    const departamentoId = this.form.controls.departamentoId.value;

    if (departamentoId && !this.cargandoMunicipios()) {
      this.form.controls.departamentoId.setValue(departamentoId);
    }
  }

  mensajeCampo(campo: keyof typeof this.form.controls): string | null {
    const servidor = this.erroresCampos()[campo];

    if (servidor) return servidor;

    const control = this.form.controls[campo];

    if (!control.touched || !control.invalid) return null;

    if (control.hasError('required')) {
      return this.transloco.translate('puentes.required');
    }

    const claves: Record<string, string> = {
      nombre: 'puentes.nameInvalid',
      ruta: 'puentes.routeInvalid',
      latitud: 'puentes.latitudeInvalid',
      longitud: 'puentes.longitudeInvalid',
      kilometraje: 'puentes.kilometerInvalid',
    };

    return this.transloco.translate(claves[campo] ?? 'puentes.required');
  }

  registrar(): void {
    if (
      this.guardando() ||
      this.puenteRegistrado() ||
      this.cargandoDepartamentos() ||
      this.cargandoMunicipios()
    ) {
      return;
    }

    this.form.markAllAsTouched();

    if (this.form.invalid) return;

    const values = this.form.getRawValue();
    const municipio = this.municipios().find((item) => item.id === values.municipioId);

    if (!municipio || municipio.departamentoId !== values.departamentoId) {
      this.erroresCampos.set({
        municipioId: this.transloco.translate('puentes.invalidTerritory'),
      });
      return;
    }

    if (values.latitud === null || values.longitud === null) return;

    this.enviar({
      nombre: values.nombre.trim(),
      departamentoId: values.departamentoId,
      municipioId: values.municipioId,
      ruta: values.ruta.trim(),
      kilometraje: values.kilometraje,
      latitud: values.latitud,
      longitud: values.longitud,
      confirmarCercania: false,
    });
  }

  confirmarRegistro(): void {
    if (!this.solicitudPendiente || !this.advertencia() || this.guardando()) return;

    this.enviar({
      ...this.solicitudPendiente,
      confirmarCercania: true,
    });
  }

  cancelarConfirmacion(): void {
    this.advertencia.set(null);
    this.solicitudPendiente = null;
  }

  registrarOtro(): void {
    this.puenteRegistrado.set(null);
    this.advertencia.set(null);
    this.solicitudPendiente = null;
    this.errorRegistro.set(null);
    this.erroresCampos.set({});
    this.apiErrors.clear();

    this.form.reset({
      nombre: '',
      departamentoId: '',
      municipioId: '',
      ruta: '',
      kilometraje: null,
      latitud: null,
      longitud: null,
    });

    this.form.controls.municipioId.disable({ emitEvent: false });
  }

  fechaGuatemala(fecha: string): string {
    return new Intl.DateTimeFormat('es-GT', {
      timeZone: 'America/Guatemala',
      dateStyle: 'medium',
      timeStyle: 'medium',
    }).format(new Date(fecha));
  }

  private enviar(request: CrearPuenteRequest): void {
    this.guardando.set(true);
    this.errorRegistro.set(null);
    this.erroresCampos.set({});
    this.advertencia.set(null);
    this.solicitudPendiente = null;
    this.apiErrors.clear();
    this.form.disable({ emitEvent: false });

    this.api
      .registrar(request)
      .pipe(
        finalize(() => {
          this.guardando.set(false);
          this.form.enable({ emitEvent: false });

          if (this.municipios().length === 0) {
            this.form.controls.municipioId.disable({ emitEvent: false });
          }
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (puente) => {
          this.puenteRegistrado.set(puente);
        },
        error: (error: HttpErrorResponse) => {
          const problem = error.error as PuenteProblemDetails | null;

          if (
            error.status === 409 &&
            problem?.code === 'puente_cercano' &&
            problem.requiereConfirmacion === true
          ) {
            this.apiErrors.clear();
            this.solicitudPendiente = { ...request, confirmarCercania: false };
            this.advertencia.set(problem);
            return;
          }

          const campos: Record<string, string> = {};

          if (error.status === 422) {
            for (const detalle of problem?.errores ?? []) {
              if (detalle.campo && detalle.mensaje) {
                campos[detalle.campo] = detalle.mensaje;
              }
            }
          }

          this.erroresCampos.set(campos);
          this.errorRegistro.set(
            problem?.detail ?? problem?.title ?? this.transloco.translate('puentes.saveFailed'),
          );
        },
      });
  }
}
