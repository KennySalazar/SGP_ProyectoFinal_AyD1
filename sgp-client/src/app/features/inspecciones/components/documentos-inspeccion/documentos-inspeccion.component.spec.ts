import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UserSession } from '../../../../core/models/auth.models';
import { AuthStore } from '../../../../core/services/auth.store';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';
import { DocumentoInspeccionResponse } from '../../models/inspeccion.models';
import { DocumentosInspeccionComponent } from './documentos-inspeccion.component';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

const BASE = '/api/v1/inspecciones/inspeccion-1/documentos';

function documento(id: string): DocumentoInspeccionResponse {
  return {
    id,
    inspeccionId: 'inspeccion-1',
    nombreOriginal: `Informe ${id}.pdf`,
    tamanoBytes: 2_500_000,
    subidoPorId: 'autor',
    subidoEn: '2026-10-20T15:00:00Z',
  };
}

describe('DocumentosInspeccionComponent', () => {
  let fixture: ComponentFixture<DocumentosInspeccionComponent>;
  let componente: DocumentosInspeccionComponent;
  let http: HttpTestingController;
  const usuario = signal<UserSession | null>({ id: 'autor', role: 'ESTUDIANTE' } as UserSession);
  const enLinea = signal(true);

  beforeEach(async () => {
    usuario.set({ id: 'autor', role: 'ESTUDIANTE' } as UserSession);
    enLinea.set(true);
    await TestBed.configureTestingModule({
      imports: [DocumentosInspeccionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: AuthStore, useValue: { user: usuario } },
        { provide: ConnectivityService, useValue: { online: enLinea } },
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
      ],
    })
      .overrideComponent(DocumentosInspeccionComponent, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      })
      .compileComponents();
    fixture = TestBed.createComponent(DocumentosInspeccionComponent);
    componente = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function iniciar(estado: string, documentos: DocumentoInspeccionResponse[] = []): void {
    fixture.componentRef.setInput('inspeccion', { id: 'inspeccion-1', autorId: 'autor', estado });
    fixture.detectChanges();
    http.expectOne(BASE).flush(documentos);
    fixture.detectChanges();
  }

  function seleccionar(archivo: File): void {
    const entrada = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      'input[type="file"]',
    )!;
    Object.defineProperty(entrada, 'files', { value: [archivo], configurable: true });
    entrada.dispatchEvent(new Event('change'));
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('el autor adjunta un PDF a su borrador y aparece en la lista', () => {
    iniciar('BORRADOR');

    seleccionar(new File(['%PDF-1.7'], 'Plano.pdf', { type: 'application/pdf' }));
    const subida = http.expectOne(BASE);
    expect(subida.request.method).toBe('POST');
    subida.flush(documento('d1'));
    fixture.detectChanges();

    expect(componente.documentos()).toHaveLength(1);
    expect(texto()).toContain('Informe d1.pdf');
    expect(texto()).toContain('2.4 MB');
    expect(texto()).toContain('20/10/2026 09:00');
  });

  it('muestra el rechazo del servidor junto a la subida', () => {
    iniciar('BORRADOR');

    seleccionar(new File(['hola'], 'falso.pdf', { type: 'application/pdf' }));
    http
      .expectOne(BASE)
      .flush(
        { detail: 'El contenido real del archivo no corresponde a un documento PDF.' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(texto()).toContain('El contenido real del archivo no corresponde a un documento PDF.');
    expect(componente.subiendo()).toBe(false);
  });

  it('no sube un archivo de más de 20 MB', () => {
    iniciar('BORRADOR');
    const enorme = new File(['x'], 'enorme.pdf', { type: 'application/pdf' });
    Object.defineProperty(enorme, 'size', { value: 20 * 1024 * 1024 + 1 });

    seleccionar(enorme);
    fixture.detectChanges();

    http.expectNone(BASE);
    expect(texto()).toContain('inspecciones.documentos.errores.tamano');
  });

  it('con 10 documentos ya no ofrece adjuntar otro', () => {
    iniciar(
      'BORRADOR',
      Array.from({ length: 10 }, (_, i) => documento(`d${i}`)),
    );

    expect(componente.limiteAlcanzado()).toBe(true);
    expect((fixture.nativeElement as HTMLElement).querySelector('input[type="file"]')).toBeNull();
    expect(texto()).toContain('inspecciones.documentos.errores.limite');
  });

  it('sin conexión avisa que los PDF requieren conexión', () => {
    enLinea.set(false);
    iniciar('BORRADOR');

    expect((fixture.nativeElement as HTMLElement).querySelector('input[type="file"]')).toBeNull();
    expect(texto()).toContain('inspecciones.documentos.sinConexion');
  });

  it('una inspección publicada solo permite descargar', () => {
    iniciar('PUBLICADA', [documento('d1')]);

    expect(componente.editable()).toBe(false);
    expect(texto()).not.toContain('inspecciones.documentos.retirar');
    expect(texto()).toContain('inspecciones.documentos.descargar');
  });

  it('quien no es el autor no puede adjuntar aunque sea borrador', () => {
    usuario.set({ id: 'catedratico', role: 'CATEDRATICO' } as UserSession);
    iniciar('BORRADOR', [documento('d1')]);

    expect(componente.editable()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).querySelector('input[type="file"]')).toBeNull();
  });

  it('descarga con el nombre original', () => {
    iniciar('PUBLICADA', [documento('d1')]);
    const crear = vi.fn(() => 'blob:documento');
    const liberar = vi.fn();
    vi.stubGlobal('URL', { createObjectURL: crear, revokeObjectURL: liberar });
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    componente.descargar(documento('d1'));
    http.expectOne(`${BASE}/d1/contenido`).flush(new Blob(['%PDF-1.7']));

    expect(crear).toHaveBeenCalled();
    expect(clic).toHaveBeenCalled();
    expect(liberar).toHaveBeenCalledWith('blob:documento');
    clic.mockRestore();
    vi.unstubAllGlobals();
  });

  it('retira un documento tras confirmarlo', () => {
    iniciar('BORRADOR', [documento('d1'), documento('d2')]);

    componente.confirmarRetiro(documento('d1'));
    componente.retirar();
    const retiro = http.expectOne(`${BASE}/d1`);
    expect(retiro.request.method).toBe('DELETE');
    retiro.flush(null);

    expect(componente.documentos().map((d) => d.id)).toEqual(['d2']);
    expect(componente.documentoARetirar()).toBeNull();
  });

  it('ofrece reintentar si falla la carga de la lista', () => {
    fixture.componentRef.setInput('inspeccion', {
      id: 'inspeccion-1',
      autorId: 'autor',
      estado: 'BORRADOR',
    });
    fixture.detectChanges();
    http.expectOne(BASE).flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(componente.errorCarga()).toBe(true);
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.estado-error button')!
      .click();
    http.expectOne(BASE).flush([]);
    expect(componente.errorCarga()).toBe(false);
  });
});
