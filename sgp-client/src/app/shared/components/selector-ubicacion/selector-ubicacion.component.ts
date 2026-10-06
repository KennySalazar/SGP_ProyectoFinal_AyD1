import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  ViewChild,
  signal,
} from '@angular/core';
import type { Map as Mapa, Marker, Popup } from 'maplibre-gl';
import { cargarEstilosMaplibre, resolverMaplibre } from '../../utils/maplibre';
import { CoordenadaGeografica, PuntoMapa, coordenadaValida } from '../../utils/ubicacion-guatemala';

// Seis decimales equivalen a ~11 cm: suficiente para ubicar un puente y evita campos de 15 dígitos.
const FACTOR_DECIMALES = 1e6;

function redondear(valor: number): number {
  return Math.round(valor * FACTOR_DECIMALES) / FACTOR_DECIMALES;
}

@Component({
  selector: 'app-selector-ubicacion',
  standalone: true,
  templateUrl: './selector-ubicacion.component.html',
  styleUrl: './selector-ubicacion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectorUbicacionComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('contenedor', { static: true })
  private contenedor!: ElementRef<HTMLDivElement>;

  @Input() latitud: number | null = null;
  @Input() longitud: number | null = null;
  @Input() deshabilitado = false;
  @Input() cercanos: readonly PuntoMapa[] = [];

  @Output() readonly coordenadaSeleccionada = new EventEmitter<CoordenadaGeografica>();

  readonly cargando = signal(true);
  readonly errorMapa = signal(false);

  private libreria: typeof import('maplibre-gl') | null = null;
  private mapa: Mapa | null = null;
  private marcador: Marker | null = null;
  private marcadoresCercanos: Marker[] = [];
  private observador: ResizeObserver | null = null;
  private destruido = false;

  ngAfterViewInit(): void {
    void this.inicializar();
  }

  ngOnChanges(): void {
    this.sincronizarMarcador();
    this.sincronizarCercanos();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.observador?.disconnect();
    this.marcador?.remove();
    this.limpiarCercanos();
    this.mapa?.remove();
    this.mapa = null;
  }

  private async inicializar(): Promise<void> {
    try {
      // Carga MapLibre al abrir el selector.
      const libreria = resolverMaplibre(await import('maplibre-gl'));

      if (this.destruido) return;

      cargarEstilosMaplibre(libreria.getVersion(), () => this.mapa?.resize());

      this.libreria = libreria;
      this.mapa = new libreria.Map({
        container: this.contenedor.nativeElement,
        center: [-90.3, 15.5],
        zoom: 6,
        renderWorldCopies: false,
        style: {
          version: 8,
          sources: {
            osm: {
              type: 'raster',
              tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
              tileSize: 256,
              attribution:
                '© <a href="https://www.openstreetmap.org/copyright">' +
                'OpenStreetMap</a> contributors',
            },
          },
          layers: [
            {
              id: 'osm',
              type: 'raster',
              source: 'osm',
            },
          ],
        },
      });

      this.mapa.addControl(new libreria.NavigationControl({ showCompass: false }), 'top-right');

      this.mapa.on('load', () => {
        if (!this.destruido) this.cargando.set(false);
      });

      this.mapa.on('error', () => {
        if (!this.destruido) {
          this.cargando.set(false);
          this.errorMapa.set(true);
        }
      });

      this.mapa.on('click', (evento) => {
        this.seleccionar(evento.lngLat.lat, evento.lngLat.lng);
      });

      this.observador = new ResizeObserver(() => this.mapa?.resize());
      this.observador.observe(this.contenedor.nativeElement);

      this.sincronizarMarcador();
      this.sincronizarCercanos();
    } catch {
      if (!this.destruido) {
        this.cargando.set(false);
        this.errorMapa.set(true);
      }
    }
  }

  private seleccionar(latitud: number, longitud: number): void {
    if (this.deshabilitado) return;

    // Solo normaliza si el mapa devolvió una longitud fuera de rango; la fórmula agrega ruido
    // de punto flotante (-90.7 pasaría a -90.69999999999999).
    const longitudNormalizada =
      longitud >= -180 && longitud <= 180
        ? longitud
        : ((((longitud + 180) % 360) + 360) % 360) - 180;

    this.coordenadaSeleccionada.emit({
      latitud: redondear(latitud),
      longitud: redondear(longitudNormalizada),
    });
  }

  private sincronizarMarcador(): void {
    const mapa = this.mapa;
    const libreria = this.libreria;

    if (!mapa || !libreria) return;

    if (!coordenadaValida(this.latitud, this.longitud)) {
      this.marcador?.remove();
      this.marcador = null;
      return;
    }

    const posicion: [number, number] = [this.longitud as number, this.latitud as number];

    if (!this.marcador) {
      const marcador = new libreria.Marker({
        color: '#2563eb',
        draggable: !this.deshabilitado,
      })
        .setLngLat(posicion)
        .addTo(mapa);

      marcador.getElement().setAttribute('aria-label', 'Ubicación seleccionada');

      marcador.on('dragend', () => {
        const punto = marcador.getLngLat();
        this.seleccionar(punto.lat, punto.lng);
      });

      this.marcador = marcador;
    } else {
      this.marcador.setLngLat(posicion).setDraggable(!this.deshabilitado);
    }

    // También centra el mapa cuando se pegan coordenadas de un GPS.
    if (!mapa.getBounds().contains(posicion)) {
      mapa.jumpTo({ center: posicion });
    }
  }

  private sincronizarCercanos(): void {
    this.limpiarCercanos();

    const mapa = this.mapa;
    const libreria = this.libreria;

    if (!mapa || !libreria) return;

    for (const cercano of this.cercanos) {
      if (!coordenadaValida(cercano.latitud, cercano.longitud)) continue;

      const popup: Popup = new libreria.Popup({ offset: 25 }).setText(cercano.titulo);

      const marcador = new libreria.Marker({ color: '#d97706' })
        .setLngLat([cercano.longitud, cercano.latitud])
        .setPopup(popup)
        .addTo(mapa);

      marcador.getElement().setAttribute('aria-label', cercano.titulo);
      this.marcadoresCercanos.push(marcador);
    }
  }

  private limpiarCercanos(): void {
    for (const marcador of this.marcadoresCercanos) {
      marcador.remove();
    }

    this.marcadoresCercanos = [];
  }
}
