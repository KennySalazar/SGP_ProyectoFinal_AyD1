import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { InspeccionApiService } from '../../../inspecciones/services/inspeccion-api.service';
import { InspeccionPublicadaResumenResponse } from '../../../inspecciones/models/inspeccion.models';
import { AuthStore } from '../../../../core/services/auth.store';
import { AppShellComponent } from '../../../../layouts/app-shell/app-shell.component';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { IniciarInspeccionDialogComponent } from '../../../inspecciones/components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component';
import { ForoPuenteComponent } from '../../../foro/components/foro-puente/foro-puente.component';
import { MantenimientoPuenteComponent } from '../../../mantenimiento/components/mantenimiento-puente/mantenimiento-puente.component';
import { PaginaResponse, PuenteResponse } from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';

@Component({
  selector: 'app-ficha-puente-page',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    DatePipe,
    PaginatorModule,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    TagModule,
    AppShellComponent,
    SelectorUbicacionComponent,
    IniciarInspeccionDialogComponent,
    MantenimientoPuenteComponent,
    ForoPuenteComponent,
  ],
  templateUrl: './ficha-puente.page.html',
  styleUrl: './ficha-puente.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FichaPuentePage implements OnInit {
  readonly auth = inject(AuthStore);
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(PuenteApiService);
  private readonly inspeccionesApi = inject(InspeccionApiService);
  private readonly destroyRef = inject(DestroyRef);
  private solicitudHistorial: Subscription | null = null;
  readonly historial = signal<PaginaResponse<InspeccionPublicadaResumenResponse> | null>(null);
  readonly cargandoHistorial = signal(false);
  readonly errorHistorial = signal(false);
  readonly paginaHistorial = signal(0);
  readonly tamanioHistorial = 20;

  readonly cargando = signal(true);
  readonly error = signal(false);
  readonly puente = signal<PuenteResponse | null>(null);
  readonly mostrarModalIniciar = signal(false);

  constructor() {
    effect((onCleanup) => {
      const puente = this.puente();
      const autenticado = this.auth.authenticated();
      const usuario = this.auth.user();
      this.historial.set(null);
      this.errorHistorial.set(false);
      this.cargandoHistorial.set(false);
      if (autenticado && usuario && puente) {
        untracked(() => this.cargarHistorial(0));
      }
      onCleanup(() => this.solicitudHistorial?.unsubscribe());
    });
  }

  cargarHistorial(pagina = this.paginaHistorial()): void {
    const puente = this.puente();
    if (!this.auth.authenticated() || !this.auth.user() || !puente || pagina < 0) return;
    this.solicitudHistorial?.unsubscribe();
    this.paginaHistorial.set(pagina);
    this.historial.set(null);
    this.errorHistorial.set(false);
    this.cargandoHistorial.set(true);
    this.solicitudHistorial = this.inspeccionesApi
      .listarPublicadasPorPuente(puente.id, pagina, this.tamanioHistorial)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (respuesta) => {
          this.historial.set({
            ...respuesta,
            content: respuesta.content.filter((inspeccion) => inspeccion.estado === 'PUBLICADA'),
          });
          this.cargandoHistorial.set(false);
        },
        error: () => {
          this.errorHistorial.set(true);
          this.cargandoHistorial.set(false);
        },
      });
  }

  cambiarPaginaHistorial(evento: PaginatorState): void {
    if (evento.page !== undefined) this.cargarHistorial(evento.page);
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set(true);
      this.cargando.set(false);
      return;
    }

    this.api
      .obtenerPorId(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (datos) => {
          this.puente.set(datos);
          this.cargando.set(false);
        },
        error: () => {
          this.error.set(true);
          this.cargando.set(false);
        },
      });
  }

  severidadEstado(estado: string | null | undefined): 'success' | 'warn' | 'danger' | 'secondary' {
    switch (estado) {
      case 'Bueno':
        return 'success';
      case 'Regular':
        return 'warn';
      case 'Malo':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  puedeIniciarInspeccion(): boolean {
    const p = this.puente();
    if (!p || !p.activo) {
      return false;
    }
    const user = this.auth.user();
    if (!user) {
      return false;
    }
    if (user.role === 'CATEDRATICO') {
      return true;
    }
    if (user.role === 'PROFESIONAL_EXTERNO' && user.colegiadoVerificado === true) {
      return true;
    }
    return false;
  }

  abrirModalIniciarInspeccion(): void {
    this.mostrarModalIniciar.set(true);
  }
}
