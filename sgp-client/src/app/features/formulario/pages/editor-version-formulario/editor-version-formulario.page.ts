import {
  ChangeDetectionStrategy,
  Component,
  computed,
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
  PreguntaVisual,
  SeccionVisual,
  TipoDatoVisual,
  TipoMapeoCampo,
  VersionFormulario,
} from '../../models/formulario.models';
import { FormularioApiService } from '../../services/formulario-api.service';
import { esquemaASeccionesVisuales, seccionesVisualesAEsquema } from '../../utils/esquema-visual';

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

  // Estado del Editor Visual
  modoEdicion = signal<'visual' | 'codigo'>('visual');
  seccionesVisuales = signal<SeccionVisual[]>([]);
  dialogoSeccion = signal<boolean>(false);
  modoSeccion = signal<'crear' | 'editar'>('crear');
  seccionSeleccionadaClave = signal<string | null>(null);

  dialogoPregunta = signal<boolean>(false);
  modoPregunta = signal<'crear' | 'editar'>('crear');
  preguntaSeleccionadaClave = signal<string | null>(null);
  seccionDestinoClave = signal<string>('');
  opcionesSeleccion = signal<string[]>([]);

  readonly totalSecciones = computed(() => this.seccionesVisuales().length);
  readonly totalPreguntas = computed(() =>
    this.seccionesVisuales().reduce((acc, s) => acc + s.preguntas.length, 0),
  );

  readonly tiposDato: { label: string; value: TipoDatoVisual; icon: string }[] = [
    { label: 'Texto corto', value: 'string', icon: 'pi pi-align-left' },
    { label: 'Texto largo / Área de texto', value: 'multiline', icon: 'pi pi-align-justify' },
    { label: 'Número entero', value: 'integer', icon: 'pi pi-hashtag' },
    { label: 'Número decimal', value: 'number', icon: 'pi pi-percentage' },
    { label: 'Booleano (Sí/No)', value: 'boolean', icon: 'pi pi-check-square' },
    { label: 'Selección única (Dropdown)', value: 'select', icon: 'pi pi-list' },
    { label: 'Fecha', value: 'date', icon: 'pi pi-calendar' },
  ];

  readonly seccionForm = this.fb.nonNullable.group({
    clave: ['', [Validators.required, Validators.pattern(/^[a-z0-9_]+$/)]],
    titulo: ['', [Validators.required, Validators.maxLength(150)]],
    descripcion: [''],
    esRepetible: [false],
  });

  readonly preguntaForm = this.fb.nonNullable.group({
    seccionClave: ['', Validators.required],
    clave: ['', [Validators.required, Validators.pattern(/^[a-z0-9_]+$/)]],
    titulo: ['', [Validators.required, Validators.maxLength(200)]],
    descripcion: [''],
    tipo: ['string' as TipoDatoVisual, Validators.required],
    obligatorio: [false],
    soloLectura: [false],
    longitudMinima: [null as number | null],
    longitudMaxima: [null as number | null],
    valorMinimo: [null as number | null],
    valorMaximo: [null as number | null],
    vinculo: [''],
    opcionNueva: [''],
  });

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
          this.seccionesVisuales.set(
            esquemaASeccionesVisuales(ver.esquema, ver.vinculosAlmacenamiento),
          );

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

  obtenerEtiquetaTipo(tipo: TipoDatoVisual): string {
    const t = this.tiposDato.find((item) => item.value === tipo);
    return t ? t.label : tipo;
  }

  obtenerIconoTipo(tipo: TipoDatoVisual): string {
    const t = this.tiposDato.find((item) => item.value === tipo);
    return t ? t.icon : 'pi pi-tag';
  }

  cambiarModoEdicion(modo: 'visual' | 'codigo'): void {
    if (modo === 'visual') {
      try {
        const parsed = JSON.parse(this.esquemaTexto() || '{}');
        const vinculos = this.vinculosTexto() ? JSON.parse(this.vinculosTexto()) : {};
        this.seccionesVisuales.set(esquemaASeccionesVisuales(parsed, vinculos));
        this.errorValidacion.set(null);
      } catch {
        this.errorValidacion.set(
          'El JSON Schema contiene errores de formato y no puede cambiarse a la vista visual sin corregirlos.',
        );
        return;
      }
    } else {
      this.sincronizarVisualACodigo();
    }
    this.modoEdicion.set(modo);
  }

  sincronizarVisualACodigo(): void {
    const ver = this.version();
    let vinculosActuales: Record<string, unknown> = {};
    try {
      vinculosActuales = this.vinculosTexto() ? JSON.parse(this.vinculosTexto()) : {};
    } catch {
      vinculosActuales = ver?.vinculosAlmacenamiento ?? {};
    }

    const { esquema, vinculos } = seccionesVisualesAEsquema(
      this.seccionesVisuales(),
      ver?.esquema,
      vinculosActuales,
    );

    this.esquemaTexto.set(JSON.stringify(esquema, null, 2));
    this.vinculosTexto.set(JSON.stringify(vinculos, null, 2));
  }

  abrirCrearSeccion(): void {
    this.modoSeccion.set('crear');
    this.seccionForm.reset({
      clave: '',
      titulo: '',
      descripcion: '',
      esRepetible: false,
    });
    this.dialogoSeccion.set(true);
  }

  abrirEditarSeccion(sec: SeccionVisual): void {
    this.modoSeccion.set('editar');
    this.seccionSeleccionadaClave.set(sec.clave);
    this.seccionForm.reset({
      clave: sec.clave,
      titulo: sec.titulo,
      descripcion: sec.descripcion || '',
      esRepetible: Boolean(sec.esRepetible),
    });
    this.dialogoSeccion.set(true);
  }

  guardarSeccionModal(): void {
    if (this.seccionForm.invalid) {
      this.seccionForm.markAllAsTouched();
      return;
    }

    const val = this.seccionForm.getRawValue();
    const clave = val.clave.trim();
    const titulo = val.titulo.trim();
    const descripcion = val.descripcion.trim();
    const esRepetible = val.esRepetible;

    const lista = [...this.seccionesVisuales()];

    if (this.modoSeccion() === 'crear') {
      if (lista.some((s) => s.clave === clave)) {
        this.errorValidacion.set('Ya existe una sección con esa clave.');
        return;
      }
      lista.push({
        clave,
        titulo,
        descripcion,
        esRepetible,
        preguntas: [],
      });
    } else {
      const idx = lista.findIndex((s) => s.clave === this.seccionSeleccionadaClave());
      if (idx !== -1) {
        lista[idx] = {
          ...lista[idx],
          titulo,
          descripcion,
          esRepetible,
        };
      }
    }

    this.seccionesVisuales.set(lista);
    this.sincronizarVisualACodigo();
    this.dialogoSeccion.set(false);
  }

  eliminarSeccion(secClave: string): void {
    const lista = this.seccionesVisuales().filter((s) => s.clave !== secClave);
    this.seccionesVisuales.set(lista);
    this.sincronizarVisualACodigo();
  }

  abrirCrearPregunta(secClave?: string): void {
    this.modoPregunta.set('crear');
    const defaultSec = secClave || this.seccionesVisuales()[0]?.clave || '';
    this.preguntaForm.reset({
      seccionClave: defaultSec,
      clave: '',
      titulo: '',
      descripcion: '',
      tipo: 'string',
      obligatorio: false,
      soloLectura: false,
      longitudMinima: null,
      longitudMaxima: null,
      valorMinimo: null,
      valorMaximo: null,
      vinculo: '',
      opcionNueva: '',
    });
    this.opcionesSeleccion.set([]);
    this.dialogoPregunta.set(true);
  }

  abrirEditarPregunta(secClave: string, p: PreguntaVisual): void {
    this.modoPregunta.set('editar');
    this.seccionDestinoClave.set(secClave);
    this.preguntaSeleccionadaClave.set(p.clave);
    this.preguntaForm.reset({
      seccionClave: secClave,
      clave: p.clave,
      titulo: p.titulo,
      descripcion: p.descripcion || '',
      tipo: p.tipo,
      obligatorio: p.obligatorio,
      soloLectura: Boolean(p.soloLectura),
      longitudMinima: p.longitudMinima ?? null,
      longitudMaxima: p.longitudMaxima ?? null,
      valorMinimo: p.valorMinimo ?? null,
      valorMaximo: p.valorMaximo ?? null,
      vinculo: p.vinculo || '',
      opcionNueva: '',
    });
    this.opcionesSeleccion.set(p.opciones ? [...p.opciones] : []);
    this.dialogoPregunta.set(true);
  }

  agregarOpcion(): void {
    const opcion = this.preguntaForm.controls.opcionNueva.value.trim();
    if (opcion && !this.opcionesSeleccion().includes(opcion)) {
      this.opcionesSeleccion.update((opts) => [...opts, opcion]);
      this.preguntaForm.controls.opcionNueva.setValue('');
    }
  }

  eliminarOpcion(index: number): void {
    this.opcionesSeleccion.update((opts) => opts.filter((_, i) => i !== index));
  }

  guardarPreguntaModal(): void {
    if (this.preguntaForm.invalid) {
      this.preguntaForm.markAllAsTouched();
      return;
    }

    const val = this.preguntaForm.getRawValue();
    const secClave = val.seccionClave;
    const sec = this.seccionesVisuales().find((s) => s.clave === secClave);
    if (!sec) return;

    const clave = val.clave.trim();
    if (this.modoPregunta() === 'crear' && sec.preguntas.some((p) => p.clave === clave)) {
      this.errorValidacion.set('Ya existe una pregunta con esa clave en esta sección.');
      return;
    }

    const opciones =
      val.tipo === 'select'
        ? this.opcionesSeleccion().length > 0
          ? [...this.opcionesSeleccion()]
          : ['OPCION_1']
        : undefined;

    const nueva: PreguntaVisual = {
      clave,
      titulo: val.titulo.trim(),
      descripcion: val.descripcion.trim() || undefined,
      tipo: val.tipo,
      obligatorio: val.obligatorio,
      soloLectura: val.soloLectura,
      opciones,
      longitudMinima: val.longitudMinima != null ? Number(val.longitudMinima) : undefined,
      longitudMaxima: val.longitudMaxima != null ? Number(val.longitudMaxima) : undefined,
      valorMinimo: val.valorMinimo != null ? Number(val.valorMinimo) : undefined,
      valorMaximo: val.valorMaximo != null ? Number(val.valorMaximo) : undefined,
      vinculo: val.vinculo.trim() || undefined,
    };

    if (this.modoPregunta() === 'crear') {
      sec.preguntas.push(nueva);
    } else {
      const idx = sec.preguntas.findIndex((p) => p.clave === this.preguntaSeleccionadaClave());
      if (idx !== -1) {
        sec.preguntas[idx] = nueva;
      }
    }

    this.seccionesVisuales.set([...this.seccionesVisuales()]);
    this.sincronizarVisualACodigo();
    this.dialogoPregunta.set(false);
  }

  eliminarPregunta(secClave: string, pregClave: string): void {
    const sec = this.seccionesVisuales().find((s) => s.clave === secClave);
    if (sec) {
      sec.preguntas = sec.preguntas.filter((p) => p.clave !== pregClave);
      this.seccionesVisuales.set([...this.seccionesVisuales()]);
      this.sincronizarVisualACodigo();
    }
  }

  guardarEsquema(): void {
    const ver = this.version();
    if (!ver || ver.estado === 'PUBLICADA') return;

    if (this.modoEdicion() === 'visual') {
      this.sincronizarVisualACodigo();
    }

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
          this.seccionesVisuales.set(
            esquemaASeccionesVisuales(actualizada.esquema, actualizada.vinculosAlmacenamiento),
          );
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
