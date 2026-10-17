import { TranslocoTestingModule } from '@jsverse/transloco';
import es from '../../../../../../public/i18n/es.json';

/** Transloco con las traducciones reales de la aplicación, para probar los textos en español. */
export const traduccionesDePrueba = () =>
  TranslocoTestingModule.forRoot({
    langs: { es },
    translocoConfig: { availableLangs: ['es'], defaultLang: 'es' },
    preloadLangs: true,
  });
