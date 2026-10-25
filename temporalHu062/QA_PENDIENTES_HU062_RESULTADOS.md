# HU-062 — QA de criterios parciales y bloqueados

Fecha local: 24 de octubre de 2026, America/Guatemala. Base revisada: `215ca29`, rama `test/hu-062-red-degradada`.

## Dictamen

Se cerró el bloqueo técnico de pérdida literal de paquetes: **2/2 casos aprobados**, más **1/1 regresión del proxy aprobada**, sin omitidas, flaky ni reintentos de Playwright. CA4, CA6, CA7 y CA8 pasan a CUMPLE. CA10 conserva pendiente la ratificación formal del responsable sobre Playwright/ADR-0008. No se declara aceptación integral ni autorización de merge.

No se repitieron las suites completas de fases 1–3: se conservan sus resultados históricos y se ejecutó la regresión pertinente al proxy compartido. No se modificó código de producción, dependencias, migraciones ni CI.

## Fuente de aceptación localizada

La HU-062 y la decisión 6 están en `.context/sprints/sprint_4_SGP.md`, apartados «HU-062 — Pruebas E2E con red degradada» y «Decisiones que toma el responsable de cada bloque». La HU exige latencia, pérdida de paquetes y corte de subida; conservación, ausencia de duplicados y sincronización posterior; elección documentada de herramienta y resultados en el manual técnico.

El sprint indica expresamente que sus recomendaciones son de partida, «no como mandato»: Cypress es la recomendación de la decisión 6, no una obligación ya ratificada. DT-CAL-04 del enunciado permite Playwright o Cypress. Esto corrige la afirmación anterior de que la fuente no estaba disponible. No acredita por sí solo la aprobación formal del ADR-0008; se solicitó al responsable esa decisión y su identificación, sin inventarla.

## Ejecución y aislamiento

Comando ejecutado desde `sgp-client`, con Node 24.22.1, Java 21.0.12.1 y Maven 3.9.16 seleccionados en PATH:

```bash
bash e2e/hu061/run-hu062-fase1.sh paquetes
```

Playwright 1.64.1 y Chromium local. El runtime Node 24 se descargó temporalmente de nodejs.org y se verificó con su SHA-256 oficial; no cambió el manifiesto del proyecto. Maven empaquetó con `-o -DskipTests package`: no se atribuye ejecución de pruebas unitarias Java a ese comando. El frontend se construyó en producción. Los logs y adjuntos reales están en `/tmp/hu062-paquetes.DKAaNH`.

Se utilizaron PostGIS tmpfs y backend nuevos, red Docker exclusiva etiquetada, fixture `hu061_aceptacion` y almacenamiento exclusivo de objetos/temporales. Se reutilizó la imagen backend local por ID, montando el JAR recién empaquetado de solo lectura; `imagen.txt` y `jar.sha256` registran las identidades. No se cargó `.env` ni se usaron bases o volúmenes habituales. El SQL del catálogo local se revisó y no se ejecutó.

Un contenedor operador con únicamente NET_ADMIN compartió solo el namespace de red del backend desechable; Java no recibió capacidades. Se comprobaron etiquetas, redes, PID, igualdad del namespace entre backend/operador y diferencia con el del anfitrión antes de inyectar. La disponibilidad de netem se probó previamente en un contenedor sin red externa, eliminado al terminar. Las ejecuciones privilegiadas se autorizaron mediante las aprobaciones del entorno.

El runner copia `tc`, su cargador y bibliotecas locales en una carpeta temporal montada de solo lectura. El operador usa la red del backend directamente; no entra al namespace del host ni necesita SYS_ADMIN, `nsenter` o `--privileged`. Docker administra su bridge habitual; no se configuraron qdisc de interfaces del anfitrión.

## Perfil P y resultados

Cada caso crea mediante la UI una inspección offline con respuestas `PRESENTE/MODERADO/NO`, cuatro fotos/categorías y publicación pendiente. Los contextos de navegador, UUID, claves y preparación son independientes; se reutiliza el entorno desechable entre ambos casos y se verifica un incremento exacto de una inspección por caso.

El proxy retiene únicamente la primera creación seleccionada. Se instala `prio 620:` en `eth0`, con tráfico predeterminado por banda no degradada. El filtro IPv4/TCP selecciona puerto de origen 8090 y destino gateway de esa red, enviando esas respuestas a `netem 621: loss 100%`. El flujo backend→PostGIS no coincide. Se libera la creación y se exige `drops > 0`; no se simula esa pérdida con errores HTTP.

Un proceso del operador, independiente de Playwright, espera nueve segundos y guarda contadores, retira únicamente la qdisc creada y registra el estado restaurado. El test comprueba además la desaparición de los handles y el retorno a `noqueue`; su `finally` retira anticipadamente la qdisc si falla. La destrucción final del backend elimina también su namespace.

| Evidencia | Corrida 1 | Corrida 2 |
| --- | --- | --- |
| Inspección | `01a17135-2a0d-7e59-bf73-9de946ffe759` | `01a17135-84d9-770c-9ae8-7ea5e9d71b1e` |
| Descartes al tomar el snapshot | 2 | 3 |
| Descartes acumulados antes de retirar netem | **36** | **33** |
| Fotos locales durante la pérdida | 4, Blobs/SHA-256/metadatos intactos | 4, Blobs/SHA-256/metadatos intactos |
| Operaciones locales durante la pérdida | 11, identidades y claves conservadas | 11, identidades y claves conservadas |
| Resultado local | PUBLICADA / CONFIRMADA, cola vacía | PUBLICADA / CONFIRMADA, cola vacía |
| Resultado remoto del caso | 1 inspección añadida, 4 fotos, 2 transiciones, 1 IC | 1 inspección añadida, 4 fotos, 2 transiciones, 1 IC |
| Restauración | `noqueue`, sin handles de prueba | `noqueue`, sin handles de prueba |
| Duración del recorrido completo | 24,6 s | 22,1 s |

Ambos verificaron por SQL/API/disco cuatro fotografías DISPONIBLES, IDs y categorías correctos, cuatro originales y cuatro miniaturas decodificables, igualdad entre descarga y disco, tamaños/checksums del JPEG normalizado y exactamente ocho objetos nuevos. No quedaron objetos adicionales ni temporales fotográficos/multipart. El replay de publicación devolvió la misma respuesta sin modificar conteos. El acumulado remoto de inspecciones fue 1 y 2 respectivamente; cada caso añadió una, sin duplicación.

Se compararon las cuatro fotos locales completas, respuestas, IDs y claves durante la pérdida, antes del acuse. TCP recuperó al restaurar la red; no se exige un nuevo intento HTTP cuando basta su retransmisión.

`paquetes.json` registra **2 aprobadas en 47,6 s**, cero omitidas, inesperadas o flaky. `regresion-proxy.json` registra **1 aprobada en 11,3 s**: confirmación de creación perdida después de commit, reintento con la misma identidad y replay seguro. No se suman estas ejecuciones a los totales históricos como si fueran pruebas diferentes.

## Incidencia del harness y limpieza

La primera ejecución (`/tmp/hu062-paquetes.JylkAK`) detectó un defecto del harness: el operador con capacidades reducidas no podía escribir la evidencia en el directorio privado, por su grupo/permisos. Los dos casos fallaron al esperar ese archivo; no se cuentan como aprobados ni se atribuyen a producción. Se corrigió el grupo del operador y se habilitó escritura al grupo propietario solo en el directorio temporal. La nueva ejecución completa pasó. No se relajaron aserciones de pérdida, conservación, integridad o restauración.

La ejecución final terminó con código 0. `limpieza.log` registra la retirada de operador, backend y PostGIS por los IDs creados y de su red; se eliminaron los directorios exclusivos de objetos y temporales. Se conservan logs y evidencia privada. Las trazas de la ejecución fallida pueden contener sesiones y no se versionan.

También pasaron lint, formato del frontend, Prettier de los módulos E2E modificados, sintaxis Node/Bash y `git diff --check`. No hubo cambios Java que requirieran una nueva comprobación Spotless.

## Matriz vigente

| CA | Estado | Evidencia o condición pendiente |
| --- | --- | --- |
| CA1 — Red normal | CUMPLE | Evidencia histórica de fases 1 y 3 conservada. |
| CA2 — Sin conexión | CUMPLE | Evidencia histórica de fases 1 y 3 conservada. |
| CA3 — Latencia alta | CUMPLE | Evidencia histórica de fases 2 y 3 conservada. |
| CA4 — Pérdida/fallos | **CUMPLE** | Dos casos con descartes reales, retirada independiente y recuperación; regresión de confirmación perdida aprobada. |
| CA5 — Corte fotográfico | CUMPLE | Evidencia histórica de fase 3 conservada; no se modifica su mecanismo. |
| CA6 — Pendientes | **CUMPLE** | Durante los descartes se conservan respuestas, cuatro fotos completas, metadatos, IDs y claves; después cola vacía. |
| CA7 — Servidor | **CUMPLE** | Oráculo SQL/API/disco completo tras descartes, archivos íntegros y replay sin duplicados en ambas corridas. |
| CA8 — Reproducibilidad | **CUMPLE** | Modo `paquetes`, parámetros fijos, dos preparaciones independientes, contadores, watchdog, restauración y limpieza probados. |
| CA9 — Evidencias | CUMPLE | Evidencia histórica de fase 3 conservada; se agregan contadores/snapshots/reportes de paquetes. |
| CA10 — Herramienta/ejecución | **BLOQUEADO documentalmente** | Fuente y recomendación localizadas, ejecución manual probada y ADR actualizado. Falta ratificación trazable del responsable sobre Playwright/ADR-0008. |
| CA11 — Documentación | CUMPLE | Guía única, ADR y matriz actualizados con resultado efectivo y límites. |

Resultado vigente: **10 CUMPLE, 0 PARCIAL, 1 BLOQUEADO documentalmente**. No quedan pruebas técnicas pendientes de los criterios solicitados. Autonomía de ocho horas, otros navegadores, S3 y staging conservan el alcance previamente documentado.
