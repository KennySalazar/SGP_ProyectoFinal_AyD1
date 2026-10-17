import { describe, expect, it } from 'vitest';
import { ESQUEMA_SIECA } from '../testing/esquema-sieca';
import { calcularVisibilidad, cumpleCondicion } from './condiciones';
import { NodoObjeto } from './modelos';
import { resolverEsquema } from './resolutor';

const esquema = resolverEsquema(ESQUEMA_SIECA);

function objeto(ruta: string): NodoObjeto {
  let actual: NodoObjeto = esquema.raiz;
  for (const parte of ruta.split('/')) {
    const hijo = actual.hijos.find((candidato) => candidato.clave === parte);
    if (!hijo) throw new Error(`No existe ${ruta}`);
    actual = hijo.clase === 'arreglo' ? hijo.elemento : (hijo as NodoObjeto);
  }
  return actual;
}

const valores = (datos: Record<string, unknown>) => (clave: string) => datos[clave];

describe('cumpleCondicion', () => {
  const condicion = { propiedades: { existencia: { const: 'PRESENTE' } } };

  it('se cumple solo cuando el valor coincide', () => {
    expect(cumpleCondicion(condicion, valores({ existencia: 'PRESENTE' }))).toBe(true);
    expect(cumpleCondicion(condicion, valores({ existencia: 'AUSENTE' }))).toBe(false);
  });

  it('no se cumple mientras la propiedad que decide no tenga valor', () => {
    expect(cumpleCondicion(condicion, valores({}))).toBe(false);
    expect(cumpleCondicion(condicion, valores({ existencia: '' }))).toBe(false);
    expect(cumpleCondicion(condicion, valores({ existencia: null }))).toBe(false);
  });

  it('considera false como una respuesta válida', () => {
    const noExiste = { propiedades: { existe: { const: false } } };
    expect(cumpleCondicion(noExiste, valores({ existe: false }))).toBe(true);
  });

  it('entiende las listas que contienen un valor y las restricciones de enum o distinto de', () => {
    expect(
      cumpleCondicion({ propiedades: { c: { contiene: 'OTRO' } } }, valores({ c: ['OTRO', 'X'] })),
    ).toBe(true);
    expect(cumpleCondicion({ propiedades: { c: { contiene: 'OTRO' } } }, valores({ c: [] }))).toBe(
      false,
    );
    expect(cumpleCondicion({ propiedades: { c: { enum: ['A', 'B'] } } }, valores({ c: 'B' }))).toBe(
      true,
    );
    expect(cumpleCondicion({ propiedades: { c: { distintoDe: 'A' } } }, valores({ c: 'A' }))).toBe(
      false,
    );
  });
});

describe('calcularVisibilidad con el esquema real', () => {
  const cortina = () => objeto('subestructura/estribo_entrada/cortina_superior');

  it('oculta los indicadores de un elemento hasta que se declara su existencia', () => {
    const visibles = calcularVisibilidad(cortina(), valores({}));

    expect(visibles.get('existencia')).toBe(true);
    expect(visibles.get('grietas_una_direccion')).toBe(false);
    expect(visibles.get('material')).toBe(false);
  });

  it('muestra los indicadores cuando el elemento está presente', () => {
    const visibles = calcularVisibilidad(cortina(), valores({ existencia: 'PRESENTE' }));

    expect(visibles.get('grietas_una_direccion')).toBe(true);
    expect(visibles.get('exposicion_acero')).toBe(true);
  });

  it('si el elemento no aplica o está ausente, solo queda su existencia (sin medidas ni «Otros»)', () => {
    for (const existencia of ['NO_APLICA', 'AUSENTE', undefined]) {
      const visibles = calcularVisibilidad(cortina(), valores({ existencia }));

      expect(visibles.get('existencia')).toBe(true);
      for (const campo of ['altura_m', 'ancho_m', 'otros', 'material', 'grietas_una_direccion']) {
        expect(visibles.get(campo), `${campo} con existencia ${existencia}`).toBe(false);
      }
    }
  });

  it('si el elemento está presente, muestra también sus medidas y «Otros»', () => {
    const visibles = calcularVisibilidad(cortina(), valores({ existencia: 'PRESENTE' }));

    expect(visibles.get('altura_m')).toBe(true);
    expect(visibles.get('ancho_m')).toBe(true);
    expect(visibles.get('otros')).toBe(true);
  });

  it('oculta todo lo demás cuando una característica del cauce no aplica (maxProperties 1)', () => {
    const cauce = objeto('caracteristicas_cauce');

    const noAplica = calcularVisibilidad(cauce, valores({ aplica: false }));
    expect(noAplica.get('aplica')).toBe(true);
    expect(noAplica.get('nombre_rio')).toBe(false);
    expect(noAplica.get('tipo_cuerpo_agua')).toBe(false);

    const aplica = calcularVisibilidad(cauce, valores({ aplica: true }));
    expect(aplica.get('nombre_rio')).toBe(true);
    expect(aplica.get('tipo_cuerpo_agua')).toBe(true);
    expect(aplica.get('tipo_cuerpo_agua_otro')).toBe(false);
  });

  it('encadena condiciones: el detalle de OTRO solo aparece si el cauce aplica y se eligió OTRO', () => {
    const cauce = objeto('caracteristicas_cauce');

    const conOtro = calcularVisibilidad(cauce, valores({ aplica: true, tipo_cuerpo_agua: 'OTRO' }));
    expect(conOtro.get('tipo_cuerpo_agua_otro')).toBe(true);

    const sinAplicar = calcularVisibilidad(
      cauce,
      valores({ aplica: false, tipo_cuerpo_agua: 'OTRO' }),
    );
    expect(sinAplicar.get('tipo_cuerpo_agua_otro')).toBe(false);
  });

  it('un dato oculto no activa las condiciones que dependen de él', () => {
    const losa = objeto('elementos_no_estructurales/losa_acceso_entrada');
    // buen_estado conserva un valor, pero el elemento no está presente: asentado sigue oculto.
    const visibles = calcularVisibilidad(
      losa,
      valores({ existencia: 'NO_APLICA', buen_estado: false }),
    );

    expect(visibles.get('buen_estado')).toBe(false);
    expect(visibles.get('asentado')).toBe(false);
  });

  it('muestra el detalle cuando la losa está presente y no está en buen estado', () => {
    const losa = objeto('elementos_no_estructurales/losa_acceso_entrada');
    const visibles = calcularVisibilidad(
      losa,
      valores({ existencia: 'PRESENTE', buen_estado: false }),
    );

    expect(visibles.get('buen_estado')).toBe(true);
    expect(visibles.get('asentado')).toBe(true);
    expect(visibles.get('bache')).toBe(true);
  });

  it('evalúa condiciones sobre una lista que contiene OTRO', () => {
    const estado = objeto('caracteristicas_cauce/estado_cauce');

    expect(
      calcularVisibilidad(estado, valores({ condiciones: ['EROSIONADO'] })).get('otro_descripcion'),
    ).toBe(false);
    expect(
      calcularVisibilidad(estado, valores({ condiciones: ['OTRO'] })).get('otro_descripcion'),
    ).toBe(true);
  });
});
