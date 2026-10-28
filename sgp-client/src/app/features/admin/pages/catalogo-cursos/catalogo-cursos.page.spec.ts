import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { CursoApiService } from '../../services/curso-api.service';
import { CursoResponse } from '../../models/curso.models';
import { CatalogoCursosPage } from './catalogo-cursos.page';

const antiguo: CursoResponse = {
  id: 'curso-1',
  nombre: 'Puentes',
  periodo: '7',
  catedratico: { id: 'cat-1', email: 'cat@ejemplo.com' },
  fechaInicio: '2025-03-10',
  fechaFin: '2025-11-20',
  estado: 'VIGENTE',
};

describe('Periodos y fechas del curso', () => {
  const api = { listar: vi.fn(), listarCatedraticos: vi.fn(), crear: vi.fn(), actualizar: vi.fn() };
  beforeEach(() => {
    vi.resetAllMocks();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2027-03-10T12:00:00Z'));
    api.listar.mockReturnValue(of({ content: [antiguo] }));
    api.listarCatedraticos.mockReturnValue(of([]));
    api.crear.mockReturnValue(of({ ...antiguo, id: 'nuevo' }));
    api.actualizar.mockReturnValue(of(antiguo));
    TestBed.configureTestingModule({
      providers: [
        { provide: CursoApiService, useValue: api },
        { provide: ApiErrorService, useValue: { clear: vi.fn() } },
      ],
    });
  });
  afterEach(() => vi.useRealTimers());
  const crear = () => TestBed.runInInjectionContext(() => new CatalogoCursosPage());
  it.each([
    ['2027-1', '2027-01-01', '2027-06-30'],
    ['2027-2', '2027-07-01', '2027-12-31'],
  ])('completa las fechas de %s', (periodo, inicio, fin) => {
    const pagina = crear();
    pagina.form.controls.periodo.setValue(periodo);
    expect(pagina.form.controls.fechaInicio.value).toBe(inicio);
    expect(pagina.form.controls.fechaFin.value).toBe(fin);
  });
  it('actualiza ambas fechas cuando cambia el periodo y limpia si se borra', () => {
    const pagina = crear();
    pagina.form.controls.periodo.setValue('2027-1');
    pagina.form.controls.periodo.setValue('2027-2');
    expect(pagina.form.controls.fechaInicio.value).toBe('2027-07-01');
    pagina.form.controls.periodo.setValue('');
    expect(pagina.form.controls.fechaInicio.value).toBe('');
    expect(pagina.form.controls.fechaFin.value).toBe('');
  });
  it('obtiene el año en Guatemala incluso al cambiar de año en UTC', () => {
    vi.setSystemTime(new Date('2028-01-01T02:00:00Z'));
    expect(crear().anioActual).toBe(2027);
    vi.setSystemTime(new Date('2028-01-01T07:00:00Z'));
    expect(crear().anioActual).toBe(2028);
  });
  it('envia el periodo con año y las fechas generadas al crear', () => {
    const pagina = crear();
    pagina.form.patchValue({ nombre: 'Puentes', catedraticoId: 'cat-1', periodo: '2027-2' });
    pagina.guardar();
    expect(api.crear).toHaveBeenCalledExactlyOnceWith({
      nombre: 'Puentes',
      catedraticoId: 'cat-1',
      periodo: '2027-2',
      fechaInicio: '2027-07-01',
      fechaFin: '2027-12-31',
    });
    expect(pagina.form.controls.periodo.value).toBe('');
    expect(pagina.form.controls.fechaInicio.value).toBe('');
  });
  it('requiere elegir un periodo valido para un curso antiguo sin modificarlo en el servidor', () => {
    const pagina = crear();
    pagina.editar(antiguo);
    expect(pagina.form.controls.periodo.value).toBe('');
    pagina.guardar();
    expect(api.actualizar).not.toHaveBeenCalled();
    pagina.form.controls.periodo.setValue('2025-2');
    pagina.guardar();
    expect(api.actualizar).toHaveBeenCalledWith('curso-1', {
      nombre: 'Puentes',
      periodo: '2025-2',
      catedraticoId: 'cat-1',
      fechaInicio: '2025-07-01',
      fechaFin: '2025-12-31',
    });
  });
  it('permite cambiar entre los dos periodos conservando el año del curso editado', () => {
    const pagina = crear();
    pagina.editar({ ...antiguo, periodo: '2025-2' });
    expect(pagina.form.controls.fechaInicio.value).toBe(antiguo.fechaInicio);
    pagina.form.controls.periodo.setValue('2025-1');
    expect(pagina.form.controls.fechaInicio.value).toBe('2025-01-01');
    expect(pagina.form.controls.fechaFin.value).toBe('2025-06-30');
    expect(pagina.periodos().map((p) => p.valor)).toEqual(['2025-1', '2025-2']);
  });
  it('rechaza un periodo escrito arbitrariamente aunque sus fechas sean validas', () => {
    const pagina = crear();
    pagina.editar({ ...antiguo, periodo: '2025-2' });
    pagina.form.controls.periodo.setValue('2025-4');
    pagina.guardar();
    expect(pagina.form.controls.periodo.hasError('periodoInvalido')).toBe(true);
    expect(api.actualizar).not.toHaveBeenCalled();
  });
  it('vuelve a generar fechas despues de cancelar una edicion', () => {
    const pagina = crear();
    pagina.editar(antiguo);
    pagina.cancelarEdicion();
    pagina.form.controls.periodo.setValue('2027-1');
    expect(pagina.form.controls.fechaFin.value).toBe('2027-06-30');
  });
  it('conserva las fechas personalizadas al guardar', () => {
    const pagina = crear();
    pagina.editar({ ...antiguo, periodo: '2025-2' });
    pagina.form.controls.fechaFin.setValue('2025-12-15');
    pagina.guardar();
    expect(api.actualizar).toHaveBeenCalledWith(
      'curso-1',
      expect.objectContaining({ fechaInicio: antiguo.fechaInicio, fechaFin: '2025-12-15' }),
    );
  });
  it('rechaza un rango de fechas invertido sin enviarlo', () => {
    const pagina = crear();
    pagina.editar({ ...antiguo, periodo: '2025-2' });
    pagina.form.controls.fechaFin.setValue('2025-01-01');
    pagina.guardar();
    expect(pagina.form.controls.fechaFin.hasError('rango')).toBe(true);
    expect(api.actualizar).not.toHaveBeenCalled();
  });
  it('distingue el estado administrativo de las fechas inclusivas', () => {
    const pagina = crear();
    expect(pagina.vigencia(antiguo)).toBe('admin.courses.expired');
    expect(pagina.vigencia({ ...antiguo, estado: 'FINALIZADO' })).toBe('admin.courses.inactive');
    expect(pagina.vigencia({ ...antiguo, fechaInicio: '2027-03-11', fechaFin: '2027-06-30' })).toBe(
      'admin.courses.upcoming',
    );
    expect(pagina.vigencia({ ...antiguo, fechaInicio: '2027-03-10', fechaFin: '2027-03-10' })).toBe(
      'admin.courses.inDates',
    );
  });
});
