import { AuthStore } from '../../../../core/services/auth.store';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { VersionFormularioResumen } from '../../models/formulario.models';
import { FormularioApiService } from '../../services/formulario-api.service';
import { siguienteCodigoVersion } from '../../utils/codigos-formulario';

@Component({
  selector: 'app-versiones-formulario-page',
  standalone: true,
  imports: [
    TranslocoPipe,
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    DialogModule,
    InputTextModule,
    SelectModule,
    TableModule,
    TagModule,
  ],
  templateUrl: './versiones-formulario.page.html',
  styleUrl: './versiones-formulario.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VersionesFormularioPage implements OnInit {
  private readonly formularioApi = inject(FormularioApiService);
  readonly auth = inject(AuthStore);
  readonly esAdministrador = () => this.auth.user()?.role === 'ADMINISTRADOR';
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly apiError = inject(ApiErrorService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly versiones = signal<VersionFormularioResumen[]>([]);
  readonly cargando = signal(false);
  readonly errorCarga = signal<string | null>(null);
  readonly cargandoPlantilla = signal(false);
  readonly creandoVersion = signal(false);
  readonly dialogoNuevaVisible = signal(false);
  readonly mensajeExito = signal<string | null>(null);

  readonly nuevoForm = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(50)]],
    versionBaseId: [null as string | null],
  });

  private readonly formatoFecha = new Intl.DateTimeFormat('es-GT', {
    dateStyle: 'short',
    timeStyle: 'short',
  });

  fechaLegible(fecha: string | null | undefined): string {
    if (!fecha) return '—';
    try {
      return this.formatoFecha.format(new Date(fecha));
    } catch {
      return fecha;
    }
  }

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(null);
    this.formularioApi
      .listarVersiones()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (data) => this.versiones.set(data),
        error: (err) => {
          this.errorCarga.set(this.transloco.translate('formulario.loadFailed'));
          this.apiError.normalize(err);
        },
      });
  }

  cargarPlantillaInicial(): void {
    if (!this.esAdministrador() || this.cargandoPlantilla() || this.cargando()) return;
    const codigos = new Set(this.versiones().map((version) => version.codigo));
    let numero = 1;
    while (codigos.has(`SIECA-v${numero}`)) numero++;
    this.cargandoPlantilla.set(true);
    this.mensajeExito.set(null);
    this.formularioApi
      .cargarPlantillaSieca(`SIECA-v${numero}`)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargandoPlantilla.set(false)),
      )
      .subscribe({
        next: (nueva) => {
          this.mensajeExito.set(
            this.transloco.translate('formulario.templateLoaded', {
              codigo: nueva.codigo,
            }),
          );
          this.router.navigate(['/admin/formularios', nueva.id]);
        },
        error: (err) => this.apiError.normalize(err),
      });
  }

  abrirDialogoNueva(): void {
    const versiones = this.versiones();
    const base =
      versiones.find((v) => v.activa) ?? versiones.find((v) => v.estado === 'PUBLICADA') ?? null;
    this.nuevoForm.reset({
      codigo: siguienteCodigoVersion(versiones.map((v) => v.codigo)),
      versionBaseId: base?.id ?? null,
    });
    this.dialogoNuevaVisible.set(true);
  }

  cerrarDialogoNueva(): void {
    this.dialogoNuevaVisible.set(false);
  }

  crearVersion(): void {
    if (this.creandoVersion()) return;
    if (this.nuevoForm.invalid) {
      this.nuevoForm.markAllAsTouched();
      return;
    }

    const { codigo, versionBaseId } = this.nuevoForm.getRawValue();
    this.creandoVersion.set(true);

    this.formularioApi
      .crear({
        codigo: codigo.trim(),
        versionBaseId: versionBaseId || null,
        ...(!versionBaseId ? { cargarPlantillaInicial: true } : {}),
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.creandoVersion.set(false)),
      )
      .subscribe({
        next: (creada) => {
          this.cerrarDialogoNueva();
          this.router.navigate(['/admin/formularios', creada.id]);
        },
        error: (err) => this.apiError.normalize(err),
      });
  }

  obtenerOpcionesBase() {
    return [
      { label: this.transloco.translate('formulario.noBase'), value: null },
      ...this.versiones().map((v) => ({
        label: `${v.codigo} (${v.estado}${v.activa ? ' · activa' : ''})`,
        value: v.id,
      })),
    ];
  }
}
