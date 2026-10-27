import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { EstudianteAsignableResponse } from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';
import { AsignacionesPuentesPage } from './asignaciones-puentes.page';

const estudiante: EstudianteAsignableResponse = {
  cursoEstudianteId: 'inscripcion-1',
  estudianteId: 'estudiante-1',
  estudianteEmail: 'mario@ejemplo.com',
  estudianteNombre: 'Mario Yancor',
  cursoId: 'curso-1',
  cursoNombre: 'Puentes',
  periodo: '2026-2',
};

describe('Identificacion del estudiante al asignar puentes', () => {
  const api = {
    listarEstudiantesAsignables: vi.fn(),
    listarPuentesAsignables: vi.fn(),
    listarAsignaciones: vi.fn(),
    asignarPuente: vi.fn(),
  };
  beforeEach(() => {
    vi.resetAllMocks();
    api.listarEstudiantesAsignables.mockReturnValue(of([estudiante]));
    api.listarPuentesAsignables.mockReturnValue(of([]));
    api.listarAsignaciones.mockReturnValue(of([]));
    api.asignarPuente.mockReturnValue(of({ message: 'Asignado' }));
    TestBed.configureTestingModule({
      providers: [
        { provide: PuenteApiService, useValue: api },
        { provide: ApiErrorService, useValue: { normalize: vi.fn() } },
      ],
    });
  });
  function crear() {
    return TestBed.runInInjectionContext(() => new AsignacionesPuentesPage());
  }

  it('muestra nombre, correo, curso y periodo sin cambiar el valor de seleccion', () => {
    const pagina = crear();
    expect(pagina.estudiantes()[0].etiqueta).toBe(
      'Mario Yancor (mario@ejemplo.com) — Puentes (2026-2)',
    );
    expect(pagina.estudiantes()[0].cursoEstudianteId).toBe('inscripcion-1');
  });
  it.each([null, undefined, '', '   '])('usa el correo si el nombre es %s', (estudianteNombre) => {
    api.listarEstudiantesAsignables.mockReturnValue(of([{ ...estudiante, estudianteNombre }]));
    expect(crear().estudiantes()[0].etiqueta).toBe('mario@ejemplo.com — Puentes (2026-2)');
  });
  it('distingue las inscripciones de la misma persona por curso y periodo', () => {
    api.listarEstudiantesAsignables.mockReturnValue(
      of([
        estudiante,
        {
          ...estudiante,
          cursoEstudianteId: 'inscripcion-2',
          cursoNombre: 'Estructuras',
          periodo: '2027-1',
        },
      ]),
    );
    const opciones = crear().estudiantes();
    expect(opciones[0].etiqueta).not.toBe(opciones[1].etiqueta);
    expect(opciones[1].etiqueta).toContain('Estructuras (2027-1)');
  });
  it('envia la inscripcion seleccionada y el puente, sin enviar la etiqueta', () => {
    const pagina = crear();
    pagina.form.setValue({ cursoEstudianteId: 'inscripcion-1', puenteId: 'puente-1' });
    pagina.asignar();
    expect(api.asignarPuente).toHaveBeenCalledExactlyOnceWith({
      cursoEstudianteId: 'inscripcion-1',
      puenteId: 'puente-1',
    });
  });
});
