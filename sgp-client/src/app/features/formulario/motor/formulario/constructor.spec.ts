import { AbstractControl, FormArray, FormGroup } from '@angular/forms';
import { beforeEach, describe, expect, it } from 'vitest';
import { NodoObjeto } from '../esquema/modelos';
import { resolverEsquema } from '../esquema/resolutor';
import { ESQUEMA_SIECA } from '../testing/esquema-sieca';
import { llenarFormulario } from '../testing/llenar-formulario';
import { calcularAvance } from './avance';
import { construirFormulario, FormularioConstruido } from './constructor';

const esquema = resolverEsquema(ESQUEMA_SIECA);

function grupo(raiz: FormGroup, ruta: string): FormGroup {
  return ruta.split('/').reduce<AbstractControl>((actual, parte) => {
    const siguiente = (actual as FormGroup).controls[parte];
    if (!siguiente) throw new Error(`No existe ${ruta}`);
    return siguiente;
  }, raiz) as FormGroup;
}

let contador = 0;
const generarId = () => `id-${++contador}`;

describe('construirFormulario con el esquema real', () => {
  let construido: FormularioConstruido;

  beforeEach(() => {
    contador = 0;
    construido = construirFormulario(esquema, null, { generarId });
  });

  it('construye el FormGroup en ejecución con las ocho secciones', () => {
    expect(Object.keys(construido.form.controls)).toEqual(esquema.secciones.map((s) => s.clave));
    expect(construido.form.controls['tramos']).toBeInstanceOf(FormArray);
    expect(construido.form.controls['identificacion']).toBeInstanceOf(FormGroup);
  });

  it('empieza con una instancia de tramo, con identificador generado en el cliente', () => {
    const tramos = construido.form.controls['tramos'] as FormArray;

    expect(tramos.length).toBe(1);
    expect((tramos.at(0) as FormGroup).controls['instancia_id'].value).toBe('id-1');
  });

  it('no precarga ningún dato de daño: lo único con valor es el identificador de instancia', () => {
    const hojas: string[] = [];
    const recorrer = (nodo: unknown, ruta: string) => {
      if (Array.isArray(nodo)) nodo.forEach((item, i) => recorrer(item, `${ruta}[${i}]`));
      else if (typeof nodo === 'object' && nodo !== null) {
        Object.entries(nodo).forEach(([clave, valor]) => recorrer(valor, `${ruta}/${clave}`));
      } else hojas.push(ruta);
    };

    recorrer(construido.valor(), '');

    expect(hojas).toEqual(['/tramos[0]/instancia_id']);
  });

  it('bloquea los datos precargados (solo lectura) y deja editable el resto', () => {
    const identificacion = grupo(construido.form, 'identificacion');
    expect(identificacion.controls['nombre_puente'].disabled).toBe(true);
    expect(identificacion.controls['ruta_pavimentada'].enabled).toBe(true);
  });

  it('exige existencia en cada elemento y oculta sus indicadores hasta responderla', () => {
    const cortina = grupo(construido.form, 'subestructura/estribo_entrada/cortina_superior');

    expect(cortina.controls['existencia'].hasError('requerido')).toBe(true);
    expect(cortina.controls['grietas_una_direccion'].disabled).toBe(true);
    expect(construido.ocultos.has(cortina.controls['grietas_una_direccion'])).toBe(true);
  });

  it('muestra y exige los indicadores cuando el elemento está presente', () => {
    const cortina = grupo(construido.form, 'subestructura/estribo_entrada/cortina_superior');

    cortina.controls['existencia'].setValue('PRESENTE');

    const grietas = cortina.controls['grietas_una_direccion'];
    expect(grietas.enabled).toBe(true);
    expect(construido.ocultos.has(grietas)).toBe(false);
    expect(grietas.hasError('requerido')).toBe(true);
  });

  it('vuelve a ocultar los indicadores si el elemento deja de estar presente', () => {
    const cortina = grupo(construido.form, 'subestructura/estribo_entrada/cortina_superior');

    cortina.controls['existencia'].setValue('PRESENTE');
    cortina.controls['grietas_una_direccion'].setValue('LEVE');
    cortina.controls['existencia'].setValue('NO_APLICA');

    expect(cortina.controls['grietas_una_direccion'].disabled).toBe(true);
    expect(JSON.stringify(construido.valor())).not.toContain('"grietas_una_direccion":"LEVE"');
  });

  it('valida los límites numéricos', () => {
    const datos = grupo(construido.form, 'datos_generales');
    const longitud = datos.controls['longitud_m'];

    longitud.setValue(0);
    expect(longitud.hasError('minimoExclusivo')).toBe(true);
    longitud.setValue(99999);
    expect(longitud.hasError('maximo')).toBe(true);
    longitud.setValue(12.5);
    expect(longitud.errors).toBeNull();
  });

  it('exige enteros en los campos enteros', () => {
    const tramos = construido.form.controls['tramos'] as FormArray;
    const orden = (tramos.at(0) as FormGroup).controls['orden'];

    orden.setValue(1.5);
    expect(orden.hasError('entero')).toBe(true);
    orden.setValue(2);
    expect(orden.errors).toBeNull();
  });

  it('valida la longitud máxima de los textos', () => {
    const ruta = grupo(construido.form, 'identificacion').controls['nombre_puente'];
    ruta.enable();

    ruta.setValue('x'.repeat(201));
    expect(ruta.hasError('longitudMaxima')).toBe(true);
  });

  it('aplica el máximo de la lista cuando incluye LIMPIO (selección excluyente)', () => {
    const cauce = grupo(construido.form, 'caracteristicas_cauce');
    cauce.controls['aplica'].setValue(true);
    const condiciones = grupo(cauce, 'estado_cauce').controls['condiciones'];

    condiciones.setValue(['LIMPIO', 'EROSIONADO']);
    expect(condiciones.hasError('maximoSiContiene')).toBe(true);
    condiciones.setValue(['LIMPIO']);
    expect(condiciones.errors).toBeNull();
    condiciones.setValue([]);
    expect(condiciones.hasError('requerido')).toBe(true);
  });

  it('oculta todo el cauce cuando no aplica y lo muestra cuando aplica', () => {
    const cauce = grupo(construido.form, 'caracteristicas_cauce');

    cauce.controls['aplica'].setValue(false);
    expect(cauce.controls['nombre_rio'].disabled).toBe(true);
    expect(cauce.controls['estado_cauce'].disabled).toBe(true);

    cauce.controls['aplica'].setValue(true);
    expect(cauce.controls['nombre_rio'].enabled).toBe(true);
    expect(cauce.controls['estado_cauce'].enabled).toBe(true);
  });

  it('al volver a mostrar un grupo respeta lo que sus propias condiciones ocultan', () => {
    const cauce = grupo(construido.form, 'caracteristicas_cauce');
    cauce.controls['aplica'].setValue(true);
    cauce.controls['aplica'].setValue(false);
    cauce.controls['aplica'].setValue(true);

    expect(cauce.controls['tipo_cuerpo_agua_otro'].disabled).toBe(true);
    expect(cauce.controls['nombre_rio'].enabled).toBe(true);
  });

  it('al volver a mostrar un grupo no desbloquea sus datos de solo lectura', () => {
    const estribo = grupo(construido.form, 'subestructura/estribo_entrada');
    // `instancia_id` no está aquí; se comprueba con los tramos.
    const tramos = construido.form.controls['tramos'] as FormArray;
    const instancia = tramos.at(0) as FormGroup;

    expect(estribo).toBeTruthy();
    expect(instancia.controls['instancia_id'].disabled).toBe(true);
  });

  it('agrega y quita instancias repetibles, cada una con su identificador', () => {
    const tramos = construido.form.controls['tramos'] as FormArray;
    const nodo = esquema.secciones.find((s) => s.clave === 'tramos');
    if (nodo?.clase !== 'arreglo') throw new Error('tramos debería ser un arreglo');

    const segundo = construido.agregarInstancia(nodo, tramos);

    expect(tramos.length).toBe(2);
    expect(segundo.controls['instancia_id'].value).toBe('id-2');

    segundo.controls['longitud_m'].setValue(30);
    construido.quitarInstancia(tramos, 0);
    expect(tramos.length).toBe(1);
    expect(tramos.at(0).value.longitud_m).toBe(30);
  });

  it('valida el mínimo y el máximo de instancias', () => {
    const tramos = construido.form.controls['tramos'] as FormArray;

    tramos.removeAt(0);
    expect(tramos.hasError('minimoElementos')).toBe(true);
  });

  it('cada instancia repetible mantiene sus propios valores y condiciones', () => {
    const pilas = grupo(construido.form, 'subestructura').controls['pilas'] as FormArray;
    const nodo = esquema.raiz.hijos.find((h) => h.clave === 'subestructura');
    const pilasNodo = (nodo as { hijos: { clave: string }[] }).hijos.find(
      (h) => h.clave === 'pilas',
    );
    expect(pilas.length).toBe(0);
    expect(pilasNodo).toBeDefined();
  });

  it('precarga valores iniciales, incluso en instancias repetibles', () => {
    const conDatos = construirFormulario(
      esquema,
      {
        identificacion: { nombre_puente: 'Puente de prueba', ruta_pavimentada: 'SI' },
        tramos: [
          { orden: 1, longitud_m: 25.5 },
          { orden: 2, longitud_m: 40 },
        ],
      },
      { generarId },
    );

    const identificacion = grupo(conDatos.form, 'identificacion');
    expect(identificacion.controls['nombre_puente'].value).toBe('Puente de prueba');
    expect(identificacion.controls['ruta_pavimentada'].value).toBe('SI');
    expect((conDatos.form.controls['tramos'] as FormArray).length).toBe(2);
    expect((conDatos.valor()['tramos'] as { longitud_m: number }[])[1].longitud_m).toBe(40);
  });

  it('el valor incluye los datos precargados pero omite los campos vacíos', () => {
    const conDatos = construirFormulario(esquema, {
      identificacion: { nombre_puente: 'Puente de prueba', codigo: 'GT-01-0101-0001' },
    });

    const identificacion = conDatos.valor()['identificacion'] as Record<string, unknown>;
    expect(identificacion['nombre_puente']).toBe('Puente de prueba');
    expect(identificacion['codigo']).toBe('GT-01-0101-0001');
    expect(identificacion['ruta_pavimentada']).toBeUndefined();
    expect(identificacion['esviaje']).toBeUndefined();
  });

  it('conserva false como una respuesta válida', () => {
    const identificacion = grupo(construido.form, 'identificacion');
    identificacion.controls['esviaje'].setValue(false);

    expect((construido.valor()['identificacion'] as Record<string, unknown>)['esviaje']).toBe(
      false,
    );
  });

  it('el modo de solo lectura deshabilita todo el formulario y se puede revertir', () => {
    construido.establecerSoloLectura(true);
    expect(construido.form.disabled).toBe(true);
    expect(grupo(construido.form, 'identificacion').controls['ruta_pavimentada'].disabled).toBe(
      true,
    );

    construido.establecerSoloLectura(false);
    expect(grupo(construido.form, 'identificacion').controls['ruta_pavimentada'].enabled).toBe(
      true,
    );

    expect(grupo(construido.form, 'identificacion').controls['nombre_puente'].disabled).toBe(true);
    expect(
      grupo(construido.form, 'subestructura/estribo_entrada/cortina_superior').controls[
        'grietas_una_direccion'
      ].disabled,
    ).toBe(true);
  });
});

describe('avance por sección', () => {
  it('cuenta campos obligatorios completos y avanza al responderlos', () => {
    const construido = construirFormulario(esquema, null, { generarId });
    const seccion = esquema.secciones.find((s) => s.clave === 'datos_generales');
    if (!seccion) throw new Error('falta la sección');
    const control = construido.form.controls['datos_generales'];

    const antes = calcularAvance(seccion, control, construido.ocultos);
    expect(antes.obligatorios).toBeGreaterThan(0);
    expect(antes.completos).toBe(0);

    (control as FormGroup).controls['longitud_m'].setValue(30);
    const despues = calcularAvance(seccion, control, construido.ocultos);
    expect(despues.completos).toBe(1);
  });

  it('los indicadores condicionales solo cuentan cuando son visibles', () => {
    const construido = construirFormulario(esquema, null, { generarId });
    const seccion = esquema.secciones.find((s) => s.clave === 'subestructura');
    if (!seccion) throw new Error('falta la sección');
    const control = construido.form.controls['subestructura'];

    const sinResponder = calcularAvance(seccion, control, construido.ocultos);
    grupo(construido.form, 'subestructura/estribo_entrada/cortina_superior').controls[
      'existencia'
    ].setValue('PRESENTE');
    const presente = calcularAvance(seccion, control, construido.ocultos);

    expect(presente.obligatorios).toBeGreaterThan(sinResponder.obligatorios);
  });

  it('cuenta como inválido un valor que incumple una regla', () => {
    const construido = construirFormulario(esquema, null, { generarId });
    const seccion = esquema.secciones.find((s) => s.clave === 'datos_generales');
    if (!seccion) throw new Error('falta la sección');
    const control = construido.form.controls['datos_generales'] as FormGroup;

    control.controls['longitud_m'].setValue(-5);

    expect(calcularAvance(seccion, control, construido.ocultos).invalidos).toBe(1);
  });
});

describe('cauce no aplicable al recuperar el borrador', () => {
  it('mantiene fuera de validacion los campos ocultos al cargar y volver de solo lectura', () => {
    const construido = construirFormulario(esquema, { caracteristicas_cauce: { aplica: false } });
    const cauce = grupo(construido.form, 'caracteristicas_cauce');
    expect(cauce.valid).toBe(true);
    construido.establecerSoloLectura(true);
    construido.establecerSoloLectura(false);
    expect(cauce.valid).toBe(true);
    expect(cauce.controls['desbordamiento'].disabled).toBe(true);
    expect(construido.valor()['caracteristicas_cauce']).toEqual({ aplica: false });
  });
});

describe('formulario completo', () => {
  it('con todos los campos visibles llenos, el formulario es válido y el valor refleja la estructura', () => {
    const construido = construirFormulario(esquema, null, { generarId });

    llenarFormulario(construido);

    expect(construido.form.valid).toBe(true);
    const valor = construido.valor();
    expect(Object.keys(valor)).toEqual(
      expect.arrayContaining(esquema.secciones.map((s) => s.clave)),
    );
    expect(JSON.stringify(valor).length).toBeGreaterThan(5000);
  });

  it('un formulario recién creado no es válido', () => {
    expect(construirFormulario(esquema).form.valid).toBe(false);
  });
});

describe('objetos opcionales con campos obligatorios', () => {
  const esquemaOpcional = resolverEsquema({
    type: 'object',
    required: ['elemento', 'repetibles'],
    properties: {
      elemento: {
        type: 'object',
        required: ['nombre'],
        properties: {
          nombre: { type: 'string' },
          otros: {
            type: 'object',
            required: ['descripcion', 'severidad'],
            properties: {
              descripcion: { type: 'string' },
              severidad: { type: 'string', enum: ['LEVE', 'SEVERO'] },
            },
          },
        },
      },
      repetibles: {
        type: 'array',
        minItems: 1,
        items: { type: 'object', required: ['nombre'], properties: { nombre: { type: 'string' } } },
      },
    },
  });

  let construido: FormularioConstruido;
  const otros = () => grupo(construido.form, 'elemento/otros');

  beforeEach(() => {
    construido = construirFormulario(esquemaOpcional, null, { generarId });
    construido.form.get('elemento.nombre')!.setValue('Cortina');
    (construido.form.get('repetibles') as FormArray).at(0).get('nombre')!.setValue('Tramo');
  });

  it('un objeto opcional sin llenar no vuelve inválido el formulario ni se envía', () => {
    expect(construido.form.valid).toBe(true);
    expect(construido.valor()['elemento']).toEqual({ nombre: 'Cortina' });
  });

  it('no cuenta como pendientes los obligatorios de un objeto opcional sin llenar', () => {
    const avance = calcularAvance(esquemaOpcional.raiz, construido.form, construido.ocultos);

    expect(avance).toEqual({ obligatorios: 2, completos: 2, invalidos: 0 });
  });

  it('al empezar a llenarlo, exige el resto de sus campos', () => {
    otros().controls['descripcion'].setValue('Fisura transversal');

    expect(construido.form.valid).toBe(false);
    expect(otros().controls['severidad'].errors).toEqual({ requerido: true });
    expect(construido.exigido(otros().controls['severidad'])).toBe(true);
    expect(
      calcularAvance(esquemaOpcional.raiz, construido.form, construido.ocultos).obligatorios,
    ).toBe(4);

    otros().controls['severidad'].setValue('LEVE');

    expect(construido.form.valid).toBe(true);
    expect(construido.valor()['elemento']).toEqual({
      nombre: 'Cortina',
      otros: { descripcion: 'Fisura transversal', severidad: 'LEVE' },
    });
  });

  it('si se vacía de nuevo, deja de exigirse', () => {
    otros().controls['descripcion'].setValue('Fisura transversal');
    expect(construido.form.valid).toBe(false);

    otros().controls['descripcion'].setValue('');

    expect(construido.form.valid).toBe(true);
    expect(construido.exigido(otros().controls['severidad'])).toBe(false);
    expect(otros().controls['severidad'].errors).toBeNull();
  });

  it('exige los campos desde el inicio cuando el objeto llega con datos', () => {
    const conDatos = construirFormulario(esquemaOpcional, {
      elemento: { nombre: 'Cortina', otros: { descripcion: 'Golpe' } },
    });

    expect(conDatos.form.valid).toBe(false);
    expect(conDatos.exigido(grupo(conDatos.form, 'elemento/otros').controls['severidad'])).toBe(
      true,
    );
  });

  it('una instancia de una lista siempre exige sus obligatorios, aunque esté vacía', () => {
    const lista = construido.form.get('repetibles') as FormArray;
    lista.at(0).get('nombre')!.setValue('');

    expect(construido.form.valid).toBe(false);
    expect(construido.valor()['repetibles']).toEqual([{}]);
  });

  it('limpiar descarta lo escrito en el objeto y deja de exigirlo', () => {
    otros().controls['descripcion'].setValue('Fisura transversal');
    otros().controls['severidad'].setValue('LEVE');
    otros().controls['descripcion'].markAsTouched();
    const elemento = esquemaOpcional.raiz.hijos.find(
      (hijo) => hijo.clave === 'elemento',
    ) as NodoObjeto;
    const nodoOtros = elemento.hijos.find((hijo) => hijo.clave === 'otros') as NodoObjeto;

    construido.limpiar(nodoOtros, otros());

    expect(otros().value).toEqual({ descripcion: null, severidad: null });
    expect(otros().touched).toBe(false);
    expect(construido.form.valid).toBe(true);
    expect(construido.valor()['elemento']).toEqual({ nombre: 'Cortina' });
  });

  it('en el formulario SIECA, «Otros (especificar)» de un elemento no bloquea el envío', () => {
    const siec = construirFormulario(esquema, null, { generarId });
    llenarFormulario(siec);
    const otrosSieca = grupo(siec.form, 'subestructura/estribo_entrada/cortina_superior/otros');
    otrosSieca.reset();

    expect(siec.form.status).not.toBe('INVALID');
    const estribo = siec.valor()['subestructura'] as Record<string, Record<string, object>>;
    expect(estribo['estribo_entrada']['cortina_superior']).not.toHaveProperty('otros');
  });
});

describe('avance con identificadores generados', () => {
  it('no cuenta el identificador de instancia entre los campos obligatorios', () => {
    const construido = construirFormulario(esquema, null, { generarId });
    const tramos = esquema.secciones.find((seccion) => seccion.clave === 'tramos')!;

    const avance = calcularAvance(tramos, construido.form.controls['tramos'], construido.ocultos);

    expect(avance).toEqual({ obligatorios: 3, completos: 0, invalidos: 0 });
  });
});

describe('existencia de un elemento y sus medidas', () => {
  let construido: FormularioConstruido;
  const cortina = () => grupo(construido.form, 'subestructura/estribo_entrada/cortina_superior');
  const enElValor = () =>
    (construido.valor()['subestructura'] as Record<string, Record<string, unknown>>)[
      'estribo_entrada'
    ]['cortina_superior'];

  beforeEach(() => {
    construido = construirFormulario(esquema, null, { generarId });
  });

  it('con «No aplica» o «Ausente» no se envían las medidas aunque se hayan escrito', () => {
    cortina().controls['altura_m'].setValue(2.5);
    cortina().controls['ancho_m'].setValue(0.4);

    for (const existencia of ['NO_APLICA', 'AUSENTE']) {
      cortina().controls['existencia'].setValue(existencia);

      expect(enElValor()).toEqual({ existencia });
      expect(construido.ocultos.has(cortina().controls['altura_m'])).toBe(true);
      expect(construido.ocultos.has(cortina().controls['ancho_m'])).toBe(true);
    }
  });

  it('al volver a «Presente» reaparecen las medidas que se habían escrito', () => {
    cortina().controls['existencia'].setValue('PRESENTE');
    cortina().controls['altura_m'].setValue(2.5);
    cortina().controls['existencia'].setValue('AUSENTE');
    expect(enElValor()).toEqual({ existencia: 'AUSENTE' });

    cortina().controls['existencia'].setValue('PRESENTE');

    expect(enElValor()).toMatchObject({ existencia: 'PRESENTE', altura_m: 2.5 });
    expect(construido.ocultos.has(cortina().controls['altura_m'])).toBe(false);
  });

  it('un elemento que no aplica no deja campos obligatorios pendientes', () => {
    const estribo = esquema.secciones.find((seccion) => seccion.clave === 'subestructura')!;
    const antes = calcularAvance(
      estribo,
      construido.form.controls['subestructura'],
      construido.ocultos,
    );

    cortina().controls['existencia'].setValue('NO_APLICA');
    const despues = calcularAvance(
      estribo,
      construido.form.controls['subestructura'],
      construido.ocultos,
    );

    expect(despues.obligatorios).toBe(antes.obligatorios);
    expect(despues.completos).toBe(antes.completos + 1);
    expect(cortina().errors).toBeNull();
  });
});

describe('edicion limitada por observaciones', () => {
  it('bloquea los campos no observados sin excluir sus datos del guardado', () => {
    const form = construirFormulario(
      resolverEsquema({
        type: 'object',
        properties: {
          otros: {
            type: 'object',
            properties: {
              recomendacion: { type: 'string' },
              descripcion: { type: 'string' },
            },
          },
        },
      }),
      { otros: { recomendacion: 'Antes', descripcion: 'Conservar' } },
    );
    form.establecerRutasEditables(['/otros/recomendacion']);
    expect(form.form.get('otros.recomendacion')?.enabled).toBe(true);
    expect(form.form.get('otros.descripcion')?.disabled).toBe(true);
    form.form.get('otros.recomendacion')?.setValue('Corregida');
    expect(form.valor()).toEqual({
      otros: { recomendacion: 'Corregida', descripcion: 'Conservar' },
    });
    form.destruir();
  });

  it('una observacion de seccion habilita sus campos y mantiene bloqueadas las otras secciones', () => {
    const form = construirFormulario(esquema);
    form.establecerRutasEditables(['/otros']);
    expect(form.form.get('otros')?.enabled).toBe(true);
    expect(form.form.get('datos_generales')?.disabled).toBe(true);
    form.destruir();
  });

  it('conserva los campos bloqueados al cambiar una condicion y permite retirar la restriccion', () => {
    const form = construirFormulario(esquema, { caracteristicas_cauce: { aplica: false } });
    form.establecerRutasEditables(['/caracteristicas_cauce']);
    form.form.get('caracteristicas_cauce.aplica')?.setValue(true);
    expect(form.form.get('otros')?.disabled).toBe(true);
    expect(form.form.get('caracteristicas_cauce.aplica')?.enabled).toBe(true);
    form.establecerRutasEditables(null);
    expect(form.form.get('otros')?.enabled).toBe(true);
    form.destruir();
  });
});
