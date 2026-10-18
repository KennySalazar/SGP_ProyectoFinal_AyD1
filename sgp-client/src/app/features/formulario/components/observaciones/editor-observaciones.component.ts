import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { ContextoEditorObservacion, PreparacionObservacion } from '../../models/observacion.models';
import { crearAnclajeFormulario } from '../../utils/anclaje-formulario';

let secuenciaEditor = 0;

@Component({
  selector: 'app-editor-observaciones',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe, ButtonModule, TextareaModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="surface-card editor" [attr.aria-labelledby]="id + '-titulo'">
      <h3 [id]="id + '-titulo'">
        {{
          (contexto().modo === 'EDITAR' ? 'observaciones.editar' : 'observaciones.crear')
            | transloco
        }}
      </h3>
      @let seleccionado = destino();
      <p>
        {{
          seleccionado.tipo === 'ELEMENTO'
            ? seleccionado.etiqueta
            : ('observaciones.general' | transloco)
        }}
      </p>
      <form [formGroup]="formulario" (ngSubmit)="prepararGuardado()" novalidate>
        <label>
          <span>{{ 'observaciones.texto' | transloco }}</span>
          <textarea
            pTextarea
            [formControl]="texto"
            [maxlength]="2000"
            rows="5"
            required
            [attr.aria-invalid]="texto.invalid"
            [attr.aria-describedby]="id + '-contador' + (mostrarError() ? ' ' + id + '-error' : '')"
          ></textarea>
        </label>
        <p [id]="id + '-contador'" class="contador" aria-live="polite">
          {{
            'observaciones.caracteres' | transloco: { cantidad: contenido().length, maximo: 2000 }
          }}
        </p>
        @if (mostrarError()) {
          <p [id]="id + '-error'" class="validation" role="alert">
            {{
              (texto.hasError('required')
                ? 'observaciones.textoObligatorio'
                : 'observaciones.textoLargo'
              ) | transloco
            }}
          </p>
        }
        <div class="acciones">
          <button pButton type="submit" [disabled]="texto.invalid">
            {{ 'observaciones.guardar' | transloco }}
          </button>
          <button
            pButton
            type="button"
            severity="secondary"
            [outlined]="true"
            (click)="cancelar.emit()"
          >
            {{ 'observaciones.cancelar' | transloco }}
          </button>
        </div>
      </form>
    </section>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .editor {
      padding: 1rem;
      color: var(--sgp-navy-900);
      box-sizing: border-box;
      max-width: 100%;
    }
    h3 {
      margin-top: 0;
    }
    form,
    label {
      display: grid;
      gap: 0.5rem;
      min-width: 0;
    }
    textarea {
      width: 100%;
      max-width: 100%;
      min-width: 44px;
      min-height: 120px;
      box-sizing: border-box;
      resize: vertical;
    }
    textarea[aria-invalid='true'] {
      border-color: #b91c1c;
    }
    .contador {
      margin: 0;
      color: var(--sgp-steel-700);
    }
    .validation {
      color: #b91c1c;
    }
    .acciones {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    button {
      min-width: 44px;
      min-height: 44px;
      max-width: 100%;
      white-space: normal;
    }
    button:focus-visible,
    textarea:focus-visible {
      outline: 3px solid var(--sgp-blue-600);
      outline-offset: 2px;
    }
    p {
      overflow-wrap: anywhere;
    }
  `,
})
export class EditorObservacionesComponent {
  readonly contexto = input.required<ContextoEditorObservacion>();
  readonly guardar = output<PreparacionObservacion>();
  readonly cancelar = output<void>();
  protected readonly id = `editor-observacion-${++secuenciaEditor}`;
  readonly texto = new FormControl('', {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.maxLength(2000),
      (control) =>
        typeof control.value === 'string' && !control.value.trim() ? { required: true } : null,
    ],
  });
  readonly formulario = new FormGroup({ texto: this.texto });
  protected readonly contenido = toSignal(this.texto.valueChanges, { initialValue: '' });
  protected readonly destino = computed(() => {
    const contexto = this.contexto();
    return contexto.modo === 'CREAR' ? contexto.destino : contexto.observacion.destino;
  });

  constructor() {
    effect(() => {
      const contexto = this.contexto();
      untracked(() =>
        this.texto.reset(contexto.modo === 'EDITAR' ? contexto.observacion.texto : ''),
      );
    });
  }

  protected mostrarError(): boolean {
    return this.texto.invalid && (this.texto.touched || this.texto.dirty);
  }

  prepararGuardado(): void {
    this.texto.markAsTouched();
    if (this.texto.invalid) return;
    const contexto = this.contexto();
    const seleccionado = this.destino();
    const destino =
      seleccionado.tipo === 'GENERAL'
        ? seleccionado
        : {
            ...seleccionado,
            anclaje: crearAnclajeFormulario(
              seleccionado.anclaje.elementoRef,
              seleccionado.anclaje.instanciaElementoId,
            ),
          };
    const contenido = { destino, texto: this.texto.value };
    this.guardar.emit(
      contexto.modo === 'CREAR'
        ? { ...contenido, modo: 'CREAR' }
        : {
            ...contenido,
            modo: 'EDITAR',
            id: contexto.observacion.id,
          },
    );
  }
}
