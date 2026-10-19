import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormRecord, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../../core/services/api-error.service';
import { VersionFormulario } from '../../../models/formulario.models';
import { FormularioApiService } from '../../../services/formulario-api.service';
import {
  aplicarReglasIc,
  CampoReglaIc,
  camposReglasIc,
  erroresReglasIc,
  ValoresReglasIc,
  valoresReglasIc,
} from '../../../utils/reglas-ic';

@Component({
  selector: 'app-reglas-ic',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    SelectModule,
  ],
  templateUrl: './reglas-ic.component.html',
  styleUrl: './reglas-ic.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReglasIcComponent {
  readonly version = input.required<VersionFormulario>();
  readonly bloqueado = input(false);
  readonly guardada = output<VersionFormulario>();
  readonly estado = output<{ pendiente: boolean; guardando: boolean }>();
  private readonly api = inject(FormularioApiService);
  private readonly apiError = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  readonly guardando = signal(false);
  readonly exito = signal(false);
  readonly errores = signal<string[]>([]);
  readonly grupos = signal<{ clave: string; campos: CampoReglaIc[] }[]>([]);
  formulario = new FormRecord<FormControl<string | number | null>>({});
  private firma = '';
  private iniciales: ValoresReglasIc = {};

  constructor() {
    this.formulario.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.registrarCambios());
    effect(() => {
      const version = this.version();
      const firma = JSON.stringify([version.id, version.reglasEvaluacionIc]);
      untracked(() => {
        if (firma !== this.firma) {
          this.firma = firma;
          this.cargar(version);
        }
        this.sincronizarBloqueo();
      });
    });
    effect(() => {
      this.bloqueado();
      this.guardando();
      untracked(() => this.sincronizarBloqueo());
    });
  }

  private cargar(version: VersionFormulario): void {
    const campos = camposReglasIc(version);
    this.iniciales = valoresReglasIc(version);
    for (const clave of Object.keys(this.formulario.controls))
      this.formulario.removeControl(clave, { emitEvent: false });
    for (const campo of campos) {
      const validadores = [Validators.required];
      if (campo.tipo === 'texto')
        validadores.push(Validators.maxLength(50), Validators.pattern(/\S/));
      else if (campo.tipo === 'agregacion')
        validadores.push(Validators.pattern(/^(MAXIMO|PROMEDIO)$/));
      else {
        validadores.push(Validators.min(0));
        if (campo.maximo !== undefined) validadores.push(Validators.max(campo.maximo));
      }
      this.formulario.addControl(
        campo.clave,
        new FormControl(
          version.estado === 'BORRADOR' && campo.valorFijo !== undefined
            ? campo.valorFijo
            : this.iniciales[campo.clave],
          validadores,
        ),
        { emitEvent: false },
      );
    }
    this.formulario.setValidators((control) =>
      erroresReglasIc(campos, control.getRawValue()).length ? { reglasInvalidas: true } : null,
    );
    this.formulario.updateValueAndValidity({ emitEvent: false });
    this.grupos.set(
      [...new Set(campos.map((c) => c.grupo))].map((clave) => ({
        clave,
        campos: campos.filter((c) => c.grupo === clave),
      })),
    );
    this.exito.set(false);
    this.actualizarEstado();
  }

  private sincronizarBloqueo(): void {
    if (this.version().estado !== 'BORRADOR' || this.bloqueado() || this.guardando())
      this.formulario.disable({ emitEvent: false });
    else this.formulario.enable({ emitEvent: false });
  }

  actualizarEstado(): void {
    const valores = this.formulario.getRawValue();
    this.errores.set(
      this.version().estado === 'BORRADOR'
        ? erroresReglasIc(camposReglasIc(this.version()), valores)
        : [],
    );
    this.estado.emit({
      pendiente: JSON.stringify(valores) !== JSON.stringify(this.iniciales),
      guardando: this.guardando(),
    });
  }

  registrarCambios(): void {
    this.exito.set(false);
    this.actualizarEstado();
  }

  restablecer(): void {
    if (this.guardando() || this.bloqueado()) return;
    this.cargar(this.version());
    this.sincronizarBloqueo();
  }

  guardar(): void {
    const version = this.version();
    if (
      version.estado !== 'BORRADOR' ||
      this.bloqueado() ||
      this.guardando() ||
      this.formulario.invalid
    )
      return;
    this.formulario.markAllAsTouched();
    this.actualizarEstado();
    if (this.errores().length) return;
    this.guardando.set(true);
    this.actualizarEstado();
    this.api
      .actualizarBorrador(version.id, {
        esquema: version.esquema,
        reglasEvaluacionIc: aplicarReglasIc(version, this.formulario.getRawValue()),
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.guardando.set(false);
          this.actualizarEstado();
        }),
      )
      .subscribe({
        next: (actualizada) => {
          this.firma = JSON.stringify([actualizada.id, actualizada.reglasEvaluacionIc]);
          this.cargar(actualizada);
          this.exito.set(true);
          this.guardada.emit(actualizada);
        },
        error: (error) => this.apiError.normalize(error),
      });
  }
}
