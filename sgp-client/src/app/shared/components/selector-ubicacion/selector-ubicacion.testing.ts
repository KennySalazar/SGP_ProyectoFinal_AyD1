import { Component, input, output } from '@angular/core';
import { CoordenadaGeografica, PuntoMapa } from '../../utils/ubicacion-guatemala';

@Component({ selector: 'app-selector-ubicacion', standalone: true, template: '' })
export class SelectorUbicacionStubComponent {
  readonly latitud = input<number | null>(null);
  readonly longitud = input<number | null>(null);
  readonly deshabilitado = input(false);
  readonly cercanos = input<readonly PuntoMapa[]>([]);

  readonly coordenadaSeleccionada = output<CoordenadaGeografica>();
}
