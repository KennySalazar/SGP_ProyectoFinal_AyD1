import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ModeracionForoPage } from './moderacion.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string, params?: Record<string, unknown>): string {
    return params ? `${key} ${JSON.stringify(params)}` : key;
  }
}

const RUTA = '/api/v1/moderacion/inspecciones-sin-revision';
const RECIENTE = {
  inspeccionId: 'inspeccion-2',
  puenteId: 'puente-1',
  puenteCodigo: 'QZ-001',
  puenteNombre: 'Puente Samalá',
  fechaInspeccion: '2026-10-15',
  publicadoEn: '2026-10-20T08:50:00-06:00',
  autorUsuario: 'pro.externo',
  autorRol: 'PROFESIONAL_EXTERNO',
  hiloId: 'hilo-2',
  totalComentarios: 3,
};
const ANTIGUA = {
  ...RECIENTE,
  inspeccionId: 'inspeccion-1',
  publicadoEn: '2026-10-19T08:00:00-06:00',
  autorUsuario: null,
  autorRol: 'CATEDRATICO',
  hiloId: null,
  totalComentarios: 0,
};

function pagina(content: object[]) {
  return { content, number: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe('ModeracionForoPage', () => {
  let fixture: ComponentFixture<ModeracionForoPage>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModeracionForoPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
      ],
    })
      .overrideComponent(ModeracionForoPage, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      })
      .compileComponents();
    fixture = TestBed.createComponent(ModeracionForoPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const elemento = () => fixture.nativeElement as HTMLElement;

  it('Escenario: lista las publicadas sin revisión en el orden del servidor con acceso a su foro', () => {
    const request = http.expectOne((r) => r.url === RUTA);
    expect(request.request.params.get('pagina')).toBe('0');
    expect(request.request.params.get('tamanio')).toBe('20');
    request.flush(pagina([RECIENTE, ANTIGUA]));
    fixture.detectChanges();

    const filas = elemento().querySelectorAll('article.fila');
    expect(filas).toHaveLength(2);
    expect(filas[0].textContent).toContain('@pro.externo');
    expect(filas[0].textContent).toContain('foro.comentarios');
    expect(filas[1].textContent).toContain('foro.moderacion.pagina.sinHilo');
    expect(filas[0].querySelector('a')?.getAttribute('href')).toBe(
      '/inspecciones/inspeccion-2/publicada',
    );
  });

  it('Escenario: sin pendientes lo dice', () => {
    http.expectOne((r) => r.url === RUTA).flush(pagina([]));
    fixture.detectChanges();

    expect(elemento().textContent).toContain('foro.moderacion.pagina.vacia');
  });

  it('Escenario: si falla ofrece reintentar', () => {
    http.expectOne((r) => r.url === RUTA).flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(elemento().textContent).toContain('foro.moderacion.pagina.error');

    elemento().querySelector<HTMLButtonElement>('.estado button')?.click();
    http.expectOne((r) => r.url === RUTA).flush(pagina([RECIENTE]));
    fixture.detectChanges();
    expect(elemento().querySelectorAll('article.fila')).toHaveLength(1);
  });
});
