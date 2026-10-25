# HU-062 — Evaluación de bloqueos y cierre

Fecha de referencia: 24 de octubre de 2026, America/Guatemala.

## 1. Dictamen y evidencia utilizada

**HU-062 todavía no está apta para merge como historia completamente aceptada.** La solución mínima es ejecutar únicamente el perfil de pérdida literal pendiente en un entorno autorizado, reutilizando el harness, y obtener la decisión documental del responsable sobre Playwright/ADR-0008. No corresponde volver a implementar las fases ni cambiar a Cypress.

Fuentes consultadas: [AGENTS.md](AGENTS.md), [README](README.md), [arquitectura](docs/ARCHITECTURE.md), [plan](PLAN_IMPLEMENTACION_HU062.md), resultados de [fase 1](FASE_1_HU062_RESULTADOS.md), [fase 2](FASE_2_HU062_RESULTADOS.md), [fase 3](FASE_3_HU062_RESULTADOS.md), [ADR-0008](docs/adr/0008-hu062-pruebas-e2e-red-degradada.md) y [guía HU-062](docs/testing/HU-062.md). Se revisaron también Compose, Dockerfile, runner, configuración Playwright y manifiesto existentes.

Se conserva la evidencia registrada: fase 1 **7/7**, fase 2 **6/6 ejecuciones nuevas y 7/7 regresiones**, fase 3 **12/12 funcionales**, además del fallo deliberado verificado y restaurado. No son totales sumables de pruebas diferentes: incluyen regresiones repetidas. Esta evaluación **no ejecutó suites ni renovó esos resultados**.

El corte de imagen de fase 3 acreditó 385/771 bytes binarios y recepción TCP del prefijo por el backend; acredita subida interrumpida, **no descartes de paquetes**. Ninguna fase contiene contadores de netem que permitan cerrar ese requisito.

## 2. Verificaciones actuales de solo lectura

| Aspecto | Observación comprobada | Implicación |
|---|---|---|
| Docker | Contexto `default`, Engine 29.7.2/API 1.55; Ubuntu 24.04.4 LTS; AppArmor, seccomp y cgroupns declarados. | Hay Docker operativo, pero sus controles no prueban autorización para netem. |
| Servicios habituales | Cuatro contenedores SGP en `sgp_proyectofinal_ayd1_default`; `legal_backend` y `legal_db` en `backend_default`. | No usar esas redes, contenedores, bases ni volúmenes. |
| Backend habitual | `Privileged=false`, `CapAdd=null`, usuario `sgp`, red Compose propia. PostgreSQL también sin capacidades adicionales declaradas. | No se cambia ni se utiliza para degradación. |
| Imagen reutilizable | Backend local `sgp_proyectofinal_ayd1-backend:latest`, ID `sha256:c85ac29d775d7b24cd124640ae970ac03de1343b59501888782fa8b0525195a4`, entrada `java -jar /app/app.jar`; PostGIS `postgis/postgis:16-3.5` local. | Se podría reutilizar el runtime con el JAR actual montado de solo lectura; la etiqueta local no prueba que su JAR incorporado corresponda al cambio revisado. No se descargó ni construyó una imagen. |
| Herramientas del sistema | `tc` iproute2-6.1.0 y `nsenter` util-linux 2.39.3; también `ip`, `ss`, Python 3, `capsh` y `getcap`. | No falta instalar el cliente `tc` en el anfitrión. |
| Permisos de esta sesión | UID 1001; `CapEff`, `CapPrm` y `CapBnd` en cero; `NoNewPrivs=1`. Sin file capabilities en `tc`/`nsenter`. | Esta sesión no puede administrar una qdisc ni entrar de forma autorizada al namespace de otro proceso. |
| Acceso Docker desde el aislamiento del agente | El acceso inicial al socket fue denegado; las consultas fuera de ese aislamiento permitieron solo lectura de metadatos. | Acceso al daemon y capacidades del proceso son cosas distintas. No se concedió NET_ADMIN/SYS_ADMIN, ni se probó una ejecución privilegiada. |
| Kernel | Configuración del kernel activo: `CONFIG_NET_SCH_NETEM=m`, `CONFIG_NET_SCH_PRIO=m`, `CONFIG_NET_CLS_U32=m`; no aparecieron esos módulos en `/proc/modules` al consultar. | Soporte configurado como módulos; disponibilidad/carga efectiva y funcionamiento **sin verificar**. No se ejecutó `modprobe` ni una operación que pudiera cargarlos. |
| Runtime con `tc` | Fase 2 documenta que la imagen backend tiene `nsenter` y carece de `tc`. No se volvió a arrancar para comprobarlo. | Añadir solo `NET_ADMIN` al backend no resuelve por sí mismo herramientas, usuario ni acceso al namespace. |
| Recursos HU-062 | La consulta de contenedores, incluidos detenidos, con etiqueta `hu062.aislado=true` no devolvió recursos. | No hubo entorno de red HU-062 activo sobre el que ejecutar el perfil. |

Comandos de consulta utilizados: `docker context show`, `docker version`, `docker info` con campos seleccionados, `docker ps`, `docker image ls/inspect`, `docker network ls/inspect`, `docker inspect` limitado a identidad/red/capacidades; `command -v`, `id`, lecturas de `/proc/self/status`, `/proc/modules` y `/boot/config-<kernel>`, `tc -V`, `nsenter --version`, `getcap`. Se evitó imprimir variables de entorno de los contenedores.

**No se ejecutaron** `sudo`, `unshare`, entrada con `nsenter`, comandos de configuración `tc`, creación de contenedores/redes, cambios de capacidades o carga de módulos. Los intentos de permisos registrados en fase 2 son antecedentes, no acciones de esta evaluación.

## 3. Alternativa mínima y límite de aislamiento

**Viable como diseño condicionado; bloqueada para ejecutarla con los permisos actuales.** El runner ejecuta hoy Spring Boot como proceso Java del anfitrión en 18090 y únicamente PostGIS en Docker. Una qdisc en PostGIS degradaría otro flujo. No es válido aplicar netem a loopback del anfitrión para probar la API.

La opción de menor cambio, si el responsable la autoriza, es mover **solo el backend de esa ejecución de prueba** a un contenedor nuevo con namespace propio, conservar PostGIS desechable y el proxy/Playwright actuales, y ejecutar los binarios instalados `nsenter`/`tc` mediante un operador autorizado. Así no hace falta instalar `tc` en producción, crear otro proxy ni descargar una imagen de herramientas.

Una red Docker exclusiva separa el tráfico de los proyectos, pero comparte kernel y daemon con el anfitrión. Docker crea su bridge/veth y reglas al gestionar esa red; no representa aislamiento absoluto ni ausencia de cambios internos en el host. No se propone aplicar qdisc, filtros o cambios manuales a interfaces del anfitrión. Si la exigencia significa **cero creación de interfaces/reglas incluso por Docker**, la alternativa es un equipo/VM de pruebas desechable ya habilitado por el responsable, separado de este anfitrión. No se crea esa infraestructura en esta entrega. Este límite deriva del funcionamiento documentado del [driver bridge de Docker](https://docs.docker.com/engine/network/drivers/bridge/).

### Procedimiento propuesto, no implementado ni ejecutado

1. **Autorizar y comprobar el entorno.** Identificar operador y equipo, permisos, módulos disponibles y política de aislamiento. Si hace falta instalar, descargar, cargar módulos o desactivar controles, detenerse y pedir autorización específica; no hacerlo implícitamente.
2. **Crear los recursos exclusivos con el runner existente.** Red con nombre único y etiqueta de la ejecución; solo backend de pruebas y PostGIS tmpfs en esa red. Mantener el fixture `hu061_aceptacion`, sus guardias y credenciales ficticias. No usar `network=host` ni compartir namespace con servicios existentes; no montar socket Docker, `.env` o volúmenes habituales dentro de los contenedores.
3. **Usar el código revisado.** Montar de solo lectura el JAR actual empaquetado con el procedimiento existente sobre el runtime backend local fijado por ID. Registrar hash del JAR. Mantener archivos fotográficos y temporales en directorios exclusivos, con UID/GID de prueba que pueda escribirlos; sin otorgar capacidades a la aplicación Java. Ajustar únicamente los parámetros del harness: escucha 0.0.0.0:8090 dentro del contenedor, publicación exclusiva 127.0.0.1:18090, JDBC al alias del PostGIS aislado, SMTP ficticio y `spring.config.import=`. Validar runtime Java 21, salud, fixture y acceso a archivos antes de degradar. Si la imagen no sirve, documentar el bloqueo antes de construir otra.
4. **Validar identidad y namespace.** Guardar IDs recién creados, etiquetas, PID y referencia de namespace del backend; comprobar que no coincide con el host ni con servicios habituales y que sus únicas redes son las de esta ejecución. Mantener el backend sin reinicios durante la inyección. Revalidar identidad antes de cada acción; PID reutilizado o namespace cambiado implica abortar, no seguir por nombre.
5. **Preparar un solo caso P reutilizando helpers.** Precargar Service Worker/formulario, crear offline inspección, respuestas, cuatro fotos e intención final. Reutilizar los oráculos existentes. Retener en el proxy la primera creación seleccionada antes de reenviarla; auth, health y lecturas diagnósticas fuera de la ventana degradada.
6. **Inyectar únicamente dentro del namespace validado.** El operador autorizado entra solo al namespace de red; conserva los binarios del host, sin entrar a su mount namespace. Allí instala una qdisc propia con clasificación: tráfico no coincidente por banda sin degradación y únicamente respuestas TCP de origen 8090 hacia el peer proxy por banda netem con **`loss 100%`**. Validar interfaz, dirección efectiva del peer y filtro, sin asumir dirección por NAT. No afectar backend→PostGIS. Registrar estructura y contadores iniciales. Una qdisc inesperada preexistente obliga a abortar, no a reemplazarla.
7. **Demostrar descartes y pendientes.** Liberar esa creación y observar incremento **mayor que cero** del contador `dropped` de la qdisc netem seleccionada, asociado al flujo mediante el filtro y la petición registrada. Antes de restaurar, adjuntar snapshot de respuestas, cuatro Blobs/hashes/UUID/claves y operación local aún sin confirmación. El límite de observación/activación será **10 segundos**, conforme al perfil P del plan; sin descartes o sin evidencia local, el caso no pasa. No exigir un reintento HTTP si TCP recupera por retransmisión.
8. **Restaurar y verificar realmente.** Retirar solo la qdisc creada por el test, conservar contadores de antes/durante y comprobar ausencia de la degradación después. Esperar PUBLICADA/CONFIRMADA y cola completa vacía por estados. Reutilizar SQL/API/disco: una inspección adicional, cuatro fotos DISPONIBLES/categorías, claves estables, originales/miniaturas decodificables, tamaños/checksums, replay sin duplicados y ausencia de objetos parciales. No aceptar el contador de paquetes como sustituto de estos oráculos.
9. **Limpiar y repetir únicamente lo pendiente.** Trap para EXIT/INT/TERM y vigilancia independiente que retire la pérdida al cumplir 10 s aunque falle el test; destruir backend propio garantiza destruir su namespace al terminar. Eliminar por IDs y etiquetas backend/PostGIS/red/directorios propios, comprobando desaparición y puertos libres. Destruir contenedores antes de eliminar la red; no usar prune ni down global. Ejecutar P dos veces con preparaciones independientes, conforme al plan, conservando artefactos y sin repetir suites completas.

Un límite de 10 s no puede garantizarse únicamente con un `finally` del test: debe existir el temporizador independiente de retirada o destrucción del contenedor. SIGKILL/apagado del equipo puede impedir el trap; contenedores sin política de reinicio y limpieza posterior por IDs/etiquetas reducen ese riesgo. Ni la retirada ni los contadores ni esa limpieza del perfil P están probados todavía.

### Cambios mínimos futuros, solo tras autorización

| Elemento existente | Cambio acotado necesario |
|---|---|
| `sgp-client/e2e/hu061/run-hu062-fase1.sh` | Modo explícito de paquetes: backend en contenedor/red efímeros, validaciones de identidad, operador autorizado, vigilancia de retirada y limpieza por ID; conservar modos anteriores. |
| `sgp-client/e2e/hu061/server.mjs` y helpers compartidos | Reutilizar barrera previa a creación; solo añadir el punto de coordinación si el actual no permite observar descartes/pendientes con seguridad. Sin simular pérdida mediante HTTP. |
| Pruebas Playwright de HU-062 | Un caso P junto a los perfiles existentes, con contadores y snapshots, usando preparación/oráculo compartidos; dos ejecuciones seleccionadas de ese caso. |
| Guía, resultados y evaluación | Registrar comandos realmente aprobados/ejecutados, perfil, evidencias, limpieza y actualización de CA4/6/7/8. |

No se prepara ahora un script privilegiado, una nueva imagen o un segundo framework. El procedimiento es una propuesta; no se presenta como comando operativo ya validado.

## 4. Autorización exacta y riesgos

| Intervención que debe aprobar el responsable | Alcance permitido y riesgo que debe aceptar |
|---|---|
| Entorno temporal | Crear y destruir una red Docker y dos contenedores propios, usando imágenes locales y datos exclusivos. Docker puede crear bridge/veth/reglas propias en el host. Si eso está excluido, proporcionar equipo aislado ya habilitado. |
| Operador de netem | Un operador/proceso controlado con permisos para entrar al namespace de red del backend (**CAP_SYS_ADMIN** para `setns` en los user namespaces pertinentes) y administrar sus qdisc (**CAP_NET_ADMIN**). Autorizar solo el ID/namespace de esta ejecución, interfaz y filtro descritos, con límite y limpieza. Son capacidades amplias: no otorgarlas al agente, Java ni servicios habituales, ni usar `--privileged`. |
| Kernel | Confirmar módulos disponibles y habilitados en el equipo elegido. Si se requiere carga explícita o automática de módulos, aprobarla separadamente o proporcionar un equipo donde ya estén listos. No conceder SYS_MODULE al contenedor. |
| Evidencia y decisión de herramienta | Responsable identificado que revise/ratifique ADR-0008 y confirme el alcance CA1–CA11 o aporte la fuente original/Decisión 6. La autorización de red no ratifica la herramienta ni acepta resultados todavía. |

Entrar en otro namespace requiere permisos que están documentados en [setns(2)](https://man7.org/linux/man-pages/man2/setns.2.html); [Docker](https://docs.docker.com/engine/containers/run/) distingue NET_ADMIN de la concesión completa `--privileged`. Su presencia no está demostrada por disponer del socket Docker. El filtrado y la pérdida deben verificarse con estadísticas reales del mecanismo [netem](https://man7.org/linux/man-pages/man8/tc-netem.8.html).

Los riesgos concretos son seleccionar el PID/namespace equivocado, degradar también DB u otros flujos, dejar pérdida activa, reutilizar una imagen con JAR antiguo, cargar módulos del kernel compartido y filtrar sesiones mediante trazas. Se mitigan con IDs/namespace verificados, filtro por flujo, bloqueo de reinicios, vigilancia independiente, JAR actual fijado por hash, equipo autorizado y artefactos privados. No se promete riesgo cero con Docker sobre un host compartido.

**Punto de detención:** no se dispone de autorización para esas operaciones ni se demostró soporte efectivo del kernel. Se detiene aquí la parte operativa. No se solicita sudo, no se conceden capacidades ni se prueban alternativas privilegiadas para sortear el bloqueo. Una aceptación explícita de alcance podría cambiar el criterio, pero un error HTTP nunca se registra como pérdida literal certificada.

## 5. Playwright y ADR-0008

**La justificación técnica está completa y lista para revisión del responsable.** El ADR describe contexto, continuidad con ADR-0007, reutilización de dependencias/fixtures/proxy/worker, selección de un solo E2E, perfiles exactos, separación de pérdida de transporte y paquetes, oráculos reales, evidencias, ejecución manual y límites. La guía enlazada aporta comandos ejecutados y resultados; el manifiesto/configuración confirman Playwright existente. Habilitar Cypress añadiría trabajo sin resolver permisos o netem.

Recomendación: mantener Playwright y presentar el ADR tal como está para ratificación, acompañado de esta evaluación. No es necesario reescribirlo para afirmar una aprobación inexistente. La búsqueda local acotada en el árbol AyD1 no localizó otro archivo de HU-062 o Decisión 6 que resuelva la autoridad; no prueba que ese documento no exista fuera de ese árbol.

Para cerrar CA10, el responsable debe aportar la Decisión 6 vinculante y verificar concordancia, **o** emitir una decisión trazable que ratifique expresamente Playwright/ADR-0008 y defina qué autoridad sustituye la referencia no disponible. Registrar responsable, fecha y referencia de aprobación antes de cambiar el estado del ADR a aceptado. Autorizar las implementaciones anteriores no prueba por sí solo esa ratificación formal. No se aprueba ni cambia el ADR en esta evaluación.

## 6. Matriz final de aceptación

Fuente del alcance: CA1–CA11 del plan, no una historia original localizada. **CUMPLE**: evidencia del escenario exigido en su alcance probado; **PARCIAL**: parte verificada, falta comprobarla en otro perfil obligatorio; **BLOQUEADO**: no puede cerrarse sin habilitación/decisión externa. Estos estados evalúan cierre; no representan defectos de producción. Por eso CA4 y CA10, antes descritos como parcialmente cubiertos, figuran ahora BLOQUEADO conservando sus partes verificadas.

| CA | Estado | Evidencia específica y condición pendiente |
|---|---|---|
| CA1 — Red normal | **CUMPLE** | Fase 1 y regresión fase 3: inspección/respuestas/cuatro fotos originalmente pendientes, publicación real, cola vacía y sin duplicados. No se deduce de HU-061 con fotos precargadas. |
| CA2 — Sin conexión | **CUMPLE** | Fase 1 y regresión fase 3: creación offline, conservación en Dexie de respuestas/Blobs/hash/claves, recarga desde Service Worker y cero escrituras de la aplicación para el caso. |
| CA3 — Latencia alta | **CUMPLE** | Dos corridas fase 2 y regresión fase 3 del perfil 1500 ms antes de reenviar + 1500 ms antes de entregar; pendientes durante espera y resultado remoto íntegro. |
| CA4 — Pérdida/fallos | **BLOQUEADO** | Fallos previos al procesamiento y respuesta de creación perdida/reintentos comprobados en fase 2. Falta pérdida literal con contador de descartes, restauración y resultado real. Requiere entorno/operador autorizados. |
| CA5 — Corte fotográfico | **CUMPLE** | Fase 3: recepción TCP del multipart con media imagen, Blob/clave conservados, recuperación única; variante de pérdida de 201 persistido con respuesta idéntica y sin duplicados. |
| CA6 — Pendientes | **PARCIAL** | Verificado en normal/offline/latencia/fallos/cortes, incluidas operaciones FOTO, bytes/metadatos y claves. Falta snapshot y recuperación en el perfil de paquetes; no se extrapola desde otros fallos. |
| CA7 — Servidor | **PARCIAL** | SQL/API/disco e integridad de cuatro originales/miniaturas y publicación única en perfiles ejecutados. Falta el mismo oráculo tras descartes reales. |
| CA8 — Reproducibilidad | **PARCIAL** | Perfiles por UUID/ordinal, barreras y limpieza documentados para escenarios existentes. Falta ejecutar P dos veces con parámetros fijos, contadores, restauración y limpieza verificadas. |
| CA9 — Evidencias | **CUMPLE** | Fase 3 probó una aserción deliberada tras éxito remoto: PNG 1280×720, ZIP íntegro con eventos de traza/red, logs/diagnóstico local-remoto y restauración seguida de 12/12. No acredita paquetes. |
| CA10 — Herramienta/ejecución | **BLOQUEADO** | Playwright, guía y ejecución manual real disponibles; justificación técnica lista. Falta ratificación trazable del responsable sobre ADR-0008/Decisión 6 y alcance documental. |
| CA11 — Documentación | **CUMPLE** | Guía única HU-062 enlazada desde README, ADR e informes con resultados y límites. El registro del futuro caso P deberá agregarse al ejecutarlo. |

Resultado: **6 CUMPLE, 3 PARCIAL, 2 BLOQUEADO**. Los dos bloqueos principales son operativos (paquetes) y documentales (autoridad). No hay evidencia para declarar todos los criterios cumplidos.

## 7. Condiciones exactas para estar apta para merge

1. El responsable confirma la fuente/alcance CA1–CA11 y deja ratificación trazable de Playwright/ADR-0008, resolviendo la referencia ausente a Decisión 6. Si aparece un mandato incompatible, resolverlo antes del merge, sin migrar de framework automáticamente.
2. Se autoriza un entorno y operador con las condiciones de los apartados 3/4; se valida soporte efectivo de netem y aislamiento. No basta un permiso concedido o un binario instalado.
3. Se implementa solo el caso P y la preparación mínima aprobada; dos ejecuciones independientes pasan sin omitidas ni reintentos del runner que oculten fallos. Deben existir descartes **> 0** en el flujo seleccionado, evidencia local durante la pérdida, recuperación, oráculo remoto completo y restauración/limpieza confirmadas. Con eso pueden cerrarse CA4 y las partes pendientes de CA6/7/8.
4. Se conservan las evidencias válidas de fases 1–3 sobre el código revisado; se ejecuta únicamente la regresión pertinente a los cambios posteriores del harness. No se exige por defecto otra auditoría ni una repetición integral. Revisar cualquier cambio posterior que invalide evidencia; si surge un defecto funcional, documentarlo sin corregir producción dentro de esta tarea.
5. Se actualizan guía/resultados/matriz con los resultados efectivos del caso P, la aprobación documental y sus referencias, sin credenciales ni trazas de sesiones versionadas. La revisión final confirma alcance, `git diff --check`, ausencia de recursos/red degradada y ningún cambio de producción, dependencias, migraciones o CI.

Si se decide aceptar la historia con una excepción, el responsable debe registrar explícitamente el criterio dispensado, justificación, riesgo, responsable y seguimiento. Eso sería un merge con excepción autorizada, **no cumplimiento técnico de pérdida literal ni 11/11 demostrado**. Esta evaluación no concede esa excepción.

La autonomía manual de ocho horas, otros navegadores, S3/staging y mediciones de rendimiento permanecen fuera de esta evaluación automatizada; no se añaden como bloqueos nuevos. Solo cambiaría su tratamiento una fuente de aceptación vinculante que los exija.

## 8. Cambios y validación de esta entrega

Único archivo creado en esta evaluación: `EVALUACION_CIERRE_HU062.md`. Se preservan los cambios de fases anteriores presentes en el workspace. No se modifica el ADR, el runner, producción ni configuración de red; no se crean commits. La validación se limita a consultas de solo lectura, revisión documental, enlaces locales y `git diff --check`; no se atribuyen resultados de nuevas pruebas funcionales.
