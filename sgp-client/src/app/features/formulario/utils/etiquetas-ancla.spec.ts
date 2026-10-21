import { describe, expect, it } from 'vitest';
import { crearEtiquetadorAncla } from './etiquetas-ancla';

const ESQUEMA = {
  type: 'object',
  title: 'Formulario',
  properties: {
    subestructura: {
      type: 'object',
      title: '4. Subestructura',
      properties: {
        estribo_entrada: {
          type: 'object',
          title: 'Estribo de entrada',
          properties: {
            viga_apoyo: {
              type: 'object',
              title: 'Viga de apoyo',
              properties: { exposicion_acero: { type: 'string', title: 'Exposición del acero' } },
            },
          },
        },
        pilas: {
          type: 'array',
          title: 'Pilas',
          items: {
            type: 'object',
            title: 'Pila',
            properties: {
              instancia_id: { type: 'string', format: 'uuid' },
              socavacion: {
                type: 'object',
                title: 'Socavación',
                properties: { aguas_arriba: { type: 'string', title: 'Aguas arriba' } },
              },
            },
          },
        },
      },
    },
  },
};

const DATOS = {
  subestructura: {
    pilas: [{ instancia_id: 'pila-1' }, { instancia_id: 'pila-2' }],
  },
};

describe('crearEtiquetadorAncla', () => {
  const etiqueta = crearEtiquetadorAncla(ESQUEMA, DATOS);

  it('usa el título del esquema para un elemento y para un campo', () => {
    expect(etiqueta({ elementoRef: '/subestructura/estribo_entrada/viga_apoyo' })).toBe(
      'Viga de apoyo',
    );
    expect(
      etiqueta({ elementoRef: '/subestructura/estribo_entrada/viga_apoyo/exposicion_acero' }),
    ).toBe('Exposición del acero');
  });

  it('indica qué instancia es en un elemento repetible', () => {
    expect(etiqueta({ elementoRef: '/subestructura/pilas', instanciaElementoId: 'pila-2' })).toBe(
      'Pilas · Pila 2',
    );
    expect(
      etiqueta({ elementoRef: '/subestructura/pilas/socavacion', instanciaElementoId: 'pila-1' }),
    ).toBe('Socavación · Pila 1');
  });

  it('muestra solo el título si la instancia no está en los datos', () => {
    expect(
      etiqueta({ elementoRef: '/subestructura/pilas/socavacion', instanciaElementoId: 'otra' }),
    ).toBe('Socavación');
  });

  it('humaniza el último segmento de una ruta que el esquema no conoce', () => {
    expect(etiqueta({ elementoRef: '/subestructura/viga_nueva' })).toBe('Viga nueva');
    expect(etiqueta({ elementoRef: '/a~1b' })).toBe('A/b');
  });

  it('no falla sin esquema o con un esquema que no se puede resolver', () => {
    expect(crearEtiquetadorAncla(null, null)({ elementoRef: '/subestructura/viga_nueva' })).toBe(
      'Viga nueva',
    );
    expect(crearEtiquetadorAncla({ type: 'array' }, {})({ elementoRef: '/otro_elemento' })).toBe(
      'Otro elemento',
    );
  });
});
