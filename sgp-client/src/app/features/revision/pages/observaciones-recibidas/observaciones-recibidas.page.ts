import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { finalize } from 'rxjs';
import { PaginaObservacionesRecibidas } from '../../models/revision.models';
import { RevisionApiService } from '../../services/revision-api.service';

@Component({
  selector: 'app-observaciones-recibidas-page',
  standalone: true,
  imports: [DatePipe, RouterLink, TranslocoPipe, ButtonModule, PaginatorModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-heading">
      <p class="page-kicker">{{ 'revision.detalle.kicker' | transloco }}</p>
      <h1 class="page-title">{{ 'revision.autor.titulo' | transloco }}</h1>
      <p>{{ 'revision.autor.descripcion' | transloco }}</p>
    </header>
    @if (cargando()) {
      <p role="status">{{ 'revision.detalle.cargando' | transloco }}</p>
    }
    @if (error()) {
      <p role="alert">{{ 'revision.autor.error' | transloco }}</p>
      <button pButton type="button" (click)="cargar()">
        {{ 'revision.autor.reintentar' | transloco }}
      </button>
    }
    @if (pagina(); as p) {
      @if (p.empty) {
        <p>{{ 'revision.autor.vacio' | transloco }}</p>
      }
      @for (ciclo of p.content; track ciclo.revisionId) {
        <article class="surface-card ciclo">
          <h2>
            {{ 'revision.detalle.ciclo' | transloco }} {{ ciclo.numeroCiclo }} ·
            {{ 'revision.detalle.estados.' + ciclo.estadoRevision | transloco }}
          </h2>
          <p>
            <strong>{{ ciclo.puenteNombre }}</strong> · {{ ciclo.puenteCodigo }}
          </p>
          <p>
            {{ 'revision.detalle.inspeccion' | transloco }}:
            {{
              ciclo.fechaInspeccion
                ? (ciclo.fechaInspeccion | date: 'dd/MM/yyyy')
                : ('revision.detalle.sinDato' | transloco)
            }}
          </p>
          <p>
            {{ 'revision.detalle.enviada' | transloco }}:
            {{ ciclo.enviadaEn | date: 'dd/MM/yyyy HH:mm' }}
          </p>
          <a pButton [routerLink]="['/observaciones', ciclo.revisionId]">
            <i class="pi pi-comments" aria-hidden="true"></i
            >{{ 'revision.autor.abrir' | transloco }}
          </a>
        </article>
      }
      @if (p.totalElements > p.size) {
        <p-paginator
          [first]="p.number * p.size"
          [rows]="p.size"
          [totalRecords]="p.totalElements"
          (onPageChange)="cambiarPagina($event)"
        />
      }
    }
  `,
  styles: `
    .ciclo {
      padding: 1rem;
      margin-block: 1rem;
      overflow-wrap: anywhere;
    }
    a,
    button {
      min-width: 44px;
      min-height: 44px;
      max-width: 100%;
      white-space: normal;
    }
  `,
})
export class ObservacionesRecibidasPage {
  private readonly api = inject(RevisionApiService);
  private readonly destruir = inject(DestroyRef);
  readonly pagina = signal<PaginaObservacionesRecibidas | null>(null);
  readonly cargando = signal(false);
  readonly error = signal(false);
  constructor() {
    this.cargar();
  }
  cargar(pagina = 0): void {
    if (this.cargando()) return;
    this.pagina.set(null);
    this.error.set(false);
    this.cargando.set(true);
    this.api
      .recibidas(pagina)
      .pipe(
        takeUntilDestroyed(this.destruir),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (p) => this.pagina.set(p),
        error: () => this.error.set(true),
      });
  }
  cambiarPagina(e: PaginatorState): void {
    if (e.page !== undefined) this.cargar(e.page);
  }
}
