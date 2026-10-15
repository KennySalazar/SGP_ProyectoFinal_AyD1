import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { combineLatest, finalize, map } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { FormularioDinamicoComponent } from '../../components/formulario-dinamico/formulario-dinamico.component';
import { SeccionApoyo } from '../../components/secciones-apoyo';
import { FormularioActivo } from '../../models/formulario.models';
import { FormularioApiService } from '../../services/formulario-api.service';
import { ReporteFotograficoPendienteComponent } from './reporte-fotografico-pendiente.component';

/** Datos que en una inspección real vienen del inventario del puente y de la sesión (RN-FRM-04). */
const DATOS_PRECARGADOS_DE_EJEMPLO: Record<string, unknown> = {
  identificacion: {
    nombre_puente: 'Puente de ejemplo',
    codigo: 'EJ-001',
    ruta: 'CA-1',
    kilometraje: 12.5,
    departamento: 'Quetzaltenango',
    municipio: 'Quetzaltenango',
    nombre_inspector: 'Inspector de ejemplo',
  },
};

/**
 * Muestra el formulario dinámico con la versión activa (o una publicada, con `?version=<id>`) para
 * revisar cómo se genera. No guarda nada: la captura de inspecciones se integra en HU-024/HU-025.
 */
@Component({
  selector: 'app-vista-previa-formulario-page',
  standalone: true,
  imports: [TranslocoPipe, ButtonModule, FormularioDinamicoComponent],
  templateUrl: './vista-previa-formulario.page.html',
  styleUrl: './vista-previa-formulario.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VistaPreviaFormularioPage implements OnInit {
  private readonly api = inject(FormularioApiService);
  private readonly apiError = inject(ApiErrorService);
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly formulario = viewChild(FormularioDinamicoComponent);

  /** El reporte fotográfico no sale del esquema: llega con el módulo de archivos (HU-028). */
  protected readonly seccionesApoyo = toSignal(
    combineLatest([
      this.transloco.selectTranslate('formulario.vistaPrevia.photoReportTitle'),
      this.transloco.selectTranslate('formulario.vistaPrevia.photoReportDescription'),
    ]).pipe(
      map(([titulo, descripcion]): SeccionApoyo[] => [
        {
          clave: 'reporte-fotografico',
          titulo,
          descripcion,
          componente: ReporteFotograficoPendienteComponent,
        },
      ]),
    ),
    { initialValue: [] as SeccionApoyo[] },
  );

  protected readonly version = signal<FormularioActivo | null>(null);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly sinVersionActiva = signal(false);

  protected readonly soloLectura = signal(false);
  protected readonly valor = signal<Record<string, unknown>>({});
  protected readonly resultadoRevision = signal<{
    valido: boolean;
    faltantes: number;
    invalidos: number;
  } | null>(null);
  protected readonly valoresIniciales = DATOS_PRECARGADOS_DE_EJEMPLO;

  ngOnInit(): void {
    const idVersion = this.route.snapshot.queryParamMap.get('version');
    const carga = idVersion
      ? this.api.obtenerEsquemaPublicado(idVersion)
      : this.api.obtenerActiva();

    carga
      .pipe(
        finalize(() => this.cargando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (version) => this.version.set(version),
        error: (error: HttpErrorResponse) => {
          if (error.status === 404 && !idVersion) {
            this.sinVersionActiva.set(true);
            return;
          }
          this.errorCarga.set(this.apiError.normalize(error));
        },
      });
  }

  protected alternarSoloLectura(): void {
    this.soloLectura.update((valor) => !valor);
    this.resultadoRevision.set(null);
  }

  protected revisar(): void {
    const formulario = this.formulario();
    const valido = formulario?.revisar() ?? false;
    const avance = formulario?.avanceTotal();
    this.resultadoRevision.set({
      valido,
      faltantes: avance ? avance.obligatorios - avance.completos : 0,
      invalidos: avance?.invalidos ?? 0,
    });
  }

  protected json(): string {
    return JSON.stringify(this.valor(), null, 2);
  }
}
