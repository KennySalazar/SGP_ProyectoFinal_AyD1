import { describe, expect, it } from 'vitest';
import { mensajeCampo, nombreCampo } from './nombre-campo';

const esquema = {
  type: 'object',
  properties: {
    caracteristicas_cauce: {
      type: 'object',
      title: 'Caracteristicas del cauce',
      properties: { nombre_rio: { type: 'string', title: 'Nombre del rio o cauce' } },
    },
    tramos: {
      type: 'array',
      title: 'Tramos',
      items: { type: 'object', properties: { largo: { type: 'number', title: 'Longitud (m)' } } },
    },
  },
};

describe('Nombres de campos pendientes de inspeccion', () => {
  it('usa los titulos de la version del formulario', () => {
    expect(nombreCampo('caracteristicas_cauce.nombre_rio', esquema)).toBe(
      'Caracteristicas del cauce > Nombre del rio o cauce',
    );
  });
  it('conserva la validacion y reemplaza su identificador tecnico', () => {
    expect(
      mensajeCampo(
        'caracteristicas_cauce.nombre_rio',
        "El campo 'caracteristicas_cauce.nombre_rio' es obligatorio",
        esquema,
      ),
    ).toBe("El campo 'Caracteristicas del cauce > Nombre del rio o cauce' es obligatorio");
  });
  it('presenta los indices repetibles empezando en uno', () => {
    expect(nombreCampo('datos.tramos[1].largo', esquema)).toBe(
      'Tramos > Elemento 2 > Longitud (m)',
    );
  });
  it('mantiene el contexto cuando el mensaje no incluye la ruta', () => {
    expect(mensajeCampo('caracteristicas_cauce.nombre_rio', 'Longitud maxima: 100', esquema)).toBe(
      'Caracteristicas del cauce > Nombre del rio o cauce: Longitud maxima: 100',
    );
  });
  it('usa nombres legibles cuando no hay esquema o la propiedad no existe', () => {
    expect(nombreCampo('/subestructura/estribo_entrada', null)).toBe(
      'Subestructura > Estribo entrada',
    );
    expect(nombreCampo('campo_nuevo', esquema)).toBe('Campo nuevo');
  });
});
