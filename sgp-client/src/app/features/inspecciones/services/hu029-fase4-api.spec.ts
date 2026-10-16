import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { afterEach, beforeEach, expect } from 'vitest';
import { ejecutarGherkin } from '../../../../testing/gherkin';
import source from './hu029-fase4-api.feature';
import { InspeccionApiService } from './inspeccion-api.service';
import { ValidacionEvidenciaResponse } from '../models/fotografia.models';
let api: InspeccionApiService;
let http: HttpTestingController;
let request: TestRequest;
let respuesta: ValidacionEvidenciaResponse | void;
let error: HttpErrorResponse | undefined;
let completada: boolean;
const evidencia: ValidacionEvidenciaResponse = {
  valida: false,
  totalDisponibles: 3,
  maximoPermitido: 60,
  faltantes: [{ categoria: 'CAUCE', requeridas: 1, disponibles: 0 }],
};
beforeEach(() => {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  api = TestBed.inject(InspeccionApiService);
  http = TestBed.inject(HttpTestingController);
  respuesta = undefined;
  error = undefined;
  completada = false;
});
afterEach(() => http.verify());
ejecutarGherkin(source, 'HU-029 fase 4: contratos HTTP', [
  {
    patron: /^consulta la evidencia de la inspección$/,
    ejecutar: () => {
      api.consultarEvidenciaFotografica('inspeccion').subscribe((r) => (respuesta = r));
    },
  },
  {
    patron: /^realiza GET a evidencia-fotografica$/,
    ejecutar: () => {
      request = http.expectOne('/api/v1/inspecciones/inspeccion/evidencia-fotografica');
      expect(request.request.method).toBe('GET');
    },
  },
  {
    patron: /^el servidor responde con faltante CAUCE y máximo 60$/,
    ejecutar: () => {
      request.flush(evidencia);
    },
  },
  {
    patron: /^conserva el diagnóstico incompleto del servidor$/,
    ejecutar: () => {
      expect(respuesta).toEqual(evidencia);
    },
  },
  {
    patron: /^retira la fotografía$/,
    ejecutar: () => {
      api.retirarFotografia('inspeccion', 'foto').subscribe({
        next: (r) => (respuesta = r),
        error: (e) => (error = e),
        complete: () => (completada = true),
      });
    },
  },
  {
    patron: /^realiza DELETE sobre el par de identificadores$/,
    ejecutar: () => {
      request = http.expectOne('/api/v1/inspecciones/inspeccion/fotografias/foto');
      expect(request.request.method).toBe('DELETE');
      expect(request.request.body).toBeNull();
    },
  },
  {
    patron: /^el servidor confirma con 204 sin cuerpo$/,
    ejecutar: () => {
      request.flush(null, { status: 204, statusText: 'No Content' });
    },
  },
  {
    patron: /^completa la retirada sin inventar una respuesta$/,
    ejecutar: () => {
      expect(completada).toBe(true);
      expect(respuesta).toBeNull();
      expect(error).toBeUndefined();
    },
  },
  {
    patron: /^el servidor responde con error (\d+)$/,
    ejecutar: (estado) => {
      http
        .expectOne('/api/v1/inspecciones/inspeccion/fotografias/foto')
        .flush({ code: 'codigo-servidor' }, { status: Number(estado), statusText: 'Error' });
    },
  },
  {
    patron: /^propaga exactamente el estado (\d+)$/,
    ejecutar: (estado) => {
      expect(error?.status).toBe(Number(estado));
      expect(error?.error.code).toBe('codigo-servidor');
      expect(completada).toBe(false);
    },
  },
]);
