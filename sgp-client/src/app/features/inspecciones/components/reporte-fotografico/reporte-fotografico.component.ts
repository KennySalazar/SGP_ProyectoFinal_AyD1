import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
  untracked,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../../../../core/services/auth.store';
import {
  FotografiasLocalesService,
  FotografiaPendiente,
} from '../../../../offline/services/fotografias-locales.service';
import { uuidV7 } from '../../../../offline/services/uuid-v7';
import { IniciarInspeccionResponse } from '../../models/inspeccion.models';
import {
  CATEGORIAS_FOTO,
  CategoriaFoto,
  FotografiaResponse,
  ImagenPreparada,
  MetadatosFotografia,
  ValidacionEvidenciaResponse,
} from '../../models/fotografia.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { ProcesadorFotografiasService } from '../../services/procesador-fotografias.service';
@Component({
  selector: 'app-reporte-fotografico',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe, ButtonModule, SelectModule, DialogModule],
  templateUrl: './reporte-fotografico.component.html',
  styleUrl: './reporte-fotografico.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReporteFotograficoComponent implements OnDestroy {
  readonly inspeccion = input.required<IniciarInspeccionResponse>();
  readonly contextoElemento = input<{ elementoRef: string; instanciaElementoId?: string } | null>(
    null,
  );
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
  readonly evidencia = signal<ValidacionEvidenciaResponse | null>(null);
  readonly cargando = signal(false);
  readonly accesoDenegado = signal(false);
  readonly edicionBloqueada = signal(false);
  readonly fotoPorRetirar = signal<FotografiaResponse | null>(null);
  readonly errorRetirada = signal<string | null>(null);
  readonly retiradaConfirmada = signal(false);
  readonly puedeModificar = computed(
    () =>
      this.auth.user()?.id === this.inspeccion().autorId &&
      this.inspeccion().estado === 'BORRADOR' &&
      !this.accesoDenegado() &&
      !this.edicionBloqueada(),
  );
  readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private stream: MediaStream | null = null;
  private revision = 0;
  private carga = 0;
  constructor() {
    effect(() => {
      const inspeccion = this.inspeccion();
      const usuario = this.auth.user();
      untracked(() => {
        this.limpiar();
        if (usuario) void this.recargar(usuario.id, inspeccion.id, this.revision);
      });
    });
  }
  private vigente(usuario: string, inspeccion: string, revision: number) {
    return (
      this.auth.user()?.id === usuario &&
      this.inspeccion().id === inspeccion &&
      this.revision === revision
    );
  }
  async actualizar() {
    const usuario = this.auth.user()?.id;
    if (!usuario || this.ocupado() || this.cargando()) return;
    await this.recargar(usuario, this.inspeccion().id, this.revision);
  }
  private async recargar(usuario: string, id: string, revision: number) {
    const carga = ++this.carga;
    const vigente = () => this.vigente(usuario, id, revision) && carga === this.carga;
    this.cargando.set(true);
    this.evidencia.set(null);
    // La cola pertenece al autor; leer el servidor no requiere tomar su contexto local.
    const local = async () => {
      if (usuario !== this.inspeccion().autorId) return;
      try {
        const contexto = this.inspeccion();
        await this.locales.guardarContexto(
          usuario,
          id,
          contexto.puenteId,
          contexto.estado,
          contexto,
        );
        if (!vigente() || this.accesoDenegado()) return;
        const pendientes = await this.locales.pendientes(usuario, id);
        if (vigente() && !this.accesoDenegado()) this.pendientes.set(pendientes);
      } catch {
        if (vigente()) this.error.set('persistencia');
      }
    };
    const remoto = async () => {
      try {
        const [fotos, evidencia] = await Promise.all([
          firstValueFrom(this.api.listarFotografias(id)),
          firstValueFrom(this.api.consultarEvidenciaFotografica(id)),
        ]);
        if (!vigente()) return;
        this.accesoDenegado.set(false);
        this.limpiarConfirmadas();
        this.confirmadas.set(fotos);
        this.evidencia.set(evidencia);
        for (const foto of fotos) {
          try {
            const blob = await firstValueFrom(this.api.contenidoFotografia(id, foto.id, true));
            if (!vigente() || this.accesoDenegado()) return;
            const miniatura = URL.createObjectURL(blob);
            this.confirmadas.update((lista) =>
              lista.map((f) => (f.id === foto.id ? { ...f, miniatura } : f)),
            );
          } catch (e) {
            if (!vigente()) return;
            this.error.set(this.errorApi(e, 'lectura'));
            if (this.accesoDenegado()) return;
          }
        }
      } catch (e) {
        if (vigente()) this.error.set(this.errorApi(e, 'lectura'));
      }
    };
    await Promise.all([local(), remoto()]);
    if (vigente()) this.cargando.set(false);
  }
  solicitarRetirada(foto: FotografiaResponse) {
    if (
      !this.puedeModificar() ||
      this.ocupado() ||
      this.cargando() ||
      !this.confirmadas().some((f) => f.id === foto.id && f.inspeccionId === foto.inspeccionId)
    )
      return;
    this.errorRetirada.set(null);
    this.fotoPorRetirar.set(foto);
  }
  cancelarRetirada() {
    if (this.ocupado()) return;
    this.fotoPorRetirar.set(null);
    this.errorRetirada.set(null);
  }
  async confirmarRetirada() {
    const foto = this.fotoPorRetirar();
    const usuario = this.auth.user()?.id;
    if (!foto || !usuario || !this.puedeModificar() || this.ocupado()) return;
    const revision = this.revision;
    this.ocupado.set(true);
    this.errorRetirada.set(null);
    this.retiradaConfirmada.set(false);
    this.aviso.set(false);
    try {
      // DELETE solo online: una desconexión conserva la fotografía confirmada.
      if (!navigator.onLine) throw { status: 0 };
      await firstValueFrom(this.api.retirarFotografia(this.inspeccion().id, foto.id));
      if (!this.vigente(usuario, foto.inspeccionId, revision)) return;
      this.confirmadas.update((lista) =>
        lista.filter((f) => {
          if (f.id !== foto.id) return true;
          this.revocar(f.miniatura);
          return false;
        }),
      );
      this.fotoPorRetirar.set(null);
      this.retiradaConfirmada.set(true);
      this.error.set(null);
      await this.recargar(usuario, foto.inspeccionId, revision);
    } catch (e) {
      if (revision === this.revision) this.errorRetirada.set(this.errorApi(e, 'retirada'));
    } finally {
      if (revision === this.revision) this.ocupado.set(false);
    }
  }
  puedeReintentar(foto: FotografiaPendiente) {
    if (!this.puedeModificar()) return false;
    if (!foto.ultimoError) return true;
    if (
      ['formato', 'metadatos', 'tamano', 'anclaje', 'conflicto', 'rechazo'].includes(
        foto.ultimoError,
      )
    )
      return false;
    if (foto.ultimoError !== 'limite') return true;
    // Solo la capacidad puede corregirse desde este panel, retirando otra foto.
    return (
      foto.ultimoError === 'limite' &&
      !!this.evidencia() &&
      this.evidencia()!.totalDisponibles < this.evidencia()!.maximoPermitido
    );
  }
  private errorApi(e: unknown, defecto: string): string {
    const fallo = (e ?? {}) as { status?: number; error?: { code?: string } };
    if (fallo.status === 401 || fallo.status === 403 || fallo.status === 404) {
      this.accesoDenegado.set(true);
      this.limpiarConfirmadas();
      this.evidencia.set(null);
      this.pendientes.set([]);
      this.cerrarCamara();
      this.borrador.set(null);
      this.revocar(this.previsualizacion());
      this.previsualizacion.set(null);
      return fallo.status === 401 ? 'sesion' : 'acceso';
    }
    if (fallo.status === 409 && fallo.error?.code === 'estado_inspeccion_no_permite_archivos') {
      this.edicionBloqueada.set(true);
      this.cerrarCamara();
    }
    const errores: Record<string, string> = {
      estado_inspeccion_no_permite_archivos: 'estado',
      limite_fotografias_excedido: 'limite',
      idempotencia_solicitud_distinta: 'conflicto',
      fotografia_identidad_conflictiva: 'conflicto',
      fotografia_metadatos_invalidos: 'metadatos',
      categoria_fotografia_invalida: 'metadatos',
      contenido_fotografico_invalido: 'formato',
      anclaje_fotografico_invalido: 'anclaje',
      carga_demasiado_grande: 'tamano',
    };
    return errores[fallo.error?.code ?? ''] ?? (fallo.status === 422 ? 'rechazo' : defecto);
  }
  async seleccionar(evento: Event) {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo || !this.puedeModificar() || this.ocupado() || this.cargando()) return;
    await this.preparar(() => this.procesador.galeria(archivo));
  }
  async abrirCamara() {
    if (!this.puedeModificar() || this.ocupado() || this.cargando()) return;
    this.error.set(null);
    const revision = this.revision;
    this.ocupado.set(true);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
      if (revision !== this.revision) {
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
    if (!this.puedeModificar() || this.ocupado()) return;
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
      if (revision !== this.revision) return;
      this.revocar(this.previsualizacion());
      this.borrador.set(foto);
      this.previsualizacion.set(URL.createObjectURL(foto.blob));
    } catch (e) {
      if (revision === this.revision)
        this.error.set(
          e instanceof Error && ['tamano', 'formato', 'dimensiones', 'camara'].includes(e.message)
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
      inspeccionId = this.inspeccion().id;
    if (
      !imagen ||
      !categoria ||
      !propietarioId ||
      !this.puedeModificar() ||
      this.cargando() ||
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
      await this.enviar(foto, revision);
    } catch {
      if (revision === this.revision) this.error.set('persistencia');
    } finally {
      if (revision === this.revision) this.ocupado.set(false);
    }
  }
  async reintentar(foto: FotografiaPendiente) {
    if (
      this.ocupado() ||
      this.cargando() ||
      !this.puedeReintentar(foto) ||
      this.auth.user()?.id !== foto.propietarioId ||
      foto.inspeccionId !== this.inspeccion().id
    )
      return;
    const revision = this.revision;
    this.ocupado.set(true);
    try {
      await this.enviar(foto, revision);
    } finally {
      if (revision === this.revision) this.ocupado.set(false);
    }
  }
  private async enviar(foto: FotografiaPendiente, revision: number) {
    this.error.set(null);
    this.aviso.set(false);
    this.retiradaConfirmada.set(false);
    try {
      await firstValueFrom(
        this.api.adjuntarFotografia(
          foto.inspeccionId,
          foto.blob,
          foto.metadatos as MetadatosFotografia,
          foto.idempotencyKey,
        ),
      );
      await this.locales.confirmar(foto.id, foto.propietarioId);
      if (!this.vigente(foto.propietarioId, foto.inspeccionId, revision)) return;
      this.pendientes.update((lista) => lista.filter((f) => f.id !== foto.id));
      this.aviso.set(true);
      await this.recargar(foto.propietarioId, foto.inspeccionId, revision);
    } catch (e) {
      if (!this.vigente(foto.propietarioId, foto.inspeccionId, revision)) return;
      const codigo = this.errorApi(e, 'carga');
      this.error.set(codigo);
      if (codigo === 'limite') this.evidencia.set(null);
      this.pendientes.update((lista) =>
        lista.map((f) => (f.id === foto.id ? { ...f, ultimoError: codigo } : f)),
      );
      try {
        await this.locales.registrarError(foto.id, foto.propietarioId, codigo);
      } catch {
        if (revision === this.revision) this.error.set('persistencia');
      }
    }
  }
  async abrirOriginal(foto: FotografiaResponse) {
    if (this.accesoDenegado() || !this.auth.user()) return;
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
    } catch (e) {
      if (revision === this.revision) this.error.set(this.errorApi(e, 'lectura'));
    }
  }
  etiqueta(categoria: string) {
    return this.i18n.translate('inspecciones.fotos.categorias.' + categoria);
  }
  private revocar(url?: string | null) {
    if (url) URL.revokeObjectURL(url);
  }
  private limpiarConfirmadas() {
    this.confirmadas().forEach((f) => this.revocar(f.miniatura));
    this.confirmadas.set([]);
  }
  private limpiar() {
    this.revision++;
    this.cerrarCamara();
    this.revocar(this.previsualizacion());
    this.limpiarConfirmadas();
    this.evidencia.set(null);
    this.cargando.set(false);
    this.accesoDenegado.set(false);
    this.edicionBloqueada.set(false);
    this.fotoPorRetirar.set(null);
    this.errorRetirada.set(null);
    this.retiradaConfirmada.set(false);
    this.previsualizacion.set(null);
    this.borrador.set(null);
    this.pendientes.set([]);
    this.confirmadas.set([]);
    this.ocupado.set(false);
    this.error.set(null);
    this.aviso.set(false);
  }
  ngOnDestroy() {
    this.limpiar();
  }
}
