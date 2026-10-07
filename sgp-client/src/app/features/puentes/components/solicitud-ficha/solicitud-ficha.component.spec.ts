import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslocoPipe } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { SelectorUbicacionStubComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.testing';
import { SolicitudAltaPuenteResponse } from '../../models/puente.models';
import { SolicitudFichaComponent } from './solicitud-ficha.component';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('SolicitudFichaComponent', () => {
  let fixture: ComponentFixture<SolicitudFichaComponent>;

  function solicitud(overrides: Partial<SolicitudAltaPuenteResponse> = {}) {
    return {
      id: 's1',
      nombre: 'Puente propuesto',
      departamento: { id: 'd1', codigoIne: '01', nombre: 'Guatemala' },
      municipio: { id: 'm1', departamentoId: 'd1', codigoIne: '0101', nombre: 'Guatemala' },
      ruta: 'CA-9',
      kilometraje: 12.5,
      latitud: 14.6,
      longitud: -90.5,
      justificacion: 'Falta en el catálogo',
      estado: 'PENDIENTE',
      motivoDecision: null,
      revisadoEn: null,
      puenteCreadoId: null,
      puenteCreadoCodigo: null,
      creadoEn: '2026-10-06T12:00:00Z',
      ...overrides,
    } as SolicitudAltaPuenteResponse;
  }

  function texto(): string {
    return fixture.nativeElement.textContent as string;
  }

  function mostrar(
    datos: SolicitudAltaPuenteResponse,
    entradas: Record<string, unknown> = {},
  ): void {
    fixture.componentRef.setInput('solicitud', datos);
    for (const [nombre, valor] of Object.entries(entradas)) {
      fixture.componentRef.setInput(nombre, valor);
    }
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.overrideComponent(SolicitudFichaComponent, {
      remove: { imports: [TranslocoPipe, SelectorUbicacionComponent] },
      add: { imports: [TraduccionTestPipe, SelectorUbicacionStubComponent] },
    });
    fixture = TestBed.createComponent(SolicitudFichaComponent);
  });

  it('muestra los datos de una solicitud pendiente sin sección de decisión', () => {
    mostrar(solicitud());

    expect(texto()).toContain('Puente propuesto');
    expect(texto()).toContain('CA-9');
    expect(texto()).toContain('12.5');
    expect(texto()).toContain('Falta en el catálogo');
    expect(texto()).toContain('puentes.solicitud.estado.PENDIENTE');
    expect(texto()).not.toContain('puentes.revision.decisionTitle');
    expect(texto()).not.toContain('puentes.revision.requester');
  });

  it('indica cuando no hay justificación ni kilometraje', () => {
    mostrar(solicitud({ justificacion: null, kilometraje: null }));

    expect(texto()).toContain('puentes.revision.noJustification');
    expect(texto()).toContain('—');
  });

  it('en una aprobada muestra el código del puente creado, no un motivo', () => {
    mostrar(
      solicitud({
        estado: 'APROBADA',
        puenteCreadoCodigo: 'GT-09-0901-0001',
        revisadoEn: '2026-10-07T15:30:00Z',
      }),
    );

    expect(texto()).toContain('puentes.revision.decisionTitle');
    expect(texto()).toContain('puentes.revision.createdBridge');
    expect(texto()).toContain('GT-09-0901-0001');
    expect(texto()).toContain('puentes.revision.reviewedAt');
    expect(texto()).not.toContain('puentes.revision.rejectionReason');
  });

  it('en una rechazada muestra el motivo del rechazo', () => {
    mostrar(
      solicitud({
        estado: 'RECHAZADA',
        motivoDecision: 'Ya existe en el catálogo',
        revisadoEn: '2026-10-07T15:30:00Z',
      }),
    );

    expect(texto()).toContain('puentes.revision.rejectionReason');
    expect(texto()).toContain('Ya existe en el catálogo');
    expect(texto()).not.toContain('puentes.revision.createdBridge');
  });

  it('muestra solicitante y revisor solo cuando se piden', () => {
    mostrar(solicitud({ estado: 'RECHAZADA', motivoDecision: 'x' }), {
      solicitanteEmail: 'cat@sgp.local',
      revisadoPorEmail: 'admin@sgp.local',
    });
    expect(texto()).not.toContain('cat@sgp.local');
    expect(texto()).not.toContain('admin@sgp.local');

    fixture.componentRef.setInput('mostrarSolicitante', true);
    fixture.componentRef.setInput('mostrarRevisor', true);
    fixture.detectChanges();

    expect(texto()).toContain('puentes.revision.requester');
    expect(texto()).toContain('cat@sgp.local');
    expect(texto()).toContain('puentes.revision.reviewedBy');
    expect(texto()).toContain('admin@sgp.local');
  });

  it('muestra el mapa en solo lectura, acercado y con los puentes cercanos', () => {
    const cercanos = [{ id: 'c1', titulo: 'GT-1 — X', latitud: 14.6, longitud: -90.5 }];
    mostrar(solicitud(), { cercanos });

    const mapa = fixture.debugElement.query(By.directive(SelectorUbicacionStubComponent))
      .componentInstance as SelectorUbicacionStubComponent;
    expect(mapa.latitud()).toBe(14.6);
    expect(mapa.longitud()).toBe(-90.5);
    expect(mapa.soloLectura()).toBe(true);
    expect(mapa.zoom()).toBe(15);
    expect(mapa.cercanos()).toEqual(cercanos);
  });
});
