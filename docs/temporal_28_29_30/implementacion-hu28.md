# Propuesta técnica de implementación de HU-028 e integración de HU-019

**Fecha del análisis:** 15 de octubre de 2026.  
**Repositorio analizado:** `SGP_ProyectoFinal_AyD1`.  
**Revisión de referencia:** `97d3b2d3826d34c29d6ed4dbe994938136193b7e`.  
**Reevaluación:** 15 de octubre de 2026, después de integrar HU-024; revisión anterior `4d59c05`.  
**Estado:** propuesta pendiente de autorización para implementar.

Este documento registra el análisis del código y el plan propuesto. Su creación no implica autorización para modificar código, generar migraciones, instalar dependencias ni cambiar la base de datos.

La solución recomendada es implementar una única infraestructura de archivos en el módulo `archivo`, utilizada por las fotografías de HU-028 y las actas de HU-019. Los permisos, las asociaciones y las reglas de negocio deben permanecer en sus respectivos módulos.

HU-024 ya incorpora la creación de inspecciones en `BORRADOR`, entidades/repositorios, autor autenticado, versión de formulario fijada y diálogo de inicio. La dependencia de creación tiene implementación disponible. Todavía faltan consulta por ID, edición persistente de daños, continuación del borrador y sincronización offline. Estas capacidades no deben confundirse con la ausencia completa de HU-024 ni darse por implementadas por haber integrado su código.

Se conserva la arquitectura y la numeración de fases 0–6. Los ajustes principales son reutilizar `Inspeccion` y los modelos existentes de idempotencia, integrar fotografías desde el resultado del diálogo actual y resolver la coexistencia de claves idempotentes entre operaciones. Solo se localizó este plan previo; no se encontró otra versión aprobada. La autorización actual permite actualizar la planificación, no comenzar la implementación.

## A. Resumen de la arquitectura actual

### Fuentes y alcance de la revisión

Se leyeron completamente [HU-028](hu28.md) y [el documento pendiente de HU-019](../SGP_ProyectoFinal_AyD1/docs/pendientes/HU-019-acta-calibracion.md), y se contrastaron con servicios, controladores, migraciones, frontend y pruebas. También se consultó [HU-029](hu29.md) para evaluar compatibilidad futura.

Las rutas abreviadas de este documento se resuelven dentro de `SGP_ProyectoFinal_AyD1`:

| Alias | Ruta |
|---|---|
| `B/` | `sgp-api/src/main/java/gt/usac/cunoc/sgp/` |
| `F/` | `sgp-client/src/app/` |
| `M/` | `sgp-api/src/main/resources/db/migration/` |
| `T/` | `sgp-api/src/test/java/gt/usac/cunoc/sgp/` |

El proyecto utiliza Spring Boot 3.3.13 y Java 21, con JPA, consultas JDBC específicas, PostgreSQL/PostGIS, Flyway, MapStruct y Spring Security. El frontend utiliza Angular 21, componentes standalone, PrimeNG, Signals, Transloco y Dexie.

La organización por dominios y el flujo `controller → service → repository` están establecidos en [ARCHITECTURE.md](../SGP_ProyectoFinal_AyD1/docs/ARCHITECTURE.md). HU-028 puede incorporarse dentro de esta arquitectura.

| Área | Implementación comprobada | Implicación |
|---|---|---|
| Formularios/HU-019 | Versiones, pesos, escala, publicación, inmutabilidad y auditoría | Deben conservarse al incorporar actas |
| Inspecciones | HU-024 añade `Inspeccion`, acompañantes, datos técnicos, repositorios, servicio y controlador de inicio | Reutilizar el agregado; la creación dispone de pruebas unitarias/MVC satisfactorias, pero la persistencia real no se verificó en esta revisión |
| Estados de inspección | V8 admite seis estados; la entidad representa el estado como `String` e inicia en `BORRADOR`, con `@Version` | No se encontró una máquina de transiciones ni edición del borrador; se requiere bloqueo al confirmar archivos |
| Archivos | V10 contiene `archivo`, `archivo_inspeccion`, `archivo_acta_calibracion` y evidencia de mantenimiento | Se reutiliza el esquema; falta la implementación funcional |
| Seguridad | JWT y HU-024: creación para Catedrático y Profesional externo con colegiado verificado, cuenta activa, activada y verificada | El autor procede de `JwtData`; para fotografías añadir comprobación de propiedad y estado con 404/409 |
| Idempotencia | V11, entidad `OperacionIdempotente`, repositorio y reproducción parcial dentro de `InspeccionService` | Reutilizar entidad/repositorio; faltan comparación de huella/ruta, reserva concurrente y recuperación segura |
| Almacenamiento | MinIO declarado en Compose | No se encontraron adaptadores, cliente integrado ni `AlmacenamientoArchivos` |
| Offline | Tablas Dexie para inspecciones, fotos y cola; UUID v7 | El interceptor offline solo delega; no hay motor de sincronización de fotografías |
| Pruebas | JUnit, Mockito, MockMvc, Testcontainers/PostGIS, JaCoCo y Vitest | Hay infraestructura aprovechable para integración y regresión |

La ficha del puente abre ahora `IniciarInspeccionDialogComponent`; este llama a `InspeccionApiService`, recibe el ID persistible, autor, estado y versión, muestra el resultado y emite `inspeccionIniciada`. La ficha todavía no consume ese evento ni navega a un editor. El formulario dinámico sigue disponible como vista previa sin guardado, con un marcador fotográfico. La sección documental del puente sigue mostrando un aviso vacío.

El [ADR de staging](../SGP_ProyectoFinal_AyD1/docs/adr/0002-despliegue-staging-aws-compose-ghcr.md) establece MinIO en desarrollo y S3 en nube, y reconoce que la integración del backend sigue pendiente. El adaptador local debe quedar para los ambientes donde corresponda, sin sustituir esa decisión de despliegue.

### Documentación localizada y ausente

| Documento solicitado | Resultado |
|---|---|
| HU-028 | Disponible en `HUS/hu28.md` |
| HU-029 | Disponible en `HUS/hu29.md`; leído para evaluar compatibilidad futura |
| HU-019 completa | No localizada; está disponible el documento específico de pendientes |
| HU-024 | No localizada como historia completa; su alcance se contrastó con el código y las pruebas incorporadas en `97d3b2d` |
| V10 | Disponible y revisada; también V7, V8 y V11 |
| RN-ARC-01/02/03 | Referenciadas en HU-028; no se localizó su definición independiente completa |
| DT-ALM-01 a 04 | Referencias en HU-028 y documentación de despliegue; sin documento completo de esas decisiones |
| DT-OFF-05/07/08, DT-SEC-08, DT-DEP-03 | Referencias y criterios concretos en las historias; sin documento independiente completo localizado |

El plan utiliza los criterios explícitos disponibles y no atribuye requisitos adicionales a los documentos ausentes.

### HU-024 integrada: alcance comprobado y límites

El flujo examinado es `FichaPuentePage → IniciarInspeccionDialogComponent → InspeccionApiService → InspeccionController → InspeccionService → repositorios JPA → tablas de V8/V11`.

| Funcionalidad | Evidencia | Estado y límite de verificación |
|---|---|---|
| Inicio de inspección | `POST /api/v1/inspecciones`, `IniciarInspeccionRequest/Response` | Implementado; pruebas unitarias y MVC aprobadas. No se ejecutó el recorrido contra PostgreSQL |
| Creación de borrador | Constructor `Inspeccion`, `InspeccionRepository.save`, transacción y auditoría | Implementado en código; la prueba de servicio utiliza repositorios simulados |
| Autor y rol | UUID desde `JwtData`; `validarInspector` comprueba cuenta y rol | Escenarios cubiertos por las pruebas existentes; no sustituye la autorización específica de archivos |
| Versión fija | `findByActivaTrue()` al crear; relación `version_formulario_id` | Asociación disponible; los adjuntos deben usar `getVersionFormulario()`, aunque después cambie la versión activa |
| Fechas y GPS inicial | Fecha de Guatemala, rechazo de fecha futura/anterior a construcción y advertencia a más de 500 m | Casos de servicio probados; GPS inicial es opcional y no acredita ubicación/fecha de cada fotografía |
| Datos permanentes | `GET /api/v1/inspecciones/puentes/{puenteId}/datos-permanentes`; 200 o 204; copia solo con confirmación | Implementado; se conservan fuera de los daños |
| Acompañantes | Entidad con cascada y DTO | Implementado parcialmente: hay diferencias de nombres de campos entre frontend y backend |
| Idempotencia de inicio | Búsqueda por usuario/clave y por ID | Solo repetición básica cubierta; no están resueltos payload distinto, concurrencia ni coexistencia entre endpoints |
| Continuar/editar borrador | No hay `GET /api/v1/inspecciones/{id}` ni endpoint de edición de `datos` | Pendiente; la cabecera `Location` de creación apunta a una ruta de detalle todavía no implementada |
| Grupos de daño y repetibles | Motor dinámico existente; la nueva inspección inicia con `datos={}` | Motor reutilizable; falta integrar su persistencia y la existencia de instancias para anclajes repetibles |
| Envío/publicación | Servicios de confirmación y apoyo a publicación existentes | HU-024 no incorpora transiciones completas; HU-026 continúa pendiente según su documentación de integración |
| Offline | Dexie y cola existentes; el diálogo usa HTTP directo mediante servicio | No se guarda el resultado en Dexie ni se encola el inicio; no hay reintento persistente |

### Contratos de HU-024 que necesita HU-028

| Contrato | Disponible | Ajuste mínimo previsto |
|---|---|---|
| Identidad de inspección | `Inspeccion.id`, `IniciarInspeccionResponse.id` | Asociar `archivo_inspeccion.inspeccion_id`; no crear otro modelo de inspección |
| Propiedad | `autorId` persistido y usuario autenticado | Comparar en backend antes de leer/escribir; no confiar en el `autorId` enviado por frontend |
| Estado y eliminación | `getEstado()`, `getEliminadoEn()`, `findByIdAndEliminadoEnIsNull` | Añadir consulta con bloqueo en el repositorio existente y comprobar `BORRADOR` al confirmar |
| Versión del formulario | Relación JPA y `versionFormularioId` en respuesta | Validar `elemento_ref` contra el esquema de esa versión |
| Instancia repetible | UUID en el motor y columna `instancia_elemento_id` en V10 | Acordar persistencia de instancias con el flujo de edición; no usar índices de arreglos |
| Contexto frontend | `resultado()` y evento `inspeccionIniciada` | Integrar el panel fotográfico en ese flujo; acordar consulta/reanudación sin construir un segundo editor |
| Idempotencia | Entidad/repositorio V11 existentes | Servicio compartido que compruebe método, ruta, huella, entidad y resultado; proteger también el consumidor de HU-024 |

### Hallazgos preexistentes que afectan la integración

Estos hallazgos proceden de la lectura del código integrado; no son fallos introducidos por HU-028 ni todos están reproducidos mediante pruebas de integración.

| Hallazgo | Evidencia | Consecuencia y tratamiento propuesto |
|---|---|---|
| Reutilización de clave sin comparar solicitud | `InspeccionService.iniciarInspeccion`, líneas 98–112, no compara método, ruta, hash ni tipo de entidad | V11 tiene unicidad global por usuario/clave. Una clave de carga puede interpretarse como inicio de inspección; exigir 409 ante incompatibilidad en ambos consumidores antes de compartir operaciones |
| UUID de inspección perteneciente a otro autor | Líneas 115–122 no rechazan explícitamente el ID ajeno y permiten seguir hacia `save` | Riesgo de colisión o actualización mediante merge; requiere prueba de persistencia y rechazo explícito. No se afirma una sobrescritura reproducida |
| Reintento con identidad nueva | El diálogo genera `crypto.randomUUID()` dentro de cada envío y usa ese mismo valor como clave | Un error de red seguido de otro envío puede crear otro borrador. Reutilizar identidad/clave de la operación y la utilidad UUID v7 cuando se integre el reintento |
| DTO de acompañantes divergentes | Frontend: `id`, `colegiado`; backend: `usuarioId`, `numeroColegiado` | El contrato no conserva esos campos de extremo a extremo. Añadir prueba HTTP real antes de tocar el formulario de inicio; corrección acotada si se autoriza |
| Validación de entrada incompleta | `dispositivoId` solo tiene `@NotNull`; lista de acompañantes sin validación en cascada; GPS sin validadores de rango | Las pruebas existentes no garantizan límites, contenido de acompañantes o pares GPS completos. Añadir cobertura y no reutilizar estas validaciones para fotografías |
| Posible incompatibilidad de tipo SQL/JPA | `InspeccionDatoTecnico.anioConstruccion` es `Integer`; V8 declara `SMALLINT` | Verificar con `ddl-auto=validate` en PostGIS aislado; riesgo de validación del esquema todavía no reproducido |

La falta de editor, carga de archivos y sincronización es funcionalidad pendiente, no una prueba fallida. La limitación inicial de Mockito y el acceso a Docker son problemas del entorno de diagnóstico, descritos en G.

## B. Estado actual de HU-028

“Parcial” significa que existe una pieza aprovechable, no que el escenario completo funcione.

| Criterio | Estado actual | Código relacionado | Trabajo pendiente |
|---|---|---|---|
| 1. Capturar o seleccionar fotografía | Pendiente; contexto de creación disponible | `F/features/inspecciones/components/iniciar-inspeccion-dialog/`; `IniciarInspeccionResponse` | Integrar cámara, galería y carga en el flujo actual después de crear el borrador |
| 2. Compresión: máximo 1600 px, JPEG/WebP, objetivo 200–400 KB | Pendiente | Sin procesador de imágenes localizado | Extraer metadatos, orientar, redimensionar y comprimir antes de persistir/subir; verificar dimensiones en backend |
| 3. Cinco categorías permitidas | Parcial: restricciones SQL | `M/V10__mantenimiento_y_archivos.sql` | Enum, DTO, validación y selector para `ACCESO`, `SUPERESTRUCTURA`, `SUBESTRUCTURA`, `CAUCE`, `OTRA` |
| 4. Rechazar foto sin categoría | Parcial: restricción SQL | V10, `archivo_foto_inspeccion_categoria_obligatoria` | Rechazo comprensible antes de intentar insertar |
| 5. Anclaje opcional mediante `elemento_ref` | Parcial; versión fija disponible | V10; `B/inspeccion/entity/Inspeccion.java`; motor de formularios | Usar la versión asociada; integrar grupos de daño cuando exista guardado de `datos` y validar la identidad de repetibles |
| 6. Conservar GPS y fecha de captura | Parcial: columnas SQL | V10, `ubicacion_captura`, `capturado_en` | Capturar o extraer antes de quitar EXIF; transmitir y guardar como metadatos explícitos |
| 7. Rechazar ausencia de ubicación/fecha | Parcial: restricciones para fotos disponibles | V10 | Validación frontend/backend y mensaje específico |
| 8. Original comprimido y miniatura de 300 px | Parcial: columnas SQL | V10, claves del original y miniatura | Procesamiento, almacenamiento de ambos objetos y confirmación conjunta |
| 9. Interfaz y adaptadores MinIO/local | Pendiente | `docker-compose.yml`; módulo `archivo` sin implementación funcional | Crear la interfaz exigida y adaptadores intercambiables |
| 10. Nombre generado por servidor | Parcial: columna SQL | V10, `nombre_almacenado` | Generar claves seguras e independientes del nombre y UUID suministrados por el cliente |
| 11. Solo autor y solo `BORRADOR`; 404/409 | Pendiente para fotografías; autor/estado persistibles ya modelados | `B/inspeccion/entity/Inspeccion.java`; `B/inspeccion/repository/InspeccionRepository.java` | Reutilizar el agregado y añadir autorización/bloqueo específicos de archivos |
| 12. UUID v7 e `Idempotency-Key` | Parcial: V11 y modelos Java incorporados por HU-024 | `B/sync/entity/OperacionIdempotente.java`; `B/sync/repository/OperacionIdempotenteRepository.java`; utilidad UUID v7 | Ampliar comportamiento, no duplicar modelos; resolver comparación de solicitud y colisiones con inicio de inspección |
| 13. Petición máxima de 25 MB | Parcial | `application.properties`; Nginx del cliente y plantilla HTTPS | Añadir respuesta 413 clara y verificar límites reales en toda la cadena HTTP |
| 14. Acta PDF, máximo 20 MB, administrador y versión | Parcial: asociación SQL | V10; módulo `formulario` | Validación real, servicio compartido, endpoints, autorización, interfaz y auditoría |

Dos diferencias importantes respecto a otros servicios:

- `ConfirmacionEstadoIcService` devuelve 403 para otro autor y admite más estados que `BORRADOR`. Esa política no puede trasladarse directamente a la carga de fotografías, que exige 404/409.
- El límite multipart de 20 MB por archivo y 25 MB por petición ya existe, pero `ApiExceptionHandler` no contiene un tratamiento específico para exceso de tamaño. Debe verificarse que estos errores no terminen en su manejador genérico de 500.

### Contraste específico de HU-019

El documento de pendientes coincide con el código respecto a la ausencia del flujo de actas. Necesita estas precisiones:

1. Los permisos de administrador y pruebas de 403 ya existen para pesos y escala. Lo pendiente es aplicarlos y probarlos en las nuevas operaciones de actas.
2. La asociación SQL con versiones ya existe; no hace falta crear una segunda relación equivalente.
3. La publicación valida pesos, escala, esquema y mapeos, pero no exige acta. Convertir el acta en requisito de publicación sería un cambio funcional que necesita confirmación.
4. La auditoría actual de versiones captura pesos y reglas, pero no documentos asociados.
5. V10 no aplica a `ACTA_CALIBRACION` la restricción de PDF y 20 MB que sí aplica a `DOCUMENTO_INSPECCION`.
6. V10 permite varias actas por versión; el documento habla del acta en singular, sin definir unicidad ni reemplazo.

También se observó una llamada duplicada a `validarPesosParaPublicacion` en `B/formulario/service/VersionFormularioService.java`. Es un hallazgo previo; no es necesario corregirlo para integrar archivos.

Los archivos marcados como nuevos en la siguiente tabla son propuestas:

| Pendiente HU-019 | Estado actual | Dependencia HU-028 | Solución propuesta | Archivos afectados |
|---|---|---|---|---|
| Adjuntar PDF | No hay endpoint ni servicio de carga | Recepción, validación y almacenamiento compartidos | Servicio de actas en `formulario` que use el servicio de archivos | Nuevos `B/formulario/service/ActaCalibracionService.java`, `B/formulario/controller/ActaCalibracionController.java`; nuevos componentes de `B/archivo/` |
| Validar contenido real y 20 MB | Multipart limita tamaño; falta validación funcional y CHECK para actas | Validador PDF y metadatos calculados por servidor | Analizar estructura PDF, medir bytes y reforzar integridad SQL | Nuevo `B/archivo/validation/ValidadorPdf.java`; nueva migración |
| Asociar a versión correcta | Tabla `archivo_acta_calibracion` disponible, sin implementación Java | Registro `archivo` confirmado | Guardar archivo y asociación dentro de una misma transacción | Nuevas entidades/repositorios de `B/archivo/`; `ActaCalibracionService` |
| Consulta autorizada | Ausente | Lectura mediante `AlmacenamientoArchivos` | Endpoint administrativo que autorice antes de entregar bytes | Nuevo controlador/servicio de actas; adaptadores compartidos |
| Mostrar ausencia de acta | DTO e interfaz no lo representan | Consulta de asociaciones disponibles | Respuesta explícita de ausencia y mensaje “Sin acta de calibración” | Nuevos DTO/modelos de acta; `F/features/formulario/services/formulario-api.service.ts`; editor de versión; `sgp-client/public/i18n/es.json` |
| Auditar asociación | Auditoría de configuración existente; no incluye actas | Metadatos confirmados de archivo | Registrar actor, fecha y asociación anterior/posterior en la transacción | `ActaCalibracionService`, reutilizando `AuditoriaWriter` |
| Rechazar no administradores con 403 | Probado para otras operaciones, no para actas | Identidad JWT y autorización existente | Proteger carga, listado y descarga en backend | Nuevo controlador/servicio y pruebas por rol |
| Integridad ante fallos | Sin coordinación almacenamiento–BD | Protocolo común de carga y recuperación | Confirmar solo archivos completos; compensar y recuperar intentos interrumpidos | Servicios compartidos de `B/archivo/` y pruebas de integración |

## C. Componentes reutilizables

| Componente existente | Ruta real usando los alias definidos | Reutilización propuesta |
|---|---|---|
| Esquema de archivos y asociaciones | `M/V10__mantenimiento_y_archivos.sql` | Mapear sus tablas y respetar tipos de uso y claves foráneas |
| Registro y modelos de idempotencia | `M/V11__foro_notificaciones_y_sincronizacion.sql`; `B/sync/entity/OperacionIdempotente.java`; `B/sync/repository/OperacionIdempotenteRepository.java` | Reutilizar tabla, entidad y repositorio; añadir coordinación sin repetirlos |
| Agregado de inspección | `B/inspeccion/entity/Inspeccion.java` | ID, autor, versión fija, estado, eliminación y versión optimista |
| Repositorio de inspecciones | `B/inspeccion/repository/InspeccionRepository.java` | Consulta de existentes/no eliminadas; añadir bloqueo para confirmación de adjuntos |
| Inicio y datos permanentes | `B/inspeccion/service/InspeccionService.java`; `B/inspeccion/controller/InspeccionController.java`; `B/inspeccion/dto/IniciarInspeccionResponse.java` | Conservar POST y GET actuales; usar respuesta para el contexto inicial de fotografías |
| Datos técnicos y acompañantes | `B/inspeccion/entity/InspeccionDatoTecnico.java`; `B/inspeccion/entity/InspeccionAcompanante.java`; `B/inspeccion/repository/InspeccionDatoTecnicoRepository.java` | Preservar precarga confirmada y relaciones/cascadas; no mezclarlas con archivos |
| Diálogo y HTTP de inspecciones | `F/features/inspecciones/components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component.ts`; `F/features/inspecciones/services/inspeccion-api.service.ts`; `F/features/inspecciones/models/inspeccion.models.ts` | Integrar fotografías desde el resultado actual y ampliar HTTP mediante servicio |
| Punto de entrada | `F/features/puentes/pages/ficha-puente/ficha-puente.page.{ts,html}` | Reutilizar apertura del diálogo y consumir su evento si se acuerda navegación/reanudación |
| Identidad y autorización | `B/common/security/JwtData.java`, `B/common/security/JwtAuthenticationFilter.java`; `B/common/config/SecurityConfiguration.java` | Obtener usuario de sesión y proteger métodos/endpoints |
| Errores de API | `B/common/exception/ApiException.java`, `B/common/exception/ProblemDetails.java`, `B/common/exception/ApiExceptionHandler.java` | Mantener el formato actual de errores |
| Auditoría transaccional | `B/common/audit/service/AuditoriaWriter.java` | Usar `registrarEnTransaccion` para la asociación del acta |
| UUID v7 | `B/common/util/UuidV7Generator.java`; `F/offline/services/uuid-v7.ts` | Identificadores internos y UUID de fotografía generado una vez por el cliente |
| Versiones y configuración | `B/formulario/entity/VersionFormulario.java`; `B/formulario/repository/VersionFormularioRepository.java` | Resolver la versión y conservar sus reglas vigentes |
| Referencias del formulario | `B/formulario/validation/CampoSchemaExtractor.java`; motor del frontend | Aprovechar el recorrido del esquema; complementar la validación de grupos evaluables e instancias |
| Formulario extensible | `F/features/formulario/components/secciones-apoyo.ts`, `F/features/formulario/components/renderizadores.ts`, `F/features/formulario/components/formulario-dinamico/` | Incorporar reporte fotográfico y acciones contextualizadas |
| Identidad de repetibles | `F/features/formulario/motor/formulario/constructor.ts` | Mantener `instancia_id`, evitando posiciones del arreglo como identidad |
| Persistencia offline | `F/offline/db/sgp-db.ts` | Ampliar `LocalPhoto` y usar la cola existente |
| HTTP y sesión | `F/core/interceptors/auth.interceptor.ts`, `F/core/interceptors/problem.interceptor.ts` | Enviar autorización y conservar la clave idempotente en reintentos |
| Tareas coordinadas | `B/common/config/SchedulingConfiguration.java` | Usar Scheduler/ShedLock si se incorpora reconciliación periódica |

No se encontraron componentes funcionales reutilizables para subida multipart, validación real de PDF/imágenes, almacenamiento binario, miniaturas o acceso a MinIO.

`vinculosAlmacenamiento` del formulario describe correspondencias entre campos y persistencia estructurada. No es una abstracción de almacenamiento de archivos.

### Distribución de responsabilidades

| Módulo | Responsabilidad |
|---|---|
| `archivo` | Validación del contenido, procesamiento de imágenes, metadatos físicos, almacenamiento y recuperación de cargas fallidas |
| `inspeccion` | Autor, estado, categoría y anclaje de fotografías |
| `formulario` | Permisos administrativos, asociación y ciclo de vida de actas |
| `sync` | Reserva y resultado de operaciones idempotentes reutilizando V11 |
| `offline` del frontend | Persistencia local de fotografía, metadatos y operación pendiente |

`AlmacenamientoArchivos` debe ofrecer operaciones sobre claves y flujos de bytes —guardar, abrir, comprobar y eliminar— sin conocer inspecciones, roles ni versiones. Ambos casos de uso consumirán la misma interfaz y los mismos adaptadores.

Para lectura se proponen inicialmente endpoints autorizados. Esto permite usar el mismo contrato con MinIO y almacenamiento local, y facilita revocar acceso. En Angular, las imágenes protegidas deben obtenerse mediante el servicio HTTP autenticado y convertirse en URLs temporales de `Blob`, liberándolas posteriormente.

## D. Componentes que necesitan cambios

Los cambios siguientes son propuestos; no fueron realizados durante el análisis.

| Archivo | Modificación propuesta | Justificación | Riesgo |
|---|---|---|---|
| `sgp-api/pom.xml` | Dependencias estrictamente necesarias para objetos, PDF y WebP | Actualmente no están integradas | Medio: compatibilidad, tamaño y arquitectura de contenedor |
| `sgp-api/src/main/resources/application*.properties` | Configuración del proveedor, límites operativos y directorio/bucket | Seleccionar adaptador sin cambiar dominio | Medio: configuración incorrecta por ambiente |
| `B/common/exception/ApiExceptionHandler.java` | Tratamiento específico de tamaño y multipart inválido | Respuestas claras, especialmente 413 | Medio: preservar respuestas actuales |
| `B/inspeccion/repository/InspeccionRepository.java` | Añadir consulta de inspección no eliminada con bloqueo | Revalidar autor/estado al confirmar archivos sin otro repositorio paralelo | Medio: orden de bloqueos y concurrencia |
| `B/sync/entity/OperacionIdempotente.java` y `B/sync/repository/OperacionIdempotenteRepository.java` | Ampliaciones mínimas para reserva/finalización atómica cuando se diseñe el servicio compartido | Ya existen; no deben volver a crearse | Medio: compatibilidad con V11 y HU-024 |
| `B/inspeccion/service/InspeccionService.java` | Protección acotada de reproducción idempotente frente a ruta/huella/entidad distintas y revisión de colisiones UUID | Necesaria antes de coexistir con operaciones de archivos en V11 | Alto: mantener POST válido y evitar interpretar una carga como inspección |
| `F/features/inspecciones/services/inspeccion-api.service.ts` | Añadir operaciones fotográficas o delegación al servicio transversal de archivos | El servicio de feature ya existe | Bajo: mantener contratos de inicio y datos permanentes |
| `F/features/inspecciones/components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component.{ts,html}` | Integrar panel en resultado y conservar identidad de operación al reintentar | Aprovechar borrador y contexto existentes | Medio: duplicación del inicio o pérdida de contexto al cerrar |
| `F/features/puentes/pages/ficha-puente/ficha-puente.page.{ts,html}` | Consumir `inspeccionIniciada` si se elige continuar desde la ficha | Actualmente el evento no tiene consumidor en la ficha | Medio: no mostrar fotos privadas en el catálogo público |
| `sgp-client/nginx.conf` y `infra/staging/https/nginx-https.conf.template` | Respuesta 413 coherente manteniendo 25 MB | El proxy puede rechazar antes del backend | Bajo/medio |
| `F/offline/db/sgp-db.ts` | Metadatos completos, propietario local y actualización de esquema cuando corresponda | `LocalPhoto` hoy solo guarda ID, inspección, blob, MIME y sincronización | Alto: datos locales existentes |
| `F/features/formulario/services/formulario-api.service.ts` | Métodos administrativos de actas | Mantener HTTP fuera de componentes | Bajo |
| `F/features/formulario/pages/editor-version-formulario/editor-version-formulario.page.{ts,html,scss}` | Integrar panel de acta | Completar HU-019 en su pantalla existente | Medio: editor ya operativo |
| `sgp-client/public/i18n/es.json` | Textos de carga, errores, categorías y ausencia de acta | Mantener Transloco | Bajo |
| `F/app.routes.ts` | Cambio condicional: ruta lazy solo si se acuerda una vista de reanudación y no la aporta el editor de inspecciones | La carga inicial puede integrarse en el diálogo existente; evitar otra interfaz de inspección | Medio |
| `.github/workflows/ci.yml` | Ejecutar las pruebas frontend acordadas | Actualmente ejecuta lint, formato y build, pero no `npm test` | Medio: duración y fallos previos |
| `docs/pendientes/HU-019-acta-calibracion.md` | Actualizar estado únicamente tras verificar integración | Evitar declarar completada una capacidad parcial | Bajo |

Los archivos nuevos necesarios se concentran en:

- `B/archivo/`: entidades y repositorios para las tres tablas relevantes, DTO/mapper, `AlmacenamientoArchivos`, adaptadores, validadores, procesamiento y coordinación de cargas.
- `B/sync/`: servicio de coordinación idempotente; entidad y repositorio ya existen y se reutilizan.
- `B/inspeccion/`: controlador, DTO y servicio específicos de fotografías. Se reutilizan `Inspeccion`, `InspeccionRepository`, el controlador de inicio y su contrato; no se crean sustitutos.
- `B/formulario/`: controlador, servicio y DTO específicos de actas.
- `F/features/inspecciones/`: componentes y procesamiento fotográficos; se amplía el servicio API existente y se integra el resultado del diálogo de HU-024.
- `F/offline/services/`: persistencia y reintento de fotografías.
- `F/features/formulario/`: componente y modelos de actas.
- Pruebas correspondientes y una migración aditiva.

Es posible mantener sin cambios los DTO públicos actuales de versiones: el panel de actas puede consultar un endpoint separado. Tampoco se requiere introducir relaciones bidireccionales entre todas las entidades JPA.

## E. Plan de implementación por fases

### Impacto de la incorporación de HU-024

| Fase | Estado anterior | Estado actual | Cambios necesarios |
|---|---|---|---|
| 0 | Localizar HU-024 y cerrar contratos | Creación y contratos centrales disponibles; verificación de persistencia/continuación pendiente | Cerrar solo decisiones restantes y validar coexistencia de idempotencia |
| 1 | Crear modelos de archivos e idempotencia | Modelos de archivos pendientes; `Inspeccion` y modelos V11 ya existen | Reutilizar modelos existentes, revisar mapeos y diseñar ampliación aditiva |
| 2 | Infraestructura de almacenamiento y servicio idempotente desde tabla | Sigue necesaria; HU-024 es ahora un consumidor real de V11 | Añadir pruebas cruzadas y protección mínima en reproducción de inicio |
| 3 | API dependiente de entrega de HU-024 | Puede apoyarse en el agregado y repositorio integrados | Añadir consulta con bloqueo y autorización específica; no recrear CRUD de inspecciones |
| 4 | Esperar pantalla real de inspecciones | Diálogo y resultado disponibles; editor persistente de daños/reanudación ausente | Integrar fotografía inicial en el flujo actual; coordinar anclajes de daño e instancias sin duplicar editor |
| 5 | Actas después de infraestructura; alternativa si faltaba HU-024 | Se mantiene independiente de la edición de inspecciones | Adelantar tras fase 2 para comprobar infraestructura con PDF; mantener todos los pendientes de HU-019 |
| 6 | Regresión conjunta con futura HU-024 | Añadir regresión de inicio real y coexistencia de operaciones | Probar creación→foto, metadatos, versiones históricas, reintentos y límites; integrar PostGIS cuando esté disponible |

No se elimina ni agrega ninguna fase. Las fases 1–2 y 5 no dependen de la edición de borradores; la fase 3 requiere la creación ya integrada, y el cierre de la fase 4 requiere resolver continuación y anclajes persistentes.

### Fase 0. Cerrar contratos y comprobar dependencias

**Objetivo:** establecer qué puede integrarse realmente antes de desarrollar.

- Dar por localizada la integración de HU-024 en `97d3b2d`; conservar los contratos de inicio examinados y acordar consulta/reanudación, edición de `datos` e identidad persistida de repetibles.
- Revisar los hallazgos de idempotencia y UUID antes de habilitar operaciones de archivos que compartan V11.
- Resolver las decisiones de actas y metadatos descritas en H.
- Definir endpoints, respuestas y catálogo de errores.
- Comprobar, al iniciar la implementación, la descarga de la imagen MinIO configurada en cada máquina. Esta comprobación no se realizó durante el análisis.
- Verificar el proveedor de cada ambiente y la integración necesaria con S3/IAM.

| Aspecto | Detalle |
|---|---|
| Archivos existentes/nuevos | Consulta de historias, ADR y contratos actuales; documentación del contrato acordado cuando se autorice implementar |
| Reutilización | Arquitectura, rutas `/api/v1`, agregado de inspección y respuesta de inicio ya disponibles |
| Dependencias | Confirmación del flujo de continuación/edición y decisiones de negocio; acceso a entorno de pruebas aislado |
| Riesgo | Confundir pruebas con repositorios simulados con persistencia verificada o atribuir al inicio funcionalidades de edición |
| Verificación | Demostrar el contrato disponible y distinguir integración real de pruebas con datos preparados |
| Criterio de terminación | Contratos disponibles documentados, hallazgos clasificados y decisiones pendientes delimitadas; la entrega de creación HU-024 ya no es un bloqueo general |

### Fase 1. Persistencia y modelos compartidos

**Objetivo:** aprovechar V10/V11 y completar sus garantías necesarias.

- Mapear `archivo`, `archivo_inspeccion` y `archivo_acta_calibracion`.
- Incorporar tipos de uso, categorías y estados existentes.
- Reutilizar `OperacionIdempotente` y `OperacionIdempotenteRepository`, respetando la unicidad actual `(usuario_id, clave)`; no recrear los modelos incorporados por HU-024.
- Vincular `ArchivoInspeccion` al agregado `Inspeccion` existente; evitar una colección inversa de archivos o cascadas nuevas salvo necesidad demostrada.
- Incorporar una restricción para `ACTA_CALIBRACION`: PDF y máximo **20 971 520 bytes**, consistente con la convención existente.
- Diseñar el registro duradero mínimo de intentos de carga para recuperación tras caída del proceso, asociado a la operación idempotente. Debe registrar las claves generadas por intento y su vigencia; no debe fingir que un archivo incompleto está `DISPONIBLE`.
- Aplicar reglas adicionales de unicidad de actas solo después de resolver su cardinalidad.

| Aspecto | Detalle |
|---|---|
| Archivos existentes | V10/V11 como referencia, sin editarlas; `Inspeccion`, entidad/repositorio de idempotencia y pruebas de migración |
| Archivos nuevos | Entidades/repositorios de `archivo`; soporte duradero de intentos según el diseño acordado y migración aditiva. La siguiente versión observada sigue siendo V19, que debe comprobarse nuevamente antes de crearla |
| Reutilización | PostGIS, UUID, agregado HU-024, modelos V11 y convenciones de persistencia |
| Dependencias | Fase 0 |
| Riesgo | Restricciones incompatibles con datos existentes o asociaciones duplicadas |
| Pruebas | Migración sobre PostGIS, tipos de uso incompatibles, categoría obligatoria, metadatos y límites de acta |
| Criterio de terminación | Modelos compatibles con tablas existentes y migración verificada sin alterar contratos operativos |

#### Compatibilidad de V10 y V11 después de HU-024

Las migraciones V10/V11 no cambiaron entre `4d59c05` y `97d3b2d`. La compatibilidad revisada aquí es estática; no se aplicaron migraciones a una base de datos.

- V10 ya proporciona la relación con `inspeccion` de V8 y con `version_formulario`; sus claves compuestas comprueban que el tipo de uso coincida con el vínculo. El nuevo agregado JPA de HU-024 permite reutilizar esa relación sin otra tabla de inspecciones.
- V10 requiere categoría para fotos y, al estar `DISPONIBLE`, GPS, fecha, miniatura, dimensiones positivas y MIME JPEG/WebP. No sirve como registro provisional de una carga incompleta.
- Máximo de 1600 px, tamaño real de miniatura y validez del contenido requieren validación de aplicación; guardar un MIME en SQL no valida los bytes.
- La restricción PDF/20 MB falta para `ACTA_CALIBRACION`; se mantiene la propuesta de migración aditiva. La cantidad de actas por versión necesita decisión previa.
- V11 admite reserva `EN_PROCESO` y finalización `COMPLETADA`; los modelos Java existentes pueden ampliarse para esas operaciones. Su unicidad es por usuario/clave, no por endpoint: el servicio debe comparar método, ruta, huella y entidad.
- V11 no contiene un protocolo completo de vigencia de intentos ni claves de objetos provisionales. El soporte duradero de recuperación debe diseñarse de forma mínima y compatible, sin duplicar la tabla de operaciones.
- `DatabaseMigrationIntegrationTest` espera actualmente 18 migraciones. Al autorizar una migración nueva, se debe actualizar esa expectativa y añadir pruebas de restricciones.

### Fase 2. Almacenamiento, validación e idempotencia compartidos

**Objetivo:** establecer una infraestructura utilizable por ambas historias.

- Implementar `AlmacenamientoArchivos` y adaptadores MinIO/local.
- Configurar claves privadas generadas por el servidor y evitar sobrescrituras entre intentos.
- Validar bytes, tamaño, formato y dimensiones reales.
- Generar miniaturas manteniendo proporciones.
- Implementar reserva idempotente, huella de solicitud y respuesta persistida.
- Reutilizar los modelos V11 incorporados por HU-024. El algoritmo de `InspeccionService` no es un servicio general: no compara huellas ni discrimina rutas/entidades. Antes de compartir V11, proteger ese consumidor con el cambio mínimo necesario y mantener sus solicitudes válidas.
- Generar claves independientes para iniciar inspección, subir fotografía y adjuntar acta. El servidor debe rechazar colisiones entre operaciones aunque el cliente no respete esa separación.
- Incorporar compensación y recuperación de cargas interrumpidas.
- Verificar acceso privado y tratamiento de errores del proveedor.

Para PDF, se propone evaluar PDFBox para analizar estructura, además de comprobar firma y tamaño. Su API proporciona carga y análisis de documentos; detectar `%PDF` por sí solo no basta. Véase la [documentación de PDFBox](https://pdfbox.apache.org/3.0/migration.html).

Java 21 incluye JPEG entre sus formatos estándar de ImageIO, pero WebP requiere soporte adicional. La aceptación de WebP y la generación de miniaturas deben verificarse expresamente en las imágenes de ejecución AMD64/ARM64 utilizadas por el proyecto. Véanse los [formatos de ImageIO](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/package-summary.html).

#### Protocolo de integridad propuesto

1. Autorizar y validar la solicitud.
2. Reservar la operación y registrar las claves del intento.
3. Escribir y verificar el PDF, o el original y su miniatura.
4. En una transacción corta, volver a comprobar permisos/estado y guardar metadatos, asociación, resultado idempotente y auditoría aplicable.
5. Ante fallo, retirar únicamente los objetos del intento fallido.
6. Recuperar intentos abandonados mediante el registro duradero, evitando limpiar cargas activas.

Una transacción de PostgreSQL no revierte escrituras en MinIO o disco. La garantía debe combinar confirmación transaccional de referencias con compensación y recuperación de objetos huérfanos.

| Aspecto | Detalle |
|---|---|
| Archivos existentes | `pom.xml`, propiedades, handler de errores, entidad/repositorio V11 e integración mínima en `InspeccionService` |
| Archivos nuevos | Interfaz, adaptadores, validadores, servicios de carga/recuperación e idempotencia |
| Reutilización | V11, `Clock`, UUID, `ApiException`, Scheduler/ShedLock |
| Dependencias | Fase 1 |
| Riesgos | Huérfanos, archivos referenciados incompletos, consumo excesivo de memoria y eliminación accidental de objetos confirmados |
| Pruebas | Contrato común para ambos adaptadores, fallos parciales, reinicio, concurrencia, recuperación y reutilización de clave entre POST de inspección, fotografía y acta |
| Criterio de terminación | Misma batería de pruebas satisfactoria para MinIO/local y protocolo de recuperación demostrado |

#### Comportamiento idempotente

| Situación | Resultado propuesto |
|---|---|
| Misma clave y misma solicitud completada | Reproducir el resultado, sin nuevas escrituras ni auditorías duplicadas |
| Misma clave y contenido, ruta o metadatos distintos | 409 |
| Misma clave utilizada antes para otro tipo de entidad | 409 sin buscar ni devolver una inspección a partir del ID de un archivo |
| Dos solicitudes simultáneas iguales | Una reserva efectiva y una sola asociación |
| Mismo UUID de fotografía con otra clave | Detectar existencia y comparar identidad, asociación y contenido; no duplicar |
| Operación abandonada | Recuperación controlada con exclusión de intentos activos |
| Reintento tras cambiar el estado de la inspección | Distinguir reproducción de una operación ya completada de una carga nueva; siempre comprobar acceso vigente |

La huella debe incluir metadatos normalizados, destino y checksum de contenido; no depender de los separadores variables de multipart.

### Fase 3. API de fotografías de HU-028

**Objetivo:** completar el caso de uso del backend.

Contrato aditivo propuesto:

- `POST /api/v1/inspecciones/{id}/fotografias`
- `GET /api/v1/inspecciones/{id}/fotografias`
- Lectura autorizada de contenido y miniatura mediante rutas sin exponer claves internas.

La carga recibe archivo y metadatos: UUID v7, categoría, ubicación, fecha de captura y anclaje opcional. La identidad del autor procede de JWT.

Reglas:

- Inspección inexistente o ajena: 404.
- Inspección propia fuera de `BORRADOR`: 409.
- Categoría/metadatos inválidos: error de validación.
- Comprobación del estado bajo bloqueo al confirmar la asociación.
- Validación del anclaje contra la versión de la inspección, no contra la versión activa del sistema.
- Para repetibles, verificar también la instancia correspondiente.
- Usar `InspeccionRepository` existente y añadir una consulta con bloqueo para confirmar. `@Version` de la inspección no evita por sí solo carreras si la carga solo inserta en las tablas de archivos.
- La autorización de inicio de HU-024 admite Catedrático y Profesional externo verificado. Para fotografías se conserva el control de propietario y estado; cualquier regla sobre cambio posterior de rol/colegiado debe acordarse en fase 0.

| Aspecto | Detalle |
|---|---|
| Archivos existentes | `InspeccionRepository` para bloqueo; `InspeccionController`/`InspeccionService` solo si se acuerda una consulta mínima de contexto para reanudar |
| Archivos nuevos | Controlador, DTO y servicio de fotografías; no otro agregado ni repositorio paralelo de inspecciones |
| Reutilización | Infraestructura de fase 2, `Inspeccion`, repositorio HU-024 y versión de formulario asociada |
| Dependencias | Fases 0–2; creación disponible. Consulta/reanudación y persistencia de instancias siguen siendo necesarias para esos recorridos específicos |
| Riesgos | Carrera entre carga y envío/publicación; autorización tomada del servicio de confirmación con semántica incorrecta |
| Pruebas | Matriz de autor/estado, referencias inválidas, concurrencia con transición e idempotencia |
| Criterio de terminación | Carga y consulta correctas sin modificar cálculo IC, confirmación ni publicación existentes |

### Fase 4. Captura, compresión y frontend de inspecciones

**Objetivo:** completar el recorrido del inspector.

- Montar el reporte fotográfico reutilizable en el resultado de `IniciarInspeccionDialogComponent`, utilizando `res.id`, `autorId`, `estado` y `versionFormularioId`. La ficha puede consumir `inspeccionIniciada` para continuar el recorrido acordado.
- Ampliar `InspeccionApiService` y modelos de fotografía; no crear otro servicio de inicio ni otra pantalla para repetir HU-024.
- Acordar consulta autorizada por ID y reanudación. Para grupos de daño, integrar `SeccionApoyo`/renderizadores con el editor persistente cuando exista; no convertir la vista previa en un segundo editor.
- Cámara y galería.
- Extracción de EXIF antes de comprimir.
- Orientación correcta y lado mayor máximo de 1600 px.
- JPEG/WebP con ajuste de calidad hacia 200–400 KB.
- Categoría obligatoria y anclaje contextual.
- Persistencia conjunta del blob comprimido, metadatos, UUID y clave idempotente.
- Reintento conservando esos mismos valores.
- Estados visibles de pendiente, cargando, confirmado y error.
- Separación de datos locales por usuario.
- Mantener UUID/clave de una operación durante los reintentos; el diálogo actual genera un UUID nuevo por envío. La cola de fotos debe enviar primero la inspección pendiente y esperar su confirmación antes de cargar sus archivos.

El objetivo de 200–400 KB no debe convertirse automáticamente en rechazo de imágenes menores de 200 KB ni en un límite obligatorio de 400 KB.

Para cámara, ubicación y momento deben corresponder a la captura. Para galería, la ubicación actual y `lastModified` no demuestran dónde y cuándo se tomó la fotografía. Si faltan metadatos confiables, la propuesta es rechazar la carga hasta que se acuerde una política alternativa.

La compresión puede apoyarse en canvas, verificando el MIME producido y un fallback permitido. La API puede devolver PNG si el formato solicitado no está soportado, por lo que no basta con solicitar WebP. Véase la [documentación de `toBlob`](https://developer.mozilla.org/en-US/docs/Web/API/HTMLCanvasElement/toBlob).

| Aspecto | Detalle |
|---|---|
| Archivos existentes | Diálogo de inicio, `InspeccionApiService`, modelos de inspección, ficha del puente, `sgp-db.ts` y traducciones; rutas solo si se acuerda reanudación mediante navegación |
| Archivos nuevos | Procesamiento, persistencia offline y componentes fotográficos; HTTP de feature reutiliza el servicio existente |
| Reutilización | Resultado/evento HU-024, `SeccionApoyo`, `RENDERIZADORES_NODO`, motor de formularios, UUID, interceptores y conectividad |
| Dependencias | Fase 3 para carga desde resultado; consulta/reanudación y guardado de daños/instancias para completar todo el flujo contextual |
| Riesgos | Duplicar inspección al reintentar, perder contexto al cerrar, pérdida de EXIF, datos locales antiguos y asociación a instancia incorrecta |
| Pruebas | Inicio→fotografía sin recrear inspección, conservación de UUID/clave, cierre/reapertura, GPS denegado, galería sin EXIF, reordenamiento de repetibles y red interrumpida |
| Criterio de terminación | Flujo cámara/galería → compresión → persistencia local → API → consulta, sobre una inspección real |

La vista previa del formulario debe conservar su finalidad de demostración; no debe convertirse accidentalmente en un editor persistente de inspecciones.

### Fase 5. Integración y finalización de pendientes de HU-019

**Objetivo:** completar las actas usando la infraestructura ya construida.

Componentes necesarios de HU-028:

- `AlmacenamientoArchivos` y adaptadores.
- Validación real de PDF y tamaño.
- Registro de archivos y recuperación de intentos.
- Lectura privada.
- Protección contra duplicados.
- Metadatos calculados en servidor.

Cambios de HU-019:

- Nuevo `ActaCalibracionService` que resuelva la versión y autorice al administrador.
- Endpoints administrativos para carga, consulta de asociaciones y descarga.
- Asociación mediante `archivo_acta_calibracion`.
- Panel de actas en el editor de versión.
- Mensaje explícito cuando no exista acta.
- Auditoría transaccional con usuario, fecha e identificadores/metadatos anteriores y posteriores.

Se propone mantener estos contratos separados de los actuales de pesos, escala y publicación. Adjuntar un PDF no debe cambiar por sí mismo `calibradoEn`, pesos, escala ni hash del esquema.

Para la auditoría de asociación conviene reutilizar `AuditoriaWriter.registrarEnTransaccion`: el aspecto general actual registra después de confirmar y tolera fallos de auditoría; no proporciona la misma garantía de confirmación conjunta.

| Aspecto | Detalle |
|---|---|
| Archivos existentes | Servicio API frontend, editor, traducciones y documento de pendientes |
| Archivos nuevos | Controlador/servicio/DTO de actas, componente y pruebas |
| Servicios e interfaces compartidos | Los de fase 2; no se crean adaptadores exclusivos para formularios |
| Dependencias | Fases 1–2 y definición del ciclo de vida de actas. Se recomienda adelantarla tras fase 2: no requiere editor, reanudación ni sincronización de inspecciones |
| Riesgos de regresión | Alterar la publicación, permitir reemplazos indebidos, asociar a la versión activa equivocada o duplicar auditoría por reintentos |
| Pruebas de integración | PDF válido y falso, límites, acceso por todos los roles, versión incorrecta, fallo de almacenamiento, rollback de asociación y auditoría, reintentos y concurrencia |

#### Criterios de finalización de la integración

1. Un administrador puede adjuntar un PDF válido de hasta 20 MB a la versión indicada.
2. Los no administradores reciben 403; sin sesión, 401.
3. La consulta y descarga también respetan acceso administrativo.
4. La ausencia de acta se muestra explícitamente.
5. Archivo, asociación y auditoría son consistentes.
6. Las pruebas existentes de pesos, escala, publicación e inmutabilidad siguen funcionando.
7. El documento pendiente se actualiza con evidencia de integración.

### Fase 6. Integración, regresión y cierre

**Objetivo:** demostrar el funcionamiento conjunto y la compatibilidad.

| Aspecto | Detalle |
|---|---|
| Archivos existentes/nuevos | Pruebas backend/frontend, CI y documentación de resultados |
| Reutilización | Infraestructura actual de pruebas |
| Dependencias | Fases anteriores, creación HU-024 disponible y contratos de continuación/edición resueltos para los recorridos de daños y repetibles |
| Riesgos | Pruebas aisladas satisfactorias que oculten fallos de proxy, navegador o almacenamiento real |
| Pruebas | Las descritas en G, incluyendo HTTP real para límites y ambos adaptadores |
| Criterio de terminación | Criterios de HU-028 y pendientes de HU-019 verificados, con limitaciones documentadas y sin regresiones conocidas |

## F. Análisis de riesgos

| Riesgo | Evidencia/impacto | Mitigación |
|---|---|---|
| Edición/reanudación pendiente | HU-024 crea borrador, pero no ofrece consulta por ID, edición de daños ni consumidor del evento en la ficha | Integrar carga desde resultado y coordinar continuidad con el editor; no declarar terminado el recorrido de daños con solo pruebas simuladas |
| Colisiones idempotentes entre módulos | HU-024 reproduce por usuario/clave sin validar ruta, hash ni entidad | Protección mínima del consumidor existente antes de habilitar cargas; pruebas cruzadas y claves independientes |
| Reintento de inicio crea otro UUID | El diálogo genera identidad dentro del handler | Conservar operación hasta confirmar y probar pérdida de respuesta del servidor |
| UUID de inspección ajeno | Falta rechazo explícito antes de `save` | Probar colisión con persistencia real y rechazar antes de crear/mezclar el agregado |
| Divergencia DTO de acompañantes | `colegiado` frente a `numeroColegiado`, `id` frente a `usuarioId` | Prueba de contrato extremo a extremo; no dar por cubierto el contrato por mocks |
| Validación del esquema JPA pendiente | Nuevo mapeo de año `Integer` frente a `SMALLINT` SQL | Ejecutar `ddl-auto=validate` en PostGIS aislado y resolver solo incompatibilidades demostradas |
| Cambio accidental de HU-019 | Publicación actual no requiere acta | Mantener comportamiento salvo decisión expresa |
| Autorización incorrecta | Confirmación IC usa reglas distintas | Política específica para fotografías y pruebas 404/409 |
| Carrera con envío/publicación | Validar antes del almacenamiento no basta | Revalidar bajo bloqueo al confirmar; respetar orden puente→inspección si se bloquean ambos |
| Fallos entre BD y almacenamiento | No hay transacción distribuida | Registro de intentos, compensación y recuperación persistente |
| Duplicados concurrentes | PK del archivo no resuelve toda la operación | Reserva V11, huella y unicidad de asociaciones |
| PDF o imagen falsificados | Nombre y MIME del cliente no son prueba | Parser/decodificación real y límites de recursos |
| Pérdida de GPS/fecha | Compresión elimina EXIF | Extraer antes y guardar datos explícitos |
| Daño a evidencia PDF | Reescribir un acta puede alterar firmas | Validar y conservar bytes originales; no normalizar PDF como una imagen |
| Exposición de documentos | URL directa eludiría permisos de entidad | Almacenamiento privado y endpoints autorizados |
| Filtración entre sesiones locales | Dexie no incluye propietario en `LocalPhoto` | Aislamiento por usuario y pruebas de cambio de sesión |
| Cambio de proveedor | Igual interfaz no traslada archivos existentes | Plan operativo de copia/verificación al migrar almacenamiento |
| Presupuesto frontend cercano al máximo | Build actual: 495.31 kB iniciales frente a 500 kB de error | Mantener procesamiento/componentes fotográficos en carga diferida cuando corresponda y comprobar presupuesto en fase 4 |
| Línea base de calidad no completamente verde | Spotless falla en 3 archivos y Prettier en `es.json` | Registrar como preexistente; corregir de forma acotada en trabajo autorizado antes de exigir CI verde para la integración |
| Regresión del formulario | Adjuntos mezclados con JSON evaluable | Mantener asociaciones fuera de `datos` y del cálculo IC |
| Incompatibilidad con HU-029 | Mínimos, máximo 60, retirada y lectura privada pendientes | Contar solo fotos disponibles y mantener políticas extensibles sin anticipar reglas no acordadas |

HU-029 debe reutilizar el mismo servicio de fotografías. Sus futuras reglas de mínimo por categoría, máximo de 60 y retirada no justifican otra infraestructura.

## G. Estrategia de pruebas

### Cobertura de criterios

| Criterios de B | Pruebas necesarias |
|---|---|
| 1–2 | Cámara/galería, JPEG/WebP, orientación, dimensiones, compresión y fallback |
| 3–5 | Categorías válidas/inválidas, ausencia de categoría, anclaje opcional, referencia ajena y repetibles |
| 6–7 | Conservación de GPS/fecha después de quitar EXIF, ausencia de ambos y coordenadas inválidas |
| 8–10 | Original/miniatura, nombres hostiles, claves generadas, lectura y fallos en ambos adaptadores |
| 11 | Autor/otro usuario, todos los estados, sesión ausente y carrera con transición |
| 12 | Repetición secuencial y concurrente, clave reutilizada con otro cuerpo, UUID duplicado y caída tras confirmar |
| 13 | Peticiones alrededor de 25 MB atravesando Nginx y servidor real |
| 14/HU-019 | PDF verdadero/falso/truncado, límite de 20 MB, autorización, asociación, ausencia y auditoría |

Las pruebas de tamaño deben cubrir el límite exacto y un byte adicional. Para multipart hay que distinguir tamaño del archivo de tamaño total de la petición. MockMvc por sí solo no sustituye esta comprobación.

### Pruebas adicionales tras HU-024

- Crear una inspección mediante el servicio real, asociar una foto y verificar autor, estado, versión y claves foráneas en PostGIS aislado.
- Validar los nuevos mapeos JPA, cascadas de acompañantes y datos técnicos sin modificar las tablas V8/V10/V11 existentes.
- Clave igual con distinto cuerpo, ruta o entidad; UUID ajeno; creación y cargas concurrentes. Una operación fotográfica nunca debe reproducirse como una inspección.
- Mantener UUID/clave tras timeout y comprobar que inicio y fotografía no se duplican; ordenar la cola para confirmar inspección antes de archivos.
- Cambiar la versión activa después del inicio y validar la foto contra la versión fija original.
- Confirmar que `datos={}` y la precarga técnica no cambian al adjuntar archivos ni alteran el cálculo IC.
- Contrato JSON de acompañantes entre frontend y backend, incluidos nombres de campos y validación en cascada.
- Consumir el resultado/evento de inicio, cerrar/reanudar y evitar exposición de borradores desde la ficha pública.
- Probar grupos de daño y anclajes repetibles solo con instancias persistidas, incluyendo reordenamiento/eliminación y cambios de estado concurrentes.

### Regresión existente

Las siguientes pruebas deben conservarse:

- `T/inspeccion/service/InspeccionServiceTest.java`
- `T/inspeccion/controller/InspeccionControllerTest.java`
- `T/formulario/service/VersionFormularioServiceTest.java`
- `T/formulario/service/VersionFormularioAuditoriaSnapshotProviderTest.java`
- `T/formulario/controller/VersionFormularioControllerSecurityTest.java`
- `T/integration/VersionFormularioIntegrationTest.java`
- `T/integration/DatabaseMigrationIntegrationTest.java`
- `T/integration/PublicacionIndiceCondicionIntegrationTest.java`
- `T/integration/ConfirmacionEstadoIcIntegrationTest.java`
- Pruebas de auditoría, cálculo IC y acceso al catálogo.
- Pruebas frontend del servicio de formularios, editor, vista previa, motor dinámico y privacidad del catálogo.
- `F/features/inspecciones/components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component.spec.ts` y `F/features/puentes/pages/ficha-puente/ficha-puente.page.spec.ts`.

La regresión debe comprobar especialmente que agregar fotografías o actas no cambia entradas del cálculo IC, pesos ni versiones publicadas.

### Validaciones previstas después de implementar

- Backend con Java 21: `mvn -B spotless:check verify`.
- Frontend: `npm test`, `npm run lint`, `npm run build`, `npm run format:check`.
- Integración PostGIS + MinIO y contrato equivalente con directorio temporal.
- Recorrido real en navegador móvil, porque Vitest/jsdom no demuestra cámara, GPS y codificación reales.
- `git diff --check` y revisión de alcance.

### Diagnóstico ejecutado en la reevaluación

Las verificaciones se ejecutaron sobre una copia temporal de la revisión `97d3b2d`, sin `.env`, usando dependencias ya instaladas y Maven offline. Los reportes/cachés permanecen en `/tmp/hu028-baseline.ShTVT6`; no se instalaron dependencias ni se modificó código/configuración del proyecto. Las ejecuciones interrumpidas anteriormente no se cuentan como pruebas aprobadas.

| Verificación | Resultado | Alcance y limitación |
|---|---|---|
| Java/Maven | Java 21.0.12, Maven 3.8.7 | Entorno de backend compatible |
| Node | Terminal: 18.20.8; pruebas: Node 24.19.0 ya instalado | Se utilizó el ejecutable existente mediante PATH temporal; no se instaló ni cambió configuración |
| Compilación backend y pruebas | Fuentes principales y de pruebas compiladas | No demuestra validación de mapeos contra PostgreSQL |
| Backend relacionado, ejecución estándar | 160 pruebas: 92 errores de entorno, 0 fallos de aserción | Mockito no pudo inicializar su instrumentación; no es evidencia de 92 defectos de negocio |
| Backend relacionado, agente al arrancar JVM | **160 aprobadas, 0 errores/fallos**, en 8 clases | Byte Buddy y JaCoCo ya instalados, proporcionados únicamente mediante `-DargLine`; misma selección de pruebas |
| Frontend relacionado | **217 aprobadas en 12 archivos** | Diálogo, ficha, editor/vista previa, motor y servicios de formularios; HTTP/GPS simulados, sin recorrido real de cámara/BD |
| `npm run lint` | Aprobado | Verificación de frontend completo en copia temporal |
| `npm run build` | Aprobado con advertencias | Inicial: 495.31 kB; SCSS del editor: 9.58 kB; advertencia CommonJS de MapLibre. No se aumentaron presupuestos |
| `npm run format:check` | Falló | Formato preexistente de `sgp-client/public/i18n/es.json`; no se aplicó formateo |
| `mvn -o -B spotless:check` | Falló | Formato en `AcompananteResponse.java`, `InspeccionService.java` e `InspeccionServiceTest.java`; no se aplicó corrección |
| Testcontainers/PostGIS/MinIO | No ejecutados | El acceso al socket Docker dio `permission denied`; no se iniciaron servicios ni se ejecutaron migraciones contra bases existentes |

Comandos de selección ejecutados, desde la copia temporal:

```bash
mvn -o -B -Dstyle.color=never \
  -Dtest=InspeccionServiceTest,InspeccionControllerTest,VersionFormularioServiceTest,VersionFormularioControllerSecurityTest,VersionFormularioAuditoriaSnapshotProviderTest,ConfirmacionEstadoIcControllerSecurityTest,CalculoIndiceCondicionServiceTest,PreparadorEntradaIcServiceTest test

# Repetición de la misma selección con instrumentación al arrancar la JVM:
# -DargLine="-javaagent:<byte-buddy-agent-1.14.19.jar instalado> -javaagent:<jacoco-agent-0.8.12.jar instalado>=destfile=<ruta temporal>"

npm test -- \
  --include='src/app/features/inspecciones/**/*.spec.ts' \
  --include='src/app/features/puentes/pages/ficha-puente/*.spec.ts' \
  --include='src/app/features/formulario/**/*.spec.ts'
```

La persistencia real, concurrencia y rollback de HU-024 siguen sin verificación de integración en esta revisión. Sus nuevas pruebas de servicio utilizan repositorios simulados y las de controlador simulan el servicio. No se afirma que HU-024 esté completamente validada por estos resultados.

La configuración local de MinIO sigue presente con API interna `minio:9000`, publicación local `127.0.0.1:19000`, consola `19001` y volumen `minio_data`. La integración de HU-024 no modificó Compose ni añadió consumidor/bucket en backend. Se conserva la infraestructura definida y se mantiene pendiente comprobar disponibilidad, bucket y permisos; la conectividad y la imagen no se declaran verificadas por inspección de configuración.

## H. Decisiones técnicas pendientes

| Decisión | Recomendación |
|---|---|
| Continuación/edición del borrador | Creación disponible en `97d3b2d`; acordar consulta por ID, reanudación, guardado de `datos` y responsable de esos contratos antes del cierre de fase 4 |
| Integración visual | Reutilizar resultado/evento del diálogo de inicio y el motor existente; decidir cómo reanudar sin otro editor paralelo |
| Coexistencia idempotente | Autorizar el ajuste acotado en `InspeccionService` antes de compartir V11, definir respuesta ante clave de otro endpoint y tiempo de reserva |
| Correcciones preexistentes HU-024 | Confirmar alcance de correcciones de DTO/validación/UUID; los problemas de idempotencia que afectan directamente la coexistencia deben resolverse antes de habilitar cargas |
| Una o varias actas por versión | Acordarlo antes de imponer unicidad; V10 admite varias |
| Actas sobre versiones publicadas | Definir si se permite adjunción inicial a versiones históricas y qué ocurre con reemplazos; mantener intacta la configuración publicada |
| Acta obligatoria para publicar | No introducir el requisito sin confirmación; actualmente no existe |
| Permisos posteriores al inicio | HU-024 permite Catedrático y Profesional externo verificado. Confirmar efecto de cambios posteriores de rol/colegiado sobre carga; autor y `BORRADOR` siguen siendo obligatorios |
| Fotografías de galería sin GPS/fecha | Rechazarlas por defecto; cualquier captura manual de esos datos requiere una regla acordada |
| PDF cifrado o no inspeccionable | Rechazar si no puede validarse; confirmar compatibilidad esperada con actas firmadas |
| Interpretación de miniatura “300 px” | Lado mayor máximo de 300 px, proporcional y sin ampliar imágenes pequeñas |
| Formatos de entrada desde galería | Acordar si se aceptan formatos convertibles en dispositivo; backend recibe únicamente JPEG/WebP |
| SDK y soporte S3/IAM | Verificar la compatibilidad del adaptador de objetos con MinIO local y el despliegue AWS existente |
| Recuperación y retención | Fijar tiempo máximo de intento, recuperación de reservas y conservación de resultados idempotentes |
| Error de validación | Conservar 400 para entrada mal formada y 422 para reglas semánticas, sin modificar globalmente contratos existentes |

## I. Recomendación final y orden de implementación

El orden recomendado es:

1. **Fase 0:** cerrar contratos restantes y decisiones; HU-024 de creación ya está localizada e integrada.
2. **Fase 1:** modelos de archivos, asociaciones y ampliaciones mínimas, reutilizando `Inspeccion` y `OperacionIdempotente`.
3. **Fase 2:** almacenamiento, validación, recuperación e idempotencia compartidos, incluida protección de coexistencia con HU-024.
4. **Fase 5:** integrar y finalizar HU-019. Se recomienda adelantarla porque permite validar almacenamiento, PDF, autorización y auditoría sin depender de edición de daños o del motor de sincronización.
5. **Fase 3:** API fotográfica sobre el agregado HU-024 existente.
6. **Fase 4:** fotografía desde el resultado de inicio, reanudación, captura, compresión, cola y anclajes coordinados con el editor persistente.
7. **Fase 6:** integración y regresión conjunta. Ejecutar las pruebas aplicables también al terminar cada fase, no solo al final.

HU-024 permite comenzar los modelos y la infraestructura de HU-028: la ausencia total de inspección dejó de ser un bloqueo. El inicio de fase 1 es técnicamente viable para su alcance compartido, después de confirmar las decisiones que afectan el esquema y autorizar implementación. La preparación no está completamente cerrada: quedan pruebas PostGIS/MinIO, protección idempotente cruzada, contratos de continuación/daños/offline y fallos preexistentes de formato que impiden una línea base de CI completamente verde.

La arquitectura compartida sigue siendo válida. Se mantienen las siete fases, ajustando 0–4 y 6; la fase 5 conserva íntegramente los pendientes de actas y solo cambia su posición recomendada. No se requiere recrear HU-024 ni cambiar pesos, escala, cálculo IC o publicación por integrar archivos.

La implementación de las fases queda pendiente de autorización expresa. Este documento es una propuesta de trabajo, no evidencia de funcionalidades ya implementadas.
