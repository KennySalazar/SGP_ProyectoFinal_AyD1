import { AnclajeObservacionesComponent } from '../observaciones/anclaje-observaciones.component';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { AbstractControl, FormArray, FormGroup } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { NodoArreglo, NodoCampo, NodoFormulario, NodoObjeto } from '../../motor/esquema/modelos';
import { FormularioConstruido } from '../../motor/formulario/constructor';
import { esOpcional, valorDelNodo } from '../../motor/formulario/valor';
import { hijosVisibles } from '../../motor/formulario/visibles';
import {
  CampoFormularioComponent,
  LONGITUD_TEXTO_LARGO,
} from '../campo-formulario/campo-formulario.component';
import { RenderizadorNodoDirective } from '../renderizador-nodo.directive';
import { RENDERIZADORES_NODO, RENDERIZADORES_PREDETERMINADOS } from '../renderizadores';

function esTextoLargo(nodo: NodoCampo): boolean {
  return nodo.tipo === 'texto' && (nodo.longitudMaxima ?? 0) >= LONGITUD_TEXTO_LARGO;
}

/** Dibuja un nodo del esquema: un campo, un grupo de campos o una lista de instancias repetibles. */
@Component({
  selector: 'app-nodo-formulario',
  standalone: true,
  imports: [
    AnclajeObservacionesComponent,
    TranslocoPipe,
    ButtonModule,
    CampoFormularioComponent,
    RenderizadorNodoDirective,
  ],
  templateUrl: './nodo-formulario.component.html',
  styleUrl: './nodo-formulario.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.ancho-completo]': 'anchoCompleto()' },
})
export class NodoFormularioComponent {
  /** Los que aporta quien usa el formulario tienen prioridad sobre los predeterminados. */
  private readonly renderizadores = [
    ...(inject(RENDERIZADORES_NODO, { optional: true }) ?? []),
    ...(inject(RENDERIZADORES_PREDETERMINADOS, { optional: true }) ?? []),
  ];

  readonly nodo = input.required<NodoFormulario>();
  readonly control = input.required<AbstractControl>();
  readonly construido = input.required<FormularioConstruido>();
  /** Profundidad dentro de la sección; el nivel 0 no repite el título que ya muestra la sección. */
  readonly nivel = input(0);
  readonly mostrarLeyenda = input(true);
  /** Dibuja los campos con el estilo de la grilla de daños (botones amplios, 44 px), como en un elemento. */
  readonly modoGrilla = input(false);

  /** Renderizador alterno registrado para este nodo, si lo hay (por ejemplo, la grilla de daños). */
  protected readonly renderizador = computed(() =>
    this.renderizadores.find((candidato) => candidato.coincide(this.nodo())),
  );
  protected readonly entradasRenderizador = computed(() => ({
    nodo: this.nodo(),
    control: this.control(),
    construido: this.construido(),
  }));

  protected readonly campo = computed(() => this.nodo() as NodoCampo);
  protected readonly objeto = computed(() => this.nodo() as NodoObjeto);
  protected readonly arreglo = computed(() => this.nodo() as NodoArreglo);

  /** Si la mayoría de sus hijos son grupos (o listas) se necesitan columnas más anchas que para campos. */
  protected readonly conGrupos = computed(() => {
    const nodo = this.nodo();
    if (nodo.clase !== 'objeto') return false;
    const grupos = nodo.hijos.filter((hijo) => hijo.clase !== 'campo').length;
    return grupos * 2 >= nodo.hijos.length;
  });

  /**
   * Lo que por su contenido necesita toda la fila: un grupo de varios grupos (para que estos se
   * repartan en columnas), una lista de instancias o un texto largo. Evita columnas a medio llenar.
   */
  protected readonly anchoCompleto = computed(() => {
    const nodo = this.nodo();
    if (this.nivel() === 0) return false;
    switch (nodo.clase) {
      case 'campo':
        return esTextoLargo(nodo);
      case 'arreglo':
        return true;
      case 'objeto':
        return (
          nodo.hijos.some((hijo) => hijo.clase === 'campo' && esTextoLargo(hijo)) ||
          (nodo.hijos.filter((hijo) => hijo.clase !== 'campo').length >= 2 && this.conGrupos())
        );
    }
  });

  /** Objeto que puede omitirse: se presenta plegado hasta que se empieza a llenar. */
  protected readonly esOpcional = computed(() => {
    const nodo = this.nodo();
    return nodo.clase === 'objeto' && this.nivel() > 0 && esOpcional(nodo);
  });
  protected readonly empezado = computed(() => {
    this.construido().revision();
    const nodo = this.nodo();
    return (
      nodo.clase === 'objeto' &&
      valorDelNodo(nodo, this.control(), this.construido().ocultos) !== undefined
    );
  });

  /** Hijos que se muestran: los que no están ocultos por una condición. */
  protected readonly hijosVisibles = computed(() => {
    this.construido().revision();
    const nodo = this.nodo();
    if (nodo.clase !== 'objeto') return [];
    return hijosVisibles(nodo, this.control() as FormGroup, this.construido().ocultos);
  });

  protected readonly instancias = computed(() => {
    this.construido().revision();
    return this.nodo().clase === 'arreglo' ? [...(this.control() as FormArray).controls] : [];
  });

  protected readonly puedeAgregar = computed(() => {
    this.construido().revision();
    const lista = this.control() as FormArray;
    const maximo = this.arreglo().maximoElementos;
    return lista.enabled && (maximo === undefined || lista.length < maximo);
  });

  protected readonly puedeQuitar = computed(() => {
    this.construido().revision();
    const lista = this.control() as FormArray;
    return lista.enabled && lista.length > this.arreglo().minimoElementos;
  });

  /** Un bloque opcional ya empezado se puede descartar entero, sin vaciar campo por campo. */
  protected readonly puedeLimpiar = computed(() => {
    this.construido().revision();
    return this.empezado() && this.control().enabled;
  });

  protected limpiar(): void {
    this.construido().limpiar(this.objeto(), this.control() as FormGroup);
  }

  protected controlDe(hijo: NodoFormulario): AbstractControl {
    return (this.control() as FormGroup).controls[hijo.clave];
  }

  protected agregar(): void {
    this.construido().agregarInstancia(this.arreglo(), this.control() as FormArray);
  }

  protected quitar(indice: number): void {
    this.construido().quitarInstancia(this.control() as FormArray, indice);
  }
}
