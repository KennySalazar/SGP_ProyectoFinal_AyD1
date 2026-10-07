import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { TestBed } from '@angular/core/testing';
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
      providers: [provideHttpClient(), provideHttpClientTesting()],
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
});
