import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CampanaAvisosComponent } from './campana-avisos.component';
import { NotificacionesStore } from '../../services/notificaciones.store';
import { NotificacionResponse } from '../../models/notificacion.models';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string, params?: { count?: number }): string {
    if (key === 'avisos.unreadAriaLabel' && params?.count !== undefined) {
      return `${params.count} avisos sin leer`;
    }
    if (key === 'avisos.emptyState') {
      return 'No tienes avisos';
    }
    if (key === 'avisos.offlineNotice') {
      return 'Sin conexión. Mostrando el último contador conocido. No se pudo actualizar.';
    }
    return key;
  }
}

describe('CampanaAvisosComponent', () => {
  let fixture: ComponentFixture<CampanaAvisosComponent>;
  let component: CampanaAvisosComponent;

  const mockStore = {
    unreadCount: signal(3),
    avisos: signal<NotificacionResponse[]>([
      {
        id: 'aviso-1',
        tipo: 'INSPECCION_ENVIADA',
        titulo: 'Nueva inspección',
        mensaje: 'Se envió una inspección',
        entidad: 'INSPECCION',
        entidadId: '019a0000-0000-7000-8000-000000000001',
        claveEvento: 'clave-1',
        creadoEn: '2026-10-18T10:00:00Z',
        leido: false,
      },
    ]),
    totalElements: signal(1),
    pagina: signal(0),
    soloNoLeidos: signal(false),
    loading: signal(false),
    sinConexionAviso: signal(false),
    cargarAvisos: vi.fn(),
    marcarLeida: vi.fn(),
    marcarTodasLeidas: vi.fn(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CampanaAvisosComponent, TraduccionTestPipe],
      providers: [provideRouter([]), { provide: NotificacionesStore, useValue: mockStore }],
    })
      .overrideComponent(CampanaAvisosComponent, {
        remove: { imports: [] },
        add: { imports: [TraduccionTestPipe] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(CampanaAvisosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('muestra la campana con el aria-label accesible y contador en aria-live', () => {
    const btn = fixture.nativeElement.querySelector('.campana-btn') as HTMLButtonElement;
    expect(btn).not.toBeNull();
    expect(btn.getAttribute('aria-label')).toBe('3 avisos sin leer');

    const badge = fixture.nativeElement.querySelector('.campana-badge') as HTMLElement;
    expect(badge).not.toBeNull();
    expect(badge.getAttribute('aria-live')).toBe('polite');
    expect(badge.textContent?.trim()).toBe('3');
  });

  it('abre el dropdown al pulsar la campana y carga avisos', () => {
    const btn = fixture.nativeElement.querySelector('.campana-btn') as HTMLButtonElement;
    btn.click();
    fixture.detectChanges();

    expect(component.abierto()).toBe(true);
    expect(mockStore.cargarAvisos).toHaveBeenCalled();

    const dropdown = fixture.nativeElement.querySelector('.avisos-dropdown');
    expect(dropdown).not.toBeNull();
  });

  it('permite marcar un aviso y navega', () => {
    component.abierto.set(true);
    fixture.detectChanges();

    const avisoItem = fixture.nativeElement.querySelector('.aviso-item') as HTMLButtonElement;
    expect(avisoItem).not.toBeNull();
    avisoItem.click();

    expect(mockStore.marcarLeida).toHaveBeenCalledWith(mockStore.avisos()[0]);
    expect(component.abierto()).toBe(false);
  });

  it('muestra aviso de sin conexión cuando no se pudo actualizar', () => {
    component.abierto.set(true);
    mockStore.sinConexionAviso.set(true);
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('.offline-banner');
    expect(banner).not.toBeNull();
    expect(banner.textContent).toContain('Sin conexión');
  });

  it('muestra estado vacío cuando no hay avisos', () => {
    component.abierto.set(true);
    mockStore.avisos.set([]);
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('.empty-state');
    expect(empty).not.toBeNull();
    expect(empty.textContent).toContain('No tienes avisos');
  });
});
