import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
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
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { Subject, catchError, map, of, switchMap, timer } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import {
  CoordenadaGeografica,
  PuntoMapa,
  coordenadaValida,
} from '../../../../shared/utils/ubicacion-guatemala';
import {
  DepartamentoResponse,
  MunicipioResponse,
  PuenteFormInicial,
  PuenteFormValores,
  PuenteProblemDetails,
  UbicacionTerritorialResponse,
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
  selector: 'app-puente-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    ButtonModule,
    InputTextModule,
    SelectModule,
    SelectorUbicacionComponent,
  ],
  templateUrl: './puente-form.component.html',
  styleUrl: './puente-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PuenteFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PuenteApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly transloco = inject(TranslocoService);

  readonly guardando = input(false);
  readonly erroresServidor = input<Record<string, string>>({});
  readonly errorTerritorioServidor = input<string | null>(null);
  readonly cercanos = input<readonly PuntoMapa[]>([]);
  readonly valoresIniciales = input<PuenteFormInicial | null>(null);
  readonly ocultarAcciones = input(false);
  readonly etiquetaEnviar = input('puentes.register');
  readonly etiquetaEnviando = input('puentes.saving');

  readonly enviar = output<PuenteFormValores>();
  readonly cambio = output<void>();

  private readonly consultasUbicacion = new Subject<CoordenadaGeografica | null>();

  private ultimaCoordenada = '';

  readonly territorio = signal<UbicacionTerritorialResponse | null>(null);
  readonly resolviendoUbicacion = signal(false);
  readonly errorUbicacion = signal<string | null>(null);

  readonly zonaUtm = computed(() => this.territorio()?.zonaUtm ?? null);

  readonly departamentos = computed<DepartamentoResponse[]>(() => [
    ...new Map(
      (this.territorio()?.candidatos ?? []).map((candidato) => [
        candidato.departamento.id,
        candidato.departamento,
      ]),
    ).values(),
  ]);

  readonly municipios = signal<MunicipioResponse[]>([]);

  readonly form = this.fb.group({
    nombre: this.fb.nonNullable.control('', [textoObligatorio, Validators.maxLength(200)]),
    departamentoId: this.fb.nonNullable.control({ value: '', disabled: true }, Validators.required),
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
    effect(() => {
      const guardando = this.guardando();

      untracked(() => {
        if (guardando) {
          this.form.disable({ emitEvent: false });
          return;
        }

        this.form.enable({ emitEvent: false });
        this.sincronizarControlesTerritoriales();
      });
    });

    effect(() => {
      const mensaje = this.errorTerritorioServidor();

      if (mensaje === null) return;

      untracked(() => {
        this.territorio.set(null);
        this.municipios.set([]);
        this.form.patchValue({ departamentoId: '', municipioId: '' }, { emitEvent: false });
        this.errorUbicacion.set(mensaje);
        this.sincronizarControlesTerritoriales();
      });
    });

    effect(() => {
      const valores = this.valoresIniciales();

      if (valores) untracked(() => this.form.patchValue(valores));
    });

    this.consultasUbicacion
      .pipe(
        switchMap((coordenada) => {
          if (!coordenada) return of(null);

          // Cada nuevo punto cancela la espera y la petición anterior.
          return timer(300).pipe(
            switchMap(() => this.api.resolverUbicacion(coordenada.latitud, coordenada.longitud)),
            map((respuesta) => ({ coordenada, respuesta })),
            catchError((error: HttpErrorResponse) => {
              this.errorUbicacion.set(this.detalleError(error, 'puentes.locationLookupFailed'));
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => {
        this.resolviendoUbicacion.set(false);
        if (!resultado) return;

        const actual = this.form.getRawValue();

        if (
          actual.latitud !== resultado.coordenada.latitud ||
          actual.longitud !== resultado.coordenada.longitud
        ) {
          return;
        }

        if (resultado.respuesta.candidatos.length === 0) {
          this.errorUbicacion.set(this.transloco.translate('puentes.locationWithoutMunicipality'));
          return;
        }

        this.territorio.set(resultado.respuesta);

        const departamentos = this.departamentos();
        if (departamentos.length === 1) {
          this.form.controls.departamentoId.setValue(departamentos[0].id, { emitEvent: false });
          this.actualizarMunicipios(departamentos[0].id);
        }

        this.sincronizarControlesTerritoriales();
      });

    this.form.controls.departamentoId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((departamentoId) => {
        this.actualizarMunicipios(departamentoId);
        this.sincronizarControlesTerritoriales();
      });

    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.consultarSiCambioCoordenada();
      this.cambio.emit();
    });
  }

  seleccionarCoordenada(coordenada: CoordenadaGeografica): void {
    if (this.guardando()) return;

    this.form.patchValue({
      latitud: coordenada.latitud,
      longitud: coordenada.longitud,
    });

    this.form.controls.latitud.markAsTouched();
    this.form.controls.longitud.markAsTouched();
  }

  reintentarUbicacion(): void {
    if (this.guardando() || this.resolviendoUbicacion()) return;
    this.consultarSiCambioCoordenada(true);
  }

  quitarUbicacion(): void {
    if (this.guardando()) return;

    this.form.patchValue({
      latitud: null,
      longitud: null,
    });

    this.form.controls.latitud.markAsUntouched();
    this.form.controls.longitud.markAsUntouched();
    this.apiErrors.clear();
  }

  ubicacionValidada(): boolean {
    const territorio = this.territorio();
    const values = this.form.getRawValue();

    return (
      territorio !== null &&
      !this.resolviendoUbicacion() &&
      this.errorUbicacion() === null &&
      territorio.latitud === values.latitud &&
      territorio.longitud === values.longitud &&
      territorio.candidatos.some(
        (candidato) =>
          candidato.departamento.id === values.departamentoId &&
          candidato.municipio.id === values.municipioId &&
          candidato.municipio.departamentoId === values.departamentoId,
      )
    );
  }

  mensajeCampo(campo: keyof typeof this.form.controls): string | null {
    const servidor = this.erroresServidor()[campo];
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

  enviarFormulario(): void {
    if (this.guardando()) return;

    this.form.markAllAsTouched();
    if (this.form.invalid || !this.ubicacionValidada()) return;

    const values = this.form.getRawValue();
    if (values.latitud === null || values.longitud === null) return;

    this.enviar.emit({
      nombre: values.nombre.trim(),
      departamentoId: values.departamentoId,
      municipioId: values.municipioId,
      ruta: values.ruta.trim(),
      kilometraje: values.kilometraje,
      latitud: values.latitud,
      longitud: values.longitud,
    });
  }

  private consultarSiCambioCoordenada(forzar = false): void {
    const { latitud, longitud } = this.form.getRawValue();
    const clave = `${latitud ?? ''}|${longitud ?? ''}`;

    if (!forzar && clave === this.ultimaCoordenada) return;
    this.ultimaCoordenada = clave;

    this.territorio.set(null);
    this.municipios.set([]);
    this.errorUbicacion.set(null);
    this.form.patchValue({ departamentoId: '', municipioId: '' }, { emitEvent: false });
    this.sincronizarControlesTerritoriales();

    if (latitud === null || longitud === null || !coordenadaValida(latitud, longitud)) {
      this.resolviendoUbicacion.set(false);
      this.consultasUbicacion.next(null);
      return;
    }

    this.resolviendoUbicacion.set(true);
    this.consultasUbicacion.next({ latitud, longitud });
  }

  private actualizarMunicipios(departamentoId: string): void {
    const opciones = (this.territorio()?.candidatos ?? [])
      .filter((candidato) => candidato.departamento.id === departamentoId)
      .map((candidato) => candidato.municipio);

    this.municipios.set(opciones);
    this.form.controls.municipioId.setValue(opciones.length === 1 ? opciones[0].id : '', {
      emitEvent: false,
    });
  }

  private sincronizarControlesTerritoriales(): void {
    const departamento = this.form.controls.departamentoId;
    const municipio = this.form.controls.municipioId;

    if (!this.guardando() && this.departamentos().length > 1) {
      departamento.enable({ emitEvent: false });
    } else {
      departamento.disable({ emitEvent: false });
    }

    if (!this.guardando() && this.municipios().length > 1) {
      municipio.enable({ emitEvent: false });
    } else {
      municipio.disable({ emitEvent: false });
    }
  }

  private detalleError(error: HttpErrorResponse, clavePredeterminada: string): string {
    const problem = error.error as PuenteProblemDetails | null;
    return problem?.detail ?? problem?.title ?? this.transloco.translate(clavePredeterminada);
  }
}
