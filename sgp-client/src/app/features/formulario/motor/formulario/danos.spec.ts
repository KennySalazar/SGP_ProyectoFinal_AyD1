import { AbstractControl, FormGroup } from '@angular/forms';
import { beforeEach, describe, expect, it } from 'vitest';
import { NodoFormulario, NodoObjeto } from '../esquema/modelos';
import { resolverEsquema } from '../esquema/resolutor';
import { ESQUEMA_SIECA } from '../testing/esquema-sieca';
import { construirFormulario, FormularioConstruido } from './constructor';
import {
  activacionDe,
  esElementoEvaluable,
  estadoElemento,
  indicadoresDe,
  resumirElementos,
} from './danos';

const esquema = resolverEsquema(ESQUEMA_SIECA);

function nodo(ruta: string): NodoObjeto {
  let actual: NodoFormulario = esquema.raiz;
  for (const parte of ruta.split('/')) {
    const hijo: NodoFormulario | undefined =
      actual.clase === 'objeto' ? actual.hijos.find((h) => h.clave === parte) : undefined;
    if (!hijo) throw new Error(`No existe ${ruta}`);
    actual = hijo.clase === 'arreglo' ? hijo.elemento : hijo;
  }
  return actual as NodoObjeto;
}

function recorrer(actual: NodoFormulario, visitar: (n: NodoFormulario) => void): void {
  visitar(actual);
  if (actual.clase === 'objeto') actual.hijos.forEach((h) => recorrer(h, visitar));
  if (actual.clase === 'arreglo') recorrer(actual.elemento, visitar);
}

const CORTINA = 'subestructura/estribo_entrada/cortina_superior';

describe('indicadores y elementos evaluables del esquema real', () => {
  it('cada indicador declara su valor sano en el esquema', () => {
    const cortina = indicadoresDe(nodo(CORTINA));
    expect(cortina.map((i) => i.clave)).toEqual([
      'grietas_una_direccion',
      'grietas_dos_direcciones',
      'perdida_petreos_golpe',
      'exposicion_acero',
    ]);
    expect(cortina.every((i) => i.sinDanos === 'NO_PRESENTA')).toBe(true);

    const junta = indicadoresDe(nodo('elementos_no_estructurales/junta_entrada'));
    expect(junta.map((i) => [i.clave, i.sinDanos])).toEqual([
      ['falta_elemento', false],
      ['junta_limpia', true],
    ]);
    expect(indicadoresDe(nodo('caracteristicas_cauce/estado_cauce'))[0].sinDanos).toEqual([
      'LIMPIO',
    ]);
  });

  it('el material y las medidas describen el elemento: no son indicadores', () => {
    const claves = indicadoresDe(nodo(CORTINA)).map((i) => i.clave);

    for (const descriptivo of ['existencia', 'material', 'altura_m', 'ancho_m', 'otros']) {
      expect(claves).not.toContain(descriptivo);
    }
  });

  it('reconoce los 45 elementos evaluables del formulario', () => {
    let total = 0;
    recorrer(esquema.raiz, (n) => {
      if (n.clase === 'objeto' && n.evaluable) {
        total++;
        expect(esElementoEvaluable(n), n.ruta).toBe(true);
      }
    });

    expect(total).toBe(45);
  });

  it('la existencia es la condición que activa al elemento, cuando la tiene', () => {
    expect(activacionDe(nodo(CORTINA))).toEqual({ clave: 'existencia', valor: 'PRESENTE' });
    expect(activacionDe(nodo('otros/senalizacion/informativa'))).toEqual({
      clave: 'existe',
      valor: true,
    });
    expect(activacionDe(nodo('elementos_no_estructurales/losa_acceso_entrada'))).toEqual({
      clave: 'existencia',
      valor: 'PRESENTE',
    });
    // La socavación y el cauce siempre se evalúan.
    expect(activacionDe(nodo('subestructura/estribo_entrada/socavacion'))).toBeNull();
    expect(activacionDe(nodo('caracteristicas_cauce/estado_cauce'))).toBeNull();
  });
});

describe('«Sin daños en este elemento»', () => {
  let construido: FormularioConstruido;
  const elemento = (ruta: string) => construido.form.get(ruta.split('/').join('.')) as FormGroup;
  const valores = (ruta: string) =>
    Object.fromEntries(
      Object.entries(elemento(ruta).controls).map(([clave, c]) => [
        clave,
        (c as AbstractControl).value,
      ]),
    );
  const estado = (ruta: string) => estadoElemento(nodo(ruta), elemento(ruta), construido.ocultos);

  beforeEach(() => {
    construido = construirFormulario(esquema, null, { generarId: () => 'id' });
  });

  it('los daños inician vacíos: ningún elemento viene marcado (RN-INS-10)', () => {
    expect(estado(CORTINA)).toMatchObject({ tipo: 'pendiente', marcados: 0 });
    expect(valores(CORTINA)['grietas_una_direccion']).toBeNull();
  });

  it('en una sola operación declara presente el elemento y marca sano cada indicador', () => {
    construido.marcarSinDanos(nodo(CORTINA), elemento(CORTINA));

    expect(valores(CORTINA)).toMatchObject({
      existencia: 'PRESENTE',
      grietas_una_direccion: 'NO_PRESENTA',
      grietas_dos_direcciones: 'NO_PRESENTA',
      perdida_petreos_golpe: 'NO_PRESENTA',
      exposicion_acero: 'NO_PRESENTA',
    });
    expect(estado(CORTINA)).toMatchObject({ tipo: 'sano', marcados: 4, total: 4, danos: 0 });
  });

  it('no inventa lo que no es un daño: el material y las medidas siguen sin responder', () => {
    construido.marcarSinDanos(nodo(CORTINA), elemento(CORTINA));

    expect(valores(CORTINA)).toMatchObject({ material: null, altura_m: null, ancho_m: null });
    expect(valores(CORTINA)['otros']).toBeDefined();
    const subestructura = construido.valor()['subestructura'] as Record<
      string,
      Record<string, Record<string, unknown>>
    >;
    expect(subestructura['estribo_entrada']['cortina_superior']).not.toHaveProperty('otros');
  });

  it('si el elemento ya tenía un daño, la acción lo sobrescribe: afirma que está sano', () => {
    elemento(CORTINA).controls['existencia'].setValue('PRESENTE');
    elemento(CORTINA).controls['exposicion_acero'].setValue('SEVERO');

    construido.marcarSinDanos(nodo(CORTINA), elemento(CORTINA));

    expect(valores(CORTINA)['exposicion_acero']).toBe('NO_PRESENTA');
  });

  it('editar cualquier indicador lo deja de marcar como sano y conserva el cambio', () => {
    construido.marcarSinDanos(nodo(CORTINA), elemento(CORTINA));
    expect(estado(CORTINA).tipo).toBe('sano');

    elemento(CORTINA).controls['grietas_una_direccion'].setValue('LEVE');

    expect(estado(CORTINA)).toMatchObject({ tipo: 'con-danos', danos: 1, marcados: 4 });
    expect(valores(CORTINA)['grietas_una_direccion']).toBe('LEVE');
    // Los demás indicadores siguen sanos.
    expect(valores(CORTINA)['exposicion_acero']).toBe('NO_PRESENTA');
  });

  it('deshacer la marca deja el elemento pendiente otra vez', () => {
    construido.marcarSinDanos(nodo(CORTINA), elemento(CORTINA));

    construido.limpiarDanos(nodo(CORTINA), elemento(CORTINA));

    expect(estado(CORTINA).tipo).toBe('pendiente');
    expect(valores(CORTINA)['existencia']).toBe('PRESENTE');
  });

  it('un grupo a medias queda «en proceso», no sano', () => {
    elemento(CORTINA).controls['existencia'].setValue('PRESENTE');
    elemento(CORTINA).controls['grietas_una_direccion'].setValue('NO_PRESENTA');

    expect(estado(CORTINA)).toMatchObject({ tipo: 'en-proceso', marcados: 1, total: 4 });
  });

  it('un elemento que no aplica o está ausente no es pendiente: ya se declaró', () => {
    elemento(CORTINA).controls['existencia'].setValue('NO_APLICA');

    expect(estado(CORTINA)).toMatchObject({ tipo: 'no-presente', noPresente: 'NO_APLICA' });
    elemento(CORTINA).controls['existencia'].setValue('AUSENTE');
    expect(estado(CORTINA)).toMatchObject({ tipo: 'no-presente', noPresente: 'AUSENTE' });
  });

  it('usa el valor sano de cada indicador: «falta elemento» false y «junta limpia» true', () => {
    const junta = 'elementos_no_estructurales/junta_entrada';

    construido.marcarSinDanos(nodo(junta), elemento(junta));

    expect(valores(junta)).toMatchObject({
      existencia: 'PRESENTE',
      falta_elemento: false,
      junta_limpia: true,
    });
    expect(estado(junta).tipo).toBe('sano');
  });

  it('en una losa de acceso en buen estado no pide ni inventa los daños condicionados', () => {
    const losa = 'elementos_no_estructurales/losa_acceso_entrada';

    construido.marcarSinDanos(nodo(losa), elemento(losa));

    expect(valores(losa)).toMatchObject({ buen_estado: true, asentado: null, fisurado: null });
    expect(estado(losa)).toMatchObject({ tipo: 'sano', total: 1 });

    // Si luego se declara con daños, aparecen los indicadores y el elemento deja de ser sano.
    elemento(losa).controls['buen_estado'].setValue(false);
    expect(estado(losa)).toMatchObject({ tipo: 'con-danos', total: 4, marcados: 1 });
  });

  it('la socavación, que no tiene existencia, se marca sin daños con «No hay»', () => {
    const socavacion = 'subestructura/estribo_entrada/socavacion';

    construido.marcarSinDanos(nodo(socavacion), elemento(socavacion));

    expect(valores(socavacion)).toMatchObject({ aguas_arriba: 'NO_HAY', aguas_abajo: 'NO_HAY' });
    expect(estado(socavacion).tipo).toBe('sano');
  });

  it('el cauce (selección múltiple) queda como «Limpio» y la señalización como «Bueno»', () => {
    const cauce = 'caracteristicas_cauce/estado_cauce';
    // El cauce solo se evalúa si el puente cruza un cuerpo de agua.
    construido.form.get('caracteristicas_cauce.aplica')!.setValue(true);
    construido.marcarSinDanos(nodo(cauce), elemento(cauce));
    expect(valores(cauce)['condiciones']).toEqual(['LIMPIO']);
    expect(estado(cauce).tipo).toBe('sano');

    const senal = 'otros/senalizacion/informativa';
    construido.marcarSinDanos(nodo(senal), elemento(senal));
    expect(valores(senal)).toMatchObject({ existe: true, estado: 'BUENO' });
  });

  it('los indicadores numéricos se marcan en cero', () => {
    const pasamanos = 'elementos_no_estructurales/pasamanos_barandal';

    construido.marcarSinDanos(nodo(pasamanos), elemento(pasamanos));

    expect(valores(pasamanos)).toMatchObject({ golpeado_longitud_m: 0, pintura: 'NO_PRESENTA' });
  });

  it('el resumen cuenta los elementos de una sección y cuántos se revisaron', () => {
    const seccion = esquema.secciones.find((s) => s.clave === 'superestructura')!;
    const control = construido.form.controls['superestructura'];
    const antes = resumirElementos(seccion, control, construido.ocultos);

    construido.marcarSinDanos(
      nodo('superestructura/losa_rodamiento'),
      elemento('superestructura/losa_rodamiento'),
    );
    elemento('superestructura/diafragmas_acero').controls['existencia'].setValue('NO_APLICA');
    const despues = resumirElementos(seccion, control, construido.ocultos);

    expect(antes).toEqual({ total: 7, revisados: 0 });
    expect(despues).toEqual({ total: 7, revisados: 2 });
  });
});
