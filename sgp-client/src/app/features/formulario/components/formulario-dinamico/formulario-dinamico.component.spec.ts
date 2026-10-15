import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { EsquemaJson, NodoFormulario } from '../../motor/esquema/modelos';
import { ESQUEMA_SIECA } from '../../motor/testing/esquema-sieca';
import { traduccionesDePrueba } from '../../motor/testing/traducciones';
import { RENDERIZADORES_NODO } from '../renderizadores';
import { SeccionApoyo } from '../secciones-apoyo';
import { FormularioDinamicoComponent } from './formulario-dinamico.component';

type Esquema = Record<string, unknown>;

const TITULOS_SIECA = Object.values(
  (ESQUEMA_SIECA as { properties: Record<string, { title?: string; $ref?: string }> }).properties,
).map((seccion) => seccion.title as string);

async function crear(
  esquema: EsquemaJson,
  entradas: {
    valoresIniciales?: Esquema | null;
    soloLectura?: boolean;
    seccionesApoyo?: SeccionApoyo[];
  } = {},
  proveedores: unknown[] = [],
): Promise<ComponentFixture<FormularioDinamicoComponent>> {
  TestBed.configureTestingModule({
    imports: [FormularioDinamicoComponent, traduccionesDePrueba()],
    providers: proveedores as never[],
  });
  const fixture = TestBed.createComponent(FormularioDinamicoComponent);
  fixture.componentRef.setInput('esquema', esquema);
  if (entradas.valoresIniciales !== undefined) {
    fixture.componentRef.setInput('valoresIniciales', entradas.valoresIniciales);
  }
  if (entradas.soloLectura !== undefined) {
    fixture.componentRef.setInput('soloLectura', entradas.soloLectura);
  }
  if (entradas.seccionesApoyo !== undefined) {
    fixture.componentRef.setInput('seccionesApoyo', entradas.seccionesApoyo);
  }
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

const raiz = (fixture: ComponentFixture<unknown>) => fixture.nativeElement as HTMLElement;
const botones = (fixture: ComponentFixture<unknown>, texto: string) =>
  Array.from(raiz(fixture).querySelectorAll<HTMLButtonElement>('button')).filter((boton) =>
    boton.textContent?.includes(texto),
  );
const escribir = (entrada: HTMLInputElement, valor: string) => {
  entrada.value = valor;
  entrada.dispatchEvent(new Event('input'));
  entrada.dispatchEvent(new Event('blur'));
};
const campo = (fixture: ComponentFixture<unknown>, ruta: string) =>
  raiz(fixture).querySelector<HTMLElement>(`[data-ruta="${ruta}"]`);

/** Declara «Presente» un elemento: solo entonces se piden sus medidas, daños y «Otros». */
function declararPresente(fixture: ComponentFixture<unknown>, rutaElemento: string): void {
  campo(fixture, `${rutaElemento}/existencia`)!
    .querySelector<HTMLInputElement>('input[type="radio"]')!
    .click();
  fixture.detectChanges();
}

const CORTINA = 'subestructura/estribo_entrada/cortina_superior';

async function ir(fixture: ComponentFixture<unknown>, texto: string): Promise<void> {
  botones(fixture, texto)[0].click();
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('FormularioDinamicoComponent con el esquema SIECA', () => {
  let fixture: ComponentFixture<FormularioDinamicoComponent>;

  beforeEach(async () => {
    fixture = await crear(ESQUEMA_SIECA, {
      valoresIniciales: { identificacion: { nombre_puente: 'Puente Demo', ruta: 'CA-1' } },
    });
  });

  it('genera las ocho secciones a partir del esquema, sin preguntas fijas', () => {
    const titulos = Array.from(raiz(fixture).querySelectorAll('.seccion-titulo')).map((e) =>
      e.textContent?.trim(),
    );

    expect(titulos).toEqual(TITULOS_SIECA);
    expect(titulos).toHaveLength(8);
  });

  it('muestra solo la sección activa', () => {
    expect(raiz(fixture).querySelector('h2')?.textContent).toContain(
      '1. Identificación del puente',
    );
    expect(campo(fixture, 'identificacion/fecha_inspeccion')).not.toBeNull();
    expect(campo(fixture, 'datos_generales/longitud_m')).toBeNull();
  });

  it('muestra los datos precargados bloqueados y marcados', () => {
    const nombre = campo(fixture, 'identificacion/nombre_puente');
    const entrada = nombre?.querySelector('input') as HTMLInputElement;

    expect(entrada.value).toBe('Puente Demo');
    expect(entrada.disabled).toBe(true);
    expect(nombre?.textContent).toContain('Dato precargado');
  });

  it('usa el control que corresponde a cada tipo de dato', () => {
    const fecha = campo(fixture, 'identificacion/fecha_inspeccion');
    expect(fecha?.querySelector('input[type="date"]')).not.toBeNull();

    const pavimentada = campo(fixture, 'identificacion/ruta_pavimentada');
    // Pocas opciones cortas: botones de un toque, no una lista desplegable.
    expect(pavimentada?.querySelectorAll('input[type="radio"]')).toHaveLength(3);
    expect(pavimentada?.querySelector('p-select')).toBeNull();

    const esviaje = campo(fixture, 'identificacion/esviaje');
    expect(esviaje?.querySelectorAll('input[type="radio"]')).toHaveLength(2);
  });

  it('marca los obligatorios y los cuenta por sección', () => {
    const fecha = campo(fixture, 'identificacion/fecha_inspeccion');
    expect(fecha?.querySelector('.marca-obligatorio')).not.toBeNull();

    const avance = raiz(fixture).querySelector('.seccion')?.textContent ?? '';
    expect(avance).toMatch(/\d+ de \d+ obligatorios/);
  });

  it('conserva los valores al navegar entre secciones', async () => {
    const entrada = () =>
      campo(fixture, 'identificacion/fecha_inspeccion')?.querySelector('input') as HTMLInputElement;
    escribir(entrada(), '2026-03-05');
    fixture.detectChanges();

    await ir(fixture, 'Sección siguiente');
    expect(raiz(fixture).querySelector('h2')?.textContent).toContain('2. Datos generales');
    expect(campo(fixture, 'identificacion/fecha_inspeccion')).toBeNull();

    await ir(fixture, 'Sección anterior');
    expect(entrada().value).toBe('2026-03-05');
    expect(fixture.componentInstance.valor()['identificacion']).toMatchObject({
      fecha_inspeccion: '2026-03-05',
    });
  });

  it('muestra el error en español junto al campo cuando el valor incumple el rango', async () => {
    await ir(fixture, 'Sección siguiente');
    const numero = campo(fixture, 'datos_generales/numero_tramos');
    const entrada = numero?.querySelector('input') as HTMLInputElement;

    escribir(entrada, '500');
    fixture.detectChanges();

    const mensaje = numero?.querySelector('.error');
    expect(mensaje?.textContent).toContain('100');
    expect(mensaje?.getAttribute('role')).toBe('alert');
    expect(entrada.getAttribute('aria-invalid')).toBe('true');
    expect(entrada.getAttribute('aria-describedby')).toBe(mensaje?.id);
  });

  it('no muestra errores en campos que todavía no se tocaron', () => {
    expect(raiz(fixture).querySelector('.error')).toBeNull();
  });

  it('revisar() marca lo que falta, lleva a la primera sección incompleta y enfoca el primer problema', async () => {
    await ir(fixture, 'Sección siguiente');
    expect(raiz(fixture).querySelector('.seccion.errores')).toBeNull();

    const valido = fixture.componentInstance.revisar();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(valido).toBe(false);
    expect(raiz(fixture).querySelector('h2')?.textContent).toContain(
      '1. Identificación del puente',
    );
    expect(raiz(fixture).querySelectorAll('.error').length).toBeGreaterThan(0);

    // Las secciones incompletas dejan de decir «Pendiente» y pasan a «Faltan datos».
    const marcadas = raiz(fixture).querySelectorAll('.seccion.errores');
    expect(marcadas.length).toBeGreaterThan(0);
    expect(marcadas[0].textContent).toContain('Faltan datos');

    const primero = raiz(fixture).querySelector('.campo.invalido')!;
    expect(primero.contains(document.activeElement)).toBe(true);
  });

  it('permite agregar y quitar tramos respetando el mínimo', async () => {
    for (let i = 0; i < 2; i++) await ir(fixture, 'Sección siguiente');
    expect(raiz(fixture).querySelector('h2')?.textContent).toContain('3. Tramos');

    const instancias = () => raiz(fixture).querySelectorAll('.instancia').length;
    expect(instancias()).toBe(1);
    expect(botones(fixture, 'Quitar')[0].disabled).toBe(true);

    botones(fixture, 'Agregar')[0].click();
    fixture.detectChanges();
    expect(instancias()).toBe(2);

    botones(fixture, 'Quitar')[0].click();
    fixture.detectChanges();
    expect(instancias()).toBe(1);
  });

  it('al salir de un campo obligatorio vacío muestra que falta', async () => {
    const fecha = campo(fixture, 'identificacion/fecha_inspeccion');
    const entrada = fecha?.querySelector('input') as HTMLInputElement;
    expect(fecha?.querySelector('.error')).toBeNull();

    entrada.dispatchEvent(new Event('blur'));
    entrada.dispatchEvent(new Event('focusout', { bubbles: true }));
    fixture.detectChanges();

    expect(fecha?.querySelector('.error')?.textContent).toContain('obligatorio');
  });

  it('usa una lista desplegable cuando hay demasiadas opciones para botones', async () => {
    await ir(fixture, 'Sección siguiente');
    const material = campo(fixture, 'datos_generales/material_superestructura');

    expect(material?.querySelector('p-select')).not.toBeNull();
    expect(material?.querySelector('input[type="radio"]')).toBeNull();
  });

  it('desmarca una opción al pulsarla de nuevo, incluso en un campo obligatorio', () => {
    const esviaje = campo(fixture, 'identificacion/esviaje')!;
    const [si] = Array.from(esviaje.querySelectorAll<HTMLInputElement>('input[type="radio"]'));
    const valor = () =>
      fixture.componentInstance.valor()['identificacion'] as Record<string, unknown>;

    si.click();
    fixture.detectChanges();
    expect(valor()['esviaje']).toBe(true);
    expect(si.checked).toBe(true);

    si.click();
    fixture.detectChanges();

    expect(valor()).not.toHaveProperty('esviaje');
    expect(si.checked).toBe(false);
    expect(esviaje.querySelector('.segmento.marcado')).toBeNull();
  });

  it('ofrece limpiar la selección también en los campos obligatorios', () => {
    const esviaje = campo(fixture, 'identificacion/esviaje')!;
    expect(esviaje.querySelector('.quitar')).toBeNull();

    esviaje.querySelector<HTMLInputElement>('input[type="radio"]')!.click();
    fixture.detectChanges();
    esviaje.querySelector<HTMLButtonElement>('.quitar')!.click();
    fixture.detectChanges();

    expect(esviaje.querySelector('.segmento.marcado')).toBeNull();
    expect(esviaje.querySelector('.quitar')).toBeNull();
  });

  it('descarta un bloque opcional entero con «Limpiar este bloque»', async () => {
    for (let i = 0; i < 3; i++) await ir(fixture, 'Sección siguiente'); // 4. Sub-estructura
    declararPresente(fixture, CORTINA);

    const bloque = raiz(fixture).querySelector<HTMLDetailsElement>('details.opcional')!;
    expect(bloque.querySelector('.limpiar-bloque')).toBeNull();

    escribir(bloque.querySelector('textarea, input[type="text"]') as HTMLInputElement, 'Fisura');
    bloque.querySelector<HTMLInputElement>('input[type="radio"]')!.click(); // una severidad
    fixture.detectChanges();
    expect(bloque.open).toBe(true);

    bloque.querySelector<HTMLButtonElement>('.limpiar-bloque')!.click();
    fixture.detectChanges();

    expect(bloque.querySelector('.limpiar-bloque')).toBeNull();
    expect(bloque.querySelector('.segmento.marcado')).toBeNull();
    expect((bloque.querySelector('textarea, input[type="text"]') as HTMLInputElement).value).toBe(
      '',
    );
    expect(bloque.querySelectorAll('.marca-obligatorio')).toHaveLength(0);
  });

  it('explica las opciones de la existencia con una ayuda plegable', async () => {
    for (let i = 0; i < 3; i++) await ir(fixture, 'Sección siguiente'); // 4. Sub-estructura
    const existencia = campo(fixture, `${CORTINA}/existencia`)!;
    const boton = existencia.querySelector<HTMLButtonElement>('.ayuda-boton')!;
    const ayuda = existencia.querySelector<HTMLElement>('.ayuda')!;

    // Plegada al inicio.
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(boton.getAttribute('aria-controls')).toBe(ayuda.id);
    expect(boton.getAttribute('aria-label')).toBe('¿Qué significa cada opción?');
    expect(ayuda.hidden).toBe(true);

    boton.click();
    fixture.detectChanges();

    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(ayuda.hidden).toBe(false);
    const terminos = Array.from(ayuda.querySelectorAll('dt')).map((t) => t.textContent);
    expect(terminos).toEqual(['Presente', 'No aplica', 'Ausente']);
    expect(ayuda.textContent).toContain('El diseño del puente no incluye este elemento');
    expect(ayuda.textContent).toContain('falta por completo');

    boton.click();
    fixture.detectChanges();
    expect(ayuda.hidden).toBe(true);
  });

  it('cada ayuda se abre por separado y no aparece en campos que no la necesitan', async () => {
    for (let i = 0; i < 3; i++) await ir(fixture, 'Sección siguiente');
    const otra = 'subestructura/estribo_salida/cortina_superior';

    campo(fixture, `${CORTINA}/existencia`)!
      .querySelector<HTMLButtonElement>('.ayuda-boton')!
      .click();
    fixture.detectChanges();

    expect(
      campo(fixture, `${CORTINA}/existencia`)!.querySelector<HTMLElement>('.ayuda')!.hidden,
    ).toBe(false);
    expect(campo(fixture, `${otra}/existencia`)!.querySelector<HTMLElement>('.ayuda')!.hidden).toBe(
      true,
    );

    // Una severidad o un Sí/No no tienen explicación por opción.
    declararPresente(fixture, CORTINA);
    expect(
      campo(fixture, `${CORTINA}/grietas_una_direccion`)!.querySelector('.ayuda-boton'),
    ).toBeNull();
    expect(raiz(fixture).querySelector('[data-ruta="otros"] .ayuda-boton')).toBeNull();
  });

  it('responde una severidad con un solo toque', async () => {
    await ir(fixture, 'Sección siguiente');
    await ir(fixture, 'Sección siguiente');
    await ir(fixture, 'Sección siguiente'); // 4. Sub-estructura
    const existencia = campo(fixture, 'subestructura/estribo_entrada/cortina_superior/existencia')!;

    existencia.querySelector<HTMLInputElement>('input[type="radio"]')!.click(); // Presente
    fixture.detectChanges();

    const severidad = campo(
      fixture,
      'subestructura/estribo_entrada/cortina_superior/grietas_una_direccion',
    )!;
    const opciones = severidad.querySelectorAll<HTMLInputElement>('input[type="radio"]');
    expect(opciones).toHaveLength(4);
    opciones[1].click();
    fixture.detectChanges();

    expect(severidad.querySelector('.segmento.marcado')?.textContent).toContain('Leve');
  });

  it('rotula cada campo como obligatorio u opcional y explica el asterisco', async () => {
    expect(raiz(fixture).querySelector('.leyenda')?.textContent).toContain('Campo obligatorio');

    // Precargado: no se edita, así que no se rotula.
    const kilometraje = campo(fixture, 'identificacion/kilometraje')!;
    expect(kilometraje.textContent).not.toContain('(opcional)');

    await ir(fixture, 'Sección siguiente');
    const longitud = campo(fixture, 'datos_generales/longitud_m')!;
    expect(longitud.querySelector('.marca-obligatorio')).not.toBeNull();
    expect(longitud.textContent).not.toContain('(opcional)');

    const carga = campo(fixture, 'datos_generales/carga_diseno')!;
    expect(carga.querySelector('.marca-obligatorio')).toBeNull();
    expect(carga.textContent).toContain('(opcional)');
  });

  it('las medidas de un elemento solo aparecen si está presente y se rotulan como opcionales', async () => {
    for (let i = 0; i < 3; i++) await ir(fixture, 'Sección siguiente'); // 4. Sub-estructura

    // Sin existencia, o con «No aplica» / «Ausente», solo se responde la existencia.
    expect(campo(fixture, `${CORTINA}/altura_m`)).toBeNull();
    const opciones = campo(fixture, `${CORTINA}/existencia`)!.querySelectorAll<HTMLInputElement>(
      'input[type="radio"]',
    );
    expect(opciones).toHaveLength(3); // Presente, No aplica, Ausente
    for (const noPresente of [opciones[1], opciones[2]]) {
      noPresente.click();
      fixture.detectChanges();
      expect(campo(fixture, `${CORTINA}/altura_m`)).toBeNull();
      expect(campo(fixture, `${CORTINA}/ancho_m`)).toBeNull();
      expect(raiz(fixture).querySelector(`[data-ruta="${CORTINA}/otros"]`)).toBeNull();
    }

    declararPresente(fixture, CORTINA);

    expect(campo(fixture, `${CORTINA}/altura_m`)!.textContent).toContain('(opcional)');
    expect(campo(fixture, `${CORTINA}/ancho_m`)!.textContent).toContain('(opcional)');
    expect(campo(fixture, `${CORTINA}/existencia`)!.textContent).not.toContain('(opcional)');
  });

  it('las medidas escritas con el elemento presente se conservan si se vuelve a declarar presente', async () => {
    for (let i = 0; i < 3; i++) await ir(fixture, 'Sección siguiente');
    declararPresente(fixture, CORTINA);
    escribir(
      campo(fixture, `${CORTINA}/altura_m`)!.querySelector('input') as HTMLInputElement,
      '3',
    );

    campo(fixture, `${CORTINA}/existencia`)!
      .querySelectorAll<HTMLInputElement>('input[type="radio"]')[2]
      .click(); // Ausente
    fixture.detectChanges();
    expect(campo(fixture, `${CORTINA}/altura_m`)).toBeNull();

    campo(fixture, `${CORTINA}/existencia`)!
      .querySelectorAll<HTMLInputElement>('input[type="radio"]')[0]
      .click(); // Presente otra vez
    fixture.detectChanges();

    expect(
      (campo(fixture, `${CORTINA}/altura_m`)!.querySelector('input') as HTMLInputElement).value,
    ).toBe('3');
  });

  it('responde Sí o No con un solo toque', () => {
    const esviaje = campo(fixture, 'identificacion/esviaje');
    const [si, no] = Array.from(esviaje!.querySelectorAll<HTMLInputElement>('input[type="radio"]'));
    const valor = () =>
      (fixture.componentInstance.valor()['identificacion'] as Record<string, unknown>)['esviaje'];

    si.click();
    fixture.detectChanges();
    expect(valor()).toBe(true);
    expect(esviaje?.querySelector('.segmento.marcado')?.textContent).toContain('Sí');

    no.click();
    fixture.detectChanges();
    expect(valor()).toBe(false);
  });

  it('presenta un nombre singular para cada elemento de una lista', async () => {
    for (let i = 0; i < 2; i++) await ir(fixture, 'Sección siguiente');

    expect(botones(fixture, 'Agregar Tramo')).toHaveLength(1);
    expect(raiz(fixture).querySelector('.instancia strong')?.textContent).toContain('Tramo 1');
    expect(raiz(fixture).textContent).not.toContain('Tramos 1');
  });

  it('pliega los objetos opcionales y no exige sus campos hasta que se empiezan a llenar', async () => {
    await ir(fixture, 'Sección siguiente'); // 2
    await ir(fixture, 'Sección siguiente'); // 3
    await ir(fixture, 'Sección siguiente'); // 4. Sub-estructura
    expect(raiz(fixture).querySelector('details.opcional')).toBeNull(); // aún sin «Presente»
    declararPresente(fixture, CORTINA);

    const opcional = raiz(fixture).querySelector<HTMLDetailsElement>('details.opcional')!;
    expect(opcional.open).toBe(false);
    expect(opcional.querySelectorAll('.marca-obligatorio')).toHaveLength(0);
    expect(opcional.textContent).toContain('opcional');

    const descripcion = opcional.querySelector('textarea, input[type="text"]') as HTMLInputElement;
    escribir(descripcion, 'Fisura en la cortina');
    fixture.detectChanges();

    expect(opcional.open).toBe(true);
    expect(opcional.querySelectorAll('.marca-obligatorio').length).toBeGreaterThan(0);
  });

  it('muestra los campos condicionales solo cuando se cumple su condición', async () => {
    await ir(fixture, 'Sección siguiente');
    const origen = () => campo(fixture, 'datos_generales/tipo_cruce_otro');
    expect(origen()).toBeNull();

    fixture.componentInstance['construido']()!
      .form.get('datos_generales.tipo_cruce')!
      .setValue('OTRO');
    fixture.detectChanges();

    expect(origen()).not.toBeNull();
  });
});

describe('FormularioDinamicoComponent: reparto del espacio', () => {
  it('da toda la fila a los grupos de grupos, las listas y los textos largos', async () => {
    const fixture = await crear(ESQUEMA_SIECA);
    for (let i = 0; i < 5; i++) await ir(fixture, 'Sección siguiente'); // 6. Elementos no estructurales

    const anchos = Array.from(
      raiz(fixture).querySelectorAll<HTMLElement>('app-nodo-formulario.ancho-completo'),
    ).map((nodo) => nodo.querySelector('[data-ruta]')?.getAttribute('data-ruta'));

    // «Obras de protección» agrupa dos obras (aguas arriba y abajo): ocupa la fila y las reparte.
    expect(anchos).toContain('elementos_no_estructurales/obras_proteccion');
    // Una observación larga no queda en una columna angosta con espacio vacío debajo.
    expect(anchos.some((ruta) => ruta?.endsWith('observaciones'))).toBe(true);
    // Los campos pequeños y los elementos con sus propios campos siguen en columnas.
    expect(anchos).not.toContain('elementos_no_estructurales/pasamanos_barandal');
  });
});

describe('FormularioDinamicoComponent: reparto del espacio en «Otros»', () => {
  it('reparte la señalización en columnas y no deja a medias el grupo de recomendaciones', async () => {
    const fixture = await crear(ESQUEMA_SIECA);
    for (let i = 0; i < 7; i++) await ir(fixture, 'Sección siguiente'); // 8. Otros

    const anchos = Array.from(
      raiz(fixture).querySelectorAll<HTMLElement>('app-nodo-formulario.ancho-completo'),
    ).map((nodo) => nodo.querySelector('[data-ruta]')?.getAttribute('data-ruta'));

    expect(anchos).toContain('otros/senalizacion');
    expect(anchos).toContain('otros/recomendaciones');
  });
});

describe('FormularioDinamicoComponent en modo de solo lectura', () => {
  it('muestra el aviso, bloquea los campos y no permite agregar elementos', async () => {
    const fixture = await crear(ESQUEMA_SIECA, { soloLectura: true });

    expect(raiz(fixture).textContent).toContain('solo lectura');
    expect(raiz(fixture).querySelector('.leyenda')).toBeNull();
    const entradas = raiz(fixture).querySelectorAll<HTMLInputElement>('input');
    expect(entradas.length).toBeGreaterThan(0);
    entradas.forEach((entrada) => expect(entrada.disabled).toBe(true));

    for (let i = 0; i < 2; i++) await ir(fixture, 'Sección siguiente');
    expect(botones(fixture, 'Agregar')[0].disabled).toBe(true);
  });

  it('vuelve a ser editable al salir del modo de solo lectura', async () => {
    const fixture = await crear(ESQUEMA_SIECA, { soloLectura: true });

    fixture.componentRef.setInput('soloLectura', false);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const fecha = campo(fixture, 'identificacion/fecha_inspeccion')?.querySelector('input');
    expect(fecha?.disabled).toBe(false);
  });

  it('usa la estructura del esquema que recibe, aunque sea de otra versión', async () => {
    const antiguo = {
      type: 'object',
      properties: {
        unica: {
          title: 'Sección de la versión anterior',
          type: 'object',
          properties: { dato: { title: 'Dato antiguo', type: 'string' } },
        },
      },
    };
    const fixture = await crear(antiguo, {
      soloLectura: true,
      valoresIniciales: { unica: { dato: 'conservado' } },
    });

    expect(raiz(fixture).querySelectorAll('.seccion')).toHaveLength(1);
    expect(raiz(fixture).textContent).toContain('Dato antiguo');
    expect(raiz(fixture).querySelector<HTMLInputElement>('input')?.value).toBe('conservado');
  });
});

describe('FormularioDinamicoComponent con un esquema sintético', () => {
  const esquema: EsquemaJson = {
    type: 'object',
    properties: {
      tipos: {
        title: 'Tipos',
        type: 'object',
        required: ['texto'],
        properties: {
          texto: { title: 'Texto', type: 'string', maxLength: 50 },
          largo: { title: 'Texto largo', type: 'string', maxLength: 500 },
          numero: { title: 'Número', type: 'number', minimum: 1, maximum: 9 },
          fecha: { title: 'Fecha', type: 'string', format: 'date' },
          si_no: { title: 'Sí o no', type: 'boolean' },
          lista: { title: 'Lista', enum: ['A', 'B'] },
          muchas: { title: 'Muchas', enum: ['UNO', 'DOS', 'TRES', 'CUATRO', 'CINCO'] },
          varios: {
            title: 'Varios',
            type: 'array',
            uniqueItems: true,
            items: { enum: ['LIMPIO', 'ARENA'] },
          },
        },
      },
    },
  };

  it('dibuja cada tipo con su control', async () => {
    const fixture = await crear(esquema);

    expect(campo(fixture, 'tipos/texto')?.querySelector('input[type="text"]')).not.toBeNull();
    expect(campo(fixture, 'tipos/largo')?.querySelector('textarea')).not.toBeNull();
    expect(campo(fixture, 'tipos/numero')?.querySelector('input[type="number"]')).not.toBeNull();
    expect(campo(fixture, 'tipos/fecha')?.querySelector('input[type="date"]')).not.toBeNull();
    expect(campo(fixture, 'tipos/si_no')?.querySelectorAll('input[type="radio"]')).toHaveLength(2);
    expect(campo(fixture, 'tipos/lista')?.querySelectorAll('input[type="radio"]')).toHaveLength(2);
    expect(campo(fixture, 'tipos/muchas')?.querySelector('p-select')).not.toBeNull();
    expect(campo(fixture, 'tipos/varios')?.querySelectorAll('input[type="checkbox"]')).toHaveLength(
      2,
    );
  });

  it('permite quitar la respuesta de un Sí/No opcional', async () => {
    const fixture = await crear(esquema);
    const campoSiNo = campo(fixture, 'tipos/si_no')!;
    expect(campoSiNo.querySelector('.quitar')).toBeNull();

    campoSiNo.querySelector<HTMLInputElement>('input[type="radio"]')!.click();
    fixture.detectChanges();
    const quitar = campoSiNo.querySelector<HTMLButtonElement>('.quitar')!;
    expect(quitar.textContent).toContain('Limpiar selección');

    quitar.click();
    fixture.detectChanges();

    // Sin respuesta, no queda nada que enviar: ni siquiera el grupo opcional que lo contiene.
    expect(fixture.componentInstance.valor()).toEqual({});
    expect(campoSiNo.querySelector('.segmento.marcado')).toBeNull();
  });

  it('acumula las casillas marcadas en el orden de las opciones', async () => {
    const fixture = await crear(esquema);
    const [limpio, arena] = Array.from(
      campo(fixture, 'tipos/varios')!.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'),
    );

    arena.click();
    limpio.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.valor()['tipos']).toMatchObject({
      varios: ['LIMPIO', 'ARENA'],
    });

    arena.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.valor()['tipos']).toMatchObject({ varios: ['LIMPIO'] });
  });

  it('traduce las etiquetas de las opciones', async () => {
    const fixture = await crear(esquema);

    expect(campo(fixture, 'tipos/varios')?.textContent).toContain('Limpio');
  });

  it('avisa si el esquema no se puede interpretar', async () => {
    const fixture = await crear({ properties: { x: { $ref: '#/$defs/noExiste' } } });

    expect(raiz(fixture).querySelector('[role="alert"]')?.textContent).toContain(
      'No se puede mostrar el formulario',
    );
  });
});

describe('FormularioDinamicoComponent: una versión nueva del formulario (DT-FE-07)', () => {
  /** Copia del esquema SIECA con una pregunta más y una sección más, como haría una versión nueva. */
  function versionNueva(): EsquemaJson {
    type Propiedades = { properties: Record<string, Record<string, unknown>> };
    const esquema = JSON.parse(JSON.stringify(ESQUEMA_SIECA)) as Propiedades;
    (esquema.properties['datos_generales'] as unknown as Propiedades).properties[
      'estado_drenaje_lateral'
    ] = {
      title: '¿Estado del drenaje lateral?',
      type: 'string',
      enum: ['BUENO', 'MALO'],
    };
    esquema.properties['anexos'] = {
      title: '9. Anexos',
      type: 'object',
      properties: { croquis: { title: 'Descripción del croquis', type: 'string', maxLength: 200 } },
    };
    return esquema;
  }

  it('muestra la pregunta adicional sin cambiar ni desplegar código', async () => {
    const fixture = await crear(versionNueva());
    await ir(fixture, 'Sección siguiente'); // 2. Datos generales

    const pregunta = campo(fixture, 'datos_generales/estado_drenaje_lateral');
    expect(pregunta).not.toBeNull();
    expect(pregunta?.textContent).toContain('¿Estado del drenaje lateral?');
    expect(pregunta?.querySelectorAll('input[type="radio"]')).toHaveLength(2);

    pregunta!.querySelector<HTMLInputElement>('input[type="radio"]')!.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.valor()['datos_generales']).toMatchObject({
      estado_drenaje_lateral: 'BUENO',
    });
  });

  it('muestra también una sección nueva en la navegación', async () => {
    const fixture = await crear(versionNueva());

    const titulos = Array.from(raiz(fixture).querySelectorAll('.seccion-titulo')).map((e) =>
      e.textContent?.trim(),
    );
    expect(titulos).toHaveLength(9);
    expect(titulos.at(-1)).toBe('9. Anexos');
  });

  it('con la versión anterior la pregunta no existe: cada versión muestra su propia estructura', async () => {
    const fixture = await crear(ESQUEMA_SIECA);
    await ir(fixture, 'Sección siguiente');

    expect(campo(fixture, 'datos_generales/estado_drenaje_lateral')).toBeNull();
  });
});

describe('FormularioDinamicoComponent con secciones de apoyo', () => {
  @Component({
    selector: 'app-apoyo-prueba',
    template: '<p class="apoyo-prueba">{{ texto() }}</p>',
  })
  class ApoyoPruebaComponent {
    readonly texto = input('');
  }

  const apoyo: SeccionApoyo = {
    clave: 'reporte-fotografico',
    titulo: 'Reporte fotográfico',
    descripcion: 'Fotografías del puente.',
    componente: ApoyoPruebaComponent,
    entradas: { texto: 'Contenido de la sección de apoyo' },
  };

  it('las agrega tras las del esquema y las dibuja con las entradas indicadas', async () => {
    const fixture = await crear(ESQUEMA_SIECA, { seccionesApoyo: [apoyo] });

    const titulos = Array.from(raiz(fixture).querySelectorAll('.seccion-titulo')).map((e) =>
      e.textContent?.trim(),
    );
    expect(titulos).toHaveLength(9);
    expect(titulos.at(-1)).toBe('Reporte fotográfico');

    for (let i = 0; i < 8; i++) await ir(fixture, 'Sección siguiente');

    expect(raiz(fixture).querySelector('h2')?.textContent).toContain('Reporte fotográfico');
    expect(raiz(fixture).textContent).toContain('Fotografías del puente.');
    expect(raiz(fixture).querySelector('.apoyo-prueba')?.textContent).toBe(
      'Contenido de la sección de apoyo',
    );
    expect(raiz(fixture).textContent).toContain('Sección 9 de 9');
    // Es la última: no hay «siguiente» y tampoco aparece la leyenda de campos del esquema.
    expect(botones(fixture, 'Sección siguiente')[0].disabled).toBe(true);
    expect(raiz(fixture).querySelector('.leyenda')).toBeNull();
  });

  it('no cuentan para el avance ni para la revisión', async () => {
    const sin = await crear(ESQUEMA_SIECA);
    const avanceSin = raiz(sin).querySelector('.avance-total')?.textContent?.trim();
    TestBed.resetTestingModule();

    const con = await crear(ESQUEMA_SIECA, { seccionesApoyo: [apoyo] });

    expect(raiz(con).querySelector('.avance-total')?.textContent?.trim()).toBe(avanceSin);
    expect(con.componentInstance.revisar()).toBe(false);
    con.detectChanges();
    const apoyoNav = Array.from(raiz(con).querySelectorAll('.seccion')).at(-1)!;
    expect(apoyoNav.classList.contains('errores')).toBe(false);
    expect(apoyoNav.querySelector('progress')).toBeNull();
  });

  it('no aparecen si el esquema no se puede interpretar', async () => {
    const fixture = await crear(
      { properties: { x: { $ref: '#/$defs/noExiste' } } },
      { seccionesApoyo: [apoyo] },
    );

    expect(raiz(fixture).querySelector('.seccion')).toBeNull();
    expect(raiz(fixture).querySelector('[role="alert"]')).not.toBeNull();
  });
});

describe('FormularioDinamicoComponent con un renderizador personalizado', () => {
  @Component({
    selector: 'app-renderizador-prueba',
    template: '<p class="personalizado">Grilla de {{ nodo().titulo }}</p>',
  })
  class RenderizadorPruebaComponent {
    readonly nodo = input.required<NodoFormulario>();
    readonly control = input<unknown>();
    readonly construido = input<unknown>();
  }

  it('reemplaza el dibujo de un nodo, que es como se integrará la captura táctil de daños', async () => {
    const fixture = await crear(ESQUEMA_SIECA, {}, [
      {
        provide: RENDERIZADORES_NODO,
        multi: true,
        useValue: {
          coincide: (nodo: NodoFormulario) => nodo.ruta === 'identificacion/coordenadas_utm',
          componente: RenderizadorPruebaComponent,
        },
      },
    ]);

    expect(raiz(fixture).querySelector('.personalizado')?.textContent).toContain(
      'Grilla de Coordenadas UTM',
    );
    expect(campo(fixture, 'identificacion/coordenadas_utm')).toBeNull();
    expect(campo(fixture, 'identificacion/fecha_inspeccion')).not.toBeNull();
  });
});

describe('FormularioDinamicoComponent con unos 450 campos', () => {
  function esquemaGrande(secciones: number, camposPorSeccion: number): EsquemaJson {
    const propiedades: Record<string, unknown> = {};
    for (let s = 0; s < secciones; s++) {
      const campos: Record<string, unknown> = {};
      for (let c = 0; c < camposPorSeccion; c++) {
        campos[`campo_${c}`] =
          c % 3 === 0
            ? { title: `Número ${c}`, type: 'number', minimum: 0, maximum: 100 }
            : c % 3 === 1
              ? { title: `Texto ${c}`, type: 'string', maxLength: 80 }
              : { title: `Opción ${c}`, enum: ['LEVE', 'MODERADO', 'SEVERO'] };
      }
      propiedades[`seccion_${s}`] = {
        title: `Sección ${s + 1}`,
        type: 'object',
        required: Object.keys(campos).slice(0, 10),
        properties: campos,
      };
    }
    return { type: 'object', properties: propiedades };
  }

  it('construye el formulario y dibuja una sección sin degradar la interfaz', async () => {
    const inicio = performance.now();
    const fixture = await crear(esquemaGrande(9, 50));
    const construccion = performance.now() - inicio;

    // Solo se dibuja la sección activa: el DOM no crece con el tamaño total del formulario.
    expect(raiz(fixture).querySelectorAll('[data-ruta]').length).toBeLessThanOrEqual(52);
    expect(fixture.componentInstance['construido']()!.form.controls).toBeDefined();

    const entrada = raiz(fixture).querySelector<HTMLInputElement>('input[type="number"]')!;
    const antes = performance.now();
    for (let i = 0; i < 20; i++) {
      escribir(entrada, String(i));
      fixture.detectChanges();
    }
    const porCambio = (performance.now() - antes) / 20;

    // Cotas holgadas: detectan una regresión grave, no miden el rendimiento fino.
    expect(construccion).toBeLessThan(5000);
    expect(porCambio).toBeLessThan(250);
  });
});
