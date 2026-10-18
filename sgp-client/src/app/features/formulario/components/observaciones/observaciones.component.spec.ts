import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Dialog } from 'primeng/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ContextoEditorObservacion,
  DestinoElementoObservacion,
  ObservacionPresentacion,
} from '../../models/observacion.models';
import { NodoObjeto } from '../../motor/esquema/modelos';
import { resolverEsquema } from '../../motor/esquema/resolutor';
import { FormularioConstruido } from '../../motor/formulario/constructor';
import { ESQUEMA_SIECA } from '../../motor/testing/esquema-sieca';
import { traduccionesDePrueba } from '../../motor/testing/traducciones';
import { crearAnclajeFormulario } from '../../utils/anclaje-formulario';
import { ElementoEvaluableComponent } from '../elemento-evaluable/elemento-evaluable.component';
import { EditorObservacionesComponent } from './editor-observaciones.component';
import { MarcadorObservacionesComponent } from './marcador-observaciones.component';
import { PanelObservacionesComponent } from './panel-observaciones.component';

const UUID_PILA = '019ABC00-0000-7000-8000-000000000001';
const destino: DestinoElementoObservacion = Object.freeze({
  tipo: 'ELEMENTO',
  etiqueta: 'Columnas de pila',
  anclaje: Object.freeze(crearAnclajeFormulario('subestructura/pilas/columnas', UUID_PILA)),
});
const observaciones: readonly ObservacionPresentacion[] = Object.freeze([
  Object.freeze({ id: 'o1', texto: 'Revisar fisura.', destino, estado: 'PENDIENTE' as const }),
  Object.freeze({ id: 'o2', texto: 'Comprobar medida.', destino, estado: 'RESUELTA' as const }),
  Object.freeze({ id: 'o3', texto: 'Otra comprobación.', destino, estado: 'PENDIENTE' as const }),
  Object.freeze({
    id: 'general',
    texto: 'Revisar el conjunto.',
    destino: { tipo: 'GENERAL' as const },
    estado: null,
  }),
]);
const raiz = (fixture: ComponentFixture<unknown>) => fixture.nativeElement as HTMLElement;
const botones = (fixture: ComponentFixture<unknown>, texto: string) =>
  Array.from(raiz(fixture).querySelectorAll<HTMLButtonElement>('button')).filter(
    (boton) => boton.textContent?.trim() === texto,
  );

beforeEach(() => {
  TestBed.configureTestingModule({
    imports: [
      MarcadorObservacionesComponent,
      EditorObservacionesComponent,
      PanelObservacionesComponent,
      ElementoEvaluableComponent,
      traduccionesDePrueba(),
    ],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });
});

afterEach(() => {
  const http = TestBed.inject(HttpTestingController);
  http.expectNone(() => true);
  http.verify();
});

describe('MarcadorObservacionesComponent', () => {
  it.each([
    [0, '0 observaciones'],
    [1, '1 observación'],
    [5, '5 observaciones'],
  ])('muestra cantidad %s con texto e ícono accesible', async (cantidad, texto) => {
    const fixture = TestBed.createComponent(MarcadorObservacionesComponent);
    fixture.componentRef.setInput('cantidad', cantidad);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(raiz(fixture).textContent).toContain(texto);
    expect(raiz(fixture).querySelector('.pi-comments')?.getAttribute('aria-hidden')).toBe('true');
    expect(raiz(fixture).querySelector('button')).toBeNull();
  });

  it('emite apertura solo cuando el consumidor habilita la interacción', async () => {
    const fixture = TestBed.createComponent(MarcadorObservacionesComponent);
    const abrir = vi.fn();
    fixture.componentInstance.abrir.subscribe(abrir);
    fixture.componentRef.setInput('cantidad', 3);
    fixture.componentRef.setInput('interactivo', true);
    fixture.detectChanges();
    await fixture.whenStable();
    const boton = raiz(fixture).querySelector('button')!;
    expect(boton.getAttribute('type')).toBe('button');
    expect(boton.textContent).toContain('3 observaciones');
    boton.click();
    expect(abrir).toHaveBeenCalledOnce();
  });
});

describe('EditorObservacionesComponent', () => {
  async function crear(contexto: ContextoEditorObservacion = { modo: 'CREAR', destino }) {
    const fixture = TestBed.createComponent(EditorObservacionesComponent);
    fixture.componentRef.setInput('contexto', contexto);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  function escribir(fixture: ComponentFixture<EditorObservacionesComponent>, valor: string) {
    const textarea = raiz(fixture).querySelector('textarea')!;
    textarea.value = valor;
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }
  function enviar(fixture: ComponentFixture<EditorObservacionesComponent>) {
    raiz(fixture)
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  it.each(['', '   ', '\n\t', 'x'.repeat(2001)])(
    'rechaza texto inválido (longitud %s)',
    async (valor) => {
      const fixture = await crear();
      const guardar = vi.fn();
      fixture.componentInstance.guardar.subscribe(guardar);
      escribir(fixture, valor);
      enviar(fixture);
      expect(guardar).not.toHaveBeenCalled();
      expect(botones(fixture, 'Guardar')[0].disabled).toBe(true);
      expect(raiz(fixture).querySelector('textarea')?.getAttribute('aria-invalid')).toBe('true');
      expect(raiz(fixture).querySelector('[role="alert"]')?.textContent?.trim()).toBeTruthy();
    },
  );

  it.each([1, 1999, 2000])(
    'admite %s caracteres, muestra contador y prepara creación',
    async (longitud) => {
      const fixture = await crear();
      const guardar = vi.fn();
      fixture.componentInstance.guardar.subscribe(guardar);
      escribir(fixture, 'a'.repeat(longitud));
      expect(botones(fixture, 'Guardar')[0].disabled).toBe(false);
      expect(raiz(fixture).textContent).toContain(`${longitud} / 2000 caracteres`);
      expect(raiz(fixture).querySelector('textarea')?.getAttribute('aria-invalid')).toBe('false');
      expect(raiz(fixture).querySelector('textarea')?.maxLength).toBe(2000);
      enviar(fixture);
      expect(guardar).toHaveBeenCalledExactlyOnceWith({
        modo: 'CREAR',
        destino,
        texto: 'a'.repeat(longitud),
      });
    },
  );

  it('canoniza un anclaje interno usando el adaptador y conserva exactamente su UUID', async () => {
    const seleccionado = {
      ...destino,
      anclaje: { elementoRef: 'subestructura/pilas/columnas', instanciaElementoId: UUID_PILA },
    };
    const fixture = await crear({ modo: 'CREAR', destino: seleccionado });
    const guardar = vi.fn();
    fixture.componentInstance.guardar.subscribe(guardar);
    escribir(fixture, 'Revisar.');
    enviar(fixture);
    expect(guardar.mock.calls[0][0].destino.anclaje).toEqual(destino.anclaje);
    expect(seleccionado.anclaje.elementoRef).toBe('subestructura/pilas/columnas');
  });

  it('precarga una edición, conserva identidad y no modifica la observación original', async () => {
    const fixture = await crear({ modo: 'EDITAR', observacion: observaciones[0] });
    const guardar = vi.fn();
    fixture.componentInstance.guardar.subscribe(guardar);
    expect(raiz(fixture).querySelector('textarea')?.value).toBe('Revisar fisura.');
    escribir(fixture, 'Texto actualizado.');
    enviar(fixture);
    expect(guardar).toHaveBeenCalledExactlyOnceWith({
      modo: 'EDITAR',
      id: 'o1',
      destino,
      texto: 'Texto actualizado.',
    });
    expect(observaciones[0].texto).toBe('Revisar fisura.');
    expect(observaciones[0].estado).toBe('PENDIENTE');
  });

  it('emite cancelación sin guardar ni simular persistencia', async () => {
    const fixture = await crear();
    const guardar = vi.fn(),
      cancelar = vi.fn();
    fixture.componentInstance.guardar.subscribe(guardar);
    fixture.componentInstance.cancelar.subscribe(cancelar);
    escribir(fixture, 'Borrador local.');
    botones(fixture, 'Cancelar')[0].click();
    expect(cancelar).toHaveBeenCalledOnce();
    expect(guardar).not.toHaveBeenCalled();
  });

  it('prepara una observación general sin inventar un elemento_ref', async () => {
    const fixture = await crear({ modo: 'CREAR', destino: { tipo: 'GENERAL' } });
    const guardar = vi.fn();
    fixture.componentInstance.guardar.subscribe(guardar);
    escribir(fixture, 'Comentario general.');
    enviar(fixture);
    expect(guardar).toHaveBeenCalledExactlyOnceWith({
      modo: 'CREAR',
      destino: { tipo: 'GENERAL' },
      texto: 'Comentario general.',
    });
    expect(raiz(fixture).textContent).toContain('Observación general');
  });

  it('restablece el editor al cambiar de selección sin emitir un guardado', async () => {
    const fixture = await crear({ modo: 'EDITAR', observacion: observaciones[0] });
    const guardar = vi.fn();
    fixture.componentInstance.guardar.subscribe(guardar);
    escribir(fixture, 'Cambio sin guardar.');
    fixture.componentRef.setInput('contexto', { modo: 'CREAR', destino: { tipo: 'GENERAL' } });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.texto.value).toBe('');
    expect(guardar).not.toHaveBeenCalled();
  });

  it('vincula el textarea a su etiqueta, contador y error', async () => {
    const fixture = await crear();
    escribir(fixture, ' ');
    const textarea = raiz(fixture).querySelector('textarea')!;
    expect(textarea.closest('label')?.textContent).toContain('Texto de la observación');
    for (const id of textarea.getAttribute('aria-describedby')!.split(' ')) {
      expect(raiz(fixture).querySelector(`[id="${id}"]`)).not.toBeNull();
    }
  });
});

describe('PanelObservacionesComponent', () => {
  async function crear(acciones = false) {
    const fixture = TestBed.createComponent(PanelObservacionesComponent);
    fixture.componentRef.setInput('observaciones', observaciones);
    fixture.componentRef.setInput('permitirEdicion', acciones);
    fixture.componentRef.setInput('permitirEliminacion', acciones);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('conserva varias observaciones del mismo elemento y cuenta total y pendientes', async () => {
    const fixture = await crear();
    expect(raiz(fixture).querySelectorAll('li')).toHaveLength(4);
    expect(raiz(fixture).textContent).toContain('Total: 4 · Pendientes: 2');
    expect(
      Array.from(raiz(fixture).querySelectorAll('h4')).filter(
        (h4) => h4.textContent?.trim() === 'Columnas de pila',
      ),
    ).toHaveLength(3);
    expect(raiz(fixture).querySelector('[data-estado="RESUELTA"]')?.textContent).toContain(
      'Resuelta',
    );
    expect(raiz(fixture).querySelector('[data-estado="PENDIENTE"]')?.textContent).toContain(
      'Pendiente',
    );
  });

  it('emite navegación con el elemento y UUID de la observación elegida', async () => {
    const fixture = await crear();
    const navegar = vi.fn();
    fixture.componentInstance.navegar.subscribe(navegar);
    botones(fixture, 'Ir al elemento')[1].click();
    expect(navegar).toHaveBeenCalledExactlyOnceWith(destino);
    expect(navegar.mock.calls[0][0].anclaje.instanciaElementoId).toBe(UUID_PILA);
  });

  it('no muestra edición ni eliminación por defecto', async () => {
    const fixture = await crear();
    expect(botones(fixture, 'Editar observación')).toHaveLength(0);
    expect(botones(fixture, 'Eliminar observación')).toHaveLength(0);
  });

  it('emite edición y eliminación sin cambiar la lista, texto o estado', async () => {
    const fixture = await crear(true);
    const editar = vi.fn(),
      eliminar = vi.fn();
    fixture.componentInstance.editar.subscribe(editar);
    fixture.componentInstance.eliminar.subscribe(eliminar);
    const antes = JSON.stringify(observaciones);
    botones(fixture, 'Editar observación')[1].click();
    botones(fixture, 'Eliminar observación')[0].click();
    fixture.detectChanges();
    expect(editar).toHaveBeenCalledExactlyOnceWith(observaciones[1]);
    expect(eliminar).toHaveBeenCalledExactlyOnceWith(observaciones[0]);
    expect(JSON.stringify(observaciones)).toBe(antes);
    expect(raiz(fixture).querySelectorAll('li')).toHaveLength(4);
  });

  it('muestra la general sin ruta, navegación ni estado persistente inventado', async () => {
    const fixture = await crear();
    const general = raiz(fixture).querySelectorAll('li')[3];
    expect(general.textContent).toContain('Observación general');
    expect(general.textContent).toContain('sin estado de pendiente');
    expect(general.querySelector('button')).toBeNull();
    expect(observaciones[3].destino).toEqual({ tipo: 'GENERAL' });
    expect(observaciones[3].estado).toBeNull();
  });

  it('se actualiza con nuevas entradas y contempla una lista vacía', async () => {
    const fixture = await crear();
    fixture.componentRef.setInput('observaciones', []);
    fixture.detectChanges();
    expect(raiz(fixture).textContent).toContain('Total: 0 · Pendientes: 0');
    expect(raiz(fixture).textContent).toContain('Sin observaciones.');
  });

  it('muestra el texto como contenido, nunca como HTML ejecutable', async () => {
    const fixture = await crear();
    fixture.componentRef.setInput('observaciones', [
      { ...observaciones[0], texto: '<img src=x onerror=alert(1)>' },
    ]);
    fixture.detectChanges();
    expect(raiz(fixture).querySelector('img')).toBeNull();
    expect(raiz(fixture).textContent).toContain('<img src=x onerror=alert(1)>');
  });
});

describe('ElementoEvaluableComponent: integración opcional', () => {
  let construido: FormularioConstruido;
  afterEach(() => construido?.destruir());

  async function crear() {
    const esquema = resolverEsquema(ESQUEMA_SIECA);
    construido = new FormularioConstruido(esquema);
    const subestructura = esquema.raiz.hijos.find(
      (hijo) => hijo.clave === 'subestructura',
    ) as NodoObjeto;
    const estribo = subestructura.hijos.find(
      (hijo) => hijo.clave === 'estribo_entrada',
    ) as NodoObjeto;
    const cortina = estribo.hijos.find((hijo) => hijo.clave === 'cortina_superior')!;
    const fixture = TestBed.createComponent(ElementoEvaluableComponent);
    fixture.componentRef.setInput('nodo', cortina);
    fixture.componentRef.setInput(
      'control',
      construido.form.get('subestructura.estribo_entrada.cortina_superior'),
    );
    fixture.componentRef.setInput('construido', construido);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('no agrega marcadores ni acciones de observaciones a las pantallas actuales', async () => {
    const fixture = await crear();
    expect(raiz(fixture).querySelector('app-marcador-observaciones')).toBeNull();
    expect(raiz(fixture).querySelector('h4')?.textContent).toBe('Cortina superior');
  });

  it('añade un marcador pasivo al proporcionar cantidad, sin modificar el formulario', async () => {
    const fixture = await crear();
    const antes = JSON.stringify(construido.valor());
    fixture.componentRef.setInput('cantidadObservaciones', 2);
    fixture.detectChanges();
    const marcador = raiz(fixture).querySelector('app-marcador-observaciones')!;
    expect(marcador.textContent).toContain('2 observaciones');
    expect(marcador.querySelector('button')).toBeNull();
    expect(JSON.stringify(construido.valor())).toBe(antes);
  });

  it('propaga el evento únicamente si se habilita expresamente la interacción', async () => {
    const fixture = await crear();
    const abrir = vi.fn();
    fixture.componentInstance.abrirObservaciones.subscribe(abrir);
    const antes = JSON.stringify(construido.valor());
    fixture.componentRef.setInput('cantidadObservaciones', 3);
    fixture.componentRef.setInput('observacionesInteractivas', true);
    fixture.detectChanges();
    raiz(fixture).querySelector<HTMLButtonElement>('app-marcador-observaciones button')!.click();
    expect(abrir).toHaveBeenCalledOnce();
    expect(JSON.stringify(construido.valor())).toBe(antes);
  });
});

describe('PanelObservacionesComponent: presentación responsive y foco', () => {
  const descriptor = Object.getOwnPropertyDescriptor(window, 'matchMedia');
  let ancho: number;
  let cambios: Set<() => void>;
  let apertura: HTMLButtonElement;

  beforeEach(() => {
    ancho = 1280;
    cambios = new Set();
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: () => ({
        get matches() {
          return ancho <= 640;
        },
        addEventListener: (_tipo: string, listener: () => void) => cambios.add(listener),
        removeEventListener: (_tipo: string, listener: () => void) => cambios.delete(listener),
      }),
    });
    apertura = document.createElement('button');
    apertura.textContent = 'Abrir observaciones';
    document.body.append(apertura);
    apertura.focus();
  });

  afterEach(() => {
    apertura.remove();
    if (descriptor) Object.defineProperty(window, 'matchMedia', descriptor);
    else Reflect.deleteProperty(window, 'matchMedia');
  });

  async function crear(desplegable = true) {
    const fixture = TestBed.createComponent(PanelObservacionesComponent);
    fixture.componentRef.setInput('desplegable', desplegable);
    fixture.componentRef.setInput('observaciones', observaciones);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  async function abrir(fixture: ComponentFixture<PanelObservacionesComponent>) {
    fixture.componentRef.setInput('abierto', true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('mantiene el modo integrado pasivo sin diálogo ni captura de foco', async () => {
    ancho = 375;
    const fixture = await crear(false);
    expect(raiz(fixture).querySelector('aside')).not.toBeNull();
    expect(fixture.debugElement.query(By.directive(Dialog))).toBeNull();
    expect(document.activeElement).toBe(apertura);
    expect(botones(fixture, 'Cerrar observaciones')).toHaveLength(0);
  });

  it('abre un lateral no modal en escritorio y restaura foco al cerrar', async () => {
    const fixture = await crear();
    expect(raiz(fixture).querySelector('aside')).toBeNull();
    await abrir(fixture);
    expect(raiz(fixture).classList.contains('lateral')).toBe(true);
    expect(fixture.debugElement.query(By.directive(Dialog))).toBeNull();
    const cerrar = botones(fixture, 'Cerrar observaciones')[0];
    expect(document.activeElement).toBe(cerrar);
    const cambiar = vi.fn();
    fixture.componentInstance.abierto.subscribe(cambiar);
    cerrar.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(cambiar).toHaveBeenCalledExactlyOnceWith(false);
    expect(document.activeElement).toBe(apertura);
  });

  it('cierra el lateral con Escape y permite enfocar el formulario fuera de él', async () => {
    const fixture = await crear();
    await abrir(fixture);
    apertura.focus();
    expect(document.activeElement).toBe(apertura);
    botones(fixture, 'Cerrar observaciones')[0].focus();
    document.activeElement!.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    );
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.abierto()).toBe(false);
    expect(document.activeElement).toBe(apertura);
  });

  it('a 375 px configura una hoja modal con contención de foco y cierre por Escape', async () => {
    ancho = 375;
    const fixture = await crear();
    await abrir(fixture);
    const dialogo = fixture.debugElement.query(By.directive(Dialog)).componentInstance as Dialog;
    expect(dialogo.modal).toBe(true);
    expect(dialogo.focusTrap).toBe(true);
    expect(dialogo.closeOnEscape).toBe(true);
    expect(dialogo.position).toBe('bottom');
    expect(dialogo.style).toMatchObject({ width: '100%', maxWidth: '100vw', maxHeight: '85dvh' });
    dialogo.onShow.emit({});
    const cerrar = document.activeElement as HTMLElement;
    expect(cerrar.textContent).toContain('Cerrar observaciones');
    const dialogoDom = cerrar.closest('[role="dialog"]')!;
    expect(dialogoDom.getAttribute('aria-modal')).toBe('true');
    const titulo = document.getElementById(dialogoDom.getAttribute('aria-labelledby')!);
    expect(titulo?.textContent).toContain('Observaciones');
    document.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();
    expect(fixture.componentInstance.abierto()).toBe(false);
    dialogo.onHide.emit({});
    expect(document.activeElement).toBe(apertura);
  });

  it('restaura foco tras el cierre externo de la hoja, sin cambiar observaciones', async () => {
    ancho = 375;
    const fixture = await crear();
    await abrir(fixture);
    const dialogo = fixture.debugElement.query(By.directive(Dialog)).componentInstance as Dialog;
    dialogo.onShow.emit({});
    const antes = JSON.stringify(observaciones);
    fixture.componentRef.setInput('abierto', false);
    fixture.detectChanges();
    dialogo.onHide.emit({});
    expect(document.activeElement).toBe(apertura);
    expect(JSON.stringify(observaciones)).toBe(antes);
  });

  it('se adapta al cambiar de ancho sin duplicar contenido ni perder contadores', async () => {
    const fixture = await crear();
    await abrir(fixture);
    ancho = 375;
    cambios.forEach((listener) => listener());
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.debugElement.query(By.directive(Dialog))).not.toBeNull();
    expect(raiz(fixture).querySelector('aside')).toBeNull();
    ancho = 1280;
    cambios.forEach((listener) => listener());
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.debugElement.query(By.directive(Dialog))).toBeNull();
    expect(raiz(fixture).querySelectorAll('li')).toHaveLength(4);
    expect(raiz(fixture).textContent).toContain('Total: 4 · Pendientes: 2');
  });

  it('expone una región desplazable y alcanzable por teclado', async () => {
    const fixture = await crear();
    await abrir(fixture);
    const region = raiz(fixture).querySelector<HTMLElement>('[role="region"]')!;
    expect(region.tabIndex).toBe(0);
    expect(region.getAttribute('aria-label')).toBe('Lista de observaciones');
    region.focus();
    expect(document.activeElement).toBe(region);
  });

  it('devuelve el foco y libera el listener responsive al destruir el panel abierto', async () => {
    const fixture = await crear();
    await abrir(fixture);
    fixture.destroy();
    expect(document.activeElement).toBe(apertura);
    expect(cambios.size).toBe(0);
  });
});
