import { generateMessages } from '@cucumber/gherkin';
import { IdGenerator, SourceMediaType } from '@cucumber/messages';
import { describe, it } from 'vitest';

export interface PasoGherkin {
  patron: RegExp;
  ejecutar: (...argumentos: string[]) => unknown | Promise<unknown>;
}

/** Compila Gherkin oficial (incluidos ejemplos y antecedentes) y ejecuta cada escenario en Vitest. */
export function ejecutarGherkin(source: string, uri: string, pasos: PasoGherkin[]) {
  const mensajes = generateMessages(source, uri, SourceMediaType.TEXT_X_CUCUMBER_GHERKIN_PLAIN, {
    newId: IdGenerator.incrementing(),
    includeGherkinDocument: true,
    includePickles: true,
  });
  const errores = mensajes.flatMap((m) => (m.parseError ? [m.parseError.message] : []));
  if (errores.length) throw new Error(errores.join('\n'));
  const escenarios = mensajes.flatMap((m) => (m.pickle ? [m.pickle] : []));
  if (!escenarios.length) throw new Error(`Sin escenarios: ${uri}`);
  describe(uri, () => {
    for (const escenario of escenarios) {
      it(escenario.name, async () => {
        for (const paso of escenario.steps) {
          if (paso.argument) throw new Error(`Argumento sin soporte: ${paso.text}`);
          const coincidencias = pasos.flatMap((definicion) => {
            const match = paso.text.match(definicion.patron);
            return match ? [{ definicion, argumentos: match.slice(1) }] : [];
          });
          if (coincidencias.length !== 1) {
            throw new Error(`Se esperaba exactamente una definición para: ${paso.text}`);
          }
          try {
            await coincidencias[0].definicion.ejecutar(...coincidencias[0].argumentos);
          } catch (error) {
            throw new Error(`Falló el paso: ${paso.text}`, { cause: error });
          }
        }
      });
    }
  });
}
