import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';
import { RoleName, UserSession } from '../../../../core/models/auth.models';
import { AuthStore } from '../../../../core/services/auth.store';
import { DashboardPage } from './dashboard.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Característica: aviso de colegiado pendiente HU002', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.overrideComponent(DashboardPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
  });

  function abrirCon(role: RoleName, colegiadoVerificado: boolean | null): HTMLElement {
    const sesion: UserSession = {
      id: 'usuario-1',
      email: 'usuario@ejemplo.com',
      role,
      verified: true,
      activated: true,
      active: true,
      twoFactorEnabled: false,
      colegiadoVerificado,
    };
    TestBed.inject(AuthStore).setUser(sesion);
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('Escenario: un Profesional Externo sin verificar ve que no puede inspeccionar aún', () => {
    const vista = abrirCon('PROFESIONAL_EXTERNO', false);
    const aviso = vista.querySelector('.colegiado-pendiente[role="status"]');
    expect(aviso).not.toBeNull();
    expect(aviso?.textContent).toContain('dashboard.colegiadoPendiente.note');
  });

  it('Escenario: con el colegiado verificado ya no se muestra el aviso', () => {
    expect(abrirCon('PROFESIONAL_EXTERNO', true).querySelector('.colegiado-pendiente')).toBeNull();
  });

  it.each<RoleName>(['ADMINISTRADOR', 'CATEDRATICO', 'ESTUDIANTE'])(
    'Escenario: el rol %s no recibe el aviso',
    (role) => {
      expect(abrirCon(role, null).querySelector('.colegiado-pendiente')).toBeNull();
    },
  );
  it.each<[RoleName, string[], string[]]>([
    [
      'ADMINISTRADOR',
      [
        '/admin',
        '/admin/cursos',
        '/admin/formularios',
        '/admin/bitacora',
        '/mantenimiento/ordenes',
      ],
      ['/revisiones', '/observaciones', '/inspecciones', '/preparar-salida'],
    ],
    [
      'CATEDRATICO',
      [
        '/revisiones',
        '/estudiantes/pendientes',
        '/puentes/asignaciones',
        '/mantenimiento/ordenes',
        '/preparar-salida',
      ],
      ['/admin', '/admin/cursos', '/observaciones'],
    ],
    [
      'ESTUDIANTE',
      ['/puentes/mis-asignaciones', '/observaciones', '/inspecciones', '/preparar-salida'],
      ['/admin', '/revisiones', '/mantenimiento/ordenes'],
    ],
    [
      'PROFESIONAL_EXTERNO',
      ['/inspecciones', '/preparar-salida'],
      ['/admin', '/revisiones', '/observaciones', '/mantenimiento/ordenes'],
    ],
  ])('muestra accesos correspondientes a %s', (role, permitidos, excluidos) => {
    const vista = abrirCon(role, role === 'PROFESIONAL_EXTERNO' ? true : null);
    const rutas = Array.from(vista.querySelectorAll('a')).map((a) => a.getAttribute('href'));
    for (const ruta of [
      ...permitidos,
      '/puentes',
      '/avisos',
      '/offline',
      '/perfil',
      '/seguridad',
    ]) {
      expect(rutas).toContain(ruta);
    }
    for (const ruta of excluidos) expect(rutas).not.toContain(ruta);
    for (const key of [
      'dashboard.toImplement',
      'dashboard.basePrepared',
      'dashboard.baseServices',
      'common.pending',
    ])
      expect(vista.textContent).not.toContain(key);
  });

  it('no ofrece captura ni preparación al profesional pendiente de verificar', () => {
    const vista = abrirCon('PROFESIONAL_EXTERNO', false);
    const rutas = Array.from(vista.querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(rutas).not.toContain('/inspecciones');
    expect(rutas).not.toContain('/preparar-salida');
    expect(rutas).toContain('/puentes');
    expect(rutas).toContain('/perfil');
  });

  it('actualiza los accesos cuando cambia la cuenta de la sesión', () => {
    abrirCon('CATEDRATICO', null);
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    const auth = TestBed.inject(AuthStore);
    auth.setUser({ ...auth.user()!, role: 'ESTUDIANTE' });
    fixture.detectChanges();
    const rutas = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a')).map(
      (a) => a.getAttribute('href'),
    );
    expect(rutas).toContain('/observaciones');
    expect(rutas).not.toContain('/revisiones');
  });
});
