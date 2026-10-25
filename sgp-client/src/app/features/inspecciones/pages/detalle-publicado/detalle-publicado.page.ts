import { AprobacionCalibracionComponent } from '../../../formulario/components/aprobacion-calibracion/aprobacion-calibracion.component';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  computed,
  forwardRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { EMPTY, catchError, switchMap } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { ForoInspeccionComponent } from '../../../foro/components/foro-inspeccion/foro-inspeccion.component';
import { AnclaConTotal, AnclaForo } from '../../../foro/models/foro.models';
import {
  CONTEXTO_COMENTARIOS,
  ContextoComentariosFormulario,
} from '../../../formulario/components/comentarios/contexto-comentarios';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import { AnclajeFormulario } from '../../../formulario/utils/anclaje-formulario';
import { crearEtiquetadorAncla } from '../../../formulario/utils/etiquetas-ancla';
import { ReporteFotograficoComponent } from '../../components/reporte-fotografico/reporte-fotografico.component';
import { DocumentosInspeccionComponent } from '../../components/documentos-inspeccion/documentos-inspeccion.component';
import {
  InspeccionPublicadaDetalleResponse,
  OrdenMantenimientoDetalleItem,
} from '../../models/inspeccion.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';

@Component({
  selector: 'app-detalle-publicado-page',
  standalone: true,
  imports: [
    AprobacionCalibracionComponent,
    DatePipe,
    DecimalPipe,
    RouterLink,
    TranslocoPipe,
    TagModule,
    ButtonModule,
    TableModule,
    FormularioDinamicoComponent,
    ReporteFotograficoComponent,
    DocumentosInspeccionComponent,
    ForoInspeccionComponent,
  ],
  providers: [
    { provide: CONTEXTO_COMENTARIOS, useExisting: forwardRef(() => DetallePublicadoPage) },
  ],
  templateUrl: './detalle-publicado.page.html',
  styleUrl: './detalle-publicado.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetallePublicadoPage implements ContextoComentariosFormulario {
  private readonly api = inject(InspeccionApiService);
  private readonly route = inject(ActivatedRoute);
  /** Comentario del foro al que lleva un aviso de mención (HU-066). */
  readonly comentarioDelAviso = this.route.snapshot.queryParamMap.get('comentario') ?? null;
  private readonly errores = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly foro = viewChild<{ enfocarFormulario(): void }>('foro');
  private readonly foroEnPantalla = viewChild('foro', { read: ElementRef });
  private readonly formulario = viewChild(FormularioDinamicoComponent);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly puedeComentar = inject(AuthStore).authenticated;
  /** Deja a la vista el icono de comentarios de todos los campos (en pantallas táctiles no hay «pasar el puntero»). */
  readonly camposVisibles = signal(false);
  /** Cuántos comentarios tiene cada elemento, según el foro de la inspección. */
  readonly anclas = signal<readonly AnclaConTotal[]>([]);
  /** Elemento sobre el que se ven y se dejan los comentarios; nulo para todo el hilo. */
  readonly seleccion = signal<AnclaForo | null>(null);
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

  private readonly totales = computed(
    () => new Map(this.anclas().map((a) => [claveDeAncla(a), a.total])),
  );
  /** Nombra cada elemento como lo ve quien llena el formulario, con el esquema de esa versión. */
  readonly etiquetaDeAncla = computed(() => crearEtiquetadorAncla(this.esquema(), this.valores()));

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

  alternarCampos(): void {
    this.camposVisibles.update((visibles) => !visibles);
  }

  etiquetaDe(anclaje: AnclajeFormulario): string {
    return this.etiquetaDeAncla()(anclaje);
  }

  totalDe(anclaje: AnclajeFormulario): number {
    return this.totales().get(claveDeAncla(anclaje)) ?? 0;
  }

  /** Muestra los comentarios del elemento y deja el cursor listo para comentarlo. */
  seleccionar(anclaje: AnclajeFormulario): void {
    this.seleccion.set({
      elementoRef: anclaje.elementoRef,
      instanciaElementoId: anclaje.instanciaElementoId ?? null,
    });
    afterNextRender(
      () => {
        this.foroEnPantalla()?.nativeElement.scrollIntoView?.({ behavior: 'smooth' });
        this.foro()?.enfocarFormulario();
      },
      { injector: this.injector },
    );
  }

  /**
   * Lleva la vista al elemento del formulario al que se refiere un comentario: muestra su sección,
   * se desplaza hasta él y deja el foco en su botón de comentarios. Un comentario anclado a un campo
   * lleva al elemento que lo contiene.
   */
  irAElemento(ancla: AnclaForo): void {
    this.formulario()?.mostrarAnclaje(ancla.elementoRef);
    afterNextRender(() => this.enfocarElemento(ancla), { injector: this.injector });
  }

  private enfocarElemento(ancla: AnclaForo): void {
    const instancia = ancla.instanciaElementoId ?? '';
    const marcas = Array.from(
      this.host.nativeElement.querySelectorAll<HTMLElement>('[data-elemento-ref]'),
    );
    let referencia = ancla.elementoRef;
    let objetivo: HTMLElement | undefined;
    while (referencia && !objetivo) {
      objetivo = marcas.find(
        (m) => m.dataset['elementoRef'] === referencia && m.dataset['instanciaId'] === instancia,
      );
      referencia = referencia.slice(0, referencia.lastIndexOf('/'));
    }
    if (!objetivo) return;
    // Un grupo opcional plegado esconde su contenido: se abre para que el elemento quede a la vista.
    for (let padre = objetivo.parentElement; padre; padre = padre.parentElement) {
      if (padre instanceof HTMLDetailsElement) padre.open = true;
    }
    objetivo.scrollIntoView?.({ block: 'center' });
    objetivo.querySelector('button')?.focus();
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

function claveDeAncla(ancla: { elementoRef: string; instanciaElementoId?: string | null }): string {
  return `${ancla.elementoRef}|${ancla.instanciaElementoId ?? ''}`;
}
