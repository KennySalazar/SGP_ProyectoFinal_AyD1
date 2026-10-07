import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RoleName } from '../../../core/models/auth.models';
import {
  CambiarRolRequest,
  EstadoUsuario,
  PaginaUsuarios,
  UsuarioAdmin,
} from '../models/usuario.models';

@Injectable({ providedIn: 'root' })
export class GestionUsuariosApiService {
  private readonly http = inject(HttpClient);

  listar(
    filtros: { rol?: RoleName | null; estado?: EstadoUsuario | null },
    pagina = 0,
    tamanio = 10,
  ): Observable<PaginaUsuarios> {
    if (
      !Number.isInteger(pagina) ||
      pagina < 0 ||
      !Number.isInteger(tamanio) ||
      tamanio < 1 ||
      tamanio > 100
    ) {
      throw new RangeError(
        'La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.',
      );
    }
    const params: Record<string, string | number> = { pagina, tamanio };
    if (filtros.rol) params['rol'] = filtros.rol;
    if (filtros.estado) params['estado'] = filtros.estado;
    return this.http.get<PaginaUsuarios>('/api/v1/usuarios', { params });
  }

  desactivar(usuarioId: string, motivo: string | null): Observable<UsuarioAdmin> {
    return this.http.post<UsuarioAdmin>(
      `/api/v1/usuarios/${encodeURIComponent(usuarioId)}/desactivacion`,
      { motivo },
    );
  }

  reactivar(usuarioId: string): Observable<UsuarioAdmin> {
    return this.http.post<UsuarioAdmin>(
      `/api/v1/usuarios/${encodeURIComponent(usuarioId)}/reactivacion`,
      null,
    );
  }

  cambiarRol(usuarioId: string, request: CambiarRolRequest): Observable<UsuarioAdmin> {
    return this.http.put<UsuarioAdmin>(
      `/api/v1/usuarios/${encodeURIComponent(usuarioId)}/rol`,
      request,
    );
  }
}
