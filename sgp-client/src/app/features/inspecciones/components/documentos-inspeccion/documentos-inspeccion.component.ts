import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';
import { DocumentoInspeccionResponse } from '../../models/inspeccion.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';

export const MAXIMO_DOCUMENTOS = 10;
export const MAXIMO_BYTES_DOCUMENTO = 20 * 1024 * 1024;

/**
 * Documentos PDF de una inspección (HU-070): subida, lista y descarga. Solo el autor adjunta o
 * retira, y únicamente en borrador; los PDF requieren conexión (RN-OFF-05).
 */
@Component({
  selector: 'app-documentos-inspeccion',
  standalone: true,
  imports: [DatePipe, TranslocoPipe, ButtonModule, DialogModule],
  templateUrl: './documentos-inspeccion.component.html',
  styleUrl: './documentos-inspeccion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentosInspeccionComponent {
  private readonly api = inject(InspeccionApiService);
  private readonly auth = inject(AuthStore);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly connectivity = inject(ConnectivityService);
  private readonly destroyRef = inject(DestroyRef);

  readonly inspeccion = input.required<{ id: string; autorId: string; estado: string }>();
  readonly soloLectura = input(false);

  readonly maximo = MAXIMO_DOCUMENTOS;
  readonly documentos = signal<DocumentoInspeccionResponse[]>([]);
  readonly cargando = signal(false);
  readonly errorCarga = signal(false);
  readonly subiendo = signal(false);
  readonly error = signal<string | null>(null);
  readonly descargando = signal<string | null>(null);
  readonly documentoARetirar = signal<DocumentoInspeccionResponse | null>(null);
  readonly retirando = signal(false);

  readonly sinConexion = computed(() => !this.connectivity.online());
  readonly editable = computed(
    () =>
      this.auth.user()?.id === this.inspeccion().autorId &&
      this.inspeccion().estado === 'BORRADOR' &&
      !this.soloLectura(),
  );
  readonly limiteAlcanzado = computed(() => this.documentos().length >= MAXIMO_DOCUMENTOS);

  constructor() {
    effect(() => {
      const id = this.inspeccion().id;
      untracked(() => this.cargar(id));
    });
  }

  cargar(id = this.inspeccion().id): void {
    this.cargando.set(true);
    this.errorCarga.set(false);
    this.api
      .listarDocumentos(id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (documentos) => this.documentos.set(documentos),
        error: () => {
          this.apiErrors.clear();
          this.errorCarga.set(true);
        },
      });
  }

  seleccionar(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    entrada.value = '';
    if (!archivo || !this.editable() || this.subiendo() || this.sinConexion()) return;
    this.error.set(null);
    if (this.limiteAlcanzado()) {
      this.error.set('limite');
      return;
    }
    if (archivo.size > MAXIMO_BYTES_DOCUMENTO) {
      this.error.set('tamano');
      return;
    }
    this.subiendo.set(true);
    this.api
      .adjuntarDocumento(this.inspeccion().id, archivo)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.subiendo.set(false)),
      )
      .subscribe({
        next: (documento) => this.documentos.update((lista) => [...lista, documento]),
        error: (err: HttpErrorResponse) => {
          // El error se muestra junto a la subida; se evita duplicarlo en el aviso global.
          this.apiErrors.clear();
          this.error.set(mensajeDe(err));
        },
      });
  }

  descargar(documento: DocumentoInspeccionResponse): void {
    if (this.descargando()) return;
    this.descargando.set(documento.id);
    this.error.set(null);
    this.api
      .descargarDocumento(this.inspeccion().id, documento.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.descargando.set(null)),
      )
      .subscribe({
        next: (contenido) => {
          const url = URL.createObjectURL(contenido);
          const enlace = document.createElement('a');
          enlace.href = url;
          enlace.download = documento.nombreOriginal;
          enlace.click();
          URL.revokeObjectURL(url);
        },
        error: (err: HttpErrorResponse) => {
          this.apiErrors.clear();
          this.error.set(mensajeDe(err));
        },
      });
  }

  confirmarRetiro(documento: DocumentoInspeccionResponse): void {
    this.error.set(null);
    this.documentoARetirar.set(documento);
  }

  cancelarRetiro(): void {
    if (!this.retirando()) this.documentoARetirar.set(null);
  }

  retirar(): void {
    const documento = this.documentoARetirar();
    if (!documento || this.retirando()) return;
    this.retirando.set(true);
    this.api
      .retirarDocumento(this.inspeccion().id, documento.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.retirando.set(false)),
      )
      .subscribe({
        next: () => {
          this.documentos.update((lista) => lista.filter((d) => d.id !== documento.id));
          this.documentoARetirar.set(null);
        },
        error: (err: HttpErrorResponse) => {
          this.apiErrors.clear();
          this.error.set(mensajeDe(err));
        },
      });
  }

  tamano(bytes: number): string {
    return bytes >= 1024 * 1024
      ? `${(bytes / (1024 * 1024)).toFixed(1)} MB`
      : `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
}

/** Mensaje del servidor si lo hay; si no, la clave genérica de i18n. */
function mensajeDe(err: HttpErrorResponse): string {
  const problema = err.error as { detail?: string } | null;
  return problema?.detail ?? 'general';
}
