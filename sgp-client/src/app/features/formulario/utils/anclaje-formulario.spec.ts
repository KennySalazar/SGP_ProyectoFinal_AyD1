import { describe, expect, it } from 'vitest';
import { NodoFormulario } from '../motor/esquema/modelos';
import { resolverEsquema } from '../motor/esquema/resolutor';
import { ESQUEMA_SIECA } from '../motor/testing/esquema-sieca';
import { crearAnclajeFormulario, referenciaCanonicaFormulario } from './anclaje-formulario';

describe('referenciaCanonicaFormulario', () => {
  it.each([
    ['identificacion/fecha_inspeccion', '/identificacion/fecha_inspeccion'],
    ['subestructura', '/subestructura'],
    ['subestructura/estribo_entrada', '/subestructura/estribo_entrada'],
    ['subestructura/pilas/columnas', '/subestructura/pilas/columnas'],
    ['tramos/longitud_m', '/tramos/longitud_m'],
  ])('convierte la ruta interna %s', (ruta, referencia) => {
    expect(referenciaCanonicaFormulario(ruta)).toBe(referencia);
  });

  it.each([
    '/identificacion/fecha_inspeccion',
    '/subestructura',
    '/subestructura/estribo_entrada',
    '/grupo~1especial/campo~0especial',
    '/grupo/literal~01',
  ])('conserva la referencia canónica %s', (ruta) => {
    expect(referenciaCanonicaFormulario(ruta)).toBe(ruta);
  });

  it('escapa las tildes de una ruta interna sin escapar', () => {
    expect(referenciaCanonicaFormulario('grupo~/campo~')).toBe('/grupo~0/campo~0');
  });

  it('escapa barras y tildes dentro de segmentos originales', () => {
    expect(referenciaCanonicaFormulario(['grupo/especial', 'campo~especial'])).toBe(
      '/grupo~1especial/campo~0especial',
    );
    expect(referenciaCanonicaFormulario(['grupo', '/'])).toBe('/grupo/~1');
  });

  it('distingue el separador de una barra literal mediante segmentos explícitos', () => {
    expect(referenciaCanonicaFormulario('grupo/campo')).toBe('/grupo/campo');
    expect(referenciaCanonicaFormulario(['grupo/campo'])).toBe('/grupo~1campo');
  });

  it('no vuelve a escapar el resultado al convertirlo otra vez', () => {
    const referencia = referenciaCanonicaFormulario(['grupo/~', 'campo/~0~1']);
    expect(referencia).toBe('/grupo~1~0/campo~1~00~01');
    expect(referenciaCanonicaFormulario(referencia)).toBe(referencia);
    expect(referenciaCanonicaFormulario(referenciaCanonicaFormulario('grupo~/campo'))).toBe(
      '/grupo~0/campo',
    );
  });

  it('conserva el significado literal de ~0 y ~1 en segmentos originales', () => {
    expect(referenciaCanonicaFormulario(['~0', '~1'])).toBe('/~00/~01');
    expect(referenciaCanonicaFormulario('grupo/~0/~1')).toBe('/grupo/~00/~01');
    expect(referenciaCanonicaFormulario('/grupo/~0/~1')).toBe('/grupo/~0/~1');
  });

  it.each([
    '',
    ' ',
    '/',
    '//grupo',
    'grupo//campo',
    '/grupo//campo',
    'grupo/',
    '/grupo/',
    '/grupo/ ',
    '/grupo/campo~',
    '/grupo/campo~2',
    '/grupo/campo~x',
  ])('rechaza una ruta vacía o mal formada: %j', (ruta) => {
    expect(() => referenciaCanonicaFormulario(ruta)).toThrow('Referencia de formulario inválida');
  });

  it.each([
    'tramos/0/longitud_m',
    '/tramos/0/longitud_m',
    'tramos[0]/longitud_m',
    '/tramos[12]/longitud_m',
  ])('rechaza índices explícitos sin convertirlos en identidad: %s', (ruta) => {
    expect(() => referenciaCanonicaFormulario(ruta)).toThrow();
  });

  it('también rechaza segmentos vacíos o índices en un arreglo de claves', () => {
    for (const segmentos of [[], ['grupo', ''], ['tramos', '0'], ['tramos[0]']]) {
      expect(() => referenciaCanonicaFormulario(segmentos)).toThrow();
    }
  });

  it('comprueba el límite del backend después de aplicar los escapes', () => {
    expect(referenciaCanonicaFormulario('a'.repeat(299))).toHaveLength(300);
    expect(() => referenciaCanonicaFormulario('a'.repeat(300))).toThrow();
    expect(() => referenciaCanonicaFormulario(['~'.repeat(150)])).toThrow();
    expect(() => referenciaCanonicaFormulario('/' + 'a'.repeat(300))).toThrow();
  });

  it('no modifica los segmentos de entrada', () => {
    const segmentos = Object.freeze(['grupo/especial', 'campo~especial']);
    referenciaCanonicaFormulario(segmentos);
    expect(segmentos).toEqual(['grupo/especial', 'campo~especial']);
  });

  it('adapta todos los nodos del esquema existente sin alterar sus rutas internas', () => {
    const esquema = resolverEsquema(ESQUEMA_SIECA);
    const anterior = JSON.stringify(esquema);
    const clases = new Set<string>();
    let evaluables = 0;
    function recorrer(nodo: NodoFormulario): void {
      if (nodo.ruta) {
        expect(referenciaCanonicaFormulario(nodo.ruta)).toBe('/' + nodo.ruta);
        clases.add(nodo.clase);
        if (nodo.evaluable) evaluables++;
      }
      if (nodo.clase === 'objeto') nodo.hijos.forEach(recorrer);
      if (nodo.clase === 'arreglo') recorrer(nodo.elemento);
    }
    recorrer(esquema.raiz);
    expect(clases).toEqual(new Set(['objeto', 'campo', 'arreglo']));
    expect(evaluables).toBeGreaterThan(0);
    expect(esquema.secciones).toHaveLength(8);
    expect(JSON.stringify(esquema)).toBe(anterior);
  });
});

describe('crearAnclajeFormulario', () => {
  it('preserva exactamente el UUID recibido y no agrega índices a la ruta', () => {
    const instancia = '019ABC00-0000-7000-8000-000000000001';
    expect(crearAnclajeFormulario('subestructura/pilas/columnas', instancia)).toEqual({
      elementoRef: '/subestructura/pilas/columnas',
      instanciaElementoId: instancia,
    });
  });

  it('permite referencias sin instancia para campos, secciones y elementos evaluables', () => {
    expect(crearAnclajeFormulario('subestructura/estribo_entrada')).toEqual({
      elementoRef: '/subestructura/estribo_entrada',
    });
    expect(crearAnclajeFormulario('subestructura')).not.toHaveProperty('instanciaElementoId');
  });

  it('conserva la referencia y la instancia de un anclaje ya canónico', () => {
    const instancia = '01900000-0000-7000-8000-000000000001';
    const anclaje = crearAnclajeFormulario(['grupo/especial', 'campo~'], instancia);
    expect(crearAnclajeFormulario(anclaje.elementoRef, anclaje.instanciaElementoId)).toEqual(
      anclaje,
    );
  });

  it('rechaza una ruta con índice incluso si se proporciona un UUID separado', () => {
    expect(() =>
      crearAnclajeFormulario('tramos/0/longitud_m', '01900000-0000-7000-8000-000000000001'),
    ).toThrow();
  });
});
