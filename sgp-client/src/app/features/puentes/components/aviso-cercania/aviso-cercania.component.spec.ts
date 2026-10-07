import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoPipe } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';
import { PuenteCercanoResponse } from '../../models/puente.models';
import {
  AvisoCercaniaComponent,
  ModoAvisoCercania,
  puntosDeCercanos,
} from './aviso-cercania.component';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('AvisoCercaniaComponent: advertencia de posible duplicado', () => {
  let fixture: ComponentFixture<AvisoCercaniaComponent>;

  const activo: PuenteCercanoResponse = {
    id: 'a',
    codigo: 'GT-01-0114-0001',
    nombre: 'Puente activo',
    activo: true,
    distanciaMetros: 11.1,
    latitud: 14.4811,
    longitud: -90.615,
  };

  const inactivo: PuenteCercanoResponse = {
    ...activo,
    id: 'i',
    codigo: 'GT-01-0114-0002',
    nombre: 'Puente inactivo',
    activo: false,
    distanciaMetros: 7.7,
  };

  function mostrar(
    cercanos: PuenteCercanoResponse[],
    modo: ModoAvisoCercania = 'registro',
    total = cercanos.length,
  ): string {
    fixture.componentRef.setInput('cercanos', cercanos);
    fixture.componentRef.setInput('total', total);
    fixture.componentRef.setInput('modo', modo);
    fixture.detectChanges();
    return fixture.nativeElement.textContent as string;
  }

  beforeEach(() => {
    TestBed.overrideComponent(AvisoCercaniaComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    fixture = TestBed.createComponent(AvisoCercaniaComponent);
  });

  it('lista cada puente con su código, nombre, distancia y estado', () => {
    const texto = mostrar([activo, inactivo]);

    expect(texto).toContain('GT-01-0114-0001 — Puente activo');
    expect(texto).toContain('11.1 m');
    expect(texto).toContain('puentes.active');
    expect(texto).toContain('GT-01-0114-0002 — Puente inactivo');
    expect(texto).toContain('7.7 m');
    expect(texto).toContain('puentes.inactive');
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
  });

  it.each([
    ['registro', 'puentes.'],
    ['solicitud', 'puentes.solicitud.'],
    ['revision', 'puentes.revision.'],
  ] as const)('con activos usa los textos de %s', (modo, prefijo) => {
    const texto = mostrar([activo], modo);

    expect(texto).toContain(`${prefijo}proximityNote`);
    expect(texto).not.toContain('InactiveNote');
    expect(texto).not.toContain('OnlyInactive');
  });

  it.each([
    ['registro', 'puentes.'],
    ['solicitud', 'puentes.solicitud.'],
    ['revision', 'puentes.revision.'],
  ] as const)('con solo inactivos cambia el aviso en %s', (modo, prefijo) => {
    const texto = mostrar([inactivo], modo);

    expect(texto).toContain(`${prefijo}proximityOnlyInactiveNote`);
    expect(texto).not.toContain(`${prefijo}proximityNote`);
  });

  it('con activos e inactivos mantiene el aviso y agrega la nota de inactivos', () => {
    const texto = mostrar([activo, inactivo], 'solicitud');

    expect(texto).toContain('puentes.solicitud.proximityNote');
    expect(texto).toContain('puentes.solicitud.proximityInactiveNote');
    expect(texto).not.toContain('OnlyInactive');
  });

  it('avisa cuando solo se muestran los primeros resultados', () => {
    expect(mostrar([activo], 'registro', 5)).toContain('puentes.proximityLimit');
    expect(mostrar([activo], 'registro', 1)).not.toContain('puentes.proximityLimit');
  });
});

describe('puntosDeCercanos', () => {
  it('convierte los puentes cercanos en puntos de mapa marcando los inactivos', () => {
    const puntos = puntosDeCercanos([
      {
        id: 'a',
        codigo: 'GT-1',
        nombre: 'A',
        activo: true,
        distanciaMetros: 1,
        latitud: 14.1,
        longitud: -90.1,
      },
      {
        id: 'b',
        codigo: 'GT-2',
        nombre: 'B',
        activo: false,
        distanciaMetros: 2,
        latitud: 14.2,
        longitud: -90.2,
      },
    ]);

    expect(puntos).toEqual([
      { id: 'a', titulo: 'GT-1 — A', latitud: 14.1, longitud: -90.1, inactivo: false },
      { id: 'b', titulo: 'GT-2 — B', latitud: 14.2, longitud: -90.2, inactivo: true },
    ]);
  });
});
