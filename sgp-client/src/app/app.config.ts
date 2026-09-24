import { APP_INITIALIZER, ApplicationConfig, inject, isDevMode } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { offlineInterceptor } from './core/interceptors/offline.interceptor';
import { problemInterceptor } from './core/interceptors/problem.interceptor';
import { SessionBootstrapService } from './core/services/session-bootstrap.service';
import { SgpPreset } from './theme/sgp-preset';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([offlineInterceptor, authInterceptor, problemInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({
      ripple: true,
      theme: {
        preset: SgpPreset,
        options: {
          darkModeSelector: '.sgp-dark',
          cssLayer: false
        }
      }
    }),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    }),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const bootstrap = inject(SessionBootstrapService);
        return () => firstValueFrom(bootstrap.initialize());
      }
    }
  ]
};
