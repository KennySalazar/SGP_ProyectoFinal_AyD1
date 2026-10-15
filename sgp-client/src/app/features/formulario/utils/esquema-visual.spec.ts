import { describe, expect, it } from 'vitest';
import { SeccionVisual } from '../models/formulario.models';
import { esquemaASeccionesVisuales, seccionesVisualesAEsquema } from './esquema-visual';

describe('esquema-visual utility', () => {
  it('convierte esquema nulo o vacío en arreglo vacío', () => {
    expect(esquemaASeccionesVisuales(null)).toEqual([]);
    expect(esquemaASeccionesVisuales({})).toEqual([]);
    expect(esquemaASeccionesVisuales({ properties: {} })).toEqual([]);
  });

  it('convierte un esquema con secciones y preguntas de varios tipos', () => {
    const esquema = {
      $schema: 'https://json-schema.org/draft/2020-12/schema',
      title: 'Formulario de Prueba',
      type: 'object',
      properties: {
        identificacion: {
          title: '1. Identificación',
          description: 'Datos del puente',
          type: 'object',
          required: ['nombre_puente', 'ruta_pavimentada'],
          properties: {
            nombre_puente: {
              title: 'Nombre puente',
              type: 'string',
              minLength: 1,
              maxLength: 100,
              readOnly: true,
            },
            ruta_pavimentada: {
              title: 'Ruta pavimentada',
              type: 'string',
              enum: ['SI', 'NO', 'PARCIAL'],
            },
            fecha_inspeccion: {
              title: 'Fecha inspección',
              type: 'string',
              format: 'date',
            },
            observaciones: {
              title: 'Observaciones',
              type: 'string',
              maxLength: 500,
            },
            esviaje: {
              title: 'Esviaje',
              type: 'boolean',
            },
            numero_carriles: {
              title: 'Número de carriles',
              type: 'integer',
              minimum: 1,
              maximum: 10,
            },
            longitud_m: {
              title: 'Longitud (m)',
              type: 'number',
              minimum: 0,
            },
          },
        },
        tramos: {
          title: '2. Tramos',
          type: 'array',
          items: {
            type: 'object',
            required: ['longitud_tramo'],
            properties: {
              longitud_tramo: {
                title: 'Longitud de tramo',
                type: 'number',
              },
            },
          },
        },
      },
    };

    const vinculos = {
      'identificacion.nombre_puente': 'puente.nombre',
    };

    const secciones = esquemaASeccionesVisuales(esquema, vinculos);

    expect(secciones).toHaveLength(2);

    const sec1 = secciones[0];
    expect(sec1.clave).toBe('identificacion');
    expect(sec1.titulo).toBe('1. Identificación');
    expect(sec1.esRepetible).toBe(false);
    expect(sec1.preguntas).toHaveLength(7);

    const pNombre = sec1.preguntas.find((p) => p.clave === 'nombre_puente');
    expect(pNombre?.tipo).toBe('string');
    expect(pNombre?.obligatorio).toBe(true);
    expect(pNombre?.soloLectura).toBe(true);
    expect(pNombre?.vinculo).toBe('puente.nombre');

    const pRuta = sec1.preguntas.find((p) => p.clave === 'ruta_pavimentada');
    expect(pRuta?.tipo).toBe('select');
    expect(pRuta?.opciones).toEqual(['SI', 'NO', 'PARCIAL']);
    expect(pRuta?.obligatorio).toBe(true);

    const pFecha = sec1.preguntas.find((p) => p.clave === 'fecha_inspeccion');
    expect(pFecha?.tipo).toBe('date');

    const pObs = sec1.preguntas.find((p) => p.clave === 'observaciones');
    expect(pObs?.tipo).toBe('multiline');

    const pEsviaje = sec1.preguntas.find((p) => p.clave === 'esviaje');
    expect(pEsviaje?.tipo).toBe('boolean');

    const pCarriles = sec1.preguntas.find((p) => p.clave === 'numero_carriles');
    expect(pCarriles?.tipo).toBe('integer');
    expect(pCarriles?.valorMinimo).toBe(1);
    expect(pCarriles?.valorMaximo).toBe(10);

    const sec2 = secciones[1];
    expect(sec2.clave).toBe('tramos');
    expect(sec2.esRepetible).toBe(true);
    expect(sec2.preguntas).toHaveLength(1);
    expect(sec2.preguntas[0].clave).toBe('longitud_tramo');
    expect(sec2.preguntas[0].obligatorio).toBe(true);
  });

  it('genera esquema y vínculos a partir de secciones visuales modificadas', () => {
    const seccionesVisuales: SeccionVisual[] = [
      {
        clave: 'datos_basicos',
        titulo: 'Datos Básicos',
        descripcion: 'Sección principal',
        esRepetible: false,
        preguntas: [
          {
            clave: 'codigo_puente',
            titulo: 'Código del puente',
            tipo: 'string',
            obligatorio: true,
            soloLectura: true,
            longitudMinima: 3,
            longitudMaxima: 20,
            vinculo: 'puente.codigo',
          },
          {
            clave: 'estado_conservacion',
            titulo: 'Estado de conservación',
            tipo: 'select',
            opciones: ['BUENO', 'REGULAR', 'MALO'],
            obligatorio: true,
          },
          {
            clave: 'ancho_m',
            titulo: 'Ancho total (m)',
            tipo: 'number',
            obligatorio: false,
            valorMinimo: 0,
            valorMaximo: 50,
          },
        ],
      },
    ];

    const resultado = seccionesVisualesAEsquema(seccionesVisuales, null, {});

    expect(resultado.esquema['$schema']).toBe('https://json-schema.org/draft/2020-12/schema');
    expect(resultado.esquema['type']).toBe('object');
    expect(resultado.esquema['required']).toEqual(['datos_basicos']);

    const properties = resultado.esquema['properties'] as Record<
      string,
      {
        title: string;
        type: string;
        required: string[];
        properties: Record<
          string,
          {
            title?: string;
            type?: string;
            readOnly?: boolean;
            minLength?: number;
            maxLength?: number;
            minimum?: number;
            maximum?: number;
            enum?: string[];
          }
        >;
      }
    >;
    const propSec = properties['datos_basicos'];
    expect(propSec.title).toBe('Datos Básicos');
    expect(propSec.type).toBe('object');
    expect(propSec.required).toEqual(['codigo_puente', 'estado_conservacion']);

    const propCodigo = propSec.properties['codigo_puente'];
    expect(propCodigo.title).toBe('Código del puente');
    expect(propCodigo.type).toBe('string');
    expect(propCodigo.readOnly).toBe(true);
    expect(propCodigo.minLength).toBe(3);
    expect(propCodigo.maxLength).toBe(20);

    const propEstado = propSec.properties['estado_conservacion'];
    expect(propEstado.type).toBe('string');
    expect(propEstado.enum).toEqual(['BUENO', 'REGULAR', 'MALO']);

    const propAncho = propSec.properties['ancho_m'];
    expect(propAncho.type).toBe('number');
    expect(propAncho.minimum).toBe(0);
    expect(propAncho.maximum).toBe(50);

    expect(resultado.vinculos['datos_basicos.codigo_puente']).toBe('puente.codigo');
  });
});
