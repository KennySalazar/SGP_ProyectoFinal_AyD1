import { ERROR_GESTIONADO_LOCALMENTE } from '../../../core/interceptors/problem.interceptor';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { InspeccionApiService } from './inspeccion-api.service';
describe('InspeccionApiService', () => {
  let service: InspeccionApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InspeccionApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('lista las inspecciones propias con filtro y paginación', () => {
    service.listarMisInspecciones(2, 50, 'BORRADOR').subscribe();
    const request = http.expectOne('/api/v1/inspecciones?pagina=2&tamanio=50&estado=BORRADOR');
    expect(request.request.method).toBe('GET');
    request.flush({ content: [] });
  });
  it('elimina un borrador propio por su identificador', () => {
    service.eliminarBorrador('inspeccion/1').subscribe();
    const request = http.expectOne('/api/v1/inspecciones/inspeccion%2F1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
  it('crea una corrección de una inspección publicada con su motivo', () => {
    service.crearCorreccion('inspeccion/1', 'Ajuste de fecha').subscribe();
    const request = http.expectOne('/api/v1/inspecciones/inspeccion%2F1/correcciones');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ motivo: 'Ajuste de fecha' });
    request.flush({ id: 'correccion-1' });
  });
  it('envía una inspección con clave de idempotencia', () => {
    service.enviar('inspeccion-1', 'envio-1').subscribe();

    const request = http.expectOne('/api/v1/inspecciones/inspeccion-1/enviar');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('envio-1');
    request.flush({ id: 'inspeccion-1', estado: 'ENVIADA' });
  });

  it('recupera identificacion faltante sin perder respuestas ni valores capturados', () => {
    let resultado: unknown;
    service.obtenerBorrador('inspeccion-1', true).subscribe((valor) => (resultado = valor));
    http.expectOne('/api/v1/inspecciones/inspeccion-1/borrador').flush({
      estado: 'BORRADOR',
      puenteId: 'puente/1',
      identificacionCapturada: { ruta: 'Ruta historica', departamento: '' },
      datos: {
        identificacion: { municipio: 'Municipio capturado', kilometraje: 0 },
        otros: { recomendacion: 'Conservar' },
      },
    });
    http.expectOne('/api/v1/puentes/puente%2F1').flush({
      ruta: 'P01',
      departamento: { nombre: 'Chimaltenango' },
      municipio: { nombre: 'Acatenango' },
      kilometraje: null,
    });
    expect(resultado).toEqual(
      expect.objectContaining({
        identificacionCapturada: expect.objectContaining({
          ruta: 'Ruta historica',
          departamento: 'Chimaltenango',
          municipio: 'Municipio capturado',
          kilometraje: 0,
        }),
        datos: expect.objectContaining({ otros: { recomendacion: 'Conservar' } }),
      }),
    );
  });
  it('no consulta el puente cuando la identificacion esta completa', () => {
    service.obtenerBorrador('i', true).subscribe();
    http.expectOne('/api/v1/inspecciones/i/borrador').flush({
      estado: 'BORRADOR',
      datos: { identificacion: { ruta: 'R', departamento: 'D', municipio: 'M' } },
    });
    http.expectNone((req) => req.url.startsWith('/api/v1/puentes/'));
  });
  it('no altera publicaciones con datos faltantes', () => {
    service.obtenerBorrador('i', true).subscribe();
    http.expectOne('/api/v1/inspecciones/i/borrador').flush({ estado: 'PUBLICADA', datos: {} });
    http.expectNone((req) => req.url.startsWith('/api/v1/puentes/'));
  });
  it('propaga el fallo al recuperar el puente sin devolver datos parciales', () => {
    let fallo: unknown;
    service.obtenerBorrador('i', true).subscribe({ error: (error: unknown) => (fallo = error) });
    http.expectOne('/api/v1/inspecciones/i/borrador').flush({ estado: 'BORRADOR', puenteId: 'p' });
    http.expectOne('/api/v1/puentes/p').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(fallo).toEqual(expect.objectContaining({ status: 403 }));
  });

  it('consulta historial publicado por puente con página inicial 0 y tamaño 20', () => {
    service.listarPublicadasPorPuente('puente/1').subscribe();
    const request = http.expectOne(
      '/api/v1/inspecciones/puentes/puente%2F1/publicadas?pagina=0&tamanio=20',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ content: [] });
  });

  it('transmite la página solicitada al historial publicado sin consultar inspecciones propias', () => {
    service.listarPublicadasPorPuente('puente-1', 2, 20).subscribe();
    const request = http.expectOne(
      '/api/v1/inspecciones/puentes/puente-1/publicadas?pagina=2&tamanio=20',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ content: [] });
  });

  it('consulta el detalle publicado mediante GET y conserva el identificador codificado', () => {
    service.obtenerPublicada('inspeccion/1').subscribe();
    const request = http.expectOne('/api/v1/inspecciones/inspeccion%2F1/publicada');
    expect(request.request.method).toBe('GET');
    expect(request.request.body).toBeNull();
    request.flush({ id: 'inspeccion/1', estado: 'PUBLICADA' });
  });

  it('retira una fotografía mediante DELETE sin cuerpo ni eliminación de inspección', () => {
    service.retirarFotografia('inspeccion/1', 'foto/1').subscribe();
    const request = http.expectOne('/api/v1/inspecciones/inspeccion%2F1/fotografias/foto%2F1');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('consulta el calculo y la confirmacion del IC con el identificador codificado', () => {
    service.obtenerConfirmacionEstado('inspeccion/1').subscribe();
    const request = http.expectOne('/api/v1/inspecciones/inspeccion%2F1/confirmacion-estado');
    expect(request.request.method).toBe('GET');
    request.flush({ inspeccionId: 'inspeccion/1' });
  });

  it('envia el estado confirmado, la huella del calculo y la justificacion', () => {
    const cuerpo = {
      estadoConfirmado: 'MALO' as const,
      hashEntrada: 'hash-vigente',
      justificacion: 'Ajuste profesional',
    };
    service.confirmarEstado('inspeccion/1', cuerpo).subscribe();
    const request = http.expectOne('/api/v1/inspecciones/inspeccion%2F1/confirmacion-estado');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(cuerpo);
    request.flush({ inspeccionId: 'inspeccion/1' });
  });

  it('adjunta, lista, descarga y retira documentos PDF de una inspección (HU-070)', () => {
    const base = '/api/v1/inspecciones/inspeccion%2F1/documentos';
    const archivo = new File(['%PDF-1.7'], 'Plano.pdf', { type: 'application/pdf' });

    service.adjuntarDocumento('inspeccion/1', archivo).subscribe();
    const subida = http.expectOne(base);
    expect(subida.request.method).toBe('POST');
    expect((subida.request.body as FormData).get('archivo')).toBeInstanceOf(File);
    subida.flush({});

    service.listarDocumentos('inspeccion/1').subscribe();
    http.expectOne(base).flush([]);

    service.descargarDocumento('inspeccion/1', 'doc/1').subscribe();
    const descarga = http.expectOne(`${base}/doc%2F1/contenido`);
    expect(descarga.request.responseType).toBe('blob');
    descarga.flush(new Blob());

    service.retirarDocumento('inspeccion/1', 'doc/1').subscribe();
    const retiro = http.expectOne(`${base}/doc%2F1`);
    expect(retiro.request.method).toBe('DELETE');
    retiro.flush(null);
  });
  it('consulta conflictos propios con identificador codificado y gestiona errores localmente', () => {
    service.listarConflictosSincronizacion('inspeccion/1').subscribe();
    const req = http.expectOne('/api/v1/inspecciones/inspeccion%2F1/conflictos-sincronizacion');
    expect(req.request.context.get(ERROR_GESTIONADO_LOCALMENTE)).toBe(true);
    req.flush([]);
  });
});
