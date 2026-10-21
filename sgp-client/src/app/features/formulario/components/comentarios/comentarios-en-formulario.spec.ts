import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { EsquemaJson } from '../../motor/esquema/modelos';
import { ESQUEMA_SIECA } from '../../motor/testing/esquema-sieca';
import { traduccionesDePrueba } from '../../motor/testing/traducciones';
import { FormularioDinamicoComponent } from '../formulario-dinamico/formulario-dinamico.component';
import { CONTEXTO_COMENTARIOS, ContextoComentariosFormulario } from './contexto-comentarios';

const SECCION = '/subestructura';
const GRUPO = '/subestructura/estribo_entrada';
const ELEMENTO = '/subestructura/estribo_entrada/viga_apoyo';
const PILAS = '/subestructura/pilas';
const PILA_1 = '0198aaaa-0000-7000-8000-0000000000b1';
const PILA_2 = '0198aaaa-0000-7000-8000-0000000000b2';

function montar(
  proveedor: ContextoComentariosFormulario | null,
  seccion = SECCION,
  esquema: EsquemaJson = ESQUEMA_SIECA,
): ComponentFixture<FormularioDinamicoComponent> {
  TestBed.configureTestingModule({
    imports: [traduccionesDePrueba()],
    providers: proveedor ? [{ provide: CONTEXTO_COMENTARIOS, useValue: proveedor }] : [],
  });
  const fixture = TestBed.createComponent(FormularioDinamicoComponent);
  fixture.componentRef.setInput('esquema', esquema);
  fixture.componentRef.setInput('valoresIniciales', {
    subestructura: { pilas: [{ instancia_id: PILA_1 }, { instancia_id: PILA_2 }] },
  });
  fixture.componentRef.setInput('soloLectura', true);
  fixture.detectChanges();
  fixture.componentInstance.mostrarAnclaje(seccion);
  fixture.detectChanges();
  return fixture;
}

const contexto = (): ContextoComentariosFormulario => ({
  puedeComentar: signal(true),
  totalDe: (a) => (a.elementoRef === ELEMENTO ? 2 : 0),
  etiquetaDe: (a) => a.elementoRef,
  seleccionar: vi.fn(),
});

const anclajes = (fixture: ComponentFixture<unknown>, ref: string, instancia = '') =>
  Array.from(
    (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(
      `[data-elemento-ref="${ref}"]`,
    ),
  ).filter((e) => e.getAttribute('data-instancia-id') === instancia);

describe('Icono de comentarios junto al nombre, dentro del formulario dinámico', () => {
  it('un elemento lo lleva pegado a su nombre, en la cabecera de su tarjeta', () => {
    const fixture = montar(contexto());

    const [anclaje, ...otros] = anclajes(fixture, ELEMENTO);
    expect(otros).toHaveLength(0);
    const titulo = anclaje.closest('.elemento header.cabecera .titulo');
    expect(titulo).not.toBeNull();
    // El nombre y el icono comparten línea, y el icono no está con las acciones de la derecha.
    expect(titulo?.querySelector('h4')?.textContent?.trim()).toBeTruthy();
    expect(titulo?.querySelector('h4')?.nextElementSibling?.contains(anclaje)).toBe(true);
    expect(anclaje.closest('.acciones')).toBeNull();
    expect(anclaje.classList.contains('en-titulo')).toBe(true);
    expect(anclaje.querySelector('.cantidad')?.textContent?.trim()).toBe('2');
  });

  it('un grupo lo lleva dentro de su leyenda, junto a su nombre', () => {
    const fixture = montar(contexto());

    const [anclaje, ...otros] = anclajes(fixture, GRUPO);
    expect(otros).toHaveLength(0);
    const leyenda = anclaje.closest('legend');
    expect(leyenda?.textContent).toContain('Estribo de entrada');
    expect(anclaje.classList.contains('en-titulo')).toBe(true);
  });

  it('una sección lo lleva junto a su título', () => {
    const fixture = montar(contexto());

    const [anclaje, ...otros] = anclajes(fixture, SECCION);
    expect(otros).toHaveLength(0);
    const titulo = anclaje.closest('.titulo-seccion');
    expect(titulo?.querySelector('h2')?.textContent).toContain('Sub-estructura');
    expect(anclaje.classList.contains('en-titulo')).toBe(true);
  });

  it('cada instancia lo lleva junto a su nombre y cada una tiene el suyo', () => {
    const fixture = montar(contexto());

    for (const [id, numero] of [
      [PILA_1, '1'],
      [PILA_2, '2'],
    ]) {
      const [anclaje, ...otros] = anclajes(fixture, PILAS, id);
      expect(otros).toHaveLength(0);
      const titulo = anclaje.closest('.instancia .cabecera .titulo-instancia');
      expect(titulo?.querySelector('strong')?.textContent).toContain(numero);
    }
  });

  it('ningún nodo tiene más de un botón y ninguno queda dentro de un encabezado ni de un summary', () => {
    const secciones = Object.keys((ESQUEMA_SIECA as { properties: EsquemaJson }).properties);
    for (const clave of secciones) {
      TestBed.resetTestingModule();
      const fixture = montar(contexto(), '/' + clave);
      const html = fixture.nativeElement as HTMLElement;

      const referencias = Array.from(html.querySelectorAll('[data-elemento-ref]')).map(
        (e) => `${e.getAttribute('data-elemento-ref')}|${e.getAttribute('data-instancia-id')}`,
      );
      expect(new Set(referencias).size, clave).toBe(referencias.length);
      expect(html.querySelectorAll('summary [data-elemento-ref]'), clave).toHaveLength(0);
      expect(html.querySelectorAll('h2 button, h3 button, h4 button'), clave).toHaveLength(0);
    }
  });

  it('un grupo opcional plegado lo lleva en una fila sobre él, no dentro de su summary', () => {
    const esquema: EsquemaJson = {
      type: 'object',
      title: 'Formulario',
      required: ['general'],
      properties: {
        general: {
          type: 'object',
          title: '1. General',
          required: ['nombre'],
          properties: {
            nombre: { type: 'string', title: 'Nombre' },
            adicional: {
              type: 'object',
              title: 'Bloque adicional',
              properties: { detalle: { type: 'string', title: 'Detalle' } },
            },
          },
        },
      },
    };
    const fixture = montar(contexto(), '/general', esquema);
    const html = fixture.nativeElement as HTMLElement;

    const detalles = html.querySelector('details.opcional');
    expect(detalles).not.toBeNull();
    // Es una fila sobre el grupo, sin pegarse a su nombre.
    const anterior = detalles?.previousElementSibling;
    expect(anterior?.tagName).toBe('APP-ANCLAJE-COMENTARIOS');
    expect(anterior?.querySelector('.anclaje')?.classList.contains('en-titulo')).toBe(false);
    expect(html.querySelectorAll('summary [data-elemento-ref]')).toHaveLength(0);
    expect(anclajes(fixture, '/general/adicional')).toHaveLength(1);
  });

  it('sin quien provea el contexto el formulario no dibuja ningún botón', () => {
    const fixture = montar(null);

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-elemento-ref]')).toBeNull();
  });
});
