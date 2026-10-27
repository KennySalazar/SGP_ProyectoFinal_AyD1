import { FormularioApiService } from '../../../services/formulario-api.service';
import { mensajeCalibracion } from '../../../utils/error-calibracion';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormRecord, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { finalize, switchMap, map } from 'rxjs';
import { EstadoActa } from '../../../models/calibracion-adicional.models';
import { VersionFormulario } from '../../../models/formulario.models';
import { CalibracionAdicionalApiService } from '../../../services/calibracion-adicional-api.service';
import {
  actualizarCampoCritico,
  CampoCritico,
  camposCriticos,
  reglasCriticas,
} from '../../../utils/reglas-criticas';

@Component({
  selector: 'app-calibracion-adicional',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe, ButtonModule],
  templateUrl: './calibracion-adicional.component.html',
  styleUrl: './calibracion-adicional.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalibracionAdicionalComponent {
  readonly version = input.required<VersionFormulario>();
  readonly bloqueado = input(false);
  readonly guardada = output<VersionFormulario>();
  readonly estado = output<{ pendiente: boolean; guardando: boolean }>();
  private readonly api = inject(CalibracionAdicionalApiService);
  private readonly formularioApi = inject(FormularioApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly traduccion = inject(TranslocoService);
  readonly form = new FormRecord<FormControl<string>>({});
  readonly vistas = signal<{ regla: Record<string, unknown>; campos: CampoCritico[] }[]>([]);
  readonly confirmadas = signal<Record<number, boolean>>({});
  readonly operando = signal(false);
  readonly cambio = signal(false);
  readonly acta = signal<EstadoActa | null>(null);
  readonly cargandoActa = signal(false);
  readonly archivo = signal<File | null>(null);
  readonly error = signal<string | null>(null);
  readonly errorActa = signal<string | null>(null);
  readonly mensaje = signal<string | null>(null);
  private firma = '';
  private idActa = '';

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.marcarCambio());
    effect(() => {
      const version = this.version();
      const reglas = reglasCriticas(version);
      const firma = version.id + JSON.stringify(reglas);
      if (firma !== this.firma) {
        this.firma = firma;
        for (const clave of Object.keys(this.form.controls))
          this.form.removeControl(clave, { emitEvent: false });
        const vistas = reglas.map((regla) => ({
          regla,
          campos: camposCriticos(regla),
        }));
        for (let i = 0; i < vistas.length; i++)
          for (let j = 0; j < vistas[i].campos.length; j++)
            this.form.addControl(
              this.clave(i, j),
              new FormControl(vistas[i].campos[j].valor, {
                nonNullable: true,
                validators: [Validators.required],
              }),
              { emitEvent: false },
            );
        this.vistas.set(vistas);
        this.confirmadas.set({});
        this.cambio.set(false);
        this.form.markAsPristine();
      }
      if (version.estado === 'PUBLICADA') this.form.disable({ emitEvent: false });
      else this.form.enable({ emitEvent: false });
      if (version.id !== this.idActa) {
        this.idActa = version.id;
        this.archivo.set(null);
        this.acta.set(null);
        this.consultarActa();
      }
    });
    effect(() =>
      this.estado.emit({
        pendiente: this.cambio(),
        guardando: this.operando(),
      }),
    );
  }
  clave(i: number, j: number): string {
    return `r${i}c${j}`;
  }
  marcarCambio(): void {
    this.cambio.set(true);
    this.mensaje.set(null);
  }
  confirmar(indice: number, evento: Event): void {
    this.confirmadas.update((actual) => ({
      ...actual,
      [indice]: (evento.target as HTMLInputElement).checked,
    }));
    this.marcarCambio();
  }
  etiqueta(campo: CampoCritico): string {
    return campo.ruta
      .map((clave) => this.traduccion.translate('formulario.adicional.campos.' + clave))
      .join(' / ');
  }
  puedeGuardar(): boolean {
    return (
      this.version().estado === 'BORRADOR' &&
      !this.bloqueado() &&
      !this.operando() &&
      this.form.valid &&
      this.vistas().length > 0 &&
      this.vistas().every((vista, i) => vista.regla['pendiente'] == null || this.confirmadas()[i])
    );
  }
  descartar(): void {
    if (this.operando()) return;
    for (let i = 0; i < this.vistas().length; i++)
      for (let j = 0; j < this.vistas()[i].campos.length; j++)
        this.form.controls[this.clave(i, j)].setValue(this.vistas()[i].campos[j].valor, {
          emitEvent: false,
        });
    this.confirmadas.set({});
    this.cambio.set(false);
    this.error.set(null);
  }
  guardar(): void {
    if (!this.puedeGuardar()) return;
    const versionId = this.version().id;
    const reglas = this.vistas().map((vista, i) => {
      const copia = structuredClone(vista.regla);
      vista.campos.forEach((campo, j) =>
        actualizarCampoCritico(copia, campo, this.form.controls[this.clave(i, j)].value),
      );
      if (this.confirmadas()[i]) copia['pendiente'] = null;
      return copia;
    });
    this.operando.set(true);
    this.error.set(null);
    this.mensaje.set(null);
    this.api
      .guardarReglas(versionId, reglas, this.version().version)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.operando.set(false)),
      )
      .subscribe({
        next: (version) => {
          if (this.version().id !== versionId) return;
          this.cambio.set(false);
          this.guardada.emit(version);
          this.mensaje.set(this.traduccion.translate('formulario.adicional.guardado'));
        },
        error: (err) => this.error.set(this.detalle(err)),
      });
  }
  consultarActa(): void {
    const id = this.version().id;
    this.cargandoActa.set(true);
    this.errorActa.set(null);
    this.api
      .consultarActa(id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (this.version().id === id) this.cargandoActa.set(false);
        }),
      )
      .subscribe({
        next: (acta) => {
          if (this.version().id === id) this.acta.set(acta);
        },
        error: (err) => {
          if (this.version().id === id) this.errorActa.set(this.detalle(err));
        },
      });
  }
  seleccionar(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;
    this.errorActa.set(null);
    this.archivo.set(null);
    if (
      archivo &&
      (archivo.size === 0 ||
        archivo.size > 20 * 1024 * 1024 ||
        !archivo.name.toLowerCase().endsWith('.pdf'))
    ) {
      this.errorActa.set(this.traduccion.translate('formulario.adicional.pdfInvalido'));
    } else this.archivo.set(archivo);
    input.value = '';
  }
  adjuntar(): void {
    const archivo = this.archivo();
    if (
      this.version().estado !== 'BORRADOR' ||
      !archivo ||
      this.operando() ||
      this.bloqueado() ||
      this.cambio()
    )
      return;
    const id = this.version().id;
    this.operando.set(true);
    this.errorActa.set(null);
    this.api
      .adjuntarActa(id, archivo, this.version().version)
      .pipe(
        switchMap((acta) =>
          this.formularioApi.obtenerPorId(id).pipe(map((version) => ({ acta, version }))),
        ),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.operando.set(false)),
      )
      .subscribe({
        next: ({ acta, version }) => {
          if (this.version().id !== id) return;
          this.guardada.emit(version);
          this.acta.set(acta);
          this.archivo.set(null);
        },
        error: (err) => this.errorActa.set(this.detalle(err)),
      });
  }
  descargar(): void {
    if (!this.acta()?.acta || this.operando() || this.bloqueado()) return;
    const id = this.version().id;
    const nombre = (this.acta()?.acta?.nombreOriginal ?? 'acta.pdf').replace(/[\\/]/g, '_');
    this.operando.set(true);
    this.errorActa.set(null);
    this.api
      .descargarActa(id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.operando.set(false)),
      )
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const enlace = document.createElement('a');
          enlace.href = url;
          enlace.download = nombre;
          enlace.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
        },
        error: async (err) => {
          if (err.error instanceof Blob) {
            try {
              const datos = JSON.parse(await err.error.text());
              this.errorActa.set(datos.detail ?? this.detalle(err));
              return;
            } catch {
              this.errorActa.set(this.detalle(err));
            }
          } else this.errorActa.set(this.detalle(err));
        },
      });
  }
  fechaLegible(fecha: string): string {
    const valor = new Date(fecha);
    if (!Number.isFinite(valor.getTime())) return fecha;
    return new Intl.DateTimeFormat('es-GT', {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'America/Guatemala',
    }).format(valor);
  }
  valoresLegibles(campo: CampoCritico, i: number, j: number): string[] {
    const texto = this.form.controls[this.clave(i, j)]?.value ?? campo.valor;
    return (campo.lista ? texto.split('\n') : [texto])
      .filter((valor) => valor.trim())
      .map((valor) => {
        const catalogo = this.version().reglasEvaluacionIc['elementos'];
        if (Array.isArray(catalogo)) {
          const elemento = catalogo.find((item) => item?.elemento_ref === valor.trim());
          if (typeof elemento?.nombre === 'string') return elemento.nombre;
        }
        return valor
          .trim()
          .replace(/^\//, '')
          .split('/')
          .map((parte) => {
            const texto = parte.replace(/_/g, ' ').toLowerCase();
            return texto.charAt(0).toUpperCase() + texto.slice(1);
          })
          .join(' / ');
      });
  }
  private detalle(error: { error?: { detail?: string } }): string {
    return mensajeCalibracion(error, (k) => this.traduccion.translate(k));
  }
}
