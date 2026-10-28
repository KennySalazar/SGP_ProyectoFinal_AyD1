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
  (
    ESQUEMA_SIECA as {
      properties: Record<
        string,
        {
          title?: string;
          $ref?: string;
        }
      >;
    }
  ).properties,
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
    for (let i = 0; i < 3; i++) await ir(fixture, 'Sección siguiente');
    declararPresente(fixture, CORTINA);
    const bloque = raiz(fixture).querySelector<HTMLDetailsElement>('details.opcional')!;
    expect(bloque.querySelector('.limpiar-bloque')).toBeNull();
    escribir(bloque.querySelector('textarea, input[type="text"]') as HTMLInputElement, 'Fisura');
    bloque.querySelector<HTMLInputElement>('input[type="radio"]')!.click();
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
    for (let i = 0; i < 3; i++) await ir(fixture, 'Sección siguiente');
    const existencia = campo(fixture, `${CORTINA}/existencia`)!;
    const boton = existencia.querySelector<HTMLButtonElement>('.ayuda-boton')!;
    const ayuda = existencia.querySelector<HTMLElement>('.ayuda')!;
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
    declararPresente(fixture, CORTINA);
    expect(
      campo(fixture, `${CORTINA}/grietas_una_direccion`)!.querySelector('.ayuda-boton'),
    ).toBeNull();
    expect(raiz(fixture).querySelector('[data-ruta="otros"] .ayuda-boton')).toBeNull();
  });
  it('responde una severidad con un solo toque', async () => {
    await ir(fixture, 'Sección siguiente');
    await ir(fixture, 'Sección siguiente');
    await ir(fixture, 'Sección siguiente');
    const existencia = campo(fixture, 'subestructura/estribo_entrada/cortina_superior/existencia')!;
    existencia.querySelector<HTMLInputElement>('input[type="radio"]')!.click();
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
    for (let i = 0; i < 3; i++) await ir(fixture, 'Sección siguiente');
    expect(campo(fixture, `${CORTINA}/altura_m`)).toBeNull();
    const opciones = campo(fixture, `${CORTINA}/existencia`)!.querySelectorAll<HTMLInputElement>(
      'input[type="radio"]',
    );
    expect(opciones).toHaveLength(3);
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
      .click();
    fixture.detectChanges();
    expect(campo(fixture, `${CORTINA}/altura_m`)).toBeNull();
    campo(fixture, `${CORTINA}/existencia`)!
      .querySelectorAll<HTMLInputElement>('input[type="radio"]')[0]
      .click();
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
    await ir(fixture, 'Sección siguiente');
    await ir(fixture, 'Sección siguiente');
    await ir(fixture, 'Sección siguiente');
    expect(raiz(fixture).querySelector('details.opcional')).toBeNull();
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
    for (let i = 0; i < 5; i++) await ir(fixture, 'Sección siguiente');
    const anchos = Array.from(
      raiz(fixture).querySelectorAll<HTMLElement>('app-nodo-formulario.ancho-completo'),
    ).map((nodo) => nodo.querySelector('[data-ruta]')?.getAttribute('data-ruta'));
    expect(anchos).toContain('elementos_no_estructurales/obras_proteccion');
    expect(anchos.some((ruta) => ruta?.endsWith('observaciones'))).toBe(true);
    expect(anchos).not.toContain('elementos_no_estructurales/pasamanos_barandal');
  });
});
describe('FormularioDinamicoComponent: reparto del espacio en «Otros»', () => {
  it('reparte la señalización en columnas y no deja a medias el grupo de recomendaciones', async () => {
    const fixture = await crear(ESQUEMA_SIECA);
    for (let i = 0; i < 7; i++) await ir(fixture, 'Sección siguiente');
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
  function versionNueva(): EsquemaJson {
    type Propiedades = {
      properties: Record<string, Record<string, unknown>>;
    };
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
    await ir(fixture, 'Sección siguiente');
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
    expect(botones(fixture, 'Sección siguiente')).toHaveLength(0);
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
    expect(raiz(fixture).querySelectorAll('[data-ruta]').length).toBeLessThanOrEqual(52);
    expect(fixture.componentInstance['construido']()!.form.controls).toBeDefined();
    const entrada = raiz(fixture).querySelector<HTMLInputElement>('input[type="number"]')!;
    const antes = performance.now();
    for (let i = 0; i < 20; i++) {
      escribir(entrada, String(i));
      fixture.detectChanges();
    }
    const porCambio = (performance.now() - antes) / 20;
    expect(construccion).toBeLessThan(5000);
    expect(porCambio).toBeLessThan(250);
  });
});
describe('FormularioDinamicoComponent: captura táctil de daños (HU-034)', () => {
  let fixture: ComponentFixture<FormularioDinamicoComponent>;
  const tarjeta = (ruta: string) =>
    raiz(fixture).querySelector<HTMLElement>(`section.elemento[data-ruta="${ruta}"]`)!;
  const textoEstado = (ruta: string) =>
    tarjeta(ruta).querySelector('.estado')!.textContent!.replace(/\s+/g, ' ').trim();
  const botonSinDanos = (ruta: string) =>
    tarjeta(ruta).querySelector<HTMLButtonElement>('button.sin-danos')!;
  const marcadas = (ruta: string) =>
    Array.from(tarjeta(ruta).querySelectorAll('.grilla .segmento.marcado')).map((s) =>
      s.textContent!.trim(),
    );
  const existenciaMarcada = (ruta: string) =>
    tarjeta(ruta).querySelector('.existencia .segmento.marcado')?.textContent?.trim();
  const alPulsar = async (boton: HTMLElement) => {
    boton.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };
  beforeEach(async () => {
    fixture = await crear(ESQUEMA_SIECA);
    for (let i = 0; i < 3; i++) await ir(fixture, 'Sección siguiente');
  });
  it('cada elemento se muestra como una tarjeta con su estado escrito', () => {
    expect(tarjeta(CORTINA).querySelector('h4')?.textContent).toBe('Cortina superior');
    expect(tarjeta(CORTINA).getAttribute('role')).toBe('group');
    expect(textoEstado(CORTINA)).toBe('Pendiente');
    expect(tarjeta(CORTINA).querySelector('.grilla')).toBeNull();
  });
  it('los indicadores se agrupan en una grilla de tarjetas, no en una lista vertical', () => {
    declararPresente(fixture, CORTINA);
    const grilla = tarjeta(CORTINA).querySelector('.grilla')!;
    expect(grilla.querySelectorAll('.indicador')).toHaveLength(4);
    const primero = grilla.querySelector('.indicador')!;
    expect(
      Array.from(primero.querySelectorAll('.segmento')).map((s) => s.textContent!.trim()),
    ).toEqual(['No presenta', 'Leve', 'Moderado', 'Severo']);
  });
  it('los daños inician vacíos: ninguna severidad viene marcada (RN-INS-10)', () => {
    declararPresente(fixture, CORTINA);
    expect(marcadas(CORTINA)).toEqual([]);
    expect(fixture.componentInstance.valor()['subestructura']).toMatchObject({
      estribo_entrada: { cortina_superior: { existencia: 'PRESENTE' } },
    });
  });
  it('«Sin daños en este elemento» marca todo el grupo como sano en una sola pulsación', async () => {
    await alPulsar(botonSinDanos(CORTINA));
    expect(textoEstado(CORTINA)).toBe('Sin daños');
    expect(existenciaMarcada(CORTINA)).toBe('Presente');
    expect(marcadas(CORTINA)).toHaveLength(4);
    const indicadores = Array.from(tarjeta(CORTINA).querySelectorAll('.grilla .segmento.marcado'));
    expect(indicadores.map((s) => s.textContent!.trim())).toEqual(Array(4).fill('No presenta'));
    expect(fixture.componentInstance.valor()['subestructura']).toMatchObject({
      estribo_entrada: {
        cortina_superior: {
          existencia: 'PRESENTE',
          grietas_una_direccion: 'NO_PRESENTA',
          exposicion_acero: 'NO_PRESENTA',
        },
      },
    });
    expect(botonSinDanos(CORTINA).getAttribute('aria-pressed')).toBe('true');
  });
  it('un elemento sano pliega sus indicadores y al editar uno se vuelven a abrir', async () => {
    const indicadores = () =>
      tarjeta(CORTINA).querySelector<HTMLDetailsElement>('details.indicadores')!;
    declararPresente(fixture, CORTINA);
    expect(indicadores().open).toBe(true);
    await alPulsar(botonSinDanos(CORTINA));
    expect(indicadores().open).toBe(false);
    expect(indicadores().querySelector('summary')!.textContent).toContain('todos sin daño');
    expect(indicadores().querySelectorAll('.indicador')).toHaveLength(4);
    await alPulsar(indicadores().querySelectorAll<HTMLInputElement>('input[type="radio"]')[2]);
    expect(textoEstado(CORTINA)).toBe('Con daños');
    expect(indicadores().open).toBe(true);
  });
  it('volver a pulsarla la deshace', async () => {
    await alPulsar(botonSinDanos(CORTINA));
    await alPulsar(botonSinDanos(CORTINA));
    expect(textoEstado(CORTINA)).toBe('Pendiente');
    expect(marcadas(CORTINA)).toEqual([]);
    expect(existenciaMarcada(CORTINA)).toBe('Presente');
  });
  it('editar un indicador de un grupo sano lo deja de marcar como sano y conserva el cambio', async () => {
    await alPulsar(botonSinDanos(CORTINA));
    const primero = tarjeta(CORTINA).querySelector('.grilla .indicador')!;
    await alPulsar(primero.querySelectorAll<HTMLInputElement>('input[type="radio"]')[1]);
    expect(textoEstado(CORTINA)).toBe('Con daños');
    expect(primero.querySelector('.segmento.marcado')?.textContent).toContain('Leve');
    expect(botonSinDanos(CORTINA).getAttribute('aria-pressed')).toBe('false');
    expect(marcadas(CORTINA).filter((m) => m === 'No presenta')).toHaveLength(3);
  });
  it('un grupo a medias queda «En proceso» con la cuenta escrita', () => {
    declararPresente(fixture, CORTINA);
    tarjeta(CORTINA).querySelector<HTMLInputElement>('.grilla input[type="radio"]')!.click();
    fixture.detectChanges();
    expect(textoEstado(CORTINA)).toBe('En proceso: 1 de 4');
  });
  it('muestra la severidad con su nombre y con una marca, además del color', async () => {
    declararPresente(fixture, CORTINA);
    const indicador = tarjeta(CORTINA).querySelector('.grilla .indicador')!;
    const leve = indicador.querySelectorAll<HTMLInputElement>('input[type="radio"]')[1];
    await alPulsar(leve);
    const elegida = indicador.querySelector('.segmento.marcado')!;
    expect(elegida.textContent).toContain('Leve');
    expect(elegida.getAttribute('data-gravedad')).toBe('g1');
    expect(elegida.querySelector('.marca-eleccion')).not.toBeNull();
    const otras = Array.from(indicador.querySelectorAll('.segmento:not(.marcado)'));
    expect(otras.map((s) => s.textContent!.trim())).toEqual(['No presenta', 'Moderado', 'Severo']);
    expect(otras.some((s) => s.querySelector('.marca-eleccion'))).toBe(false);
  });
  it('el color sube con la gravedad: sano, leve, moderado y severo', () => {
    declararPresente(fixture, CORTINA);
    const segmentos = tarjeta(CORTINA).querySelectorAll('.grilla .indicador:first-child .segmento');
    expect(Array.from(segmentos).map((s) => s.getAttribute('data-gravedad'))).toEqual([
      'g0',
      'g1',
      'g2',
      'g3',
    ]);
  });
  it('un elemento que no aplica o está ausente lo refleja y no ofrece marcarlo sano', async () => {
    const opciones = tarjeta(CORTINA).querySelectorAll<HTMLInputElement>('input[type="radio"]');
    await alPulsar(opciones[1]);
    expect(textoEstado(CORTINA)).toBe('No aplica');
    expect(tarjeta(CORTINA).querySelector('.grilla')).toBeNull();
    expect(tarjeta(CORTINA).querySelector('button.sin-danos')).toBeNull();
    await alPulsar(opciones[2]);
    expect(textoEstado(CORTINA)).toBe('Ausente');
  });
  it('al revisar, el elemento sin revisar se destaca con texto y no solo con color', async () => {
    expect(tarjeta(CORTINA).classList.contains('sin-revisar')).toBe(false);
    fixture.componentInstance.revisar();
    for (let i = 0; i < 3; i++) await ir(fixture, 'Sección siguiente');
    expect(tarjeta(CORTINA).classList.contains('sin-revisar')).toBe(true);
    expect(textoEstado(CORTINA)).toContain('Falta revisarlo');
  });
  it('la navegación cuenta los elementos revisados de cada sección', async () => {
    const resumen = () =>
      raiz(fixture).querySelector('.seccion.activa .seccion-elementos')!.textContent!.trim();
    expect(resumen()).toMatch(/^0 de \d+ elementos revisados$/);
    const total = Number(/de (\d+)/.exec(resumen())![1]);
    await alPulsar(botonSinDanos(CORTINA));
    expect(resumen()).toBe(`1 de ${total} elementos revisados`);
  });
  it('los elementos de una pila repetible también usan la grilla', async () => {
    await alPulsar(botonesPorTexto('Agregar Pila')[0]);
    expect(
      raiz(fixture).querySelector('section.elemento[data-ruta="subestructura/pilas/viga_cabezal"]'),
    ).not.toBeNull();
    expect(
      raiz(fixture).querySelectorAll('section.elemento[data-ruta^="subestructura/pilas/"]'),
    ).toHaveLength(5);
  });
  it('los controles son nativos y enfocables, para recorrer la grilla con el teclado', () => {
    declararPresente(fixture, CORTINA);
    const indicador = tarjeta(CORTINA).querySelector('.grilla .indicador')!;
    const radios = Array.from(indicador.querySelectorAll<HTMLInputElement>('input[type="radio"]'));
    expect(new Set(radios.map((r) => r.name)).size).toBe(1);
    expect(radios).toHaveLength(4);
    expect(radios.every((r) => !r.disabled && r.tabIndex >= 0)).toBe(true);
    expect(botonSinDanos(CORTINA).tagName).toBe('BUTTON');
  });
  it('«No aplica» y «Ausente» se distinguen por color, ícono y texto', async () => {
    const opciones = tarjeta(CORTINA).querySelectorAll<HTMLInputElement>('input[type="radio"]');
    const chip = () => tarjeta(CORTINA).querySelector('.estado')!;
    const icono = () => chip().querySelector('i')!.className;
    await alPulsar(opciones[1]);
    expect(tarjeta(CORTINA).getAttribute('data-subtipo')).toBe('no_aplica');
    expect(chip().getAttribute('data-subtipo')).toBe('no_aplica');
    expect(icono()).toContain('pi-minus-circle');
    expect(
      tarjeta(CORTINA).querySelector('.existencia .segmento.marcado')!.getAttribute('data-valor'),
    ).toBe('NO_APLICA');
    await alPulsar(opciones[2]);
    expect(tarjeta(CORTINA).getAttribute('data-subtipo')).toBe('ausente');
    expect(icono()).toContain('pi-times-circle');
    expect(chip().textContent).toContain('Ausente');
    expect(
      tarjeta(CORTINA).querySelector('.existencia .segmento.marcado')!.getAttribute('data-valor'),
    ).toBe('AUSENTE');
  });
  it('los indicadores de severidad forman una matriz: los nombres van una sola vez', () => {
    declararPresente(fixture, CORTINA);
    const encabezado = tarjeta(CORTINA).querySelector('.matriz-cabecera')!;
    expect(encabezado.getAttribute('aria-hidden')).toBe('true');
    expect(
      Array.from(encabezado.querySelectorAll('span')).map((e) => e.textContent!.trim()),
    ).toEqual(['No presenta', 'Leve', 'Moderado', 'Severo']);
    const celdas = tarjeta(CORTINA).querySelectorAll('.matriz .indicador:first-child .segmento');
    expect(celdas).toHaveLength(4);
    expect(celdas[1].querySelector('.solo-lectores')!.textContent).toBe('Leve');
    expect(tarjeta(CORTINA).querySelectorAll('.matriz-cabecera')).toHaveLength(1);
  });
  it('las medidas opcionales van plegadas hasta que alguna tiene un valor', async () => {
    declararPresente(fixture, CORTINA);
    const medidas = () => tarjeta(CORTINA).querySelector<HTMLDetailsElement>('details.medidas')!;
    expect(medidas().querySelector('summary')!.textContent).toContain('Medidas (opcional)');
    expect(medidas().open).toBe(false);
    const altura = medidas().querySelector('input[type="number"]') as HTMLInputElement;
    escribir(altura, '2.5');
    fixture.detectChanges();
    expect(medidas().open).toBe(true);
  });
  it('el detalle de un elemento sano y completo se pliega y se puede volver a abrir', async () => {
    const detalle = () => tarjeta(CORTINA).querySelector<HTMLElement>('.detalle')!;
    const alternar = () => tarjeta(CORTINA).querySelector<HTMLButtonElement>('button.alternar')!;
    const construido = fixture.componentInstance['construido']()!;
    await alPulsar(botonSinDanos(CORTINA));
    expect(detalle().hidden).toBe(false);
    construido.form
      .get('subestructura.estribo_entrada.cortina_superior.material')!
      .setValue('ACERO');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(detalle().hidden).toBe(true);
    expect(alternar().getAttribute('aria-expanded')).toBe('false');
    expect(alternar().getAttribute('aria-controls')).toBe(detalle().id);
    await alPulsar(alternar());
    expect(detalle().hidden).toBe(false);
    expect(alternar().getAttribute('aria-expanded')).toBe('true');
    await alPulsar(alternar());
    expect(detalle().hidden).toBe(true);
    expect(tarjeta(CORTINA).querySelector('.existencia')).not.toBeNull();
  });
  it('limpiar un indicador es una ✕ en su título, sin fila propia, con nombre accesible', async () => {
    declararPresente(fixture, CORTINA);
    const fila = tarjeta(CORTINA).querySelector('.matriz .indicador')!;
    expect(fila.querySelector('.quitar')).toBeNull();
    await alPulsar(fila.querySelectorAll<HTMLInputElement>('input[type="radio"]')[1]);
    const quitar = fila.querySelector<HTMLButtonElement>('legend .quitar')!;
    expect(quitar).not.toBeNull();
    expect(quitar.querySelector('.texto-quitar')!.textContent).toBe('Limpiar selección');
    expect(quitar.getAttribute('title')).toBe('Limpiar selección');
    await alPulsar(quitar);
    expect(fila.querySelector('.segmento.marcado')).toBeNull();
    expect(fila.querySelector('.quitar')).toBeNull();
  });
  it('los indicadores con nombres largos usan botones más anchos y no entran en la matriz', () => {
    const socavacion = 'subestructura/estribo_entrada/socavacion';
    const aguasArriba = tarjeta(socavacion).querySelector('[data-ruta$="/aguas_arriba"]')!;
    expect(aguasArriba.querySelector('.segmentos.cuatro.largas')).not.toBeNull();
    expect(tarjeta(socavacion).querySelector('.matriz')).toBeNull();
    expect(
      Array.from(aguasArriba.querySelectorAll('.segmento')).map((s) => s.textContent!.trim()),
    ).toEqual(['No hay', 'Sin exposición', 'Con exposición', 'Asentamiento de la estructura']);
    declararPresente(fixture, CORTINA);
    expect(tarjeta(CORTINA).querySelector('.matriz .segmentos.largas')).toBeNull();
  });
  it('los bloques anidados de un elemento («Otros») usan también botones de la grilla', () => {
    declararPresente(fixture, CORTINA);
    const severidad = tarjeta(CORTINA).querySelector<HTMLElement>(
      'details.opcional .campo[data-ruta$="/otros/severidad"]',
    )!;
    expect(severidad.classList.contains('en-grilla')).toBe(true);
    expect(severidad.querySelectorAll('.segmento')).toHaveLength(4);
  });
  it('un elemento en proceso mantiene su detalle abierto', () => {
    declararPresente(fixture, CORTINA);
    expect(tarjeta(CORTINA).querySelector<HTMLElement>('.detalle')!.hidden).toBe(false);
  });
  it('con el teclado, Espacio y Enter marcan una opción y la desmarcan', () => {
    declararPresente(fixture, CORTINA);
    const indicador = tarjeta(CORTINA).querySelector('.grilla .indicador')!;
    const leve = indicador.querySelectorAll<HTMLInputElement>('input[type="radio"]')[1];
    const teclear = (tecla: string) => {
      const evento = new KeyboardEvent('keydown', { key: tecla, bubbles: true, cancelable: true });
      leve.dispatchEvent(evento);
      fixture.detectChanges();
      return evento;
    };
    const elegida = () => indicador.querySelector('.segmento.marcado')?.textContent?.trim() ?? null;
    expect(teclear(' ').defaultPrevented).toBe(true);
    expect(elegida()).toBe('Leve');
    teclear(' ');
    expect(elegida()).toBeNull();
    teclear('Enter');
    expect(elegida()).toBe('Leve');
    teclear('Enter');
    expect(elegida()).toBeNull();
    expect(teclear('Tab').defaultPrevented).toBe(false);
    expect(elegida()).toBeNull();
  });
  it('en modo de solo lectura no se ofrece marcar el elemento', async () => {
    fixture.componentRef.setInput('soloLectura', true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(tarjeta(CORTINA).querySelector('button.sin-danos')).toBeNull();
  });
  it('la señalización se marca sin daños con «Hay» y «Bueno»', async () => {
    for (let i = 0; i < 4; i++) await ir(fixture, 'Sección siguiente');
    const informativa = 'otros/senalizacion/informativa';
    await alPulsar(botonSinDanos(informativa));
    expect(textoEstado(informativa)).toBe('Sin daños');
    expect(fixture.componentInstance.valor()['otros']).toMatchObject({
      senalizacion: { informativa: { existe: true, estado: 'BUENO' } },
    });
  });
});
function botonesPorTexto(texto: string): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('button')).filter((b) =>
    b.textContent?.includes(texto),
  );
}

describe('Validacion visible antes de enviar', () => {
  it('senala la lista invalida aunque todos sus campos obligatorios tengan respuesta', async () => {
    const fixture = await crear(
      {
        type: 'object',
        required: ['seccion'],
        properties: {
          seccion: {
            type: 'object',
            title: 'Seccion',
            required: ['filas'],
            properties: {
              filas: {
                type: 'array',
                title: 'Filas',
                minItems: 1,
                maxItems: 1,
                items: {
                  type: 'object',
                  required: ['valor'],
                  properties: { valor: { type: 'string', title: 'Valor' } },
                },
              },
            },
          },
        },
      },
      { valoresIniciales: { seccion: { filas: [{ valor: 'uno' }, { valor: 'dos' }] } } },
    );
    expect(fixture.componentInstance.revisar()).toBe(false);
    expect(fixture.componentInstance.problemasValidacion().join(' ')).toContain('Filas');
    fixture.detectChanges();
    expect(raiz(fixture).querySelector('.seccion.errores')).not.toBeNull();
  });
});
