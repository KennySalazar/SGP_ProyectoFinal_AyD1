import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, describe, expect, it } from 'vitest';
import { routes } from '../../../../app.routes';
import { CatalogoPuentesPage } from '../../../puentes/pages/catalogo-puentes/catalogo-puentes.page';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';
import { ContrasenaVisibleComponent } from '../../../../shared/components/contrasena-visible/contrasena-visible.component';
import { LoginPage } from './login.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Acceso público al catálogo desde login', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('la contraseña se puede mostrar u ocultar y el correo no tiene ojo', () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    for (const component of [LoginPage, AuthCardComponent, ContrasenaVisibleComponent]) {
      TestBed.overrideComponent(component, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      });
    }
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    const html = fixture.nativeElement as HTMLElement;
    const campo = html.querySelector<HTMLInputElement>('input[formControlName="password"]')!;

    expect(html.querySelectorAll('button.ojo')).toHaveLength(1);
    expect(campo.closest('app-contrasena-visible')).not.toBeNull();
    expect(campo.type).toBe('password');
    html.querySelector<HTMLButtonElement>('button.ojo')!.click();
    expect(campo.type).toBe('text');
    // El campo sigue siendo del formulario de la página.
    campo.value = 'Puentes2026';
    campo.dispatchEvent(new Event('input'));
    expect(fixture.componentInstance.form.controls.password.value).toBe('Puentes2026');
  });

  it('permite explorar sin completar el formulario ni realizar login', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    for (const component of [
      LoginPage,
      CatalogoPuentesPage,
      AuthCardComponent,
      ContrasenaVisibleComponent,
    ]) {
      TestBed.overrideComponent(component, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      });
    }
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    expect(fixture.componentInstance.form.invalid).toBe(true);
    const link = fixture.nativeElement.querySelector('.catalogo-acceso a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/puentes');
    expect(link.closest('form')).toBeNull();
    expect(link.textContent).toContain('puentes.catalogo.explore');
    link.click();
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/puentes');
    TestBed.inject(HttpTestingController).expectNone((req) => req.url.includes('/api/v1/auth/'));
  });
});
