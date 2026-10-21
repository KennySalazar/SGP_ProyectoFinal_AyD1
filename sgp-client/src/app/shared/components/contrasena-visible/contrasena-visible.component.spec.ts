import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { traduccionesDePrueba } from '../../../features/formulario/motor/testing/traducciones';
import { ContrasenaVisibleComponent } from './contrasena-visible.component';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, ContrasenaVisibleComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="enviados = enviados + 1">
      <app-contrasena-visible>
        <input type="password" formControlName="password" autocomplete="new-password" />
      </app-contrasena-visible>
      <app-contrasena-visible>
        <input type="password" formControlName="confirmacion" autocomplete="new-password" />
      </app-contrasena-visible>
    </form>
  `,
})
class AnfitrionComponent {
  readonly form = new FormBuilder().nonNullable.group({ password: '', confirmacion: '' });
  enviados = 0;
}

describe('ContrasenaVisibleComponent', () => {
  let fixture: ComponentFixture<AnfitrionComponent>;

  function montar(): void {
    TestBed.configureTestingModule({ imports: [AnfitrionComponent, traduccionesDePrueba()] });
    fixture = TestBed.createComponent(AnfitrionComponent);
    fixture.detectChanges();
  }

  const html = () => fixture.nativeElement as HTMLElement;
  const campo = (indice: number) => html().querySelectorAll('input')[indice];
  const ojo = (indice: number) => html().querySelectorAll<HTMLButtonElement>('button.ojo')[indice];

  it('la contraseña empieza oculta y el botón ofrece mostrarla', () => {
    montar();

    expect(campo(0).type).toBe('password');
    expect(ojo(0).getAttribute('aria-label')).toBe('Mostrar contraseña');
    expect(ojo(0).title).toBe('Mostrar contraseña');
    expect(ojo(0).querySelector('i.pi-eye')).not.toBeNull();
    expect(ojo(0).querySelector('i.pi-eye-slash')).toBeNull();
  });

  it('al pulsar el ojo se muestra la contraseña y el botón ofrece ocultarla', () => {
    montar();

    ojo(0).click();
    fixture.detectChanges();

    expect(campo(0).type).toBe('text');
    expect(ojo(0).getAttribute('aria-label')).toBe('Ocultar contraseña');
    expect(ojo(0).querySelector('i.pi-eye-slash')).not.toBeNull();
    expect(ojo(0).querySelector('i.pi-eye')).toBeNull();
  });

  it('al pulsarlo de nuevo vuelve a ocultarla', () => {
    montar();

    ojo(0).click();
    fixture.detectChanges();
    ojo(0).click();
    fixture.detectChanges();

    expect(campo(0).type).toBe('password');
    expect(ojo(0).getAttribute('aria-label')).toBe('Mostrar contraseña');
  });

  it('alternar la vista no cambia lo escrito ni el valor del formulario', () => {
    montar();
    const anfitrion = fixture.componentInstance;
    campo(0).value = 'Secreta-12345';
    campo(0).dispatchEvent(new Event('input'));

    ojo(0).click();
    fixture.detectChanges();

    expect(campo(0).value).toBe('Secreta-12345');
    expect(anfitrion.form.controls.password.value).toBe('Secreta-12345');
    expect(campo(0).getAttribute('autocomplete')).toBe('new-password');
  });

  it('es un botón que no envía el formulario y se alcanza con el teclado', () => {
    montar();

    expect(ojo(0).type).toBe('button');
    expect(ojo(0).tabIndex).toBeGreaterThanOrEqual(0);
    ojo(0).click();
    expect(fixture.componentInstance.enviados).toBe(0);
  });

  it('cada campo tiene su propio botón y no se afectan entre sí', () => {
    montar();

    ojo(1).click();
    fixture.detectChanges();

    expect(campo(0).type).toBe('password');
    expect(campo(1).type).toBe('text');
    expect(ojo(0).getAttribute('aria-label')).toBe('Mostrar contraseña');
    expect(ojo(1).getAttribute('aria-label')).toBe('Ocultar contraseña');
  });
});
