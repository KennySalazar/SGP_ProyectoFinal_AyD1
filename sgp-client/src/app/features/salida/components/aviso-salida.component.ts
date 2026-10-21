import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PreparacionSalidaService } from '../services/preparacion-salida.service';
@Component({
  selector: 'app-aviso-salida',
  standalone: true,
  imports: [RouterLink],
  template: `@if (salida.permitida() && salida.nuevaVersion()) {
    <p role="status">
      <a routerLink="/preparar-salida"
        >Hay una version nueva del formulario; vuelve a preparar tu salida</a
      >
    </p>
  }`,
  styles: `
    a {
      display: flex;
      align-items: center;
      min-height: 44px;
      padding: 0.7rem 1rem;
      color: #674400;
      background: #fff5db;
      border-radius: 8px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvisoSalidaComponent {
  readonly salida = inject(PreparacionSalidaService);
}
