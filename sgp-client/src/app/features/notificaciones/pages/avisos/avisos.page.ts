import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { NotificacionesStore } from '../../services/notificaciones.store';
import { NotificacionResponse } from '../../models/notificacion.models';

@Component({
  selector: 'app-avisos-page',
  standalone: true,
  imports: [DatePipe, FormsModule, TranslocoPipe, PaginatorModule],
  templateUrl: './avisos.page.html',
  styleUrl: './avisos.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvisosPage implements OnInit {
  readonly store = inject(NotificacionesStore);

  ngOnInit(): void {
    void this.store.cargarAvisos(0, this.store.soloNoLeidos());
  }

  onFiltroChange(soloNoLeidos: boolean): void {
    void this.store.cargarAvisos(0, soloNoLeidos);
  }

  onMarcarTodas(): void {
    void this.store.marcarTodasLeidas();
  }

  onAvisoClick(aviso: NotificacionResponse): void {
    void this.store.marcarLeida(aviso);
  }

  onPageChange(event: PaginatorState): void {
    const page = event.page ?? 0;
    void this.store.cargarAvisos(page, this.store.soloNoLeidos());
  }
}
