import { describe, expect, it } from 'vitest';
import { etiquetaMapeo, nombresMapeo } from './nombres-mapeo';

describe('nombres de campos del mapeo', () => {
  it('usa titulos del esquema para mostrar campos anidados', () => {
    const nombres = nombresMapeo({
      type: 'object',
      properties: {
        otros: {
          type: 'object',
          title: 'Otros',
          properties: {
            senalizacion: {
              type: 'object',
              title: 'Senalizacion',
              properties: {
                horizontal: { type: 'boolean', title: 'Senalizacion horizontal' },
              },
            },
          },
        },
      },
    });
    expect(etiquetaMapeo('/otros/senalizacion/horizontal', nombres)).toBe(
      'Otros > Senalizacion > Senalizacion horizontal',
    );
  });
  it('humaniza las referencias ausentes sin exponer rutas tecnicas', () => {
    expect(etiquetaMapeo('/otros/senalizacion/preventiva', new Map())).toBe(
      'Otros > Senalizacion > Preventiva',
    );
    expect(etiquetaMapeo(null, new Map())).toBe('—');
    expect(nombresMapeo(null).size).toBe(0);
  });
});
