import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { AuthStore } from '../../../../core/services/auth.store';
import { AppShellComponent } from '../../../../layouts/app-shell/app-shell.component';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { PuenteResponse } from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';

@Component({
  selector: 'app-ficha-puente-page',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    TagModule,
    AppShellComponent,
    SelectorUbicacionComponent,
  ],
  templateUrl: './ficha-puente.page.html',
  styleUrl: './ficha-puente.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FichaPuentePage implements OnInit {
  readonly auth = inject(AuthStore);
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(PuenteApiService);

  readonly cargando = signal(true);
  readonly error = signal(false);
  readonly puente = signal<PuenteResponse | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set(true);
      this.cargando.set(false);
      return;
    }

    this.api.obtenerPorId(id).subscribe({
      next: (datos) => {
        this.puente.set(datos);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set(true);
        this.cargando.set(false);
      },
    });
  }

  severidadEstado(estado: string | null | undefined): 'success' | 'warn' | 'danger' | 'secondary' {
    switch (estado) {
      case 'Bueno':
        return 'success';
      case 'Regular':
        return 'warn';
      case 'Malo':
        return 'danger';
      default:
        return 'secondary';
    }
  }
}
