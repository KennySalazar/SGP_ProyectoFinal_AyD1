import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AvisosPage } from './avisos.page';
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

describe('AvisosPage', () => {
  let fixture: ComponentFixture<AvisosPage>;
  let component: AvisosPage;

  const mockStore = {
    unreadCount: signal(2),
    avisos: signal<NotificacionResponse[]>([
      {
        id: 'aviso-1',
        tipo: 'VEREDICTO_REVISION',
        titulo: 'Veredicto emitido',
        mensaje: 'Tu inspección ha sido aprobada',
        entidad: 'INSPECCION',
        entidadId: '019a0000-0000-7000-8000-000000000002',
        claveEvento: 'clave-2',
        creadoEn: '2026-10-18T11:00:00Z',
        leido: false,
      },
    ]),
    totalElements: signal(1),
    totalPages: signal(1),
    pagina: signal(0),
    tamanio: signal(20),
    soloNoLeidos: signal(false),
    loading: signal(false),
    sinConexionAviso: signal(false),
    cargarAvisos: vi.fn(),
    marcarLeida: vi.fn(),
    marcarTodasLeidas: vi.fn(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AvisosPage, TraduccionTestPipe],
      providers: [{ provide: NotificacionesStore, useValue: mockStore }],
    })
      .overrideComponent(AvisosPage, {
        remove: { imports: [] },
        add: { imports: [TraduccionTestPipe] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(AvisosPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('inicializa y carga los avisos', () => {
    expect(component).toBeTruthy();
    expect(mockStore.cargarAvisos).toHaveBeenCalledWith(0, false);
    const title = fixture.nativeElement.querySelector('.page-title');
    expect(title).not.toBeNull();
  });

  it('permite pulsar un aviso para marcarlo y navegar', () => {
    const row = fixture.nativeElement.querySelector('.aviso-row') as HTMLButtonElement;
    expect(row).not.toBeNull();
    row.click();

    expect(mockStore.marcarLeida).toHaveBeenCalledWith(mockStore.avisos()[0]);
  });

  it('muestra el banner offline cuando está sin conexión', () => {
    mockStore.sinConexionAviso.set(true);
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('.offline-alert');
    expect(banner).not.toBeNull();
    expect(banner.textContent).toContain('Sin conexión');
  });

  it('muestra estado vacío cuando no existen avisos', () => {
    mockStore.avisos.set([]);
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('.empty-text');
    expect(empty).not.toBeNull();
    expect(empty.textContent).toContain('No tienes avisos');
  });

  it('permite marcar todos como leídos', () => {
    const btn = fixture.nativeElement.querySelector('.btn-marcar-todas') as HTMLButtonElement;
    expect(btn).not.toBeNull();
    btn.click();

    expect(mockStore.marcarTodasLeidas).toHaveBeenCalled();
  });
});
