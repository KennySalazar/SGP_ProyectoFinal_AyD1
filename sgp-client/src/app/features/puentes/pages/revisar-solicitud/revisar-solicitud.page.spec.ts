import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AvisoCercaniaComponent } from '../../components/aviso-cercania/aviso-cercania.component';
import { SolicitudFichaComponent } from '../../components/solicitud-ficha/solicitud-ficha.component';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { SelectorUbicacionStubComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.testing';
import { RevisarSolicitudPage } from './revisar-solicitud.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('RevisarSolicitudPage: HU011', () => {
  let fixture: ComponentFixture<RevisarSolicitudPage>;
  let page: RevisarSolicitudPage;
  let http: HttpTestingController;

  const departamento = { id: 'd1', codigoIne: '01', nombre: 'Guatemala' };
  const municipio = { id: 'm1', departamentoId: 'd1', codigoIne: '0114', nombre: 'Amatitlán' };
  const URL = '/api/v1/solicitudes-puente/solicitud-1';

  const cercano = {
    id: 'puente-1',
    codigo: 'GT-01-0114-0001',
    nombre: 'Puente existente',
    activo: true,
    distanciaMetros: 11.1,
    latitud: 14.4811,
    longitud: -90.615,
  };

  function solicitud(overrides: object = {}) {
    return {
      id: 'solicitud-1',
      nombre: 'Puente propuesto',
      departamento,
      municipio,
      ruta: 'CA-9',
      kilometraje: null,
      latitud: 14.481,
      longitud: -90.615,
      justificacion: 'No aparece en el catálogo',
      estado: 'PENDIENTE',
      motivoDecision: null,
      revisadoEn: null,
      puenteCreadoId: null,
      puenteCreadoCodigo: null,
      creadoEn: '2026-10-06T12:00:00Z',
      ...overrides,
    };
  }

  function detalle(overrides: object = {}, cercanos: unknown[] = []) {
    return {
      solicitud: solicitud(overrides),
      solicitanteEmail: 'catedratico@sgp.local',
      puentesCercanos: cercanos,
      totalPuentesCercanos: cercanos.length,
    };
  }

  function cargar(cuerpo: object): void {
    http.expectOne(URL).flush(cuerpo);
    fixture.detectChanges();
  }

  const botones = () =>
    [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
  const boton = (texto: string) => botones().find((b) => b.textContent?.includes(texto));

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
      ],
    });
    TestBed.overrideComponent(RevisarSolicitudPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    TestBed.overrideComponent(AvisoCercaniaComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    TestBed.overrideComponent(SolicitudFichaComponent, {
      remove: { imports: [TranslocoPipe, SelectorUbicacionComponent] },
      add: { imports: [TraduccionTestPipe, SelectorUbicacionStubComponent] },
    });

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RevisarSolicitudPage);
    page = fixture.componentInstance;
    fixture.componentRef.setInput('id', 'solicitud-1');
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('carga la solicitud con el solicitante y su justificación', () => {
    cargar(detalle());

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Puente propuesto');
    expect(texto).toContain('catedratico@sgp.local');
    expect(texto).toContain('No aparece en el catálogo');
    expect(texto).toContain('puentes.solicitud.estado.PENDIENTE');
    expect(fixture.nativeElement.querySelector('.proximity')).toBeNull();
    expect(boton('puentes.revision.approve')).toBeTruthy();
    expect(boton('puentes.revision.reject')).toBeTruthy();
  });

  it('muestra un mensaje cuando la solicitud no existe', () => {
    http.expectOne(URL).flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(page.errorCarga()).toBe('puentes.revision.notFound');
    expect(fixture.nativeElement.textContent).toContain('puentes.revision.notFound');
  });

  it('aprueba sin cercanos con confirmarCercania en false y muestra el código', () => {
    cargar(detalle());

    page.aprobar();
    page.aprobar();

    const peticion = http.expectOne(`${URL}/aprobar`);
    expect(peticion.request.body).toEqual({ confirmarCercania: false });
    expect(page.procesando()).toBe(true);

    peticion.flush(solicitud({ estado: 'APROBADA', puenteCreadoCodigo: 'GT-01-0114-0001' }));
    fixture.detectChanges();

    expect(page.decision()?.puenteCreadoCodigo).toBe('GT-01-0114-0001');
    expect(page.procesando()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('puentes.revision.approved');
  });

  it('con puentes cercanos muestra la advertencia, los pasa al mapa y aprueba confirmando', () => {
    cargar(detalle({}, [cercano]));

    expect(fixture.nativeElement.querySelector('.proximity')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('GT-01-0114-0001 — Puente existente');
    expect(page.puntosCercanos()).toEqual([
      {
        id: 'puente-1',
        titulo: 'GT-01-0114-0001 — Puente existente',
        latitud: 14.4811,
        longitud: -90.615,
        inactivo: false,
      },
    ]);
    expect(boton('puentes.revision.approveAnyway')).toBeTruthy();

    page.aprobar();

    const peticion = http.expectOne(`${URL}/aprobar`);
    expect(peticion.request.body).toEqual({ confirmarCercania: true });
    peticion.flush(solicitud({ estado: 'APROBADA', puenteCreadoCodigo: 'GT-01-0114-0002' }));
  });

  it('si aparecen puentes cercanos mientras se revisa, muestra el error y recarga el detalle', () => {
    cargar(detalle());

    page.aprobar();
    http
      .expectOne(`${URL}/aprobar`)
      .flush(
        { code: 'puente_cercano', detail: 'Se requiere confirmar la cercanía.' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(page.errorDecision()).toBe('Se requiere confirmar la cercanía.');
    expect(page.procesando()).toBe(false);
    expect(page.decision()).toBeNull();

    cargar(detalle({}, [cercano]));
    expect(page.hayCercanos()).toBe(true);
    expect(page.errorDecision()).toBe('Se requiere confirmar la cercanía.');
  });

  it('exige un motivo para rechazar y no envía la petición sin él', () => {
    cargar(detalle());

    page.abrirRechazo();
    fixture.detectChanges();
    page.motivo.setValue('   ');
    page.rechazar();
    fixture.detectChanges();

    http.expectNone(`${URL}/rechazar`);
    expect(page.mensajeMotivo()).toBe('puentes.revision.reasonRequired');
    expect(fixture.nativeElement.textContent).toContain('puentes.revision.reasonRequired');
  });

  it('rechaza con el motivo recortado y muestra la confirmación', () => {
    cargar(detalle());

    page.abrirRechazo();
    page.motivo.setValue('  Ya existe en el catálogo  ');
    page.rechazar();

    const peticion = http.expectOne(`${URL}/rechazar`);
    expect(peticion.request.body).toEqual({ motivo: 'Ya existe en el catálogo' });
    expect(page.motivo.disabled).toBe(true);

    peticion.flush(solicitud({ estado: 'RECHAZADA', motivoDecision: 'Ya existe en el catálogo' }));
    fixture.detectChanges();

    expect(page.decision()?.estado).toBe('RECHAZADA');
    expect(fixture.nativeElement.textContent).toContain('puentes.revision.rejected');
  });

  it('cancelar el rechazo limpia el motivo y vuelve a las acciones', () => {
    cargar(detalle());

    page.abrirRechazo();
    page.motivo.setValue('Texto');
    page.cancelarRechazo();
    fixture.detectChanges();

    expect(page.rechazando()).toBe(false);
    expect(page.motivo.value).toBe('');
    expect(boton('puentes.revision.approve')).toBeTruthy();
  });

  it('si la solicitud ya fue resuelta por otro administrador recarga y deja de ofrecer acciones', () => {
    cargar(detalle());

    page.abrirRechazo();
    page.motivo.setValue('No procede');
    page.rechazar();
    http
      .expectOne(`${URL}/rechazar`)
      .flush(
        { code: 'solicitud_no_pendiente', detail: 'La solicitud ya fue aprobada.' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(page.errorDecision()).toBe('La solicitud ya fue aprobada.');
    expect(page.rechazando()).toBe(false);

    cargar(detalle({ estado: 'APROBADA', puenteCreadoCodigo: 'GT-01-0114-0001' }));

    expect(page.pendiente()).toBe(false);
    expect(boton('puentes.revision.approve')).toBeUndefined();
    expect(boton('puentes.revision.reject')).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('GT-01-0114-0001');
    expect(fixture.nativeElement.textContent).toContain('puentes.revision.createdBridge');
  });

  it('una solicitud rechazada muestra el motivo, quién la revisó y cuándo, sin acciones', () => {
    http.expectOne(URL).flush({
      ...detalle({
        estado: 'RECHAZADA',
        motivoDecision: 'Duplicado',
        revisadoEn: '2026-10-07T15:30:00Z',
      }),
      revisadoPorEmail: 'admin@sgp.local',
    });
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('puentes.revision.rejectionReason');
    expect(texto).toContain('Duplicado');
    expect(texto).toContain('puentes.revision.reviewedBy');
    expect(texto).toContain('admin@sgp.local');
    expect(texto).toContain('puentes.revision.reviewedAt');
    expect(botones()).toHaveLength(0);
  });

  it('una solicitud aprobada muestra el código del puente y el revisor sin advertencia de cercanía', () => {
    http.expectOne(URL).flush({
      ...detalle(
        {
          estado: 'APROBADA',
          puenteCreadoCodigo: 'GT-09-0901-0001',
          revisadoEn: '2026-10-07T15:30:00Z',
        },
        [cercano],
      ),
      revisadoPorEmail: 'admin@sgp.local',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.proximity')).toBeNull();
    expect(page.hayCercanos()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('GT-09-0901-0001');
    expect(fixture.nativeElement.textContent).toContain('admin@sgp.local');
  });

  describe('cercanos inactivos', () => {
    const inactivo = { ...cercano, id: 'puente-2', codigo: 'GT-01-0114-0002', activo: false };

    it('con solo inactivos explica que conviene reactivar en lugar de aprobar', () => {
      cargar(detalle({}, [inactivo]));

      const texto = (fixture.nativeElement.querySelector('.proximity') as HTMLElement)
        .textContent as string;
      expect(texto).toContain('puentes.revision.proximityOnlyInactiveNote');
      expect(texto).not.toContain('puentes.revision.proximityNote');
      expect(page.puntosCercanos()[0].inactivo).toBe(true);
    });

    it('con activos e inactivos agrega la nota sobre los inactivos', () => {
      cargar(detalle({}, [cercano, inactivo]));

      const texto = (fixture.nativeElement.querySelector('.proximity') as HTMLElement)
        .textContent as string;
      expect(texto).toContain('puentes.revision.proximityNote');
      expect(texto).toContain('puentes.revision.proximityInactiveNote');
      expect(page.puntosCercanos().map((p) => p.inactivo)).toEqual([false, true]);
    });

    it('con solo activos no muestra notas de inactivos', () => {
      cargar(detalle({}, [cercano]));

      const texto = (fixture.nativeElement.querySelector('.proximity') as HTMLElement)
        .textContent as string;
      expect(texto).not.toContain('InactiveNote');
      expect(texto).not.toContain('OnlyInactive');
    });
  });
});
