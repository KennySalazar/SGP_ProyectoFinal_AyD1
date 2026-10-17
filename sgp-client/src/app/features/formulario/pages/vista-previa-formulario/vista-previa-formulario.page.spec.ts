import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { FormularioActivo } from '../../models/formulario.models';
import { FormularioApiService } from '../../services/formulario-api.service';
import { ESQUEMA_SIECA } from '../../motor/testing/esquema-sieca';
import { traduccionesDePrueba } from '../../motor/testing/traducciones';
import { VistaPreviaFormularioPage } from './vista-previa-formulario.page';

const activa: FormularioActivo = {
  id: 'v1',
  codigo: 'SIECA-v1',
  version: 3,
  esquema: ESQUEMA_SIECA,
  hashContenido: 'abcdef0123456789',
  publicadoEn: '2026-10-13T00:00:00Z',
};

describe('VistaPreviaFormularioPage', () => {
  let api: {
    obtenerActiva: ReturnType<typeof vi.fn>;
    obtenerEsquemaPublicado: ReturnType<typeof vi.fn>;
  };

  async function crear(
    respuesta: Observable<FormularioActivo>,
    version: string | null = null,
  ): Promise<ComponentFixture<VistaPreviaFormularioPage>> {
    api.obtenerActiva.mockReturnValue(respuesta);
    api.obtenerEsquemaPublicado.mockReturnValue(respuesta);

    TestBed.configureTestingModule({
      imports: [VistaPreviaFormularioPage, traduccionesDePrueba()],
      providers: [
        { provide: FormularioApiService, useValue: api },
        {
          provide: ApiErrorService,
          useValue: { normalize: (e: HttpErrorResponse) => `error ${e.status}` },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(version ? { version } : {}) },
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(VistaPreviaFormularioPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    api = { obtenerActiva: vi.fn(), obtenerEsquemaPublicado: vi.fn() };
  });

  it('dibuja el formulario de la versión activa', async () => {
    const fixture = await crear(of(activa));
    const html = fixture.nativeElement as HTMLElement;

    expect(api.obtenerActiva).toHaveBeenCalled();
    expect(html.textContent).toContain('Versión SIECA-v1');
    expect(html.textContent).toContain('abcdef012345');
    // Las ocho secciones del esquema y, al final, la sección de apoyo del reporte fotográfico.
    expect(html.querySelectorAll('app-formulario-dinamico .seccion')).toHaveLength(9);
    const ultima = Array.from(html.querySelectorAll('app-formulario-dinamico .seccion')).at(-1);
    expect(ultima?.textContent).toContain('Reporte fotográfico');
    expect(ultima?.textContent).toContain('Sección de apoyo');
  });

  it('permite recorrer el reporte fotográfico, que se habilitará con el módulo de archivos', async () => {
    const fixture = await crear(of(activa));
    const html = fixture.nativeElement as HTMLElement;
    const seccion = Array.from(html.querySelectorAll<HTMLButtonElement>('.seccion')).at(-1)!;

    seccion.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(html.querySelector('h2')?.textContent).toContain('Reporte fotográfico');
    expect(html.textContent).toContain('módulo de archivos (HU-028)');
  });

  it('carga una versión publicada concreta cuando se indica en la dirección', async () => {
    const fixture = await crear(of(activa), 'v0');

    expect(api.obtenerEsquemaPublicado).toHaveBeenCalledWith('v0');
    expect(api.obtenerActiva).not.toHaveBeenCalled();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('app-formulario-dinamico'),
    ).not.toBeNull();
  });

  it('explica que no hay versión activa cuando el servidor responde 404', async () => {
    const fixture = await crear(throwError(() => new HttpErrorResponse({ status: 404 })));

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'no hay una versión activa',
    );
  });

  it('muestra el error de otras fallas de carga', async () => {
    const fixture = await crear(throwError(() => new HttpErrorResponse({ status: 500 })));
    const alerta = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');

    expect(alerta?.textContent).toContain('error 500');
  });

  it('alterna entre edición y solo lectura', async () => {
    const fixture = await crear(of(activa));
    const html = fixture.nativeElement as HTMLElement;
    const alternar = html.querySelector<HTMLButtonElement>('.acciones button')!;

    alternar.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alternar.getAttribute('aria-pressed')).toBe('true');
    expect(html.querySelector('.aviso-solo-lectura')).not.toBeNull();
  });

  it('revisar muestra que faltan datos en un formulario vacío', async () => {
    const fixture = await crear(of(activa));
    const html = fixture.nativeElement as HTMLElement;
    const revisar = Array.from(html.querySelectorAll<HTMLButtonElement>('.acciones button')).find(
      (boton) => boton.textContent?.includes('Revisar formulario'),
    )!;

    revisar.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(html.textContent).toMatch(/Faltan \d+ campos obligatorios/);
    expect(html.textContent).toContain('Te llevamos al primero');
  });
});
