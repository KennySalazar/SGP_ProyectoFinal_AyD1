import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnChanges,
  ViewChild,
  ViewEncapsulation,
  inject,
  input,
  signal,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { Map, Marker, Popup } from 'maplibre-gl';
// La configuración Angular limita los tipos globales; MapLibre requiere este namespace.
import type {} from 'geojson';
import { EstadoPuente, PuenteCatalogoResponse } from '../../../models/puente.models';

@Component({
  selector: 'app-mapa-puentes',
  imports: [TranslocoPipe],
  templateUrl: './mapa-puentes.component.html',
  styleUrl: './mapa-puentes.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // MapLibre inserta marcadores y popups fuera de las plantillas Angular.
  // Todos los selectores de este componente se limitan a .mapa-puentes.
  encapsulation: ViewEncapsulation.None,
})
export class MapaPuentesComponent implements AfterViewInit, OnChanges, OnDestroy {
  readonly puentes = input.required<PuenteCatalogoResponse[]>();
  readonly estados = input.required<EstadoPuente[]>();
  readonly errorMapa = signal(false);
  readonly errorTeselas = signal(false);
  readonly cantidadMarcadores = signal(0);
  readonly iconos: Record<EstadoPuente, string> = {
    Bueno: 'pi-check',
    Regular: 'pi-exclamation-triangle',
    Malo: 'pi-times',
    'Sin evaluar': 'pi-question',
  };
  private readonly traducciones = inject(TranslocoService);
  @ViewChild('contenedor', { static: true }) private contenedor!: ElementRef<HTMLDivElement>;
  private mapa?: Map;
  private libreria?: typeof import('maplibre-gl');
  private marcadores: Marker[] = [];
  private popups: Popup[] = [];
  private observador?: ResizeObserver;
  private destruido = false;

  async ngAfterViewInit(): Promise<void> {
    try {
      const libreria = await import('maplibre-gl');
      if (this.destruido) return;
      this.libreria = libreria;
      this.mapa = new libreria.Map({
        container: this.contenedor.nativeElement,
        center: [-90.3, 15.7],
        zoom: 6,
        attributionControl: { compact: false },
        style: {
          version: 8,
          sources: {
            osm: {
              type: 'raster',
              tileSize: 256,
              maxzoom: 19,
              tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
              attribution:
                '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors',
            },
          },
          layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
        },
      });
      this.mapa.addControl(new libreria.NavigationControl(), 'top-right');
      this.mapa.on('error', () => this.errorTeselas.set(true));
      this.mapa.on('load', () => this.mapa?.resize());
      this.observador = new ResizeObserver(() => this.mapa?.resize());
      this.observador.observe(this.contenedor.nativeElement);
      this.actualizarMarcadores();
      this.mapa.resize();
    } catch {
      if (!this.destruido) this.errorMapa.set(true);
    }
  }

  ngOnChanges(): void {
    this.actualizarMarcadores();
  }

  private actualizarMarcadores(): void {
    if (!this.mapa || !this.libreria) return;
    this.limpiarMarcadores();
    for (const puente of this.puentes()) {
      const { latitud, longitud } = puente;
      if (
        !puente.activo ||
        typeof latitud !== 'number' ||
        typeof longitud !== 'number' ||
        !Number.isFinite(latitud) ||
        !Number.isFinite(longitud) ||
        latitud < -90 ||
        latitud > 90 ||
        longitud < -180 ||
        longitud > 180
      )
        continue;

      const boton = document.createElement('button');
      boton.type = 'button';
      boton.className = 'mapa-marcador';
      boton.dataset['estado'] = puente.estadoActual;
      boton.title = `${puente.nombre} — ${puente.estadoActual}`;
      boton.setAttribute('aria-label', boton.title);
      // MapLibre abre el popup con keypress. Evitar que el botón produzca además
      // un clic nativo, que lo cerraría inmediatamente con Enter/Espacio.
      boton.addEventListener('keypress', (evento) => {
        if (evento.code === 'Enter' || evento.code === 'Space') evento.preventDefault();
      });
      const icono = document.createElement('i');
      icono.className = `pi ${this.iconos[puente.estadoActual]}`;
      icono.setAttribute('aria-hidden', 'true');
      boton.append(icono);
      const popup = new this.libreria.Popup({ offset: 24, maxWidth: '280px' }).setDOMContent(
        this.contenidoPopup(puente),
      );
      const marcador = new this.libreria.Marker({ element: boton })
        .setLngLat([longitud, latitud])
        .setPopup(popup)
        .addTo(this.mapa);
      this.marcadores.push(marcador);
      this.popups.push(popup);
    }
    this.cantidadMarcadores.set(this.marcadores.length);
  }

  private contenidoPopup(puente: PuenteCatalogoResponse): HTMLElement {
    const contenido = document.createElement('div');
    contenido.className = 'mapa-popup';
    const nombre = document.createElement('h3');
    nombre.textContent = puente.nombre;
    contenido.append(nombre);
    for (const [clave, valor] of [
      ['puentes.department', puente.departamento.nombre],
      ['puentes.municipality', puente.municipio.nombre],
      ['puentes.state', puente.estadoActual],
    ]) {
      const fila = document.createElement('p');
      fila.textContent = `${this.traducciones.translate(clave)}: ${valor}`;
      contenido.append(fila);
    }
    return contenido;
  }

  private limpiarMarcadores(): void {
    this.popups.forEach((popup) => popup.remove());
    this.marcadores.forEach((marcador) => marcador.remove());
    this.popups = [];
    this.marcadores = [];
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.observador?.disconnect();
    this.limpiarMarcadores();
    this.mapa?.remove();
    this.mapa = undefined;
  }
}
