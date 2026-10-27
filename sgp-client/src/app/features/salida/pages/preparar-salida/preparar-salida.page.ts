import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../../../../core/services/auth.store';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';
import { PuenteApiService } from '../../../puentes/services/puente-api.service';
import { DepartamentoResponse } from '../../../puentes/models/puente.models';
import { EstimacionSalida, PuenteSalida } from '../../models/salida.models';
import { SalidaApiService } from '../../services/salida-api.service';
import { PreparacionSalidaService } from '../../services/preparacion-salida.service';

@Component({
  selector: 'app-preparar-salida',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe],
  templateUrl: './preparar-salida.page.html',
  styleUrl: './preparar-salida.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrepararSalidaPage {
  readonly auth = inject(AuthStore);
  readonly conexion = inject(ConnectivityService);
  readonly salida = inject(PreparacionSalidaService);
  private readonly api = inject(SalidaApiService);
  private readonly territorio = inject(PuenteApiService);
  readonly estudiante = computed(() => this.auth.user()?.role === 'ESTUDIANTE');
  readonly puentes = signal<PuenteSalida[]>([]);
  readonly departamentos = signal<DepartamentoResponse[]>([]);
  readonly seleccion = signal<string[]>([]);
  readonly total = signal(0);
  readonly paginas = signal(0);
  readonly pagina = signal(0);
  readonly cargando = signal(false);
  readonly estimando = signal(false);
  readonly estimacion = signal<EstimacionSalida | null>(null);
  readonly mensaje = signal('');
  readonly confirmarLimpieza = signal(false);
  readonly listo = signal(false);
  busqueda = '';
  departamento = '';
  private turno = 0;
  private turnoEstimacion = 0;
  readonly bloqueada = computed(
    () => !this.conexion.online() || this.salida.ocupada() || !this.salida.permitida(),
  );
  readonly cantidad = computed(() => (this.estudiante() ? this.total() : this.seleccion().length));
  readonly pocoEspacio = computed(() => {
    const espacio = this.salida.espacio();
    return (
      espacio !== null &&
      espacio < Math.max(50 * 1024 * 1024, (this.estimacion()?.tamanoEstimadoBytes ?? 0) * 2)
    );
  });

  constructor() {
    effect(() => {
      const usuario = this.auth.user()?.id;

      const permitida = this.salida.permitida();
      this.turno++;
      this.turnoEstimacion++;
      this.estimando.set(false);
      this.busqueda = '';
      this.departamento = '';
      this.pagina.set(0);
      this.paginas.set(0);
      this.puentes.set([]);
      this.seleccion.set([]);
      this.departamentos.set([]);
      this.estimacion.set(null);
      this.total.set(0);
      this.listo.set(false);
      this.confirmarLimpieza.set(false);
      this.mensaje.set('');
      if (!usuario || !permitida) this.cargando.set(false);
    });
    effect(() => {
      const usuario = this.auth.user()?.id;
      const enLinea = this.conexion.online();
      const permitida = this.salida.permitida();
      untracked(() => {
        if (usuario && enLinea && permitida) {
          void this.buscar();
          void this.cargarDepartamentos(usuario);
        } else {
          this.turno++;
          this.cargando.set(false);
        }
      });
    });
  }

  private async cargarDepartamentos(usuario: string): Promise<void> {
    try {
      const d = await firstValueFrom(this.territorio.listarDepartamentos());
      if (usuario === this.auth.user()?.id) this.departamentos.set(d.content);
    } catch {
      if (usuario === this.auth.user()?.id)
        this.mensaje.set(
          'No se pudo cargar el filtro por departamento. Puedes buscar por nombre o codigo.',
        );
    }
  }

  async buscar(pagina = 0): Promise<void> {
    if (this.bloqueada()) return;
    const turno = ++this.turno;
    const usuario = this.auth.user()?.id;
    this.cargando.set(true);
    this.mensaje.set('');
    try {
      const datos = await firstValueFrom(
        this.api.buscar(this.busqueda.trim(), this.departamento, pagina),
      );
      if (turno !== this.turno || usuario !== this.auth.user()?.id) return;
      this.puentes.set(datos.content);
      this.total.set(datos.totalElements);
      this.paginas.set(datos.totalPages);
      this.pagina.set(pagina);
    } catch {
      if (turno === this.turno && usuario === this.auth.user()?.id)
        this.mensaje.set('No se pudieron consultar los puentes. Revisa la conexion y reintenta.');
    } finally {
      if (turno === this.turno) this.cargando.set(false);
    }
  }

  elegir(id: string, marcado: boolean): void {
    if (this.bloqueada()) return;
    if (marcado && !this.seleccion().includes(id) && this.seleccion().length >= 200) {
      this.mensaje.set('Puedes seleccionar hasta 200 puentes por salida.');
      return;
    }
    this.seleccion.update((ids) =>
      marcado ? [...new Set([...ids, id])] : ids.filter((i) => i !== id),
    );
    this.turnoEstimacion++;
    this.estimando.set(false);
    this.estimacion.set(null);
    this.listo.set(false);
    this.mensaje.set('');
  }
  limpiarSeleccion(): void {
    if (this.bloqueada()) return;
    this.seleccion.set([]);
    this.turnoEstimacion++;
    this.estimando.set(false);
    this.estimacion.set(null);
  }

  async estimar(): Promise<void> {
    if (this.bloqueada() || (!this.estudiante() && !this.seleccion().length)) return;
    const turno = ++this.turnoEstimacion;
    const usuario = this.auth.user()?.id;
    this.estimando.set(true);
    this.mensaje.set('');
    try {
      const datos = await firstValueFrom(
        this.api.estimar(this.salida.cuerpo(this.estudiante() ? [] : this.seleccion())),
      );
      if (turno === this.turnoEstimacion && usuario === this.auth.user()?.id)
        this.estimacion.set(datos);
    } catch {
      if (turno === this.turnoEstimacion && usuario === this.auth.user()?.id)
        this.mensaje.set(
          'No se pudo estimar la salida. Comprueba que exista un formulario activo y que tus puentes sigan autorizados.',
        );
    } finally {
      if (turno === this.turnoEstimacion) this.estimando.set(false);
    }
  }

  async preparar(): Promise<void> {
    if (this.bloqueada() || !this.estimacion()?.numeroPuentes) return;
    this.listo.set(false);
    const usuario = this.auth.user()?.id;
    const guardada = await this.salida.preparar(this.estudiante() ? [] : this.seleccion());
    if (usuario === this.auth.user()?.id) this.listo.set(guardada);
  }

  async quitar(): Promise<void> {
    this.confirmarLimpieza.set(false);
    this.listo.set(false);
    await this.salida.quitar();
  }
}
