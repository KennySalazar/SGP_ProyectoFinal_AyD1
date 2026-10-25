import { StorageService } from '../../../../offline/services/storage.service';
import { SincronizacionService } from '../../../../offline/services/sincronizacion.service';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
  untracked,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { firstValueFrom } from 'rxjs';
import { MarcadorObservacionesComponent } from '../../../formulario/components/observaciones/marcador-observaciones.component';
import { AuthStore } from '../../../../core/services/auth.store';
import {
  FotografiasLocalesService,
  FotografiaPendiente,
} from '../../../../offline/services/fotografias-locales.service';
import { uuidV7 } from '../../../../offline/services/uuid-v7';
import {
  IniciarInspeccionResponse,
  InspeccionBorradorResponse,
  InspeccionPublicadaDetalleResponse,
} from '../../models/inspeccion.models';
import {
  CATEGORIAS_FOTO,
  CategoriaFoto,
  FotografiaResponse,
  ImagenPreparada,
} from '../../models/fotografia.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { ProcesadorFotografiasService } from '../../services/procesador-fotografias.service';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';
@Component({
  selector: 'app-reporte-fotografico',
  standalone: true,
  imports: [
    MarcadorObservacionesComponent,
    ReactiveFormsModule,
    TranslocoPipe,
    ButtonModule,
    SelectModule,
    DialogModule,
  ],
  templateUrl: './reporte-fotografico.component.html',
  styleUrl: './reporte-fotografico.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReporteFotograficoComponent implements OnDestroy {
  readonly revisionSoloLectura = input<{ id: string; autorId: string; puenteId: string } | null>(
    null,
  );
  readonly observacionesCategoria = input<Partial<Record<CategoriaFoto, number>>>({});
  readonly permitirObservarCategoria = input(false);
  readonly observarCategoria = output<CategoriaFoto>();
  readonly abrirObservacionesCategoria = output<void>();
  readonly contexto = computed<{ id: string; autorId: string; puenteId: string; estado?: string }>(
    () => {
      const contexto = this.revisionSoloLectura() ?? this.inspeccion();
      if (!contexto)
        throw new Error(
          'El reporte requiere una inspección o un contexto de revisión de solo lectura',
        );
      return contexto;
    },
  );
  private readonly connectivity = inject(ConnectivityService);
  readonly esOffline = computed(() => !this.connectivity.online());
  readonly inspeccion = input<
    | IniciarInspeccionResponse
    | InspeccionBorradorResponse
    | InspeccionPublicadaDetalleResponse
    | undefined
  >();
  readonly soloLectura = input(false);
  readonly contextoElemento = input<{ elementoRef: string; instanciaElementoId?: string } | null>(
    null,
  );
  readonly storage = inject(StorageService);
  readonly fotoLocalARetirar = signal<FotografiaPendiente | null>(null);
  private readonly sync = inject(SincronizacionService);
  private readonly api = inject(InspeccionApiService);
  private readonly auth = inject(AuthStore);
  private readonly locales = inject(FotografiasLocalesService);
  private readonly procesador = inject(ProcesadorFotografiasService);
  private readonly i18n = inject(TranslocoService);
  readonly categoria = new FormControl<CategoriaFoto | null>(null, Validators.required);
  readonly categorias = [...CATEGORIAS_FOTO];
  readonly borrador = signal<ImagenPreparada | null>(null);
  readonly previsualizacion = signal<string | null>(null);
  readonly pendientes = signal<FotografiaPendiente[]>([]);
  readonly confirmadas = signal<(FotografiaResponse & { miniatura?: string })[]>([]);
  readonly ocupado = signal(false);
  readonly camaraAbierta = signal(false);
  readonly error = signal<string | null>(null);
  readonly aviso = signal(false);
  readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private stream: MediaStream | null = null;
  private revision = 0;
  private contextoActual = '';
  private generacionCarga = 0;
  readonly bloqueada = signal(false);
  readonly inventarioCargado = signal(false);
  readonly retirada = signal(false);
  readonly fotoARetirar = signal<FotografiaResponse | null>(null);
  readonly editable = computed(
    () =>
      this.auth.user()?.id === this.contexto().autorId &&
      this.contexto().estado === 'BORRADOR' &&
      !this.soloLectura() &&
      !this.revisionSoloLectura() &&
      !this.bloqueada(),
  );
  confirmadasPorCategoria(categoria: CategoriaFoto) {
    return this.confirmadas().filter((foto) => foto.categoria === categoria);
  }
  readonly inventario = computed(() => {
    const fotos = new Map(this.confirmadas().map((foto) => [foto.id, foto]));
    for (const foto of this.pendientes())
      if (!fotos.has(foto.id))
        fotos.set(foto.id, {
          id: foto.id,
          categoria: foto.metadatos.categoria,
        } as FotografiaResponse);
    return [...fotos.values()];
  });
  readonly limiteAlcanzado = computed(() => this.inventario().length >= 60);
  readonly conteos = computed(() =>
    CATEGORIAS_FOTO.map((categoria) => ({
      categoria,
      cantidad: this.inventario().filter((foto) => foto.categoria === categoria).length,
    })),
  );
  readonly faltantes = computed(() =>
    this.conteos()
      .filter((conteo) => conteo.categoria !== 'OTRA' && conteo.cantidad === 0)
      .map((conteo) => conteo.categoria),
  );
  constructor() {
    effect((onCleanup) => {
      const inspeccion = this.contexto();
      const usuario = this.auth.user();
      untracked(() => {
        const contexto = `${usuario?.id}:${usuario?.role}:${inspeccion.id}:${inspeccion.autorId}:${inspeccion.estado}`;
        if (contexto === this.contextoActual) return;
        this.contextoActual = contexto;
        this.limpiar();
        if (
          usuario &&
          usuario.id === inspeccion.autorId &&
          inspeccion.estado === 'BORRADOR' &&
          !this.revisionSoloLectura()
        ) {
          const subscription = this.locales
            .observarInventario(usuario.id, inspeccion.id)
            .subscribe((inventario) => {
              if (this.auth.user()?.id !== usuario.id || this.contexto().id !== inspeccion.id)
                return;
              this.pendientes.set(inventario.pendientes);
              const anteriores = new Map(
                this.confirmadas().map((foto) => [foto.id, foto.miniatura]),
              );
              this.confirmadas.set(
                inventario.confirmadas.map((foto) => ({
                  ...foto,
                  miniatura: anteriores.get(foto.id),
                })),
              );
            });
          onCleanup(() => subscription.unsubscribe());
        }
        if (usuario) void this.recargar(usuario.id, inspeccion.id, this.revision);
      });
    });
  }
  private vigente(usuario: string, inspeccion: string, revision: number) {
    return (
      this.auth.user()?.id === usuario &&
      this.contexto().id === inspeccion &&
      this.revision === revision
    );
  }
  private async recargar(usuario: string, id: string, revision: number) {
    const carga = ++this.generacionCarga;
    const vigente = () => this.vigente(usuario, id, revision) && carga === this.generacionCarga;
    const contexto = this.contexto();
    if (
      !this.revisionSoloLectura() &&
      usuario === contexto.autorId &&
      contexto.estado !== undefined &&
      contexto.estado !== 'PUBLICADA'
    ) {
      try {
        await this.locales.guardarContexto(
          usuario,
          id,
          contexto.puenteId,
          contexto.estado,
          contexto,
        );
        if (!vigente()) return;
        const pendientes = await this.locales.pendientes(usuario, id);
        if (!vigente()) return;
        this.pendientes.set(pendientes);
      } catch {
        if (vigente()) this.error.set('persistencia');
      }
    }
    if (!vigente()) return;
    const propias =
      usuario === contexto.autorId && contexto.estado === 'BORRADOR' && !this.revisionSoloLectura();
    const conocidas = propias ? await this.locales.inventario(usuario, id) : [];
    if (!vigente()) return;
    this.confirmadas.set(conocidas);
    this.inventarioCargado.set(true);
    if (!this.connectivity.online()) return;
    try {
      const fotos = await firstValueFrom(this.api.listarFotografias(id));
      if (!vigente()) return;
      this.confirmadas().forEach((foto) => this.revocar(foto.miniatura));
      this.confirmadas.set(fotos);
      if (propias) await this.locales.guardarInventario(usuario, id, fotos);
      this.inventarioCargado.set(true);
      for (const foto of fotos) {
        try {
          const blob = await firstValueFrom(this.api.contenidoFotografia(id, foto.id, true));
          if (!vigente()) return;
          const miniatura = URL.createObjectURL(blob);
          this.confirmadas.update((lista) =>
            lista.map((f) => (f.id === foto.id ? { ...f, miniatura } : f)),
          );
        } catch {
          if (vigente()) this.error.set('lectura');
        }
      }
    } catch {
      if (vigente()) this.error.set('lectura');
    }
  }
  async seleccionar(evento: Event) {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo || this.ocupado() || !this.editable() || this.limiteAlcanzado()) return;
    await this.preparar(() => this.procesador.galeria(archivo));
  }
  async abrirCamara() {
    if (!this.editable() || this.ocupado() || this.limiteAlcanzado()) return;
    this.error.set(null);
    const revision = this.revision;
    this.ocupado.set(true);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
      if (revision !== this.revision || !this.editable()) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }
      this.stream = stream;
      this.camaraAbierta.set(true);
      setTimeout(() => {
        const video = this.video()?.nativeElement;
        if (video && this.stream === stream) {
          video.srcObject = stream;
          void video.play().catch(() => this.error.set('camara'));
        }
      }, 0);
    } catch {
      if (revision === this.revision) this.error.set('camara');
    } finally {
      if (revision === this.revision) this.ocupado.set(false);
    }
  }
  async capturar() {
    if (!this.editable() || this.ocupado() || this.limiteAlcanzado()) return;
    const video = this.video()?.nativeElement;
    if (video) await this.preparar(() => this.procesador.camara(video));
    this.cerrarCamara();
  }
  cerrarCamara() {
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
    this.camaraAbierta.set(false);
  }
  private async preparar(accion: () => Promise<ImagenPreparada>) {
    const revision = this.revision;
    this.ocupado.set(true);
    this.error.set(null);
    this.aviso.set(false);
    this.revocar(this.previsualizacion());
    this.previsualizacion.set(null);
    this.borrador.set(null);
    try {
      const foto = await accion();
      if (revision !== this.revision || !this.editable()) return;
      this.revocar(this.previsualizacion());
      this.borrador.set(foto);
      this.previsualizacion.set(URL.createObjectURL(foto.blob));
    } catch (e) {
      if (revision === this.revision)
        this.error.set(
          e instanceof Error &&
            [
              'tamano',
              'formato',
              'dimensiones',
              'camara',
              'compresion',
              'procesamientoNoDisponible',
            ].includes(e.message)
            ? e.message
            : 'metadatos',
        );
    } finally {
      if (revision === this.revision) this.ocupado.set(false);
    }
  }
  async adjuntar() {
    const imagen = this.borrador(),
      categoria = this.categoria.value,
      propietarioId = this.auth.user()?.id,
      inspeccionId = this.contexto().id;
    if (
      !this.editable() ||
      this.limiteAlcanzado() ||
      !imagen ||
      !categoria ||
      !propietarioId ||
      propietarioId !== this.contexto().autorId ||
      this.ocupado()
    )
      return;
    const id = uuidV7();
    const foto: FotografiaPendiente = {
      id,
      inspeccionId,
      propietarioId,
      idempotencyKey: uuidV7(),
      blob: imagen.blob,
      mime: imagen.blob.type,
      sincronizado: false,
      metadatos: {
        id,
        categoria,
        latitud: imagen.latitud,
        longitud: imagen.longitud,
        capturadoEn: imagen.capturadoEn,
        ...(this.contextoElemento() ?? {}),
      },
    };
    this.ocupado.set(true);
    this.error.set(null);
    const revision = this.revision;
    try {
      await this.locales.guardar(foto);
      if (!this.vigente(propietarioId, inspeccionId, revision)) return;
      this.pendientes.update((lista) => [...lista, foto]);
      this.borrador.set(null);
      this.revocar(this.previsualizacion());
      this.previsualizacion.set(null);
      this.categoria.reset();
      void this.sync.sincronizarAhora();
    } catch (error) {
      if (revision === this.revision)
        this.error.set(
          error instanceof Error &&
            ['limite', 'espacioInsuficiente', 'estado', 'compresion'].includes(error.message)
            ? error.message
            : 'persistencia',
        );
    } finally {
      if (revision === this.revision) this.ocupado.set(false);
    }
  }
  async reintentar(foto: FotografiaPendiente) {
    if (
      !this.editable() ||
      this.auth.user()?.id !== foto.propietarioId ||
      foto.inspeccionId !== this.contexto().id
    )
      return;
    await this.sync.reintentar(foto.id);
  }
  async eliminarLocal() {
    const foto = this.fotoLocalARetirar();
    const propietario = this.auth.user()?.id;
    if (
      !foto ||
      !propietario ||
      !this.editable() ||
      this.ocupado() ||
      foto.inspeccionId !== this.contexto().id
    )
      return;
    const revision = this.revision;
    this.ocupado.set(true);
    this.error.set(null);
    try {
      await this.locales.eliminar(foto.id, propietario);
      if (revision !== this.revision) return;
      this.pendientes.update((fotos) => fotos.filter((item) => item.id !== foto.id));
      this.fotoLocalARetirar.set(null);
    } catch (error) {
      if (revision === this.revision)
        this.error.set(
          error instanceof Error && error.message === 'envioIncierto'
            ? 'envioIncierto'
            : 'retirada',
        );
    } finally {
      if (revision === this.revision) this.ocupado.set(false);
    }
  }
  solicitarRetirada(foto: FotografiaResponse) {
    if (
      !this.editable() ||
      this.ocupado() ||
      foto.inspeccionId !== this.contexto().id ||
      !this.confirmadas().some((confirmada) => confirmada.id === foto.id)
    )
      return;
    this.error.set(null);
    this.fotoARetirar.set(foto);
  }
  cancelarRetirada() {
    if (!this.ocupado()) this.fotoARetirar.set(null);
  }
  async retirar(foto: FotografiaResponse) {
    const usuario = this.auth.user()?.id;
    const id = this.contexto().id;
    if (
      !usuario ||
      !this.editable() ||
      this.ocupado() ||
      foto.inspeccionId !== id ||
      !this.confirmadas().some((confirmada) => confirmada.id === foto.id)
    )
      return;
    const revision = this.revision;
    this.ocupado.set(true);
    this.error.set(null);
    this.aviso.set(false);
    this.retirada.set(false);
    try {
      await firstValueFrom(this.api.retirarFotografia(id, foto.id));
      if (!this.vigente(usuario, id, revision)) return;
      this.generacionCarga++;
      this.confirmadas()
        .filter((f) => f.id === foto.id)
        .forEach((f) => this.revocar(f.miniatura));
      this.confirmadas.update((fotos) => fotos.filter((f) => f.id !== foto.id));
      this.retirada.set(true);
      this.fotoARetirar.set(null);
      await this.recargar(usuario, id, revision);
    } catch (e) {
      if (!this.vigente(usuario, id, revision)) return;
      const codigo = (e as { error?: { code?: string } })?.error?.code;
      if (codigo === 'estado_inspeccion_no_permite_archivos') {
        this.bloqueada.set(true);
        this.error.set('estado');
      } else this.error.set('retirada');
    } finally {
      if (this.vigente(usuario, id, revision)) this.ocupado.set(false);
    }
  }
  async abrirOriginal(foto: FotografiaResponse) {
    const revision = this.revision;
    try {
      const blob = await firstValueFrom(this.api.contenidoFotografia(foto.inspeccionId, foto.id));
      if (revision !== this.revision) return;
      const url = URL.createObjectURL(blob);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = `${foto.id}.jpg`;
      enlace.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      if (revision === this.revision) this.error.set('lectura');
    }
  }
  etiqueta(categoria: string) {
    return this.i18n.translate('inspecciones.fotos.categorias.' + categoria);
  }
  private revocar(url?: string | null) {
    if (url) URL.revokeObjectURL(url);
  }
  private limpiar() {
    this.fotoLocalARetirar.set(null);
    this.revision++;
    this.cerrarCamara();
    this.revocar(this.previsualizacion());
    this.confirmadas().forEach((f) => this.revocar(f.miniatura));
    this.previsualizacion.set(null);
    this.borrador.set(null);
    this.pendientes.set([]);
    this.confirmadas.set([]);
    this.ocupado.set(false);
    this.error.set(null);
    this.aviso.set(false);
    this.retirada.set(false);
    this.fotoARetirar.set(null);
    this.bloqueada.set(false);
    this.inventarioCargado.set(false);
  }
  ngOnDestroy() {
    this.limpiar();
  }
}
