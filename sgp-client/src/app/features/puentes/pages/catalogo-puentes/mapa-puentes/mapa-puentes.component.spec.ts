import { TestBed } from '@angular/core/testing';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Pipe, PipeTransform } from '@angular/core';
import { MapaPuentesComponent } from './mapa-puentes.component';
import { EstadoPuente, PuenteCatalogoResponse } from '../../../models/puente.models';

const mocks = vi.hoisted(() => ({
  mapas: [] as { remove: ReturnType<typeof vi.fn>; resize: ReturnType<typeof vi.fn> }[],
  marcadores: [] as {
    elemento: HTMLButtonElement;
    remove: ReturnType<typeof vi.fn>;
    setLngLat: ReturnType<typeof vi.fn>;
  }[],
  popups: [] as { contenido: HTMLElement; remove: ReturnType<typeof vi.fn> }[],
  opciones: vi.fn(),
  disconnect: vi.fn(),
}));

vi.mock('maplibre-gl', () => ({
  Map: class {
    remove = vi.fn();
    resize = vi.fn();
    addControl = vi.fn();
    on = vi.fn();
    constructor(opciones: unknown) {
      mocks.opciones(opciones);
      mocks.mapas.push(this);
    }
  },
  NavigationControl: class {},
  Marker: class {
    elemento: HTMLButtonElement;
    remove = vi.fn();
    setLngLat = vi.fn(() => this);
    setPopup = vi.fn(() => this);
    constructor({ element }: { element: HTMLButtonElement }) {
      this.elemento = element;
      mocks.marcadores.push(this);
    }
    addTo() {
      return this;
    }
  },
  Popup: class {
    contenido!: HTMLElement;
    remove = vi.fn();
    constructor() {
      mocks.popups.push(this);
    }
    setDOMContent(contenido: HTMLElement) {
      this.contenido = contenido;
      return this;
    }
  },
}));

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Mapa público de puentes', () => {
  const estados: EstadoPuente[] = ['Bueno', 'Regular', 'Malo', 'Sin evaluar'];
  function puente(estadoActual: EstadoPuente = 'Bueno'): PuenteCatalogoResponse {
    return {
      id: estadoActual,
      codigo: 'GT-01-0114-0001',
      nombre: '<img src=x onerror=alert(1)>',
      departamento: { id: 'd', codigoIne: '01', nombre: 'Guatemala' },
      municipio: { id: 'm', departamentoId: 'd', codigoIne: '0114', nombre: 'Amatitlán' },
      latitud: 14.48,
      longitud: -90.61,
      activo: true,
      estadoActual,
    };
  }

  beforeEach(() => {
    mocks.mapas.length = 0;
    mocks.marcadores.length = 0;
    mocks.popups.length = 0;
    vi.clearAllMocks();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        disconnect = mocks.disconnect;
      },
    );
    TestBed.configureTestingModule({
      providers: [{ provide: TranslocoService, useValue: { translate: (key: string) => key } }],
    });
    TestBed.overrideComponent(MapaPuentesComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.unstubAllGlobals();
  });

  async function abrir(puentes: PuenteCatalogoResponse[]) {
    const fixture = TestBed.createComponent(MapaPuentesComponent);
    fixture.componentRef.setInput('puentes', puentes);
    fixture.componentRef.setInput('estados', estados);
    fixture.detectChanges();
    await fixture.whenStable();
    // Esperar la importación dinámica de MapLibre.
    await vi.waitFor(() => expect(mocks.mapas).toHaveLength(1));
    fixture.detectChanges();
    return fixture;
  }

  it('excluye inactivos, nulos, no finitos y coordenadas fuera de rango', async () => {
    const fixture = await abrir([
      puente(),
      { ...puente(), activo: false },
      { ...puente(), latitud: null },
      { ...puente(), longitud: null },
      { ...puente(), latitud: NaN },
      { ...puente(), longitud: Infinity },
      { ...puente(), latitud: 91 },
      { ...puente(), longitud: -181 },
    ]);
    expect(mocks.marcadores).toHaveLength(1);
    expect(mocks.marcadores[0].setLngLat).toHaveBeenCalledWith([-90.61, 14.48]);
    expect(fixture.componentInstance.cantidadMarcadores()).toBe(1);
    const opciones = mocks.opciones.mock.calls[0][0];
    expect(opciones.style.sources.osm.tiles[0]).toContain('tile.openstreetmap.org');
    expect(opciones.style.sources.osm.attribution).toContain('OpenStreetMap');
    expect(opciones.attributionControl.compact).toBe(false);
  });

  it('representa los cuatro estados con icono, etiqueta accesible y leyenda textual', async () => {
    const fixture = await abrir(estados.map((estado) => puente(estado)));
    estados.forEach((estado, indice) => {
      const boton = mocks.marcadores[indice].elemento;
      expect(boton.dataset['estado']).toBe(estado);
      expect(boton.getAttribute('aria-label')).toContain(estado);
      expect(boton.querySelector('i')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.mapa-leyenda').textContent).toContain(estado);
    });
    expect(new Set(mocks.marcadores.map((m) => m.elemento.firstElementChild?.className)).size).toBe(
      4,
    );
  });

  it('construye un popup solo con datos públicos y trata el nombre como texto', async () => {
    await abrir([
      {
        ...puente(),
        fotografias: ['privada.jpg'],
        danos: 'Daño privado',
      } as PuenteCatalogoResponse,
    ]);
    const popup = mocks.popups[0].contenido;
    expect(popup.textContent).toContain(puente().nombre);
    expect(popup.textContent).toContain('Guatemala');
    expect(popup.textContent).toContain('Amatitlán');
    expect(popup.textContent).toContain('Bueno');
    expect(popup.querySelector('img')).toBeNull();
    expect(popup.textContent).not.toContain('privada.jpg');
    expect(popup.textContent).not.toContain('Daño privado');
  });

  it('limpia marcadores y popups al actualizar y libera mapa y observador al salir', async () => {
    const fixture = await abrir([puente()]);
    fixture.componentRef.setInput('puentes', [puente('Malo')]);
    fixture.detectChanges();
    expect(mocks.mapas).toHaveLength(1);
    expect(mocks.marcadores[0].remove).toHaveBeenCalledOnce();
    expect(mocks.popups[0].remove).toHaveBeenCalledOnce();
    expect(mocks.mapas[0].resize).toHaveBeenCalled();
    fixture.destroy();
    expect(mocks.mapas[0].remove).toHaveBeenCalledOnce();
    expect(mocks.marcadores[1].remove).toHaveBeenCalledOnce();
    expect(mocks.popups[1].remove).toHaveBeenCalledOnce();
    expect(mocks.disconnect).toHaveBeenCalledOnce();
  });

  it('destruye la instancia anterior al volver a abrir el mapa', async () => {
    const primero = await abrir([puente()]);
    primero.destroy();
    const segundo = TestBed.createComponent(MapaPuentesComponent);
    segundo.componentRef.setInput('puentes', [puente()]);
    segundo.componentRef.setInput('estados', estados);
    segundo.detectChanges();
    await vi.waitFor(() => expect(mocks.mapas).toHaveLength(2));
    expect(mocks.mapas[0].remove).toHaveBeenCalledOnce();
    expect(mocks.mapas[1].remove).not.toHaveBeenCalled();
    segundo.destroy();
    expect(mocks.mapas[1].remove).toHaveBeenCalledOnce();
  });
});
