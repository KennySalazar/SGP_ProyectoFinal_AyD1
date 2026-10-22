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
            cortina_superior: {
              type: 'object',
              title: 'Cortina superior',
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

const PILAS = '/subestructura/pilas';

const DATOS = {
  subestructura: {
    pilas: [{ instancia_id: 'pila-1' }, { instancia_id: 'pila-2' }],
  },
};

describe('crearEtiquetadorAncla', () => {
  const etiqueta = crearEtiquetadorAncla(ESQUEMA, DATOS);
  const VIGA = '/subestructura/estribo_entrada/viga_apoyo';

  it('una sección se nombra con su título', () => {
    expect(etiqueta({ elementoRef: '/subestructura' })).toBe('4. Subestructura');
  });

  it('un elemento se nombra con la ruta completa, de la sección hasta él', () => {
    expect(etiqueta({ elementoRef: VIGA })).toBe(
      '4. Subestructura › Estribo de entrada › Viga de apoyo',
    );
  });

  it('un campo se nombra con la ruta completa: el mismo nombre en dos elementos no se confunde', () => {
    const enViga = etiqueta({ elementoRef: VIGA + '/exposicion_acero' });
    const enCortina = etiqueta({
      elementoRef: '/subestructura/estribo_entrada/cortina_superior/exposicion_acero',
    });

    expect(enViga).toBe(
      '4. Subestructura › Estribo de entrada › Viga de apoyo › Exposición del acero',
    );
    expect(enCortina).toBe(
      '4. Subestructura › Estribo de entrada › Cortina superior › Exposición del acero',
    );
    expect(enViga).not.toBe(enCortina);
  });

  it('indica qué instancia es dentro de la ruta de un elemento repetible', () => {
    expect(etiqueta({ elementoRef: PILAS, instanciaElementoId: 'pila-2' })).toBe(
      '4. Subestructura › Pilas · Pila 2',
    );
    expect(etiqueta({ elementoRef: PILAS + '/socavacion', instanciaElementoId: 'pila-1' })).toBe(
      '4. Subestructura › Pilas · Pila 1 › Socavación',
    );
    expect(
      etiqueta({ elementoRef: PILAS + '/socavacion/aguas_arriba', instanciaElementoId: 'pila-2' }),
    ).toBe('4. Subestructura › Pilas · Pila 2 › Socavación › Aguas arriba');
  });

  it('la misma ruta en dos instancias da nombres distintos', () => {
    const uno = etiqueta({ elementoRef: PILAS + '/socavacion', instanciaElementoId: 'pila-1' });
    const dos = etiqueta({ elementoRef: PILAS + '/socavacion', instanciaElementoId: 'pila-2' });

    expect(uno).not.toBe(dos);
  });

  it('si la instancia no está en los datos deja el nombre de la lista sin número', () => {
    expect(etiqueta({ elementoRef: PILAS + '/socavacion', instanciaElementoId: 'otra' })).toBe(
      '4. Subestructura › Pilas › Socavación',
    );
  });

  it('humaniza los niveles que el esquema no conoce en lugar de mostrar la clave técnica', () => {
    expect(etiqueta({ elementoRef: '/subestructura/viga_nueva' })).toBe(
      '4. Subestructura › Viga nueva',
    );
    expect(etiqueta({ elementoRef: '/a~1b' })).toBe('A/b');
  });

  it('no repite un nivel cuyo título es igual al de su padre', () => {
    const repetido = crearEtiquetadorAncla(
      {
        type: 'object',
        properties: {
          bloque: {
            type: 'object',
            title: 'Estado',
            properties: { estado: { type: 'string', title: 'Estado' } },
          },
        },
      },
      {},
    );

    expect(repetido({ elementoRef: '/bloque/estado' })).toBe('Estado');
  });

  it('no falla sin esquema o con un esquema que no se puede resolver', () => {
    expect(crearEtiquetadorAncla(null, null)({ elementoRef: '/subestructura/viga_nueva' })).toBe(
      'Subestructura › Viga nueva',
    );
    expect(crearEtiquetadorAncla({ type: 'array' }, {})({ elementoRef: '/otro_elemento' })).toBe(
      'Otro elemento',
    );
  });
});
