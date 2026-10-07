import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, Pipe, PipeTransform } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { SelectorUbicacionStubComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.testing';
import { PuenteFormComponent } from './puente-form.component';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

@Component({
  standalone: true,
  imports: [PuenteFormComponent],
  template: `
    <app-puente-form [ocultarAcciones]="ocultar">
      <p class="extra">Campo extra</p>
      <a formAcciones class="cancelar">Cancelar</a>
    </app-puente-form>
  `,
})
class AnfitrionComponent {
  ocultar = false;
}

describe('PuenteFormComponent: contenido proyectado', () => {
  function crear(ocultar: boolean) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
        { provide: ApiErrorService, useValue: { clear: vi.fn() } },
      ],
    });
    TestBed.overrideComponent(PuenteFormComponent, {
      remove: { imports: [TranslocoPipe, SelectorUbicacionComponent] },
      add: { imports: [TraduccionTestPipe, SelectorUbicacionStubComponent] },
    });

    const fixture = TestBed.createComponent(AnfitrionComponent);
    fixture.componentInstance.ocultar = ocultar;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('coloca las acciones adicionales junto al botón de envío y el resto antes', () => {
    const raiz = crear(false);

    const acciones = raiz.querySelector('.actions') as HTMLElement;
    expect(acciones.querySelector('button[type=submit]')).not.toBeNull();
    expect(acciones.querySelector('.cancelar')).not.toBeNull();
    expect(acciones.querySelector('.extra')).toBeNull();
    expect(raiz.querySelector('.extra')).not.toBeNull();
  });

  it('no muestra las acciones adicionales cuando se ocultan las acciones', () => {
    const raiz = crear(true);

    expect(raiz.querySelector('.cancelar')).toBeNull();
    expect(raiz.querySelector('button[type=submit]')).toBeNull();
    expect(raiz.querySelector('.extra')).not.toBeNull();
  });
});
