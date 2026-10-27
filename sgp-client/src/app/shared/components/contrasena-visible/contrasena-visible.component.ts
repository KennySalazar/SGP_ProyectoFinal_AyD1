import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Envuelve un campo de contraseña y le suma un botón con un ojo para mostrarla u ocultarla. El campo
 * sigue siendo del formulario de quien lo usa (valor, validación y autocompletado no cambian): aquí
 * solo se alterna su `type` entre `password` y `text`. Empieza siempre oculta.
 */
@Component({
  selector: 'app-contrasena-visible',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-content />
    <button
      type="button"
      class="ojo"
      [attr.aria-label]="(visible() ? 'common.hidePassword' : 'common.showPassword') | transloco"
      [attr.title]="(visible() ? 'common.hidePassword' : 'common.showPassword') | transloco"
      (click)="alternar()"
    >
      <i
        class="pi"
        [class.pi-eye]="!visible()"
        [class.pi-eye-slash]="visible()"
        aria-hidden="true"
      ></i>
    </button>
  `,
  styles: `
    :host {
      position: relative;
      display: block;
    }
    /* Deja libre el lado del botón para que el texto no quede debajo del ojo. */
    :host ::ng-deep input {
      padding-right: 3.1rem;
    }
    .ojo {
      position: absolute;
      top: 50%;
      right: 0.25rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      border: 0;
      border-radius: 10px;
      background: transparent;
      color: #8092a5;
      font-size: 1rem;
      cursor: pointer;
      transform: translateY(-50%);
    }
    .ojo:hover {
      color: var(--sgp-navy-900);
    }
    .ojo:focus-visible {
      outline: 3px solid var(--sgp-blue-600);
      outline-offset: -3px;
    }
  `,
})
export class ContrasenaVisibleComponent {
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly visible = signal(false);

  alternar(): void {
    const campo = this.anfitrion.nativeElement.querySelector('input');
    if (!campo) return;
    const visible = !this.visible();
    campo.type = visible ? 'text' : 'password';
    this.visible.set(visible);
  }
}
