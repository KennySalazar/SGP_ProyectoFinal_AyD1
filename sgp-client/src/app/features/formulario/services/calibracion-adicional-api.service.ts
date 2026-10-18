import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { EstadoActa } from '../models/calibracion-adicional.models';
import { VersionFormulario } from '../models/formulario.models';

@Injectable({ providedIn: 'root' })
export class CalibracionAdicionalApiService {
  private readonly http = inject(HttpClient);
  guardarReglas(id: string, reglasAnulacion: unknown[]) {
    return this.http.put<VersionFormulario>(
      `/api/v1/formularios/${encodeURIComponent(id)}/reglas-criticas`,
      { reglasAnulacion },
    );
  }
  consultarActa(id: string) {
    return this.http.get<EstadoActa>(
      `/api/v1/formularios/${encodeURIComponent(id)}/acta-calibracion`,
    );
  }
  adjuntarActa(id: string, archivo: File) {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.put<EstadoActa>(
      `/api/v1/formularios/${encodeURIComponent(id)}/acta-calibracion`,
      datos,
    );
  }
  descargarActa(id: string) {
    return this.http.get(
      `/api/v1/formularios/${encodeURIComponent(id)}/acta-calibracion/contenido`,
      { responseType: 'blob' },
    );
  }
}
