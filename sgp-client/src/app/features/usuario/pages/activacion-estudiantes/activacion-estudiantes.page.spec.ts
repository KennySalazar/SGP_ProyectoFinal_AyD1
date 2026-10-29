import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { UsuarioApiService } from '../../services/usuario-api.service';
import { ActivacionEstudiantesPage } from './activacion-estudiantes.page';

describe('Activacion y vinculacion a varios cursos', () => {
  let page: ActivacionEstudiantesPage;
  const estudiante = {
    id: 'alumno',
    email: 'alumno@test.com',
    nombreCompleto: 'Alumno',
    nombreUsuario: 'alumno',
  };
  const api = {
    listarEstudiantesPendientes: vi.fn(),
    listarEstudiantesActivos: vi.fn(),
    listarCursosVigentes: vi.fn(),
    activarEstudiante: vi.fn(),
    vincularEstudiante: vi.fn(),
  };
  const errores = { normalize: vi.fn() };
  beforeEach(() => {
    vi.resetAllMocks();
    api.listarEstudiantesPendientes.mockReturnValue(of([estudiante]));
    api.listarEstudiantesActivos.mockReturnValue(of([estudiante]));
    api.listarCursosVigentes.mockReturnValue(
      of([
        { id: 'curso1', nombre: 'Puentes', periodo: '2026-2' },
        { id: 'curso2', nombre: 'Estructuras', periodo: '2026-2' },
      ]),
    );
    api.activarEstudiante.mockReturnValue(of({ message: 'Activado' }));
    api.vincularEstudiante.mockReturnValue(of({ message: 'Vinculado' }));
    TestBed.configureTestingModule({
      providers: [
        { provide: UsuarioApiService, useValue: api },
        { provide: ApiErrorService, useValue: errores },
      ],
    });
    page = TestBed.runInInjectionContext(() => new ActivacionEstudiantesPage());
  });
  it('conserva la activacion inicial y retira al alumno de pendientes', () => {
    page.form.setValue({ estudianteId: 'alumno', cursoId: 'curso1', seccion: ' A ' });
    page.activar();
    expect(api.activarEstudiante).toHaveBeenCalledWith({
      estudianteId: 'alumno',
      cursoId: 'curso1',
      seccion: 'A',
    });
    expect(page.estudiantes()).toEqual([]);
    expect(api.vincularEstudiante).not.toHaveBeenCalled();
  });
  it('vincula la misma cuenta activa a dos cursos sin reactivarla', () => {
    page.cambiarModo('vincular');
    for (const cursoId of ['curso1', 'curso2']) {
      page.form.setValue({ estudianteId: 'alumno', cursoId, seccion: 'B' });
      page.activar();
      expect(api.vincularEstudiante).toHaveBeenLastCalledWith({
        estudianteId: 'alumno',
        cursoId,
        seccion: 'B',
      });
      expect(page.estudiantes()).toEqual([estudiante]);
    }
    expect(api.vincularEstudiante).toHaveBeenCalledTimes(2);
    expect(api.activarEstudiante).not.toHaveBeenCalled();
    expect(page.opcionesCursos()[1].etiqueta).toBe('Estructuras (2026-2)');
  });
  it('mantiene el formulario y muestra el conflicto si ya esta inscrito', () => {
    page.cambiarModo('vincular');
    const error = { status: 409, error: { code: 'estudiante_ya_inscrito' } };
    api.vincularEstudiante.mockReturnValue(throwError(() => error));
    page.form.setValue({ estudianteId: 'alumno', cursoId: 'curso1', seccion: 'A' });
    page.activar();
    expect(errores.normalize).toHaveBeenCalledWith(error);
    expect(page.form.controls.estudianteId.value).toBe('alumno');
    expect(page.guardando()).toBe(false);
    expect(page.exito()).toBeNull();
  });
});
