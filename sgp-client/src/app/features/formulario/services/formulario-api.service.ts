import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ActualizarBorradorRequest,
  AnalisisMapeo,
  ConfiguracionIcElemento,
  ConfiguracionIcElementoRequest,
  CrearVersionRequest,
  DeclaracionMapeoRequest,
  FormularioActivo,
  MapeoCampo,
  PublicarVersionRequest,
  VersionFormulario,
  VersionFormularioResumen,
} from '../models/formulario.models';

@Injectable({ providedIn: 'root' })
export class FormularioApiService {
  private readonly http = inject(HttpClient);

  listarVersiones(): Observable<VersionFormularioResumen[]> {
    return this.http.get<VersionFormularioResumen[]>('/api/v1/formularios');
  }

  obtenerPorId(id: string): Observable<VersionFormulario> {
    return this.http.get<VersionFormulario>(`/api/v1/formularios/${encodeURIComponent(id)}`);
  }

  obtenerActiva(): Observable<FormularioActivo> {
    return this.http.get<FormularioActivo>('/api/v1/formularios/activo');
  }

  obtenerEsquemaPublicado(id: string): Observable<FormularioActivo> {
    return this.http.get<FormularioActivo>(`/api/v1/formularios/${encodeURIComponent(id)}/esquema`);
  }

  crear(request: CrearVersionRequest): Observable<VersionFormulario> {
    return this.http.post<VersionFormulario>('/api/v1/formularios', request);
  }

  cargarPlantillaSieca(codigo?: string): Observable<VersionFormulario> {
    let params = new HttpParams();
    if (codigo) {
      params = params.set('codigo', codigo);
    }
    return this.http.post<VersionFormulario>('/api/v1/formularios/plantilla-sieca', null, {
      params,
    });
  }

  actualizarBorrador(
    id: string,
    request: ActualizarBorradorRequest,
  ): Observable<VersionFormulario> {
    return this.http.put<VersionFormulario>(
      `/api/v1/formularios/${encodeURIComponent(id)}`,
      request,
    );
  }

  calibrarPesos(
    id: string,
    pesos: ConfiguracionIcElementoRequest[],
  ): Observable<ConfiguracionIcElemento[]> {
    return this.http.put<ConfiguracionIcElemento[]>(
      `/api/v1/formularios/${encodeURIComponent(id)}/pesos`,
      pesos,
    );
  }

  analizarMapeo(id: string): Observable<AnalisisMapeo> {
    return this.http.get<AnalisisMapeo>(
      `/api/v1/formularios/${encodeURIComponent(id)}/mapeos/analisis`,
    );
  }

  guardarMapeos(id: string, mapeos: DeclaracionMapeoRequest[]): Observable<MapeoCampo[]> {
    return this.http.put<MapeoCampo[]>(
      `/api/v1/formularios/${encodeURIComponent(id)}/mapeos`,
      mapeos,
    );
  }

  publicar(id: string, request?: PublicarVersionRequest): Observable<VersionFormulario> {
    return this.http.post<VersionFormulario>(
      `/api/v1/formularios/${encodeURIComponent(id)}/publicar`,
      request ?? {},
    );
  }

  configurarEscala(
    id: string,
    request: {
      valores: {
        NO_PRESENTA: number;
        LEVE: number;
        MODERADO: number;
        SEVERO: number;
      };
    },
  ): Observable<VersionFormulario> {
    return this.http.put<VersionFormulario>(
      `/api/v1/formularios/${encodeURIComponent(id)}/escala`,
      request,
    );
  }
}
