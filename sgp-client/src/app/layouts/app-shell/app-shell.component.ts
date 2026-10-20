import { AvisoSalidaComponent } from '../../features/salida/components/aviso-salida.component';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthStore } from '../../core/services/auth.store';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { RevisionApiService } from '../../features/revision/services/revision-api.service';
import { CampanaAvisosComponent } from '../../features/notificaciones/components/campana-avisos/campana-avisos.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    AvisoSalidaComponent,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    TranslocoPipe,
    CampanaAvisosComponent,
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  readonly auth = inject(AuthStore);
  readonly puedePrepararSalida = () => {
    const usuario = this.auth.user();
    return (
      !!usuario &&
      usuario.active &&
      usuario.activated &&
      usuario.verified &&
      ['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO'].includes(usuario.role) &&
      (usuario.role !== 'PROFESIONAL_EXTERNO' || usuario.colegiadoVerificado === true)
    );
  };
  readonly connectivity = inject(ConnectivityService);
  readonly revisiones = inject(RevisionApiService);
  private readonly router = inject(Router);
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
      map((evento) => evento.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Cuentas anteriores a que el perfil fuera obligatorio: solo se les avisa, no se les bloquea. */
  readonly perfilIncompleto = computed(() => {
    const usuario = this.auth.user();
    return !!usuario && (!usuario.nombreUsuario || !usuario.nombreCompleto);
  });
  readonly avisarPerfil = computed(
    () => this.perfilIncompleto() && !this.url().startsWith('/perfil'),
  );
  /** Lo que identifica a la persona en el panel lateral: su nombre, o su correo si no lo tiene. */
  readonly nombreVisible = computed(() => {
    const usuario = this.auth.user();
    return usuario?.nombreCompleto || usuario?.email || '';
  });

  logout(): void {
    this.auth
      .logout()
      .subscribe({ complete: () => this.finishLogout(), error: () => this.finishLogout() });
  }
  private finishLogout(): void {
    this.auth.clear();
    void this.router.navigate(['/login']);
  }
}
