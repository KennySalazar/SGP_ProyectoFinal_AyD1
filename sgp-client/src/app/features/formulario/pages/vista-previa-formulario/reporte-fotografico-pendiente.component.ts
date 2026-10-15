import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Marcador del reporte fotográfico (RN-FRM-07). Las fotografías las gestiona el módulo de archivos
 * (HU-028); cuando exista, la pantalla de inspección pasa su componente en lugar de este.
 */
@Component({
  selector: 'app-reporte-fotografico-pendiente',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <p class="pendiente" role="note">
      <i class="pi pi-images" aria-hidden="true"></i>
      {{ 'formulario.vistaPrevia.photoReportPending' | transloco }}
    </p>
  `,
  styles: `
    .pendiente {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin: 0;
      padding: 1.25rem;
      border: 1px dashed var(--sgp-border);
      border-radius: 12px;
      background: var(--sgp-surface-soft);
      color: var(--sgp-steel-700);
    }

    .pi {
      font-size: 1.5rem;
      color: var(--sgp-steel-500);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReporteFotograficoPendienteComponent {}
