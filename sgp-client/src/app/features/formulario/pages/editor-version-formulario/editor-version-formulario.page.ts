import { AprobacionCalibracionComponent } from '../../components/aprobacion-calibracion/aprobacion-calibracion.component';
import { AprobacionCalibracion } from '../../models/aprobacion-calibracion.models';
import { mensajeCalibracion } from '../../utils/error-calibracion';
import { AuthStore } from '../../../../core/services/auth.store';
import { etiquetaMapeo, nombresMapeo } from '../../utils/nombres-mapeo';
import { CalibracionAdicionalComponent } from './calibracion-adicional/calibracion-adicional.component';
import { criticasPendientes } from '../../utils/reglas-criticas';
import { ReglasIcComponent } from './reglas-ic/reglas-ic.component';
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
import {
  FormBuilder,
  FormControl,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
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
import { finalize, switchMap } from 'rxjs';
import { completarEquivalencias } from '../../utils/mapeos-equivalentes';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import {
  AnalisisMapeo,
  DeclaracionMapeoRequest,
  PreguntaVisual,
  SeccionVisual,
  TipoDatoVisual,
  TipoMapeoCampo,
  VersionFormulario,
  VinculoVisual,
} from '../../models/formulario.models';
import { FormularioApiService } from '../../services/formulario-api.service';
import {
  esquemaASeccionesVisuales,
  extraerVinculosVisuales,
  seccionesVisualesAEsquema,
  vinculosVisualesAObjeto,
} from '../../utils/esquema-visual';
import {
  NIVELES_SEVERIDAD,
  NivelSeveridad,
  PesoIcEditable,
  EscalaIcEditable,
  elementosCalibracion,
  escalaCalibracion,
  pesoValido,
  escalaValida,
  configuracionIcCompleta,
} from '../../utils/configuracion-ic';
type SeccionActiva = 'esquema' | 'vinculos' | 'pesos' | 'mapeos' | 'publicar';
export interface InfoVinculoDestino {
  categoria: 'puente' | 'tecnico' | 'inspeccion' | 'otro';
  etiquetaCategoria: string;
  etiquetaCampo: string;
  nombreCompleto: string;
  icono: string;
}
@Component({
  selector: 'app-editor-version-formulario-page',
  standalone: true,
  imports: [
    ReglasIcComponent,
    AprobacionCalibracionComponent,
    CalibracionAdicionalComponent,
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
  readonly auth = inject(AuthStore);
  readonly esAdministrador = () => this.auth.user()?.role === 'ADMINISTRADOR';
  private readonly apiError = inject(ApiErrorService);
  private readonly transloco = inject(TranslocoService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  registrarAprobacion(aprobacion: AprobacionCalibracion): void {
    this.version.update((version) =>
      version && version.id === aprobacion.versionFormularioId
        ? {
            ...version,
            aprobacionCalibracion: aprobacion,
            rotuloCalibracion: aprobacion.rotuloCalibracion,
          }
        : version,
    );
  }

  readonly versionId = signal<string>('');
  readonly version = signal<VersionFormulario | null>(null);
  readonly analisisMapeo = signal<AnalisisMapeo | null>(null);
  readonly esquemaOrigenMapeo = signal<Record<string, unknown> | null>(null);
  private readonly nombresDestinoMapeo = computed(() =>
    nombresMapeo(this.version()?.esquema ?? null),
  );
  private readonly nombresOrigenMapeo = computed(() => nombresMapeo(this.esquemaOrigenMapeo()));

  nombreCampoMapeo(ruta: string | null | undefined, origen = false): string {
    return etiquetaMapeo(ruta, origen ? this.nombresOrigenMapeo() : this.nombresDestinoMapeo());
  }
  readonly seccionActiva = signal<SeccionActiva>('esquema');
  readonly cargando = signal(false);
  readonly guardandoEsquema = signal(false);
  readonly guardandoPesos = signal(false);
  readonly guardandoEscala = signal(false);
  readonly estadoCriticas = signal({ pendiente: false, guardando: false });
  readonly criticasSinResolver = computed(() => {
    const ver = this.version();
    return !!ver && criticasPendientes(ver);
  });
  readonly estadoReglas = signal({ pendiente: false, guardando: false });
  readonly nivelesSeveridad = NIVELES_SEVERIDAD;
  readonly escalaEditable = signal<EscalaIcEditable>({
    NO_PRESENTA: null,
    LEVE: null,
    MODERADO: null,
    SEVERO: null,
  });
  readonly pesosPendientes = computed(() =>
    this.pesosEditables().filter((p) => !pesoValido(p.peso)),
  );
  readonly escalaCorrecta = computed(() => escalaValida(this.escalaEditable()));
  readonly calibracionGuardada = computed(() => {
    const ver = this.version();
    return !!ver && configuracionIcCompleta(ver);
  });
  readonly calibracionPendiente = computed(() => {
    const ver = this.version();
    return (
      !!ver &&
      (JSON.stringify(this.pesosEditables()) !== JSON.stringify(elementosCalibracion(ver)) ||
        JSON.stringify(this.escalaEditable()) !== JSON.stringify(escalaCalibracion(ver)))
    );
  });
  readonly puedePublicar = computed(() => {
    const ver = this.version();
    return (
      this.esAdministrador() &&
      !!ver &&
      ver.estado === 'BORRADOR' &&
      this.calibracionGuardada() &&
      !this.criticasSinResolver() &&
      !this.estadoCriticas().pendiente &&
      !this.estadoCriticas().guardando &&
      !this.calibracionPendiente() &&
      !this.estadoReglas().pendiente &&
      !this.estadoReglas().guardando &&
      !this.guardandoPesos() &&
      !this.guardandoEscala() &&
      !this.guardandoEsquema() &&
      !this.publicando() &&
      (!ver.versionAnteriorId || this.analisisMapeo()?.mapeoCompleto === true)
    );
  });
  readonly guardandoMapeos = signal(false);
  readonly publicando = signal(false);
  readonly mensajeExito = signal<string | null>(null);
  readonly errorValidacion = signal<string | null>(null);
  esquemaTexto = signal<string>('');
  vinculosTexto = signal<string>('');
  pesosEditables = signal<PesoIcEditable[]>([]);
  mapeosEditables = signal<DeclaracionMapeoRequest[]>([]);
  modoEdicion = signal<'visual' | 'codigo'>('visual');
  seccionesVisuales = signal<SeccionVisual[]>([]);
  seccionVisualActiva = signal<string>('');
  readonly seccionVisualActual = computed(() => {
    const list = this.seccionesVisuales();
    const active = this.seccionVisualActiva();
    return list.find((s) => s.clave === active) || list[0] || null;
  });
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
  readonly tiposDato: {
    label: string;
    value: TipoDatoVisual;
    icon: string;
  }[] = [
    { label: 'Texto corto', value: 'string', icon: 'pi pi-align-left' },
    {
      label: 'Texto largo / Área de texto',
      value: 'multiline',
      icon: 'pi pi-align-justify',
    },
    { label: 'Número entero', value: 'integer', icon: 'pi pi-hashtag' },
    { label: 'Número decimal', value: 'number', icon: 'pi pi-percentage' },
    { label: 'Booleano (Sí/No)', value: 'boolean', icon: 'pi pi-check-square' },
    {
      label: 'Selección única (Dropdown)',
      value: 'select',
      icon: 'pi pi-list',
    },
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
  modoVinculos = signal<'visual' | 'codigo'>('visual');
  vinculosVisuales = signal<VinculoVisual[]>([]);
  filtroVinculos = signal<string>('');
  guardandoVinculos = signal<boolean>(false);
  dialogoVinculo = signal<boolean>(false);
  modoVinculo = signal<'crear' | 'editar'>('crear');
  vinculoSeleccionadoId = signal<string | null>(null);
  readonly vinculoForm = this.fb.nonNullable.group({
    campoRef: ['', Validators.required],
    columnaDestino: ['', [Validators.required, Validators.maxLength(150)]],
    tipo: ['Columna técnica'],
    descripcion: [''],
  });
  readonly destinoPersonalizado = signal(false);
  readonly columnaPersonalizadaControl = new FormControl<string>('', {
    nonNullable: true,
  });
  readonly camposFormularioDisponibles = computed(() => {
    const list: {
      label: string;
      value: string;
    }[] = [];
    const valuesSet = new Set<string>();
    for (const sec of this.seccionesVisuales()) {
      for (const p of sec.preguntas) {
        const val = `/${sec.clave}/${p.clave}`;
        valuesSet.add(val);
        list.push({
          label: `${this.limpiarTituloSeccion(sec.titulo)} › ${p.titulo}`,
          value: val,
        });
      }
    }
    for (const v of this.vinculosVisuales()) {
      if (v.campoRef && !valuesSet.has(v.campoRef)) {
        valuesSet.add(v.campoRef);
        list.push({
          label: this.obtenerTituloCampoRef(v.campoRef),
          value: v.campoRef,
        });
      }
    }
    return list;
  });
  readonly opcionesDestino = [
    {
      grupo: 'Ficha del puente (Inventario)',
      items: [
        { valor: 'puente.codigo', etiqueta: 'Código del puente' },
        { valor: 'puente.nombre', etiqueta: 'Nombre del puente' },
        { valor: 'puente.ruta', etiqueta: 'Ruta del puente' },
        { valor: 'puente.kilometraje', etiqueta: 'Kilometraje' },
        { valor: 'puente.municipio_id', etiqueta: 'Municipio del puente' },
        {
          valor: 'puente.departamento_id',
          etiqueta: 'Departamento del puente',
        },
      ],
    },
    {
      grupo: 'Registro de inspección',
      items: [
        {
          valor: 'inspeccion.fecha_inspeccion',
          etiqueta: 'Fecha oficial de inspección',
        },
        {
          valor: 'inspeccion.nombre_autor_capturado',
          etiqueta: 'Inspector asignado',
        },
      ],
    },
    {
      grupo: 'Ficha técnica de inspección',
      items: [
        {
          valor: 'inspeccion_dato_tecnico.longitud_m',
          etiqueta: 'Longitud total (m)',
        },
        {
          valor: 'inspeccion_dato_tecnico.numero_tramos',
          etiqueta: 'Número de tramos',
        },
        {
          valor: 'inspeccion_dato_tecnico.ancho_rodadura_m',
          etiqueta: 'Ancho de rodadura (m)',
        },
        {
          valor: 'inspeccion_dato_tecnico.ancho_acera_derecha_m',
          etiqueta: 'Ancho acera derecha (m)',
        },
        {
          valor: 'inspeccion_dato_tecnico.ancho_acera_izquierda_m',
          etiqueta: 'Ancho acera izquierda (m)',
        },
        {
          valor: 'inspeccion_dato_tecnico.altura_libre_superior_m',
          etiqueta: 'Gálibo / Altura superior (m)',
        },
        {
          valor: 'inspeccion_dato_tecnico.altura_libre_sobre_cauce_m',
          etiqueta: 'Altura libre sobre cauce (m)',
        },
        {
          valor: 'inspeccion_dato_tecnico.tipologia_puente',
          etiqueta: 'Tipología del puente',
        },
        {
          valor: 'inspeccion_dato_tecnico.tipo_cruce',
          etiqueta: 'Tipo de cruce',
        },
        {
          valor: 'inspeccion_dato_tecnico.numero_vias_por_sentido',
          etiqueta: 'Vías por sentido',
        },
        {
          valor: 'inspeccion_dato_tecnico.material_superestructura',
          etiqueta: 'Material de superestructura',
        },
        {
          valor: 'inspeccion_dato_tecnico.material_subestructura',
          etiqueta: 'Material de subestructura',
        },
        {
          valor: 'inspeccion_dato_tecnico.carga_diseno',
          etiqueta: 'Carga de diseño',
        },
        {
          valor: 'inspeccion_dato_tecnico.anio_construccion',
          etiqueta: 'Año de construcción',
        },
        {
          valor: 'inspeccion_dato_tecnico.trafico_vehiculos_dia',
          etiqueta: 'Tráfico diario (veh/día)',
        },
        {
          valor: 'inspeccion_dato_tecnico.porcentaje_camiones_buses',
          etiqueta: 'Porcentaje camiones y buses',
        },
        {
          valor: 'inspeccion_dato_tecnico.ruta_pavimentada',
          etiqueta: 'Ruta pavimentada',
        },
        {
          valor: 'inspeccion_dato_tecnico.alineamiento_horizontal',
          etiqueta: 'Alineamiento horizontal',
        },
        {
          valor: 'inspeccion_dato_tecnico.esviaje',
          etiqueta: 'Esviaje / sesgo',
        },
        {
          valor: 'inspeccion_dato_tecnico.poblacion_antes',
          etiqueta: 'Población antes del puente',
        },
        {
          valor: 'inspeccion_dato_tecnico.poblacion_despues',
          etiqueta: 'Población después del puente',
        },
        {
          valor: 'inspeccion_dato_tecnico.tipo_cuerpo_agua',
          etiqueta: 'Tipo de cuerpo de agua',
        },
        {
          valor: 'inspeccion_dato_tecnico.nombre_rio',
          etiqueta: 'Nombre del río / cauce',
        },
        {
          valor: 'inspeccion_dato_tecnico.encauzamiento',
          etiqueta: 'Encauzamiento',
        },
        {
          valor: 'inspeccion_dato_tecnico.antecedente_desbordamiento',
          etiqueta: 'Antecedente de desbordamiento',
        },
        {
          valor: 'inspeccion_dato_tecnico.frecuencia_desbordamiento_anios',
          etiqueta: 'Frecuencia desbordamiento (años)',
        },
        {
          valor: 'inspeccion_dato_tecnico.fecha_ultimo_desbordamiento',
          etiqueta: 'Fecha último desbordamiento',
        },
        {
          valor: 'inspeccion_dato_tecnico.alumbrado_existe',
          etiqueta: 'Alumbrado artificial',
        },
        {
          valor: 'inspeccion_dato_tecnico.drenajes_aledanos_existen',
          etiqueta: 'Drenajes aledaños',
        },
      ],
    },
  ];
  readonly tiposVinculo = [
    { label: 'Ficha técnica de inspección', value: 'Columna técnica' },
    { label: 'Ficha del puente (Inventario)', value: 'Inventario puente' },
    { label: 'Enlazado general', value: 'Directo' },
  ];
  onDestinoChange(event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    if (val === '__custom__') {
      this.destinoPersonalizado.set(true);
      this.vinculoForm.controls.columnaDestino.setValue('__custom__');
      this.vinculoForm.controls.tipo.setValue('Directo');
    } else {
      this.destinoPersonalizado.set(false);
      this.vinculoForm.controls.columnaDestino.setValue(val);
      if (val.startsWith('puente.')) {
        this.vinculoForm.controls.tipo.setValue('Inventario puente');
      } else {
        this.vinculoForm.controls.tipo.setValue('Columna técnica');
      }
    }
  }
  readonly vinculosFiltrados = computed(() => {
    const term = this.filtroVinculos().toLowerCase().trim();
    const list = this.vinculosVisuales();
    if (!term) return list;
    return list.filter((v) => {
      const nomPreg = this.obtenerNombrePregunta(v.campoRef).toLowerCase();
      const nomSec = this.obtenerNombreSeccion(v.campoRef).toLowerCase();
      const info = this.describirVinculo(v.columnaDestino);
      return (
        nomPreg.includes(term) ||
        nomSec.includes(term) ||
        info.etiquetaCampo.toLowerCase().includes(term) ||
        info.etiquetaCategoria.toLowerCase().includes(term) ||
        v.campoRef.toLowerCase().includes(term) ||
        v.columnaDestino.toLowerCase().includes(term) ||
        Boolean(v.descripcion && v.descripcion.toLowerCase().includes(term))
      );
    });
  });
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
  readonly tiposMapeo: {
    label: string;
    value: TipoMapeoCampo;
  }[] = [
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
          this.pesosEditables.set(elementosCalibracion(ver));
          this.escalaEditable.set(escalaCalibracion(ver));
          const secs = esquemaASeccionesVisuales(ver.esquema, ver.vinculosAlmacenamiento);
          this.seccionesVisuales.set(secs);
          if (
            secs.length > 0 &&
            (!this.seccionVisualActiva() ||
              !secs.some((s) => s.clave === this.seccionVisualActiva()))
          ) {
            this.seccionVisualActiva.set(secs[0].clave);
          }
          this.vinculosVisuales.set(extraerVinculosVisuales(ver.vinculosAlmacenamiento, secs));
          this.esquemaOrigenMapeo.set(null);
          if (ver.versionAnteriorId) {
            this.formularioApi
              .obtenerPorId(ver.versionAnteriorId)
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe({
                next: (anterior) => {
                  if (this.version()?.id === id) this.esquemaOrigenMapeo.set(anterior.esquema);
                },
                error: () => this.esquemaOrigenMapeo.set(null),
              });
            this.cargarAnalisisMapeos(id);
          }
        },
        error: (err) =>
          this.errorValidacion.set(mensajeCalibracion(err, (k) => this.transloco.translate(k))),
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
        error: (err) =>
          this.errorValidacion.set(mensajeCalibracion(err, (k) => this.transloco.translate(k))),
      });
  }
  readonly dialogoRecargar = signal(false);
  readonly recargaBloqueada = computed(
    () =>
      this.cargando() ||
      this.guardandoEsquema() ||
      this.guardandoVinculos() ||
      this.guardandoPesos() ||
      this.guardandoEscala() ||
      this.estadoReglas().guardando ||
      this.estadoCriticas().guardando ||
      this.publicando(),
  );
  recargarVersion(): void {
    if (this.recargaBloqueada()) return;
    this.dialogoRecargar.set(true);
  }
  confirmarRecarga(): void {
    if (this.recargaBloqueada() || !this.dialogoRecargar()) return;
    this.dialogoRecargar.set(false);
    this.version.set(null);
    this.cargarDetalle(this.versionId());
  }
  fechaCalibracion(fecha: string): string {
    return new Intl.DateTimeFormat('es-GT', {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'America/Guatemala',
    }).format(new Date(fecha));
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
      this.seccionVisualActiva.set(clave);
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
  seleccionarSeccionVisual(clave: string): void {
    this.seccionVisualActiva.set(clave);
  }
  limpiarTituloSeccion(titulo: string): string {
    if (!titulo) return '';
    return titulo.replace(/^\d+[.\-\s:]+\s*/, '').trim();
  }
  describirVinculo(destino: string | null | undefined): InfoVinculoDestino {
    if (!destino || destino.trim().length === 0) {
      return {
        categoria: 'otro',
        etiquetaCategoria: 'Formulario',
        etiquetaCampo: 'Documento de inspección',
        nombreCompleto: 'Documento de inspección',
        icono: 'pi pi-file',
      };
    }
    const d = destino.trim();
    if (
      d.startsWith('puente.') ||
      d.includes('puente.municipio_id') ||
      d.startsWith('municipio.') ||
      d.startsWith('departamento.')
    ) {
      let campo = 'Dato de inventario';
      if (d === 'puente.codigo') campo = 'Código del puente';
      else if (d === 'puente.nombre') campo = 'Nombre del puente';
      else if (d === 'puente.ruta') campo = 'Ruta del puente';
      else if (d === 'puente.kilometraje') campo = 'Kilometraje';
      else if (d.includes('municipio')) campo = 'Municipio del puente';
      else if (d.includes('departamento')) campo = 'Departamento del puente';
      else {
        const sub = d.replace(/^puente\./, '').replace(/_/g, ' ');
        campo = sub.charAt(0).toUpperCase() + sub.slice(1);
      }
      return {
        categoria: 'puente',
        etiquetaCategoria: 'Ficha del puente',
        etiquetaCampo: campo,
        nombreCompleto: campo,
        icono: 'pi pi-id-card',
      };
    }
    if (d.startsWith('inspeccion.')) {
      let campo = 'Registro de inspección';
      if (d === 'inspeccion.fecha_inspeccion') campo = 'Fecha oficial de inspección';
      else if (d === 'inspeccion.nombre_autor_capturado') campo = 'Inspector asignado';
      else {
        const sub = d.replace(/^inspeccion\./, '').replace(/_/g, ' ');
        campo = sub.charAt(0).toUpperCase() + sub.slice(1);
      }
      return {
        categoria: 'inspeccion',
        etiquetaCategoria: 'Inspección',
        etiquetaCampo: campo,
        nombreCompleto: campo,
        icono: 'pi pi-calendar',
      };
    }
    if (d.startsWith('inspeccion_dato_tecnico.')) {
      const col = d.replace('inspeccion_dato_tecnico.', '');
      const mapaCamposTecnicos: Record<string, string> = {
        longitud_m: 'Longitud total (m)',
        numero_tramos: 'Número de tramos',
        ancho_rodadura_m: 'Ancho de rodadura (m)',
        ancho_acera_derecha_m: 'Ancho acera derecha (m)',
        ancho_acera_izquierda_m: 'Ancho acera izquierda (m)',
        altura_libre_superior_m: 'Gálibo / Altura superior (m)',
        altura_libre_sobre_cauce_m: 'Altura libre sobre cauce (m)',
        tipologia_puente: 'Tipología del puente',
        tipo_cruce: 'Tipo de cruce',
        numero_vias_por_sentido: 'Vías por sentido',
        material_superestructura: 'Material de superestructura',
        material_subestructura: 'Material de subestructura',
        carga_diseno: 'Carga de diseño',
        anio_construccion: 'Año de construcción',
        trafico_vehiculos_dia: 'Tráfico diario (veh/día)',
        porcentaje_camiones_buses: 'Porcentaje camiones y buses',
        ruta_pavimentada: 'Ruta pavimentada',
        alineamiento_horizontal: 'Alineamiento horizontal',
        esviaje: 'Esviaje / sesgo',
        poblacion_antes: 'Población antes del puente',
        poblacion_despues: 'Población después del puente',
        tipo_cuerpo_agua: 'Tipo de cuerpo de agua',
        nombre_rio: 'Nombre del río / cauce',
        encauzamiento: 'Encauzamiento',
        antecedente_desbordamiento: 'Antecedente de desbordamiento',
        frecuencia_desbordamiento_anios: 'Frecuencia desbordamiento (años)',
        fecha_ultimo_desbordamiento: 'Fecha último desbordamiento',
        alumbrado_existe: 'Alumbrado artificial',
        drenajes_aledanos_existen: 'Drenajes aledaños',
      };
      const campo = mapaCamposTecnicos[col] || col.replace(/_/g, ' ');
      return {
        categoria: 'tecnico',
        etiquetaCategoria: 'Ficha técnica',
        etiquetaCampo: campo,
        nombreCompleto: campo,
        icono: 'pi pi-table',
      };
    }
    if (d.startsWith('inspeccion_tramo.')) {
      const col = d.replace('inspeccion_tramo.', '').replace(/_/g, ' ');
      return {
        categoria: 'tecnico',
        etiquetaCategoria: 'Tramo',
        etiquetaCampo: `Tramo: ${col}`,
        nombreCompleto: `Tramo: ${col}`,
        icono: 'pi pi-list',
      };
    }
    return {
      categoria: 'otro',
      etiquetaCategoria: 'Enlazado',
      etiquetaCampo: d,
      nombreCompleto: d,
      icono: 'pi pi-database',
    };
  }
  formatearDestinoHumano(destino: string | null | undefined): string {
    return this.describirVinculo(destino).nombreCompleto;
  }
  obtenerNombrePregunta(campoRef: string): string {
    if (!campoRef) return '';
    const partes = campoRef.replace(/^\//, '').split('/');
    if (partes.length >= 2) {
      const secClave = partes[0];
      const pregClave = partes[1];
      const sec = this.seccionesVisuales().find((s) => s.clave === secClave);
      if (sec) {
        const p = sec.preguntas.find(
          (preg) => preg.clave === pregClave || preg.clave.startsWith(pregClave),
        );
        if (p) return p.titulo;
        return pregClave.replace(/_/g, ' ');
      }
    }
    return campoRef.replace(/^\//, '').replace(/\//g, ' › ').replace(/_/g, ' ');
  }
  obtenerNombreSeccion(campoRef: string): string {
    if (!campoRef) return '';
    const partes = campoRef.replace(/^\//, '').split('/');
    if (partes.length >= 1) {
      const secClave = partes[0];
      const sec = this.seccionesVisuales().find((s) => s.clave === secClave);
      if (sec) return this.limpiarTituloSeccion(sec.titulo);
    }
    return '';
  }
  obtenerTituloCampoRef(campoRef: string): string {
    const nom = this.obtenerNombrePregunta(campoRef);
    const sec = this.obtenerNombreSeccion(campoRef);
    return sec ? `${nom} (${sec})` : nom;
  }
  eliminarSeccion(secClave: string): void {
    const lista = this.seccionesVisuales().filter((s) => s.clave !== secClave);
    this.seccionesVisuales.set(lista);
    if (this.seccionVisualActiva() === secClave) {
      this.seccionVisualActiva.set(lista[0]?.clave || '');
    }
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
    if (
      this.estadoReglas().pendiente ||
      this.estadoReglas().guardando ||
      this.estadoCriticas().pendiente ||
      this.estadoCriticas().guardando
    )
      return;
    const ver = this.version();
    if (!ver || ver.estado === 'PUBLICADA') return;
    if (this.modoEdicion() === 'visual') {
      this.sincronizarVisualACodigo();
    }
    if (this.modoVinculos() === 'visual') {
      this.sincronizarVinculosVisualACodigo();
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
      .actualizarBorrador(
        ver.id,
        {
          codigo: ver.codigo,
          esquema: esquemaJson,
          vinculosAlmacenamiento: vinculosJson,
          reglasEvaluacionIc: ver.reglasEvaluacionIc,
        },
        ver.version,
      )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardandoEsquema.set(false)),
      )
      .subscribe({
        next: (actualizada) => {
          this.version.set(actualizada);
          const secs = esquemaASeccionesVisuales(
            actualizada.esquema,
            actualizada.vinculosAlmacenamiento,
          );
          this.seccionesVisuales.set(secs);
          this.vinculosVisuales.set(
            extraerVinculosVisuales(actualizada.vinculosAlmacenamiento, secs),
          );
          this.mensajeExito.set(this.transloco.translate('formulario.schemaSaved'));
          if (actualizada.versionAnteriorId) {
            this.cargarAnalisisMapeos(actualizada.id);
          }
        },
        error: (err) => {
          this.errorValidacion.set(mensajeCalibracion(err, (k) => this.transloco.translate(k)));
        },
      });
  }
  cambiarModoVinculos(modo: 'visual' | 'codigo'): void {
    if (modo === 'visual') {
      try {
        const parsed = this.vinculosTexto() ? JSON.parse(this.vinculosTexto()) : {};
        this.vinculosVisuales.set(extraerVinculosVisuales(parsed, this.seccionesVisuales()));
        this.errorValidacion.set(null);
      } catch {
        this.errorValidacion.set(
          'El JSON de vínculos contiene errores de formato y no puede cambiarse a la vista visual sin corregirlos.',
        );
        return;
      }
    } else {
      this.sincronizarVinculosVisualACodigo();
    }
    this.modoVinculos.set(modo);
  }
  sincronizarVinculosVisualACodigo(): void {
    const ver = this.version();
    let baseRaw: unknown = undefined;
    try {
      baseRaw = this.vinculosTexto()
        ? JSON.parse(this.vinculosTexto())
        : ver?.vinculosAlmacenamiento;
    } catch {
      baseRaw = ver?.vinculosAlmacenamiento;
    }
    const obj = vinculosVisualesAObjeto(this.vinculosVisuales(), baseRaw);
    this.vinculosTexto.set(JSON.stringify(obj, null, 2));
  }
  abrirCrearVinculo(): void {
    this.modoVinculo.set('crear');
    this.vinculoSeleccionadoId.set(null);
    this.destinoPersonalizado.set(false);
    this.columnaPersonalizadaControl.setValue('');
    const primerCampo = this.camposFormularioDisponibles()[0]?.value || '';
    const primerDestino = this.opcionesDestino[0]?.items[0]?.valor || 'puente.codigo';
    this.vinculoForm.reset({
      campoRef: primerCampo,
      columnaDestino: primerDestino,
      tipo: 'Inventario puente',
      descripcion: '',
    });
    this.dialogoVinculo.set(true);
  }
  abrirEditarVinculo(v: VinculoVisual): void {
    this.modoVinculo.set('editar');
    this.vinculoSeleccionadoId.set(v.id);
    const esConocido = this.opcionesDestino.some((g) =>
      g.items.some((item) => item.valor === v.columnaDestino),
    );
    if (esConocido) {
      this.destinoPersonalizado.set(false);
      this.columnaPersonalizadaControl.setValue('');
      this.vinculoForm.reset({
        campoRef: v.campoRef,
        columnaDestino: v.columnaDestino,
        tipo:
          v.tipo ||
          (v.columnaDestino.startsWith('puente.') ? 'Inventario puente' : 'Columna técnica'),
        descripcion: v.descripcion || '',
      });
    } else {
      this.destinoPersonalizado.set(true);
      this.columnaPersonalizadaControl.setValue(v.columnaDestino);
      this.vinculoForm.reset({
        campoRef: v.campoRef,
        columnaDestino: '__custom__',
        tipo: v.tipo || 'Directo',
        descripcion: v.descripcion || '',
      });
    }
    this.dialogoVinculo.set(true);
  }
  guardarVinculoModal(): void {
    if (this.vinculoForm.invalid) {
      this.vinculoForm.markAllAsTouched();
      return;
    }
    const val = this.vinculoForm.getRawValue();
    const campoRef = val.campoRef.trim();
    let columnaDestino = val.columnaDestino.trim();
    if (columnaDestino === '__custom__') {
      columnaDestino = this.columnaPersonalizadaControl.value.trim();
      if (!columnaDestino) {
        return;
      }
    }
    const tipo = val.tipo;
    const descripcion = val.descripcion.trim();
    const lista = [...this.vinculosVisuales()];
    if (this.modoVinculo() === 'crear') {
      lista.push({
        id: `vinc_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
        campoRef,
        columnaDestino,
        tipo,
        descripcion,
      });
    } else {
      const idx = lista.findIndex((v) => v.id === this.vinculoSeleccionadoId());
      if (idx !== -1) {
        lista[idx] = {
          ...lista[idx],
          campoRef,
          columnaDestino,
          tipo,
          descripcion,
        };
      }
    }
    this.vinculosVisuales.set(lista);
    this.sincronizarVinculosVisualACodigo();
    for (const sec of this.seccionesVisuales()) {
      for (const p of sec.preguntas) {
        if (`/${sec.clave}/${p.clave}` === campoRef || `${sec.clave}.${p.clave}` === campoRef) {
          p.vinculo = columnaDestino;
        }
      }
    }
    this.seccionesVisuales.set([...this.seccionesVisuales()]);
    this.dialogoVinculo.set(false);
  }
  eliminarVinculo(id: string): void {
    const encontrado = this.vinculosVisuales().find((v) => v.id === id);
    const lista = this.vinculosVisuales().filter((v) => v.id !== id);
    this.vinculosVisuales.set(lista);
    this.sincronizarVinculosVisualACodigo();
    if (encontrado) {
      for (const sec of this.seccionesVisuales()) {
        for (const p of sec.preguntas) {
          if (
            `/${sec.clave}/${p.clave}` === encontrado.campoRef ||
            `${sec.clave}.${p.clave}` === encontrado.campoRef
          ) {
            p.vinculo = undefined;
          }
        }
      }
      this.seccionesVisuales.set([...this.seccionesVisuales()]);
    }
  }
  guardarVinculos(): void {
    if (
      this.estadoReglas().pendiente ||
      this.estadoReglas().guardando ||
      this.estadoCriticas().pendiente ||
      this.estadoCriticas().guardando
    )
      return;
    const ver = this.version();
    if (!ver || ver.estado === 'PUBLICADA') return;
    if (this.modoVinculos() === 'visual') {
      this.sincronizarVinculosVisualACodigo();
    }
    let vinculosJson: Record<string, unknown>;
    try {
      vinculosJson = this.vinculosTexto() ? JSON.parse(this.vinculosTexto()) : {};
    } catch {
      this.errorValidacion.set('El contenido de vínculos no es un JSON válido.');
      return;
    }
    let esquemaJson: Record<string, unknown>;
    try {
      esquemaJson = JSON.parse(this.esquemaTexto());
    } catch {
      esquemaJson = ver.esquema;
    }
    this.guardandoVinculos.set(true);
    this.errorValidacion.set(null);
    this.mensajeExito.set(null);
    this.formularioApi
      .actualizarBorrador(
        ver.id,
        {
          codigo: ver.codigo,
          esquema: esquemaJson,
          vinculosAlmacenamiento: vinculosJson,
          reglasEvaluacionIc: ver.reglasEvaluacionIc,
        },
        ver.version,
      )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardandoVinculos.set(false)),
      )
      .subscribe({
        next: (actualizada) => {
          this.version.set(actualizada);
          this.vinculosTexto.set(JSON.stringify(actualizada.vinculosAlmacenamiento ?? {}, null, 2));
          this.vinculosVisuales.set(
            extraerVinculosVisuales(actualizada.vinculosAlmacenamiento, this.seccionesVisuales()),
          );
          this.mensajeExito.set(
            this.transloco.translate('formulario.saveBindings') + ' exitosamente.',
          );
        },
        error: (err) => {
          const msg = mensajeCalibracion(err, (k) => this.transloco.translate(k));
          this.errorValidacion.set(msg);
        },
      });
  }
  actualizarPeso(elementoRef: string, nuevoValor: number | null): void {
    if (this.version()?.estado !== 'BORRADOR' || this.guardandoPesos() || this.guardandoEscala())
      return;
    const lista = this.pesosEditables().map((p) => {
      if (p.elementoRef === elementoRef) {
        return { ...p, peso: nuevoValor };
      }
      return p;
    });
    this.pesosEditables.set(lista);
    this.mensajeExito.set(null);
    this.errorValidacion.set(null);
  }
  guardarPesos(): void {
    if (
      this.estadoReglas().pendiente ||
      this.estadoReglas().guardando ||
      this.estadoCriticas().pendiente ||
      this.estadoCriticas().guardando
    )
      return;
    const ver = this.version();
    if (
      !ver ||
      ver.estado !== 'BORRADOR' ||
      this.guardandoPesos() ||
      this.guardandoEscala() ||
      this.publicando()
    )
      return;
    if (!this.pesosEditables().length || this.pesosPendientes().length) {
      this.errorValidacion.set(this.transloco.translate('formulario.calibracion.pesosInvalidos'));
      return;
    }
    this.errorValidacion.set(null);
    this.guardandoPesos.set(true);
    this.mensajeExito.set(null);
    const payload = this.pesosEditables().map((p) => ({
      elementoRef: p.elementoRef,
      peso: p.peso!,
      descripcion: p.descripcion,
    }));
    this.formularioApi
      .calibrarPesos(ver.id, payload, ver.version)
      .pipe(
        switchMap(() => this.formularioApi.obtenerPorId(ver.id)),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardandoPesos.set(false)),
      )
      .subscribe({
        next: (actualizada) => {
          this.version.set(actualizada);
          this.pesosEditables.set(elementosCalibracion(actualizada));
          this.mensajeExito.set(this.transloco.translate('formulario.weightsSaved'));
        },
        error: (err) =>
          this.errorValidacion.set(mensajeCalibracion(err, (k) => this.transloco.translate(k))),
      });
  }
  readonly completandoEquivalencias = signal(false);

  completarEquivalencias(): void {
    if (!this.esAdministrador()) return;
    const version = this.version();
    const analisis = this.analisisMapeo();
    if (
      !version?.versionAnteriorId ||
      version.estado !== 'BORRADOR' ||
      !analisis ||
      this.guardandoMapeos() ||
      this.completandoEquivalencias()
    )
      return;
    const esquemaActual = JSON.stringify(version.esquema);
    this.completandoEquivalencias.set(true);
    this.mensajeExito.set(null);
    this.formularioApi
      .obtenerPorId(version.versionAnteriorId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.completandoEquivalencias.set(false)),
      )
      .subscribe({
        next: (anterior) => {
          if (
            this.version()?.id !== version.id ||
            this.version()?.estado !== 'BORRADOR' ||
            JSON.stringify(this.version()?.esquema) !== esquemaActual
          )
            return;
          const actuales = this.mapeosEditables();
          const completos = completarEquivalencias(
            analisis,
            actuales,
            anterior.esquema,
            version.esquema,
          );
          this.mapeosEditables.set(completos);
          this.mensajeExito.set(
            this.transloco.translate('formulario.equivalencesPrepared', {
              cantidad: completos.length - actuales.length,
            }),
          );
        },
        error: (err) =>
          this.errorValidacion.set(mensajeCalibracion(err, (k) => this.transloco.translate(k))),
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
    if (!this.esAdministrador()) return;
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
        error: (err) =>
          this.errorValidacion.set(mensajeCalibracion(err, (k) => this.transloco.translate(k))),
      });
  }
  actualizarEscala(nivel: NivelSeveridad, valor: number | null): void {
    if (this.version()?.estado !== 'BORRADOR' || this.guardandoEscala() || this.guardandoPesos())
      return;
    this.escalaEditable.update((escala) => ({ ...escala, [nivel]: valor }));
    this.mensajeExito.set(null);
    this.errorValidacion.set(null);
  }
  guardarEscala(): void {
    if (
      this.estadoReglas().pendiente ||
      this.estadoReglas().guardando ||
      this.estadoCriticas().pendiente ||
      this.estadoCriticas().guardando
    )
      return;
    const ver = this.version();
    if (
      !ver ||
      ver.estado !== 'BORRADOR' ||
      this.guardandoEscala() ||
      this.guardandoPesos() ||
      this.publicando()
    )
      return;
    if (!this.escalaCorrecta()) {
      this.errorValidacion.set(this.transloco.translate('formulario.calibracion.escalaInvalida'));
      return;
    }
    const escala = this.escalaEditable();
    this.errorValidacion.set(null);
    this.mensajeExito.set(null);
    this.guardandoEscala.set(true);
    this.formularioApi
      .configurarEscala(
        ver.id,
        {
          valores: {
            NO_PRESENTA: escala.NO_PRESENTA!,
            LEVE: escala.LEVE!,
            MODERADO: escala.MODERADO!,
            SEVERO: escala.SEVERO!,
          },
        },
        ver.version,
      )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardandoEscala.set(false)),
      )
      .subscribe({
        next: (actualizada) => {
          this.version.set(actualizada);
          this.escalaEditable.set(escalaCalibracion(actualizada));
          this.mensajeExito.set(this.transloco.translate('formulario.calibracion.escalaGuardada'));
        },
        error: (err) =>
          this.errorValidacion.set(mensajeCalibracion(err, (k) => this.transloco.translate(k))),
      });
  }
  abrirConfirmarPublicar(): void {
    if (!this.puedePublicar()) return;
    this.dialogoConfirmarPublicar.set(true);
  }
  cerrarConfirmarPublicar(): void {
    this.dialogoConfirmarPublicar.set(false);
  }
  ejecutarPublicacion(): void {
    const ver = this.version();
    if (!ver || !this.puedePublicar()) return;
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
            this.transloco.translate('formulario.publishedSuccess', {
              codigo: pub.codigo,
            }),
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
        error: (err) =>
          this.errorValidacion.set(mensajeCalibracion(err, (k) => this.transloco.translate(k))),
      });
  }
}
