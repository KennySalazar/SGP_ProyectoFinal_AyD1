import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import {
  AnalisisMapeo,
  ConfiguracionIcElemento,
  DeclaracionMapeoRequest,
  TipoMapeoCampo,
  VersionFormulario,
} from '../../models/formulario.models';
import { FormularioApiService } from '../../services/formulario-api.service';

type SeccionActiva = 'esquema' | 'vinculos' | 'pesos' | 'mapeos' | 'publicar';

@Component({
  selector: 'app-editor-version-formulario-page',
  standalone: true,
  imports: [
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    DialogModule,
    InputNumberModule,
    InputTextModule,
    SelectModule,
    TableModule,
    TagModule,
    TextareaModule,
  ],
  templateUrl: './editor-version-formulario.page.html',
  styleUrl: './editor-version-formulario.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditorVersionFormularioPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly formularioApi = inject(FormularioApiService);
  private readonly apiError = inject(ApiErrorService);
  private readonly transloco = inject(TranslocoService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly versionId = signal<string>('');
  readonly version = signal<VersionFormulario | null>(null);
  readonly analisisMapeo = signal<AnalisisMapeo | null>(null);
  readonly seccionActiva = signal<SeccionActiva>('esquema');

  readonly cargando = signal(false);
  readonly guardandoEsquema = signal(false);
  readonly guardandoPesos = signal(false);
  readonly guardandoMapeos = signal(false);
  readonly publicando = signal(false);

  readonly mensajeExito = signal<string | null>(null);
  readonly errorValidacion = signal<string | null>(null);

  // Formulario y buffers de edición
  esquemaTexto = signal<string>('');
  vinculosTexto = signal<string>('');
  pesosEditables = signal<ConfiguracionIcElemento[]>([]);
  mapeosEditables = signal<DeclaracionMapeoRequest[]>([]);

  // Diálogos
  dialogoConfirmarPublicar = signal(false);
  dialogoNuevoMapeo = signal(false);
  dialogoNuevaDerivada = signal(false);

  readonly nuevoMapeoForm = this.fb.nonNullable.group({
    elementoRefOrigen: [''],
    elementoRefDestino: [''],
    tipo: ['EQUIVALENTE' as TipoMapeoCampo, Validators.required],
    observacion: [''],
  });

  readonly nuevaDerivadaForm = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(50)]],
  });

  readonly tiposMapeo: { label: string; value: TipoMapeoCampo }[] = [
    { label: 'EQUIVALENTE', value: 'EQUIVALENTE' },
    { label: 'RENOMBRADO', value: 'RENOMBRADO' },
    { label: 'NUEVO', value: 'NUEVO' },
    { label: 'ELIMINADO', value: 'ELIMINADO' },
  ];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.versionId.set(id);
      this.cargarDetalle(id);
    }
  }

  cargarDetalle(id: string): void {
    this.cargando.set(true);
    this.mensajeExito.set(null);
    this.errorValidacion.set(null);

    this.formularioApi
      .obtenerPorId(id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (ver) => {
          this.version.set(ver);
          this.esquemaTexto.set(JSON.stringify(ver.esquema ?? {}, null, 2));
          this.vinculosTexto.set(JSON.stringify(ver.vinculosAlmacenamiento ?? {}, null, 2));
          this.pesosEditables.set(ver.pesos ? JSON.parse(JSON.stringify(ver.pesos)) : []);

          if (ver.versionAnteriorId) {
            this.cargarAnalisisMapeos(id);
          }
        },
        error: (err) => this.apiError.normalize(err),
      });
  }

  cargarAnalisisMapeos(id: string): void {
    this.formularioApi
      .analizarMapeo(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (analisis) => {
          this.analisisMapeo.set(analisis);
          this.mapeosEditables.set(
            analisis.mapeosDeclarados.map((m) => ({
              elementoRefOrigen: m.elementoRefOrigen,
              elementoRefDestino: m.elementoRefDestino,
              tipo: m.tipo,
              observacion: m.observacion,
            })),
          );
        },
        error: (err) => this.apiError.normalize(err),
      });
  }

  cambiarSeccion(seccion: SeccionActiva): void {
    this.seccionActiva.set(seccion);
    this.mensajeExito.set(null);
    this.errorValidacion.set(null);
  }

  guardarEsquema(): void {
    const ver = this.version();
    if (!ver || ver.estado === 'PUBLICADA') return;

    let esquemaJson: Record<string, unknown>;
    let vinculosJson: Record<string, unknown>;

    try {
      esquemaJson = JSON.parse(this.esquemaTexto());
    } catch {
      this.errorValidacion.set(this.transloco.translate('formulario.schemaFormatError'));
      return;
    }

    try {
      vinculosJson = this.vinculosTexto() ? JSON.parse(this.vinculosTexto()) : {};
    } catch {
      this.errorValidacion.set('El contenido de vínculos no es un JSON válido.');
      return;
    }

    this.guardandoEsquema.set(true);
    this.errorValidacion.set(null);
    this.mensajeExito.set(null);

    this.formularioApi
      .actualizarBorrador(ver.id, {
        codigo: ver.codigo,
        esquema: esquemaJson,
        vinculosAlmacenamiento: vinculosJson,
        reglasEvaluacionIc: ver.reglasEvaluacionIc,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardandoEsquema.set(false)),
      )
      .subscribe({
        next: (actualizada) => {
          this.version.set(actualizada);
          this.mensajeExito.set(this.transloco.translate('formulario.schemaSaved'));
          if (actualizada.versionAnteriorId) {
            this.cargarAnalisisMapeos(actualizada.id);
          }
        },
        error: (err) => {
          if (err?.error?.detail) {
            this.errorValidacion.set(
              `${this.transloco.translate('formulario.schemaValidationError')} ${err.error.detail}`,
            );
          } else {
            this.apiError.normalize(err);
          }
        },
      });
  }

  actualizarPeso(elementoRef: string, nuevoValor: number | null): void {
    const lista = this.pesosEditables().map((p) => {
      if (p.elementoRef === elementoRef) {
        return { ...p, peso: nuevoValor ?? 1 };
      }
      return p;
    });
    this.pesosEditables.set(lista);
  }

  guardarPesos(): void {
    const ver = this.version();
    if (!ver || ver.estado === 'PUBLICADA') return;

    this.guardandoPesos.set(true);
    this.mensajeExito.set(null);

    const payload = this.pesosEditables().map((p) => ({
      elementoRef: p.elementoRef,
      peso: p.peso,
      descripcion: p.descripcion,
    }));

    this.formularioApi
      .calibrarPesos(ver.id, payload)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardandoPesos.set(false)),
      )
      .subscribe({
        next: (pesosActualizados) => {
          this.pesosEditables.set(pesosActualizados);
          this.mensajeExito.set(this.transloco.translate('formulario.weightsSaved'));
        },
        error: (err) => this.apiError.normalize(err),
      });
  }

  abrirDialogoNuevoMapeo(): void {
    this.nuevoMapeoForm.reset({
      elementoRefOrigen: '',
      elementoRefDestino: '',
      tipo: 'EQUIVALENTE',
      observacion: '',
    });
    this.dialogoNuevoMapeo.set(true);
  }

  cerrarDialogoNuevoMapeo(): void {
    this.dialogoNuevoMapeo.set(false);
  }

  agregarDeclaracionMapeo(): void {
    if (this.nuevoMapeoForm.invalid) return;

    const val = this.nuevoMapeoForm.getRawValue();
    const lista = [
      ...this.mapeosEditables(),
      {
        elementoRefOrigen: val.elementoRefOrigen?.trim() || null,
        elementoRefDestino: val.elementoRefDestino?.trim() || null,
        tipo: val.tipo,
        observacion: val.observacion?.trim() || null,
      },
    ];
    this.mapeosEditables.set(lista);
    this.cerrarDialogoNuevoMapeo();
  }

  eliminarMapeo(index: number): void {
    const lista = [...this.mapeosEditables()];
    lista.splice(index, 1);
    this.mapeosEditables.set(lista);
  }

  guardarDeclaracionesMapeo(): void {
    const ver = this.version();
    if (!ver || ver.estado === 'PUBLICADA') return;

    this.guardandoMapeos.set(true);
    this.mensajeExito.set(null);

    this.formularioApi
      .guardarMapeos(ver.id, this.mapeosEditables())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardandoMapeos.set(false)),
      )
      .subscribe({
        next: () => {
          this.mensajeExito.set(this.transloco.translate('formulario.mappingsSaved'));
          this.cargarAnalisisMapeos(ver.id);
        },
        error: (err) => this.apiError.normalize(err),
      });
  }

  abrirConfirmarPublicar(): void {
    this.dialogoConfirmarPublicar.set(true);
  }

  cerrarConfirmarPublicar(): void {
    this.dialogoConfirmarPublicar.set(false);
  }

  ejecutarPublicacion(): void {
    const ver = this.version();
    if (!ver || ver.estado === 'PUBLICADA') return;

    this.publicando.set(true);
    this.formularioApi
      .publicar(ver.id, { mapeos: this.mapeosEditables() })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.publicando.set(false)),
      )
      .subscribe({
        next: (pub) => {
          this.cerrarConfirmarPublicar();
          this.version.set(pub);
          this.mensajeExito.set(
            this.transloco.translate('formulario.publishedSuccess', { codigo: pub.codigo }),
          );
          this.cambiarSeccion('esquema');
        },
        error: (err) => {
          this.cerrarConfirmarPublicar();
          this.apiError.normalize(err);
        },
      });
  }

  abrirDialogoNuevaDerivada(): void {
    const ver = this.version();
    const sugerido = ver ? `${ver.codigo}-copy` : '';
    this.nuevaDerivadaForm.reset({ codigo: sugerido });
    this.dialogoNuevaDerivada.set(true);
  }

  cerrarDialogoNuevaDerivada(): void {
    this.dialogoNuevaDerivada.set(false);
  }

  crearNuevaDerivada(): void {
    if (this.nuevaDerivadaForm.invalid) return;

    const ver = this.version();
    if (!ver) return;

    const nuevoCodigo = this.nuevaDerivadaForm.getRawValue().codigo.trim();
    this.cargando.set(true);

    this.formularioApi
      .crear({
        codigo: nuevoCodigo,
        versionBaseId: ver.id,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (creada) => {
          this.cerrarDialogoNuevaDerivada();
          this.router.navigate(['/admin/formularios', creada.id]);
        },
        error: (err) => this.apiError.normalize(err),
      });
  }
}
