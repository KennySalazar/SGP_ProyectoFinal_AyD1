# ADR-0008: pruebas E2E de red degradada con Playwright (HU-062)

Fecha: 24 de octubre de 2026.

## Estado y autoridad

**Propuesto para ratificación formal; implementado en el harness de HU-062.** Las instrucciones de implementación de las fases autorizan expresamente reutilizar Playwright. La revisión QA posterior localizó la HU-062 y la decisión 6 en `.context/sprints/sprint_4_SGP.md`: Cypress es una recomendación de partida, expresamente «no como mandato»; el responsable elige la herramienta y registra su decisión en un ADR. DT-CAL-04 permite Playwright o Cypress. Localizar esta fuente aclara el requisito, pero no constituye ratificación del responsable.

Este ADR complementa el [ADR-0007](0007-hu061-publicacion-offline-y-cache-privada.md), aceptado para HU-061, sin modificarlo ni cambiar sincronización, persistencia o contratos de negocio.

## Contexto

El repositorio ya tiene `@playwright/test`, configuración, navegador PWA, pruebas HU-061 y un proxy que sirve el build Angular de producción y puede reenviar `/api/` a Spring Boot. El fixture real prepara usuarios, referencias y formulario en PostGIS desechable. Cypress conserva una configuración y una prueba histórica de revisión, sin ejecutor declarado en el manifiesto actual. Activar otro framework añadiría mantenimiento sin cubrir por sí mismo la pérdida de paquetes IP/TCP.

## Decisión técnica

1. Conservar **Playwright como único ejecutor E2E habilitado** para HU-062. Reutilizar login, preparación offline por UI, IndexedDB real, worker y oráculos SQL/API/archivos de HU-061 y fases previas. No instalar otro framework ni duplicar sus pruebas.
2. Ampliar únicamente `sgp-client/e2e/hu061/server.mjs`: perfiles por UUID, método, clave y ordinal. La desconexión usa `BrowserContext.setOffline`; latencia aplica temporizadores de 1500 ms antes de reenviar y 1500 ms antes de entregar. Barreras explícitas permiten observar pendientes sin adivinar el fin con sleeps.
3. Simular fallos de comunicación con entrega JSON incompleta, antes de reenviar una creación o después de su commit real. Una respuesta sintácticamente incompleta fuerza error de transporte observable; las verificaciones remotas se realizan contra Spring Boot/PostGIS, no contra el estado del simulador.
4. Para fotografías, transmitir encabezados/metadatos y la mitad de la parte binaria manteniendo la longitud total original. Confirmar con `ss -tinH` que el socket del backend recibió el segmento antes de cortar upstream. Recuperar con el envío completo y el circuito multipart idempotente existente. No introducir fragmentación ni nuevos protocolos.
5. Probar también la pérdida de confirmación fotográfica: Spring Boot almacena y devuelve 201, el proxy corta la respuesta y el cliente conserva Blob/clave. El reintento mantiene ID, contenido y metadatos; exigir un solo archivo/vínculo/registro idempotente y respuesta idéntica. No exigir al multipart una cabecera de replay que su controlador no expone.
6. Mantener imágenes/DB/archivos de prueba aislados. El runner existente usa PostGIS en tmpfs y un proceso Java exclusivo con almacenamiento y temporales propios; limpieza por ID creado. Las APIs de control del proxy son locales y no se incorporan a producción.
7. Conservar capturas de fallo, trazas, logs y adjuntos. Verificarlos con una aserción deliberadamente fallida tras completar el oráculo real, y volver a ejecutar con esa bandera desactivada. Los informes no contienen credenciales ni tokens; las trazas de sesiones se conservan como artefactos privados.

## Pérdida literal de paquetes y límites

Un error HTTP, la latencia o el corte de un flujo no acreditan pérdida de paquetes IP/TCP. El parámetro CDP `packetLoss` localizado describe WebRTC, no certifica este REST. Para acreditar descartes reales se requiere un mecanismo como `tc/netem`, en el namespace desechable del backend, con filtro acotado, contadores y restauración comprobada.

La [revisión QA de pendientes](../../temporalHu062/QA_PENDIENTES_HU062_RESULTADOS.md) resolvió el bloqueo histórico de la [fase 2](../../temporalHu062/FASE_2_HU062_RESULTADOS.md): dos casos con contadores de descartes, snapshots locales, restauración independiente y oráculos remotos completos pasaron. El modo `paquetes` del runner utiliza backend/PostGIS en red desechable y un operador con solo `NET_ADMIN` en la red del backend, sin capacidades adicionales en Java. Monta herramientas locales y el JAR actual de solo lectura, registra su hash y limpia los IDs creados. No aplica qdisc a interfaces del anfitrión. Un watchdog externo a Playwright retira la pérdida a los nueve segundos, antes del tope de diez segundos del perfil P.

La elección actual reduce cambios y permite verificar los perfiles implementados con un solo ejecutor. Sus resultados quedan acotados a Chromium, backend real y proveedor local; no certifican S3/staging, otras redes o sincronización con la aplicación cerrada. La autonomía manual de ocho horas se mantiene fuera de la automatización.

## Ejecución y consecuencias

Procedimiento único en [docs/testing/HU-062.md](../testing/HU-062.md), con requisitos, comandos, evidencias y matriz actualizada. La ejecución manual reproducible utiliza el script existente; no se modifica CI. La ratificación del responsable sobre este ADR sigue pendiente; los resultados técnicos no sustituyen esa decisión.
