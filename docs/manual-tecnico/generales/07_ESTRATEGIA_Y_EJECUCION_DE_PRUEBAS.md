# Estrategia y ejecución de pruebas

La validación combina pruebas de lógica, contratos y seguridad con pruebas de integración y verificaciones de construcción. La presencia de una prueba no demuestra que se haya ejecutado ni que haya pasado; los resultados deben consultarse en la ejecución correspondiente.

| Alcance | Herramienta y cobertura | Comando habitual |
|---|---|---|
| Backend | JUnit y Spring Test; pruebas de servicios, controladores y seguridad. | `cd sgp-api && mvn test` |
| Integración backend | Testcontainers con PostgreSQL/PostGIS para persistencia y flujos completos. | `cd sgp-api && mvn verify` |
| Estilo backend | Spotless comprueba formato Java. | `cd sgp-api && mvn spotless:check` |
| Cliente | Pruebas unitarias Angular configuradas por el builder de pruebas del proyecto. | `cd sgp-client && npm test` |
| PWA en navegador | Playwright para escenarios de campo, conectividad y sincronización. | `cd sgp-client && npm run test:pwa` |
| Calidad cliente | ESLint, Prettier y compilación Angular. | `npm run lint`, `npm run format:check`, `npm run build` |

Las pruebas de integración de backend necesitan que Docker esté disponible para que Testcontainers levante la base temporal. La suite Playwright usa un servidor de pruebas propio y concentra los escenarios offline/PWA; algunas fases adicionales se habilitan con variables de entorno definidas por la configuración de pruebas.

Para habilitar una fase específica de HU-062, use el indicador correspondiente antes del comando, por ejemplo `HU062_FASE1=true npm run test:pwa`; también están definidos `HU062_FASE2`, `HU062_FASE3` y `HU062_PAQUETES`.

## Verificaciones de CI

El flujo de integración continua ejecuta `mvn -B spotless:check verify` para el backend. Para el cliente instala dependencias desde el archivo de bloqueo y ejecuta lint, formato y build. La configuración del flujo no ejecuta los comandos de pruebas del cliente ni la suite Playwright; deben lanzarse aparte cuando se valide ese alcance.

Al documentar un resultado de prueba, indique el comando, el entorno y su salida real. No infiera aprobación a partir de que el test esté presente o la compilación se haya completado.
