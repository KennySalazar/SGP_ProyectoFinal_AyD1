import {
  ComponentRef,
  Directive,
  effect,
  inject,
  input,
  Type,
  untracked,
  ViewContainerRef,
} from '@angular/core';

/**
 * Crea el componente de un renderizador alterno y le pasa sus entradas (`nodo`, `control` y
 * `construido`). Reemplaza a `NgComponentOutlet`, que sumaría ~2 kB al bundle inicial porque su
 * módulo se comparte con el código que ya se carga al arrancar.
 */
@Directive({ selector: '[appRenderizadorNodo]', standalone: true })
export class RenderizadorNodoDirective {
  private readonly contenedor = inject(ViewContainerRef);
  private referencia: ComponentRef<unknown> | null = null;
  private tipoActual: Type<unknown> | null = null;

  readonly appRenderizadorNodo = input.required<Type<unknown>>();
  readonly appRenderizadorNodoEntradas = input.required<Record<string, unknown>>();

  constructor() {
    effect(() => {
      const tipo = this.appRenderizadorNodo();
      const entradas = this.appRenderizadorNodoEntradas();

      untracked(() => {
        if (this.referencia === null || this.tipoActual !== tipo) {
          this.referencia?.destroy();
          this.referencia = this.contenedor.createComponent(tipo);
          this.tipoActual = tipo;
        }
        for (const [nombre, valor] of Object.entries(entradas)) {
          this.referencia.setInput(nombre, valor);
        }
      });
    });
  }
}
