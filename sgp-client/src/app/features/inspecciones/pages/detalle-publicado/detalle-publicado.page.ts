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
import { TagModule } from 'primeng/tag';
import { EMPTY, catchError, switchMap } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import { ReporteFotograficoComponent } from '../../components/reporte-fotografico/reporte-fotografico.component';
import { InspeccionPublicadaDetalleResponse } from '../../models/inspeccion.models';
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

  constructor() {
    this.route.paramMap
      .pipe(
        switchMap((params) => {
          this.detalle.set(null);
          this.error.set(null);
          this.estadoHttp.set(null);
          this.cargando.set(true);
          return this.api.obtenerPublicada(params.get('id') ?? '').pipe(
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
        },
      });
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
