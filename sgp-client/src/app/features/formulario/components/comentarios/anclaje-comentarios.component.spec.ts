import { Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AbstractControl, FormArray, FormControl, FormGroup } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { describe, expect, it, Mock, vi } from 'vitest';
import { NodoFormulario } from '../../motor/esquema/modelos';
import { AnclajeFormulario } from '../../utils/anclaje-formulario';
import { AnclajeComentariosComponent } from './anclaje-comentarios.component';
import { CONTEXTO_COMENTARIOS, ContextoComentariosFormulario } from './contexto-comentarios';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string, params?: Record<string, unknown>): string {
    return params ? `${key} ${JSON.stringify(params)}` : key;
  }
}

const NODO = { titulo: 'Viga de apoyo' } as NodoFormulario;
const ESTRIBO = '/subestructura/estribo_entrada';
/** Lo que el proveedor del contexto responde como nombre de cada elemento. */
const etiquetas: Record<string, string> = {
  '/subestructura/pilas|pila-2': 'Pilas · Pila 2',
};

function arbol(): { raiz: FormGroup; estribo: FormGroup; pila: FormGroup } {
  const estribo = new FormGroup({ material: new FormControl('') });
  const pila = new FormGroup({ instancia_id: new FormControl('pila-2') });
  const raiz = new FormGroup({
    subestructura: new FormGroup({ estribo_entrada: estribo, pilas: new FormArray([pila]) }),
  });
  return { raiz, estribo, pila };
}

function contexto(
  totales: Record<string, number>,
  puedeComentar = true,
): { ctx: ContextoComentariosFormulario; seleccionar: Mock<(a: AnclajeFormulario) => void> } {
  const seleccionar = vi.fn<(a: AnclajeFormulario) => void>();
  return {
    ctx: {
      puedeComentar: signal(puedeComentar),
      totalDe: (a) => totales[`${a.elementoRef}|${a.instanciaElementoId ?? ''}`] ?? 0,
      etiquetaDe: (a) =>
        etiquetas[a.elementoRef + '|' + (a.instanciaElementoId ?? '')] ?? 'Viga de apoyo',
      seleccionar,
    },
    seleccionar,
  };
}

function montar(
  control: AbstractControl,
  proveedor: ContextoComentariosFormulario | null,
): ComponentFixture<AnclajeComentariosComponent> {
  TestBed.configureTestingModule({
    providers: [
      {
        provide: TranslocoService,
        useValue: {
          translate: (key: string, params?: Record<string, unknown>) =>
            params ? `${key} ${JSON.stringify(params)}` : key,
        },
      },
      ...(proveedor ? [{ provide: CONTEXTO_COMENTARIOS, useValue: proveedor }] : []),
    ],
  });
  TestBed.overrideComponent(AnclajeComentariosComponent, {
    remove: { imports: [TranslocoPipe] },
    add: { imports: [TraduccionTestPipe] },
  });
  const fixture = TestBed.createComponent(AnclajeComentariosComponent);
  fixture.componentRef.setInput('nodo', NODO);
  fixture.componentRef.setInput('control', control);
  fixture.detectChanges();
  return fixture;
}

function botones(fixture: ComponentFixture<unknown>): HTMLButtonElement[] {
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
}

describe('AnclajeComentariosComponent', () => {
  it('no dibuja nada si nadie provee el contexto', () => {
    const { estribo } = arbol();
    const fixture = montar(estribo, null);

    expect((fixture.nativeElement as HTMLElement).querySelector('.anclaje')).toBeNull();
  });

  it('no dibuja nada para un nodo que no se puede anclar', () => {
    const { raiz } = arbol();
    const fixture = montar(raiz, contexto({}).ctx);

    expect((fixture.nativeElement as HTMLElement).querySelector('.anclaje')).toBeNull();
  });

  it('sin comentarios es un solo botón con el icono de comentario y sin número', () => {
    const { estribo } = arbol();
    const fixture = montar(estribo, contexto({}).ctx);

    const lista = botones(fixture);
    expect(lista).toHaveLength(1);
    expect(lista[0].querySelector('i.pi-comment')).not.toBeNull();
    expect(lista[0].querySelector('.cantidad')).toBeNull();
    // Es solo un icono: el nombre accesible dice qué hace y sobre qué elemento.
    expect(lista[0].getAttribute('aria-label')).toBe(
      'foro.ancla.comentarElemento {"elemento":"Viga de apoyo"}',
    );
    expect(lista[0].getAttribute('title')).toBe(lista[0].getAttribute('aria-label'));
  });

  it('con comentarios muestra su número junto al icono, en texto', () => {
    const { estribo } = arbol();
    const fixture = montar(estribo, contexto({ [`${ESTRIBO}|`]: 3 }).ctx);

    const lista = botones(fixture);
    expect(lista).toHaveLength(1);
    expect(lista[0].querySelector('i.pi-comments')).not.toBeNull();
    expect(lista[0].querySelector('.cantidad')?.textContent?.trim()).toBe('3');
    // El nombre accesible contiene el texto visible y agrega el elemento.
    expect(lista[0].getAttribute('aria-label')).toBe(
      'foro.ancla.cantidad {"cantidad":3} foro.ancla.en {"elemento":"Viga de apoyo"}',
    );
  });

  it('usa el singular cuando hay un solo comentario', () => {
    const { estribo } = arbol();
    const fixture = montar(estribo, contexto({ [`${ESTRIBO}|`]: 1 }).ctx);

    expect(botones(fixture)[0].getAttribute('aria-label')).toContain('foro.ancla.una');
  });

  it('quien no puede comentar ve el botón solo si hay comentarios que leer', () => {
    const { estribo } = arbol();
    const conComentarios = montar(estribo, contexto({ [`${ESTRIBO}|`]: 2 }, false).ctx);
    expect(botones(conComentarios)).toHaveLength(1);
    conComentarios.destroy();
    TestBed.resetTestingModule();

    const sinComentarios = montar(estribo, contexto({}, false).ctx);
    expect(botones(sinComentarios)).toHaveLength(0);
  });

  it('al pulsarlo elige el elemento, tenga o no comentarios', () => {
    const { estribo } = arbol();
    const { ctx, seleccionar } = contexto({});
    const fixture = montar(estribo, ctx);

    botones(fixture)[0].click();

    expect(seleccionar).toHaveBeenCalledTimes(1);
    expect(seleccionar).toHaveBeenCalledWith({ elementoRef: ESTRIBO });
  });

  it('por omisión va en una fila y, junto al nombre, en su misma línea', () => {
    const { estribo } = arbol();
    const fila = montar(estribo, contexto({}).ctx);
    expect(fila.nativeElement.querySelector('.anclaje.en-titulo')).toBeNull();
    fila.destroy();
    TestBed.resetTestingModule();

    TestBed.configureTestingModule({
      providers: [
        {
          provide: TranslocoService,
          useValue: { translate: (key: string) => key },
        },
        { provide: CONTEXTO_COMENTARIOS, useValue: contexto({}).ctx },
      ],
    });
    TestBed.overrideComponent(AnclajeComentariosComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    const enTitulo = TestBed.createComponent(AnclajeComentariosComponent);
    enTitulo.componentRef.setInput('nodo', NODO);
    enTitulo.componentRef.setInput('control', estribo);
    enTitulo.componentRef.setInput('enTitulo', true);
    enTitulo.detectChanges();

    expect(enTitulo.nativeElement.querySelector('.anclaje.en-titulo')).not.toBeNull();
  });

  it('en un elemento repetible el ancla lleva la instancia y se nombra con ella', () => {
    const { pila } = arbol();
    const { ctx, seleccionar } = contexto({ ['/subestructura/pilas|pila-2']: 4 });
    const fixture = montar(pila, ctx);

    const [boton] = botones(fixture);
    expect(boton.querySelector('.cantidad')?.textContent?.trim()).toBe('4');
    // Cada instancia se distingue por nombre: «Pila 1» y «Pila 2» no dicen lo mismo.
    expect(boton.getAttribute('aria-label')).toContain('Pilas · Pila 2');
    boton.click();
    expect(seleccionar).toHaveBeenCalledWith({
      elementoRef: '/subestructura/pilas',
      instanciaElementoId: 'pila-2',
    });
  });

  it('reacciona cuando cambian los totales', () => {
    const { estribo } = arbol();
    const total = signal(0);
    const ctx: ContextoComentariosFormulario = {
      puedeComentar: signal(true),
      totalDe: () => total(),
      etiquetaDe: () => 'Viga de apoyo',
      seleccionar: vi.fn(),
    };
    const fixture = montar(estribo, ctx);
    expect(fixture.nativeElement.querySelector('.cantidad')).toBeNull();

    total.set(5);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.cantidad').textContent.trim()).toBe('5');
  });
});
