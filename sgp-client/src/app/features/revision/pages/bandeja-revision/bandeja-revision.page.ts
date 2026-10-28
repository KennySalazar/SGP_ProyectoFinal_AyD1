import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import {
  PaginaBorradoresEstudiante,
  PaginaRevisiones,
  PaginaRevisionesResueltas,
  RevisionBandejaResponse,
} from '../../models/revision.models';
import { RevisionApiService } from '../../services/revision-api.service';

@Component({
  selector: 'app-bandeja-revision-page',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    InputTextModule,
    PaginatorModule,
    SelectModule,
    TagModule,
  ],
  templateUrl: './bandeja-revision.page.html',
  styleUrl: './bandeja-revision.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BandejaRevisionPage {
  private readonly router = inject(Router);
  private readonly api = inject(RevisionApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);

  readonly pagina = signal<PaginaRevisiones | null>(null);
  readonly borradores = signal<PaginaBorradoresEstudiante | null>(null);
  readonly revisadas = signal<PaginaRevisionesResueltas | null>(null);
  readonly cargandoRevisadas = signal(false);
  readonly errorRevisadas = signal(false);
  readonly vista = signal<'pendientes' | 'borradores' | 'revisadas'>('pendientes');
  readonly cargando = signal(false);
  readonly iniciando = signal<string | null>(null);
  readonly mensaje = signal<string | null>(null);
  readonly tamanio = signal(20);
  readonly estado = signal<'' | 'ENVIADA' | 'EN_REVISION'>('');
  readonly puente = signal('');
  readonly estudiante = signal('');
  readonly curso = signal('');
  readonly fechaDesde = signal('');
  readonly fechaHasta = signal('');

  readonly estados = [
    { label: 'Todos', value: '' },
    { label: 'Enviada', value: 'ENVIADA' },
    { label: 'En revisión', value: 'EN_REVISION' },
  ];

  constructor() {
    if (inject(ActivatedRoute).snapshot.queryParamMap.get('vista') === 'revisadas') {
      this.cambiarVista('revisadas');
    } else this.cargar();
  }

  cambiarVista(vista: 'pendientes' | 'borradores' | 'revisadas'): void {
    this.vista.set(vista);
    if (vista === 'borradores' && !this.borradores()) this.cargarBorradores();
    if (vista === 'pendientes' && !this.pagina()) this.cargar();
    if (vista === 'revisadas') this.cargarRevisadas();
  }

  cargarRevisadas(numeroPagina = 0): void {
    if (this.cargandoRevisadas()) return;
    this.cargandoRevisadas.set(true);
    this.errorRevisadas.set(false);
    this.api
      .listarRevisadas(numeroPagina, this.tamanio())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargandoRevisadas.set(false)),
      )
      .subscribe({
        next: (pagina) => this.revisadas.set(pagina),
        error: (error) => {
          this.revisadas.set(null);
          this.errorRevisadas.set(true);
          this.apiErrors.normalize(error);
        },
      });
  }

  cargarBorradores(numeroPagina = 0): void {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.api
      .listarBorradores(numeroPagina, this.tamanio())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (pagina) => this.borradores.set(pagina),
        error: (error) => this.apiErrors.normalize(error),
      });
  }

  cargar(numeroPagina = 0): void {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.mensaje.set(null);
    this.api
      .listar({
        estado: this.estado() || undefined,
        puente: this.puente(),
        estudiante: this.estudiante(),
        curso: this.curso(),
        fechaDesde: this.fechaDesde() || undefined,
        fechaHasta: this.fechaHasta() || undefined,
        pagina: numeroPagina,
        tamanio: this.tamanio(),
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (pagina) => this.pagina.set(pagina),
        error: (error) => this.apiErrors.normalize(error),
      });
  }

  limpiar(): void {
    this.estado.set('');
    this.puente.set('');
    this.estudiante.set('');
    this.curso.set('');
    this.fechaDesde.set('');
    this.fechaHasta.set('');
    this.cargar();
  }

  cambiarPagina(evento: PaginatorState): void {
    if (evento.page === undefined || evento.rows === undefined) return;
    this.tamanio.set(evento.rows);
    if (this.vista() === 'pendientes') this.cargar(evento.page);
    else if (this.vista() === 'revisadas') this.cargarRevisadas(evento.page);
    else this.cargarBorradores(evento.page);
  }

  iniciar(revision: RevisionBandejaResponse): void {
    if (this.iniciando()) return;
    if (revision.estadoRevision === 'EN_CURSO') {
      void this.router.navigate(['/revisiones', revision.revisionId]);
      return;
    }
    this.iniciando.set(revision.revisionId);
    this.mensaje.set(null);
    this.api
      .iniciar(revision.revisionId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.iniciando.set(null)),
      )
      .subscribe({
        next: () => {
          this.api.cargarContador().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
          void this.router.navigate(['/revisiones', revision.revisionId]);
        },
        error: (error) => this.apiErrors.normalize(error),
      });
  }

  severidad(estado: string): 'info' | 'warn' {
    return estado === 'EN_REVISION' ? 'warn' : 'info';
  }
}
