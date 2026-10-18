import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificacionesStore } from '../../services/notificaciones.store';
import { NotificacionResponse } from '../../models/notificacion.models';

@Component({
  selector: 'app-campana-avisos',
  standalone: true,
  imports: [RouterLink, TranslocoPipe, DatePipe, FormsModule],
  templateUrl: './campana-avisos.component.html',
  styleUrl: './campana-avisos.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampanaAvisosComponent {
  readonly store = inject(NotificacionesStore);
  private readonly elementRef = inject(ElementRef);

  readonly abierto = signal<boolean>(false);

  toggle(): void {
    const nuevoEstado = !this.abierto();
    this.abierto.set(nuevoEstado);
    if (nuevoEstado) {
      void this.store.cargarAvisos(0, this.store.soloNoLeidos());
    }
  }

  cerrar(): void {
    this.abierto.set(false);
  }

  onAvisoClick(aviso: NotificacionResponse): void {
    this.cerrar();
    void this.store.marcarLeida(aviso);
  }

  onMarcarTodas(): void {
    void this.store.marcarTodasLeidas();
  }

  onFiltroChange(soloNoLeidos: boolean): void {
    void this.store.cargarAvisos(0, soloNoLeidos);
  }

  @HostListener('document:click', ['$event'])
  onClickAfuera(event: Event): void {
    if (this.abierto() && !this.elementRef.nativeElement.contains(event.target)) {
      this.cerrar();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.abierto()) {
      this.cerrar();
    }
  }
}
