import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { EMPTY, catchError, switchMap } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import { ReporteFotograficoComponent } from '../../components/reporte-fotografico/reporte-fotografico.component';
import {
  InspeccionPublicadaDetalleResponse,
  OrdenMantenimientoDetalleItem,
} from '../../models/inspeccion.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';

@Component({
  selector: 'app-detalle-publicado-page',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    TranslocoPipe,
    TagModule,
    ButtonModule,
    TableModule,
    FormularioDinamicoComponent,
    ReporteFotograficoComponent,
  ],
  templateUrl: './detalle-publicado.page.html',
  styleUrl: './detalle-publicado.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetallePublicadoPage {
  private readonly api = inject(InspeccionApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly errores = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);

  readonly detalle = signal<InspeccionPublicadaDetalleResponse | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly estadoHttp = signal<number | null>(null);

  readonly imprimiendo = signal(false);
  readonly exportando = signal(false);
  readonly mostrarMapeo = signal(false);
  readonly ordenesMantenimiento = signal<OrdenMantenimientoDetalleItem[]>([]);

  readonly esquema = computed(() => {
    const esquema = this.detalle()?.esquemaFormulario;
    return esquema &&
      typeof esquema === 'object' &&
      !Array.isArray(esquema) &&
      Object.keys(esquema).length
      ? esquema
      : null;
  });

  readonly valores = computed(() => {
    const d = this.detalle();
    if (!d) return {};
    const permanentes = Object.fromEntries(
      Object.entries(d.datosPermanentes ?? {}).map(([clave, valor]) => [
        clave.replace(/[A-Z]/g, (letra) => '_' + letra.toLowerCase()),
        valor,
      ]),
    );
    return {
      ...(d.datos ?? {}),
      identificacion: {
        ...(d.identificacionCapturada ?? {}),
        ...((d.datos?.['identificacion'] as Record<string, unknown>) ?? {}),
      },
      datos_generales: {
        ...permanentes,
        ...((d.datos?.['datos_generales'] as Record<string, unknown>) ?? {}),
      },
    };
  });

  private readonly alIniciarImpresion = () => this.imprimiendo.set(true);
  private readonly alFinalizarImpresion = () => this.imprimiendo.set(false);

  constructor() {
    window.addEventListener('beforeprint', this.alIniciarImpresion);
    window.addEventListener('afterprint', this.alFinalizarImpresion);

    this.destroyRef.onDestroy(() => {
      window.removeEventListener('beforeprint', this.alIniciarImpresion);
      window.removeEventListener('afterprint', this.alFinalizarImpresion);
    });

    this.route.paramMap
      .pipe(
        switchMap((params) => {
          this.detalle.set(null);
          this.ordenesMantenimiento.set([]);
          this.error.set(null);
          this.estadoHttp.set(null);
          this.cargando.set(true);
          const id = params.get('id') ?? '';
          return this.api.obtenerPublicada(id).pipe(
            catchError((error: HttpErrorResponse) => {
              this.estadoHttp.set(error.status);
              this.error.set(this.errores.normalize(error));
              this.cargando.set(false);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (detalle) => {
          this.detalle.set(detalle);
          this.cargando.set(false);
          if (detalle?.id) {
            this.cargarOrdenesMantenimiento(detalle.id);
          }
        },
      });
  }

  private cargarOrdenesMantenimiento(inspeccionId: string): void {
    if (!this.api.obtenerOrdenesMantenimiento) {
      return;
    }
    this.api.obtenerOrdenesMantenimiento(inspeccionId).subscribe({
      next: (ordenes) => this.ordenesMantenimiento.set(ordenes ?? []),
      error: () => this.ordenesMantenimiento.set([]),
    });
  }

  imprimir(): void {
    this.imprimiendo.set(true);
    setTimeout(() => {
      window.print();
      setTimeout(() => this.imprimiendo.set(false), 300);
    }, 50);
  }

  exportar(): void {
    const d = this.detalle();
    if (!d || this.exportando()) return;
    this.exportando.set(true);
    this.api.exportar(d.id).subscribe({
      next: (datos) => {
        const jsonTexto = JSON.stringify(datos, null, 2);
        const blob = new Blob([jsonTexto], { type: 'application/json' });
        const url = window.URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        const fecha = d.fechaInspeccion ?? 'sin-fecha';
        const version = d.versionFormularioCodigo ?? 'formulario';
        enlace.download = `inspeccion-${d.id.substring(0, 8)}-${version}-${fecha}.json`;
        document.body.appendChild(enlace);
        enlace.click();
        document.body.removeChild(enlace);
        window.URL.revokeObjectURL(url);
        this.exportando.set(false);
      },
      error: (err) => {
        this.exportando.set(false);
        this.error.set(this.errores.normalize(err));
      },
    });
  }

  toggleMapeo(): void {
    this.mostrarMapeo.update((v) => !v);
  }

  severidad(estado: string): 'success' | 'warn' | 'danger' | 'secondary' {
    return estado === 'BUENO'
      ? 'success'
      : estado === 'REGULAR'
        ? 'warn'
        : estado === 'MALO'
          ? 'danger'
          : 'secondary';
  }
}
