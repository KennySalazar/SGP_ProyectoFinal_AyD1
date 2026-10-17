import { describe, expect, it } from 'vitest';
import { ESQUEMA_SIECA } from '../testing/esquema-sieca';
import { NodoArreglo, NodoCampo, NodoFormulario, NodoObjeto } from './modelos';
import { humanizar, resolverEsquema } from './resolutor';

function buscar(nodo: NodoFormulario, ruta: string): NodoFormulario {
  const partes = ruta.split('/');
  let actual: NodoFormulario = nodo;
  for (const parte of partes) {
    const objeto: NodoObjeto =
      actual.clase === 'objeto' ? actual : actual.clase === 'arreglo' ? actual.elemento : fallar();
    const hijo = objeto.hijos.find((candidato) => candidato.clave === parte);
    if (!hijo) throw new Error(`No existe ${ruta} (falta ${parte})`);
    actual = hijo;
  }
  return actual;
}

function fallar(): never {
  throw new Error('El nodo no tiene hijos');
}

const campo = (raiz: NodoObjeto, ruta: string) => buscar(raiz, ruta) as NodoCampo;

describe('resolverEsquema: esquema real sieca-v1', () => {
  const esquema = resolverEsquema(ESQUEMA_SIECA);

  it('produce las ocho secciones del manual, en orden, sin referencias sin resolver', () => {
    expect(esquema.secciones.map((s) => s.clave)).toEqual([
      'identificacion',
      'datos_generales',
      'tramos',
      'subestructura',
      'superestructura',
      'elementos_no_estructurales',
      'caracteristicas_cauce',
      'otros',
    ]);
    expect(esquema.titulo).toContain('Evaluación Técnica de Puentes');
    expect(JSON.stringify(esquema)).not.toContain('$ref');
  });

  it('toma los títulos y descripciones del esquema, no de textos fijos', () => {
    expect(esquema.secciones[0].titulo).toBe('1. Identificación del puente');
    expect(esquema.secciones[2].titulo).toBe('3. Tramos');
    expect(esquema.secciones[3].titulo).toMatch(/^4\./);
  });

  it('marca los datos precargados como solo lectura', () => {
    const raiz = esquema.raiz;
    expect(campo(raiz, 'identificacion/nombre_puente').soloLectura).toBe(true);
    expect(campo(raiz, 'identificacion/codigo').soloLectura).toBe(true);
    expect(campo(raiz, 'identificacion/ruta_pavimentada').soloLectura).toBe(false);
  });

  it('distingue lo obligatorio, lo condicional y lo anulable', () => {
    const raiz = esquema.raiz;
    const existencia = campo(raiz, 'subestructura/estribo_entrada/cortina_superior/existencia');
    const grietas = campo(
      raiz,
      'subestructura/estribo_entrada/cortina_superior/grietas_una_direccion',
    );
    const altura = campo(raiz, 'subestructura/estribo_entrada/cortina_superior/altura_m');

    expect(existencia.obligatorio).toBe(true);
    expect(existencia.condicional).toBe(false);
    expect(grietas.obligatorio).toBe(false);
    expect(grietas.condicional).toBe(true);
    expect(altura.obligatorio).toBe(false);
    expect(altura.condicional).toBe(false);
    expect(campo(raiz, 'identificacion/kilometraje').anulable).toBe(true);
  });

  it('identifica los elementos evaluables del Índice de Condición', () => {
    const cortina = buscar(esquema.raiz, 'subestructura/estribo_entrada/cortina_superior');
    expect(cortina.evaluable).toBe(true);
    expect(buscar(esquema.raiz, 'subestructura').evaluable).toBe(false);
  });

  it('usa la ruta sin índices como elemento_ref', () => {
    expect(campo(esquema.raiz, 'subestructura/estribo_entrada/socavacion/aguas_arriba').ruta).toBe(
      'subestructura/estribo_entrada/socavacion/aguas_arriba',
    );
  });

  it('resuelve los tipos de campo del formulario', () => {
    const raiz = esquema.raiz;
    expect(campo(raiz, 'identificacion/fecha_inspeccion').tipo).toBe('fecha');
    expect(campo(raiz, 'identificacion/esviaje').tipo).toBe('booleano');
    expect(campo(raiz, 'identificacion/ruta_pavimentada').tipo).toBe('enumerado');
    expect(campo(raiz, 'identificacion/nombre_puente').tipo).toBe('texto');
    expect(campo(raiz, 'datos_generales/longitud_m').tipo).toBe('numero');
    expect(campo(raiz, 'datos_generales/numero_tramos').tipo).toBe('entero');
    expect(campo(raiz, 'tramos/instancia_id').tipo).toBe('uuid');
  });

  it('entiende los límites de los números y de los textos', () => {
    const longitud = campo(esquema.raiz, 'datos_generales/longitud_m');
    expect(longitud.minimoExclusivo).toBe(0);
    expect(longitud.maximo).toBeGreaterThan(0);
    expect(campo(esquema.raiz, 'identificacion/nombre_puente').longitudMaxima).toBe(200);
  });

  it('convierte los enumerados en opciones con etiqueta legible', () => {
    const existencia = campo(
      esquema.raiz,
      'subestructura/estribo_entrada/cortina_superior/existencia',
    );
    expect(existencia.opciones.map((o) => o.valor)).toEqual(['PRESENTE', 'NO_APLICA', 'AUSENTE']);
    expect(existencia.opciones[1].etiqueta).toBe('No aplica');
  });

  it('soporta enumerados numéricos', () => {
    const zona = campo(esquema.raiz, 'identificacion/coordenadas_utm/zona');
    expect(zona.tipo).toBe('enumerado');
    expect(zona.opciones.map((o) => o.valor)).toEqual([15, 16]);
  });

  it('resuelve los arreglos repetibles con sus límites', () => {
    const tramos = buscar(esquema.raiz, 'tramos') as NodoArreglo;
    const pilas = buscar(esquema.raiz, 'subestructura/pilas') as NodoArreglo;

    expect(tramos.clase).toBe('arreglo');
    expect(tramos.minimoElementos).toBe(1);
    expect(tramos.maximoElementos).toBe(100);
    expect(tramos.elemento.hijos.map((h) => h.clave)).toContain('instancia_id');
    expect(pilas.clase).toBe('arreglo');
    expect(pilas.minimoElementos).toBe(0);
    expect(pilas.maximoElementos).toBe(99);
  });

  it('recupera objetos y arreglos cuando la forma estructural contradice el tipo escalar', () => {
    const esquemaDanado = JSON.parse(JSON.stringify(ESQUEMA_SIECA)) as typeof ESQUEMA_SIECA;
    const propiedades = esquemaDanado['properties'] as Record<string, Record<string, unknown>>;
    const subestructura = propiedades['subestructura'];
    const elementos = subestructura['properties'] as Record<string, Record<string, unknown>>;
    elementos['estribo_entrada']['type'] = 'string';
    elementos['pilas']['type'] = 'string';

    const resuelto = resolverEsquema(esquemaDanado);
    const nodoSubestructura = resuelto.secciones.find(
      (seccion) => seccion.clave === 'subestructura',
    )!;
    const estriboEntrada = nodoSubestructura.hijos.find((hijo) => hijo.clave === 'estribo_entrada');
    const pilas = nodoSubestructura.hijos.find((hijo) => hijo.clave === 'pilas');

    expect(estriboEntrada?.clase).toBe('objeto');
    expect(estriboEntrada?.clase === 'objeto' && estriboEntrada.hijos.length).toBeGreaterThan(0);
    expect(pilas?.clase).toBe('arreglo');
    expect(pilas?.clase === 'arreglo' && pilas.elemento.hijos.map((hijo) => hijo.clave)).toContain(
      'viga_cabezal',
    );
  });

  it('resuelve la selección múltiple con su regla de exclusión de LIMPIO', () => {
    const condiciones = campo(esquema.raiz, 'caracteristicas_cauce/estado_cauce/condiciones');
    expect(condiciones.tipo).toBe('seleccionMultiple');
    expect(condiciones.minimoElementos).toBe(1);
    expect(condiciones.opciones.map((o) => o.valor)).toEqual([
      'LIMPIO',
      'ASOLVADO_SEDIMENTADO',
      'EROSIONADO',
      'OTRO',
    ]);
    expect(condiciones.reglasLista).toEqual([{ contiene: 'LIMPIO', maximo: 1 }]);
  });

  it('aplica la restricción `not` de los elementos de una lista (sin la opción OTRO)', () => {
    const condiciones = campo(
      esquema.raiz,
      'caracteristicas_cauce/estado_zona_adyacente_estribos/condiciones',
    );
    expect(condiciones.opciones.map((o) => o.valor)).toEqual([
      'LIMPIO',
      'ASOLVADO_SEDIMENTADO',
      'EROSIONADO',
    ]);
  });

  it('lee las condiciones if/then/else de los objetos', () => {
    const cortina = buscar(
      esquema.raiz,
      'subestructura/estribo_entrada/cortina_superior',
    ) as NodoObjeto;
    expect(cortina.reglas).toHaveLength(1);
    expect(cortina.reglas[0].cuando.propiedades).toEqual({ existencia: { const: 'PRESENTE' } });
    expect(cortina.reglas[0].entonces.requeridos).toContain('grietas_una_direccion');

    const cauce = buscar(esquema.raiz, 'caracteristicas_cauce') as NodoObjeto;
    expect(cauce.reglas).toHaveLength(2);
    expect(cauce.reglas[0].sino.soloDiscriminadores).toBe(true);
    expect(cauce.reglas[1].cuando.propiedades['tipo_cuerpo_agua']).toEqual({ const: 'OTRO' });
  });

  it('lee las condiciones que dependen de que una lista contenga un valor', () => {
    const estado = buscar(esquema.raiz, 'caracteristicas_cauce/estado_cauce') as NodoObjeto;
    expect(estado.reglas[0].cuando.propiedades['condiciones']).toEqual({ contiene: 'OTRO' });
    expect(estado.reglas[0].entonces.requeridos).toEqual(['otro_descripcion']);
  });

  it('lee las propiedades prohibidas del else', () => {
    expect(JSON.stringify(esquema.raiz)).toContain('"prohibidos":["estado"]');
  });

  it('incluye «Tramos» como sección aunque sea una lista repetible', () => {
    const tramos = esquema.secciones.find((seccion) => seccion.clave === 'tramos');
    expect(tramos?.clase).toBe('arreglo');
  });
});

describe('resolverEsquema: casos del intérprete', () => {
  it('prefiere lo declarado junto a $ref (por ejemplo el título)', () => {
    const esquema = resolverEsquema({
      type: 'object',
      properties: {
        a: {
          type: 'object',
          properties: { x: { $ref: '#/$defs/base', title: 'Título propio' } },
        },
      },
      $defs: { base: { title: 'Título base', type: 'string' } },
    });
    expect(buscar(esquema.raiz, 'a/x').titulo).toBe('Título propio');
  });

  it('usa la clave humanizada cuando el campo no tiene título', () => {
    const esquema = resolverEsquema({
      type: 'object',
      properties: { a: { type: 'object', properties: { nivel_de_agua: { type: 'string' } } } },
    });
    expect(buscar(esquema.raiz, 'a/nivel_de_agua').titulo).toBe('Nivel de agua');
  });

  it('rechaza referencias que no son locales', () => {
    expect(() =>
      resolverEsquema({
        type: 'object',
        properties: { a: { $ref: 'https://ejemplo.test/otro.json' } },
      }),
    ).toThrow(/Referencia no soportada/);
  });

  it('detecta referencias circulares', () => {
    expect(() =>
      resolverEsquema({
        type: 'object',
        properties: { a: { $ref: '#/$defs/b' } },
        $defs: { b: { $ref: '#/$defs/b' } },
      }),
    ).toThrow(/circular/);
  });

  it('rechaza un esquema raíz que no es un objeto', () => {
    expect(() => resolverEsquema({ type: 'string' })).toThrow(/debe ser un objeto/);
  });

  it('rechaza un tipo que el motor no soporta', () => {
    expect(() =>
      resolverEsquema({
        type: 'object',
        properties: { a: { type: 'object', properties: { b: { type: 'null' } } } },
      }),
    ).toThrow(/Tipo no soportado/);
  });

  it('deduce el tipo de un enumerado que no lo declara', () => {
    const esquema = resolverEsquema({
      type: 'object',
      properties: {
        seccion: {
          type: 'object',
          properties: { letras: { enum: ['A', 'B'] }, cifras: { enum: [1, 2] } },
        },
      },
    });

    const letras = buscar(esquema.raiz, 'seccion/letras') as NodoCampo;
    const cifras = buscar(esquema.raiz, 'seccion/cifras') as NodoCampo;
    expect(letras.opciones.map((opcion) => opcion.valor)).toEqual(['A', 'B']);
    expect(cifras.opciones.map((opcion) => opcion.valor)).toEqual([1, 2]);
  });

  describe('orden de presentación', () => {
    /** Reproduce lo que hace JSONB: ordena las claves por longitud y luego alfabéticamente. */
    function comoJsonb(valor: unknown): unknown {
      if (Array.isArray(valor)) return valor.map(comoJsonb);
      if (typeof valor !== 'object' || valor === null) return valor;
      return Object.fromEntries(
        Object.entries(valor)
          .sort(([a], [b]) => a.length - b.length || (a < b ? -1 : a > b ? 1 : 0))
          .map(([clave, hijo]) => [clave, comoJsonb(hijo)]),
      );
    }

    function ordenes(nodo: NodoFormulario, acumulado: string[] = []): string[] {
      if (nodo.clase === 'objeto') {
        acumulado.push(`${nodo.ruta}: ${nodo.hijos.map((hijo) => hijo.clave).join(',')}`);
        nodo.hijos.forEach((hijo) => ordenes(hijo, acumulado));
      } else if (nodo.clase === 'arreglo') {
        ordenes(nodo.elemento, acumulado);
      }
      return acumulado;
    }

    it('respeta x-sgp-orden aunque las claves lleguen reordenadas', () => {
      const esquema = resolverEsquema({
        type: 'object',
        'x-sgp-orden': ['segundo_largo', 'primero'],
        properties: {
          primero: { type: 'string' },
          segundo_largo: { type: 'string' },
          sin_orden: { type: 'string' },
        },
      });

      expect(esquema.raiz.hijos.map((hijo) => hijo.clave)).toEqual([
        'segundo_largo',
        'primero',
        'sin_orden',
      ]);
    });

    it('recupera el orden del formulario SIECA aunque JSONB haya reordenado el esquema', () => {
      const original = resolverEsquema(ESQUEMA_SIECA);
      const reordenado = resolverEsquema(comoJsonb(ESQUEMA_SIECA) as typeof ESQUEMA_SIECA);

      expect(reordenado.secciones.map((seccion) => seccion.clave)).toEqual([
        'identificacion',
        'datos_generales',
        'tramos',
        'subestructura',
        'superestructura',
        'elementos_no_estructurales',
        'caracteristicas_cauce',
        'otros',
      ]);
      expect(ordenes(reordenado.raiz)).toEqual(ordenes(original.raiz));
    });

    it('ordena las secciones por el número del título cuando la versión no trae x-sgp-orden', () => {
      const sinOrden = JSON.parse(JSON.stringify(comoJsonb(ESQUEMA_SIECA))) as Record<
        string,
        unknown
      >;
      const quitar = (nodo: unknown): void => {
        if (Array.isArray(nodo)) nodo.forEach(quitar);
        else if (typeof nodo === 'object' && nodo !== null) {
          delete (nodo as Record<string, unknown>)['x-sgp-orden'];
          Object.values(nodo).forEach(quitar);
        }
      };
      quitar(sinOrden);

      const esquema = resolverEsquema(sinOrden);

      expect(esquema.secciones.map((seccion) => seccion.titulo.charAt(0))).toEqual([
        '1',
        '2',
        '3',
        '4',
        '5',
        '6',
        '7',
        '8',
      ]);
    });

    it('conserva el orden recibido cuando no hay forma de conocer otro', () => {
      const esquema = resolverEsquema({
        type: 'object',
        properties: { b: { type: 'string' }, a: { type: 'string' } },
      });

      expect(esquema.raiz.hijos.map((hijo) => hijo.clave)).toEqual(['b', 'a']);
    });
  });

  it('humaniza claves y valores de enumerados', () => {
    expect(humanizar('NO_PRESENTA')).toBe('No presenta');
    expect(humanizar('perdida_petreos_golpe')).toBe('Perdida petreos golpe');
  });
});
