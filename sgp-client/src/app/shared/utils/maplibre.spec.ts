import { afterEach, describe, expect, it, vi } from 'vitest';
import { cargarEstilosMaplibre, resolverMaplibre } from './maplibre';

describe('Utilidades de MapLibre', () => {
  afterEach(() => {
    document.querySelectorAll('link[data-maplibre-estilos]').forEach((enlace) => enlace.remove());
  });

  describe('resolverMaplibre', () => {
    const libreria = {
      Map: class {},
      getVersion: () => '1.0.0',
    } as unknown as typeof import('maplibre-gl');

    it('usa el módulo cuando expone exports nombrados (desarrollo)', () => {
      expect(resolverMaplibre(libreria)).toBe(libreria);
    });

    it('usa el export default cuando el build lo entrega empaquetado (producción)', () => {
      const soloDefault = { default: libreria } as unknown as typeof import('maplibre-gl');

      expect(resolverMaplibre(soloDefault)).toBe(libreria);
    });
  });

  describe('cargarEstilosMaplibre', () => {
    it('agrega una hoja de estilos con la versión como parámetro', () => {
      cargarEstilosMaplibre('5.24.0');

      const enlaces = document.querySelectorAll('link[data-maplibre-estilos]');

      expect(enlaces).toHaveLength(1);
      expect(enlaces[0].getAttribute('rel')).toBe('stylesheet');
      expect(enlaces[0].getAttribute('href')).toContain('maplibre.css?v=5.24.0');
    });

    it('no repite la hoja de estilos si ya fue cargada', () => {
      cargarEstilosMaplibre('5.24.0');
      cargarEstilosMaplibre('5.24.0');

      expect(document.querySelectorAll('link[data-maplibre-estilos]')).toHaveLength(1);
    });

    it('avisa cuando la hoja de estilos termina de cargar', () => {
      const alCargar = vi.fn();

      cargarEstilosMaplibre('5.24.0', alCargar);
      document.querySelector('link[data-maplibre-estilos]')?.dispatchEvent(new Event('load'));

      expect(alCargar).toHaveBeenCalledTimes(1);
    });
  });
});
