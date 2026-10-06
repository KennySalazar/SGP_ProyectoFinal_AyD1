import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CoordenadaGeografica, PuntoMapa } from '../../utils/ubicacion-guatemala';
import { SelectorUbicacionComponent } from './selector-ubicacion.component';

interface PosicionMapa {
  lat: number;
  lng: number;
}

type ManejadorMapa = (evento: { lngLat: PosicionMapa }) => void;

const fake = vi.hoisted(() => {
  const estado = {
    mapas: [] as FakeMapa[],
    marcadores: [] as FakeMarcador[],
    popups: [] as FakePopup[],
    fallar: false,
  };

  class FakeMapa {
    readonly manejadores: Record<string, ManejadorMapa> = {};
    readonly limites = { contains: vi.fn(() => true) };
    readonly remove = vi.fn();
    readonly resize = vi.fn();
    readonly jumpTo = vi.fn();
    readonly addControl = vi.fn();

    constructor(readonly opciones: Record<string, unknown>) {
      if (estado.fallar) throw new Error('WebGL no disponible');
      estado.mapas.push(this);
    }

    on(evento: string, manejador: ManejadorMapa): void {
      this.manejadores[evento] = manejador;
    }

    getBounds() {
      return this.limites;
    }
  }

  class FakePopup {
    texto = '';

    constructor() {
      estado.popups.push(this);
    }

    setText(texto: string): this {
      this.texto = texto;
      return this;
    }
  }

  class FakeMarcador {
    posicion: [number, number] = [0, 0];
    agregado = false;
    arrastrable: boolean | undefined;
    popup: FakePopup | null = null;
    readonly elemento = document.createElement('div');
    readonly manejadores: Record<string, () => void> = {};
    readonly remove = vi.fn(() => {
      this.agregado = false;
    });

    constructor(readonly opciones: { color: string; draggable?: boolean }) {
      this.arrastrable = opciones.draggable;
      estado.marcadores.push(this);
    }

    setLngLat(posicion: [number, number]): this {
      this.posicion = posicion;
      return this;
    }

    addTo(): this {
      this.agregado = true;
      return this;
    }

    setPopup(popup: FakePopup): this {
      this.popup = popup;
      return this;
    }

    setDraggable(valor: boolean): this {
      this.arrastrable = valor;
      return this;
    }

    getElement(): HTMLElement {
      return this.elemento;
    }

    on(evento: string, manejador: () => void): void {
      this.manejadores[evento] = manejador;
    }

    getLngLat(): PosicionMapa {
      return { lng: this.posicion[0], lat: this.posicion[1] };
    }
  }

  class FakeNavegacion {}

  return { estado, FakeMapa, FakeMarcador, FakePopup, FakeNavegacion };
});

// Forma que entrega el build de producción: la librería UMD solo como `default`.
// `Map: undefined` evita que vitest falle al consultar un export que no existe en el mock.
vi.mock('maplibre-gl', () => ({
  Map: undefined,
  default: {
    Map: fake.FakeMapa,
    Marker: fake.FakeMarcador,
    Popup: fake.FakePopup,
    NavigationControl: fake.FakeNavegacion,
    getVersion: () => '9.9.9',
  },
}));

describe('SelectorUbicacionComponent: selector de ubicación en el mapa', () => {
  let fixture: ComponentFixture<SelectorUbicacionComponent>;
  let seleccionadas: CoordenadaGeografica[];

  async function crear(
    entradas: {
      latitud?: number | null;
      longitud?: number | null;
      deshabilitado?: boolean;
      cercanos?: readonly PuntoMapa[];
    } = {},
  ): Promise<void> {
    fixture = TestBed.createComponent(SelectorUbicacionComponent);
    seleccionadas = [];
    fixture.componentInstance.coordenadaSeleccionada.subscribe((c) => seleccionadas.push(c));

    for (const [nombre, valor] of Object.entries(entradas)) {
      fixture.componentRef.setInput(nombre, valor);
    }

    fixture.detectChanges();
    await vi.waitFor(() => expect(fake.estado.mapas).toHaveLength(1));
    fixture.detectChanges();
  }

  const mapa = () => fake.estado.mapas[0];
  const marcador = () => fake.estado.marcadores[0];

  beforeEach(() => {
    fake.estado.mapas.length = 0;
    fake.estado.marcadores.length = 0;
    fake.estado.popups.length = 0;
    fake.estado.fallar = false;

    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        disconnect = vi.fn();
      },
    );
  });

  afterEach(() => {
    fixture.destroy();
    vi.unstubAllGlobals();
    document.querySelector('link[data-maplibre-estilos]')?.remove();
  });

  it('inicia el mapa con la librería tal como la entrega el build de producción', async () => {
    await crear();

    expect(mapa().opciones['center']).toEqual([-90.3, 15.5]);
    expect(fixture.componentInstance.errorMapa()).toBe(false);
    expect(mapa().addControl).toHaveBeenCalled();
  });

  it('carga la hoja de estilos del mapa con la versión de la librería', async () => {
    await crear();

    expect(document.querySelector('link[data-maplibre-estilos]')?.getAttribute('href')).toContain(
      'maplibre.css?v=9.9.9',
    );
  });

  it('muestra el estado de carga hasta que el mapa termina de cargar', async () => {
    await crear();
    expect(fixture.componentInstance.cargando()).toBe(true);

    mapa().manejadores['load']({ lngLat: { lat: 0, lng: 0 } });

    expect(fixture.componentInstance.cargando()).toBe(false);
  });

  it('emite la coordenada seleccionada con un clic, con seis decimales', async () => {
    await crear();

    mapa().manejadores['click']({ lngLat: { lat: 14.4812345678, lng: -90.6154321987 } });

    expect(seleccionadas).toEqual([{ latitud: 14.481235, longitud: -90.615432 }]);
  });

  it('normaliza la longitud solo cuando el mapa la devuelve fuera de rango', async () => {
    await crear();

    mapa().manejadores['click']({ lngLat: { lat: 14.5, lng: 269.5 } });
    mapa().manejadores['click']({ lngLat: { lat: 14.5, lng: -89.425548 } });

    expect(seleccionadas).toEqual([
      { latitud: 14.5, longitud: -90.5 },
      { latitud: 14.5, longitud: -89.425548 },
    ]);
  });

  it('ignora los clics cuando está deshabilitado', async () => {
    await crear({ deshabilitado: true });

    mapa().manejadores['click']({ lngLat: { lat: 14.5, lng: -90.5 } });

    expect(seleccionadas).toEqual([]);
  });

  it('muestra el marcador de la coordenada y lo quita si deja de ser válida', async () => {
    await crear({ latitud: 14.481, longitud: -90.615 });

    expect(marcador().agregado).toBe(true);
    expect(marcador().posicion).toEqual([-90.615, 14.481]);
    expect(marcador().arrastrable).toBe(true);

    fixture.componentRef.setInput('latitud', 95);
    fixture.detectChanges();

    expect(marcador().remove).toHaveBeenCalled();
  });

  it('emite la nueva coordenada al arrastrar el marcador', async () => {
    await crear({ latitud: 14.481, longitud: -90.615 });

    marcador().posicion = [-90.7, 14.5];
    marcador().manejadores['dragend']();

    expect(seleccionadas).toEqual([{ latitud: 14.5, longitud: -90.7 }]);
  });

  it('bloquea el arrastre del marcador cuando se deshabilita', async () => {
    await crear({ latitud: 14.481, longitud: -90.615 });

    fixture.componentRef.setInput('deshabilitado', true);
    fixture.detectChanges();

    expect(marcador().arrastrable).toBe(false);
  });

  it('centra el mapa cuando la coordenada queda fuera de la vista', async () => {
    await crear({ latitud: 14.481, longitud: -90.615 });
    mapa().limites.contains.mockReturnValue(false);

    fixture.componentRef.setInput('latitud', 15.1);
    fixture.componentRef.setInput('longitud', -91.2);
    fixture.detectChanges();

    expect(mapa().jumpTo).toHaveBeenCalledWith({ center: [-91.2, 15.1] });
  });

  it('muestra los puentes cercanos con su identificación', async () => {
    await crear({
      cercanos: [
        { id: 'a', titulo: 'GT-01-0114-0001 — Puente A', latitud: 14.48, longitud: -90.61 },
        { id: 'b', titulo: 'Inválido', latitud: 95, longitud: -90.61 },
      ],
    });

    expect(fake.estado.marcadores).toHaveLength(1);
    expect(marcador().popup?.texto).toBe('GT-01-0114-0001 — Puente A');
    expect(marcador().elemento.getAttribute('aria-label')).toBe('GT-01-0114-0001 — Puente A');
  });

  it('muestra un aviso y no falla cuando el mapa no se puede crear', async () => {
    fake.estado.fallar = true;
    fixture = TestBed.createComponent(SelectorUbicacionComponent);
    fixture.detectChanges();

    await vi.waitFor(() => expect(fixture.componentInstance.errorMapa()).toBe(true));
    fixture.detectChanges();

    expect(fixture.componentInstance.cargando()).toBe(false);
    expect(fixture.nativeElement.querySelector('.error')).not.toBeNull();
  });

  it('libera el mapa y los marcadores al destruirse', async () => {
    await crear({ latitud: 14.481, longitud: -90.615 });

    fixture.destroy();

    expect(mapa().remove).toHaveBeenCalled();
    expect(marcador().remove).toHaveBeenCalled();
  });
});
