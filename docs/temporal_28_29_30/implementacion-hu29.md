# Preparación de HU-029 — Validación de evidencia fotográfica y seguridad de archivos

Fecha: 15 de octubre de 2026. Revisión analizada: `2d9f40a`.

Este documento propone contratos para desarrollar HU-029 y HU-028 en ramas independientes. No acredita implementación ni aprobación de las firmas propuestas. Mushin puede comenzar las reglas y pruebas indicadas aquí; los contratos compartidos deben acordarse antes de que ambos responsables creen archivos con los mismos nombres.

## 1. Fuentes y límites del análisis

Se revisaron [AGENTS.md](../../AGENTS.md), [README.md](../../README.md), [ARCHITECTURE.md](../ARCHITECTURE.md), la [propuesta HU-028](implementacion-hu28.md), V8/V10/V11, inspecciones, seguridad JWT, errores y los contratos pendientes de publicación y actas. Se contrastó la propuesta con el código actual.

No están disponibles en este checkout `HUS/hu29.md`, `HUS/hu28.md` ni las definiciones completas de RN-ARC referenciadas por la propuesta. Aunque esta afirma haber leído esas historias, sus enlaces no permiten recuperarlas aquí. El usuario confirma para HU-029 evidencia obligatoria, máximo de 60 fotografías, contenido JPEG/WebP real, acceso autorizado y eliminación dependiente del estado. La propuesta menciona mínimos por categoría, pero no sus cantidades.

Por tanto, los mínimos exactos, la matriz de lectura por rol y los estados que admiten eliminación necesitan confirmación de la historia original. Las decisiones provisionales se identifican expresamente; no deben convertirse silenciosamente en reglas definitivas. No se deducen requisitos fotográficos de las condiciones críticas del IC.

## 2. Estado actual comprobado

Las rutas Java siguientes parten de `sgp-api/src/main/java/gt/usac/cunoc/sgp/`.

| Área | Existe actualmente | Reutilización y límite |
|---|---|---|
| Creación HU-024 | `InspeccionController`, `InspeccionService`, `IniciarInspeccionResponse` | POST `/api/v1/inspecciones` crea BORRADOR y devuelve ID, autor, estado y versión fija. No repetir este flujo. |
| Inspección | `inspeccion/entity/Inspeccion.java` | `getId()`, `getAutorId()`, `getEstado()`, `getEliminadoEn()`, `getVersionFormulario()` y `@Version`. No existe máquina completa de transiciones. |
| Repositorio | `inspeccion/repository/InspeccionRepository.java` | `Optional<Inspeccion> findConDetallesById(UUID id)` y `Optional<Inspeccion> findByIdAndEliminadoEnIsNull(UUID id)`. Todavía no tiene consulta con bloqueo. |
| Esquema de archivos | V10 `archivo`, `archivo_inspeccion`, `archivo_acta_calibracion`, `archivo_evidencia_mantenimiento` | Fotos con categoría, GPS, fecha, dimensiones, checksum, original/miniatura; relación a inspección y anclaje opcional. No hay entidades ni repositorios Java: `archivo/` contiene solo `.gitkeep`. |
| Categorías y estados SQL | Categorías ACCESO, SUPERESTRUCTURA, SUBESTRUCTURA, CAUCE, OTRA; estados DISPONIBLE, RETIRADO, PURGADO | No existen todavía sus enums Java. V10 no impone mínimos ni máximo 60 y no verifica bytes de imagen. |
| Seguridad | JWT, `JwtData`, `@PreAuthorize`, `SecurityConfiguration` | Identidad autenticada; las rutas nuevas requieren sesión por defecto. Esto no autoriza por inspección ni por archivo. No existe política de lectura de fotografías. |
| Errores | `ApiException`, `ApiExceptionHandler`, `ProblemDetails` | RFC 7807 con propiedad `code`. La API no devuelve entidades JPA. Falta manejo específico de exceso multipart. |
| Idempotencia | V11, `OperacionIdempotente`, `OperacionIdempotenteRepository` | Solo reproducción parcial en inicio. No hay servicio común, reserva concurrente ni comparación segura de método/ruta/huella. Lo resolverá HU-028, no una segunda implementación HU-029. |
| Almacenamiento | Compose local MinIO y ADR de AWS/S3 | No existen `AlmacenamientoArchivos`, adaptadores, decodificador ni endpoints de fotografías. Mantener MinIO local y S3 privado con IAM en AWS. |
| Publicación | `PublicacionIndiceCondicionService.integrarPublicacion(UUID inspeccionId, UUID usuarioId)` con transacción MANDATORY | Es apoyo al IC; exige una inspección ya PUBLICADA dentro de la transacción. No sustituye el flujo HU-026 ni valida evidencia fotográfica. |
| Frontend | Diálogo de inicio, servicio HTTP, evento `inspeccionIniciada`, UUID v7 y tablas Dexie | No hay reporte fotográfico funcional, editor persistente, consulta por ID ni motor de sincronización. HU-029 no creará otro editor. |

Referencias de código: [repositorio de inspecciones](../../sgp-api/src/main/java/gt/usac/cunoc/sgp/inspeccion/repository/InspeccionRepository.java), [V10](../../sgp-api/src/main/resources/db/migration/V10__mantenimiento_y_archivos.sql), [seguridad](../../sgp-api/src/main/java/gt/usac/cunoc/sgp/common/config/SecurityConfiguration.java) y [contrato HU-026](../pendientes/HU-020-integracion-publicacion.md).

## 3. Responsabilidades y propietario de cada integración

| Capacidad | HU-028 | HU-029 — Mushin |
|---|---|---|
| Modelo persistente | Entidades, repositorios, enums y consultas V10; bloqueo de inspección compartido | Consumir esos modelos, sin entidades o repositorios alternativos |
| Captura y carga | Cámara/galería, EXIF/GPS/fecha, compresión, miniaturas, anclajes y POST | Regla de capacidad máxima y pruebas de que la carga la invoque |
| Contenido real | Decodificador único, procesamiento, límites de recursos y dependencia JPEG/WebP | Política de formatos admitidos y pruebas de rechazo; consumir el resultado del decodificador |
| Almacenamiento | Interfaz, adaptadores S3/MinIO/local, claves del servidor y objetos privados | Verificar que todas las vías de acceso respeten autorización; no crear otro cliente de objetos |
| Integridad | Idempotencia, confirmación, compensación y recuperación de cargas | Reutilizar garantías; comprobar que un reintento confirmado no consume otra plaza |
| Autorización fotográfica | Invocar la política única en POST, listado, original y miniatura | Implementar política de acceso y mutación por autor/estado; pruebas de seguridad |
| Evidencia obligatoria | Proveer inventario confirmado, sin consultar binarios para cada conteo | Evaluar mínimos acordados y resultado estructurado; servicio invocable por HU-026 |
| Eliminación | Operación persistente de retirada sobre modelo compartido | Caso de uso y endpoint de retirada; validar estado y autor; no borrar bytes directamente |
| Interfaz | Panel y servicio API fotográficos; mostrar errores devueltos y acción de retirada | Entregar contratos/errores y pruebas de servicio/seguridad; no duplicar panel ni editor |
| Publicación | No implementar HU-026 | Entregar validador; HU-026 lo invoca en su propia transacción |

La seguridad básica no puede posponerse hasta integrar HU-029: una entrega parcial de HU-028 debe mantener autor y BORRADOR y acceso privado. Esos controles deben converger en la política única propuesta, sin sustituirlos temporalmente por un permiso universal. No introducir un bean de producción permisivo cuando falte HU-029.

HU-019 conserva PDF, permisos administrativos de actas y asociación a versión. HU-029 no aplica JPEG/WebP a todas las clases de archivo: su validador fotográfico se utiliza para FOTO_INSPECCION, no para ACTA_CALIBRACION ni documentos PDF.

## 4. Contratos compartidos propuestos

**Todo el contenido de esta sección es nuevo y pendiente de acuerdo.** Los métodos existentes se enumeraron en la sección 2. Los tipos siguientes son contratos de Java, no entidades ni una nueva capa arquitectónica. Cada record/enum se materializará en su archivo correspondiente solo cuando se utilice.

### 4.1. Modelos de fotografías — propiedad HU-028

Paquetes `archivo/model/` e `inspeccion/model/`, respectivamente:

```java
public enum CategoriaFoto { ACCESO, SUPERESTRUCTURA, SUBESTRUCTURA, CAUCE, OTRA }
public enum EstadoArchivo { DISPONIBLE, RETIRADO, PURGADO }

public record FotografiaRegistrada(
    UUID id, UUID inspeccionId, CategoriaFoto categoria, EstadoArchivo estado,
    String elementoRef, UUID instanciaElementoId) {}

public record ContextoArchivosInspeccion(
    UUID inspeccionId, UUID autorId, String estado, Instant eliminadoEn) {}
```

Conservar `String estado` de inspección evita introducir otro enum incompatible con HU-024. Los resultados del inventario deben ser inmutables y provenir del servidor. `FotografiaRegistrada` no contiene claves físicas ni datos del proveedor.

### 4.2. Inventario y retirada persistente — implementación HU-028

Interfaz propuesta en `archivo/service/RegistroFotografiasInspeccion.java`, implementada sobre los repositorios de HU-028:

```java
public interface RegistroFotografiasInspeccion {
  List<FotografiaRegistrada> consultar(UUID inspeccionId);
  Optional<FotografiaRegistrada> buscar(UUID inspeccionId, UUID fotografiaId);
  void retirar(UUID inspeccionId, UUID fotografiaId, Instant retiradoEn);
}
```

`consultar` devuelve solo FOTO_INSPECCION del destino, incluyendo sus estados para poder probar exclusiones. `buscar` exige coincidencia de ambos IDs: un UUID válido de otra inspección no autoriza acceso. `retirar` cambia DISPONIBLE a RETIRADO y fija `retirado_en`; un RETIRADO permanece sin cambios, PURGADO no se reactiva y un vínculo inexistente se rechaza. No borra objetos ni confirma una transacción independiente.

Son métodos internos sin autorización HTTP propia: sus consumidores de dominio deben autorizar y bloquear antes de mutar. No se exponen directamente en un controlador. La implementación participa en la transacción del llamador y no utiliza REQUIRES_NEW para retirar.

El responsable HU-028 añade una sola firma al repositorio existente:

```java
Optional<Inspeccion> findByIdAndEliminadoEnIsNullForUpdate(UUID id);
```

Será una consulta explícita con `@Query` y `@Lock(PESSIMISTIC_WRITE)`, no un nombre derivado asumido. HU-028 y HU-029 la usan dentro de transacciones. La publicación HU-026 debe bloquear esa misma fila. Mushin puede simular este método tras recibir el contrato mínimo; no crear otro repositorio de inspecciones.

### 4.3. Contenido real — decodificador HU-028 y política HU-029

Contrato HU-028 en `archivo/service/InspectorImagenes.java` y record en `archivo/model/`:

```java
public interface InspectorImagenes {
  ImagenInspeccionada inspeccionar(Path archivoTemporal);
}

public record ImagenInspeccionada(String mimeReal, int anchoPx, int altoPx) {}
```

El Path solo lo crea el servidor durante la recepción; jamás viene del cliente. `inspeccionar` verifica estructura y decodifica la imagen con límites de bytes/píxeles, rechazando archivos vacíos, truncados, corruptos o que no puedan procesarse. `mimeReal` proviene del lector real, no de extensión ni Content-Type. HU-028 es el único propietario del decoder y del soporte WebP; procesamiento y validación deben usar los mismos bytes temporales inmutables. El tipo y metadatos del original persistido se recalculan si el procesador transforma el formato.

Servicio HU-029 en `archivo/validation/ValidadorContenidoFotografico.java`:

```java
public ImagenInspeccionada validar(Path archivoTemporal);
```

Delega la inspección a `InspectorImagenes`, permite únicamente `image/jpeg` y `image/webp`, exige dimensiones positivas y traduce rechazo de contenido a un error de dominio acordado. HU-028 mantiene compresión, máximo 1600 px y generación de miniatura: no hacer otro procesador en HU-029. Los límites técnicos del decoder se definen una sola vez en HU-028; las pruebas reales de JPEG/WebP se completan al integrar esa implementación.

### 4.4. Políticas y evidencia — propiedad HU-029

Servicio sin I/O en `inspeccion/service/PoliticaArchivosInspeccion.java`:

```java
public void validarCarga(ContextoArchivosInspeccion contexto, UUID actorId,
                         long fotografiasDisponibles);
public void validarLectura(ContextoArchivosInspeccion contexto, UUID actorId);
public void validarRetirada(ContextoArchivosInspeccion contexto, UUID actorId);
```

Servicio sin I/O en `inspeccion/service/ReglasEvidenciaFotografica.java`:

```java
public ValidacionEvidenciaResponse evaluar(
    List<FotografiaRegistrada> fotografias,
    Map<CategoriaFoto, Integer> minimosPorCategoria);
```

El inventario debe corresponder a una sola inspección, con IDs únicos; el adaptador real garantiza esa condición. Los mínimos son una política del servidor, nunca un parámetro HTTP del inspector. Su origen y valores definitivos deben acordarse; el mapa permite pruebas sin inventar cantidades. No añadir una pantalla de configuración ni una tabla de reglas. Una política obligatoria sin definir no puede interpretarse como un mapa vacío que autorice publicar.

DTO HU-029 en `inspeccion/dto/`:

```java
public record FaltanteEvidenciaResponse(
    CategoriaFoto categoria, int requeridas, long disponibles) {}

public record ValidacionEvidenciaResponse(
    boolean valida, long totalDisponibles, int maximoPermitido,
    List<FaltanteEvidenciaResponse> faltantes) {}
```

Los records deben copiar sus listas defensivamente. El resultado distingue falta de evidencia de fallo técnico de consulta; un error de BD no equivale a cero fotos ni a validación satisfactoria.

Orquestación HU-029 en `inspeccion/service/EvidenciaFotograficaService.java`:

```java
public ValidacionEvidenciaResponse consultar(UUID inspeccionId, UUID actorId);
public void exigirParaPublicar(UUID inspeccionId);
```

`consultar` carga contexto, autoriza lectura y evalúa el inventario. `exigirParaPublicar` exige una transacción existente (`Propagation.MANDATORY`), la inspección bloqueada por HU-026 y la política definitiva de mínimos; si no se cumple evidencia lanza ApiException con 422 y código `evidencia_fotografica_incompleta`. No exige que el publicador sea el autor: la autorización de publicar pertenece a HU-026. Es una entrada interna que no se expone directamente por HTTP.

Caso de uso HU-029 en `inspeccion/service/RetiradaFotografiaService.java`:

```java
public void retirar(UUID inspeccionId, UUID fotografiaId, UUID actorId);
```

Dentro de una transacción: bloquear inspección no eliminada, validar política, buscar por ambos IDs y llamar a `RegistroFotografiasInspeccion.retirar` con `Clock.instant()`. La repetición no cambia la fecha del primer retiro. El servicio no recibe claves de almacenamiento ni llama a `AlmacenamientoArchivos`.

### 4.5. API y errores a acordar

| Propietario | Ruta propuesta | Resultado |
|---|---|---|
| HU-028 | POST `/api/v1/inspecciones/{id}/fotografias` | 201 con fotografía confirmada; `Idempotency-Key` y UUID v7 por fotografía |
| HU-028 | GET `/api/v1/inspecciones/{id}/fotografias` | 200 con fotografías DISPONIBLE; excluir retiradas y claves físicas |
| HU-028 | GET `/api/v1/inspecciones/{id}/fotografias/{fotoId}/contenido` | 200 bytes con MIME real, autorización y cabeceras privadas |
| HU-028 | GET `/api/v1/inspecciones/{id}/fotografias/{fotoId}/miniatura` | Mismos controles del original |
| HU-029 | GET `/api/v1/inspecciones/{id}/evidencia-fotografica` | 200 `ValidacionEvidenciaResponse`, aunque falten fotografías; es diagnóstico autorizado |
| HU-029 | DELETE `/api/v1/inspecciones/{id}/fotografias/{fotoId}` | 204 tras retirada lógica, incluida repetición autorizada |

Para el POST, propuesta mínima en `inspeccion/dto/FotografiaResponse.java`, propiedad HU-028:

```java
public record FotografiaResponse(
    UUID id, UUID inspeccionId, CategoriaFoto categoria,
    String elementoRef, UUID instanciaElementoId, String mimeReal,
    long tamanoBytes, int anchoPx, int altoPx,
    double latitud, double longitud, Instant capturadoEn) {}
```

El listado usa el mismo DTO; el cliente construye las rutas de contenido mediante su servicio de feature. No serializar claves, entidades JPA ni URL pública. Latitud/longitud de este DTO son metadatos confirmados, no evidencia de autenticidad de lo enviado por el dispositivo.

| Situación | HTTP y `code` propuestos |
|---|---|
| Sin sesión | 401, conservar contrato de autenticación existente |
| Inspección ausente, eliminada o sin acceso | 404 `inspeccion_no_encontrada` |
| Archivo ajeno, no asociado o no disponible para lectura | 404 `fotografia_no_encontrada` |
| Mutación fuera de estado permitido | 409 `estado_inspeccion_no_permite_archivos` |
| Nueva fotografía cuando ya hay 60 disponibles | 422 `limite_fotografias_excedido` |
| Contenido no JPEG/WebP real o corrupto | 422 `contenido_fotografico_invalido` |
| Categoría, GPS, fecha o anclaje semánticamente inválidos | 422; códigos específicos definidos por HU-028 |
| UUID/JSON/parte multipart mal formados | 400; mantener formato ProblemDetail existente |
| Exceso de tamaño | 413; handler/proxy propiedad HU-028 |
| Misma clave idempotente con solicitud diferente | 409; contrato de idempotencia propiedad HU-028 |

`ApiException` y el handler actual bastan para los errores simples. El GET diagnóstico entrega los faltantes estructurados; no cambiar globalmente el handler para duplicar ese DTO en errores. El servidor decide códigos y el frontend los traduce mediante Transloco.

## 5. Reglas de HU-029 y decisiones pendientes

### Evidencia obligatoria

Contar solo fotos FOTO_INSPECCION, asociadas al destino y DISPONIBLE, confirmadas por HU-028 con original y miniatura completos. No contar blobs locales, intentos EN_PROCESO, documentos, fotos de otras inspecciones ni retiradas/purgadas. Calcular totales y faltantes por categoría sobre el mismo inventario. Con la política de mínimos ya definida, `valida` exige tanto ausencia de faltantes como `totalDisponibles <= 60`; `maximoPermitido` devuelve 60.

Las categorías permitidas no significan que todas sean obligatorias. No imponer mínimo a OTRA ni inventar cantidades para ACCESO/SUPERESTRUCTURA/SUBESTRUCTURA/CAUCE. Los valores deben obtenerse de HU-029 original. Los ejemplos de tests con mínimos suministrados son datos de prueba, no requisitos académicos aprobados. Tampoco se incorpora evidencia por daño o condición crítica sin un requisito explícito.

### Máximo de 60 y concurrencia

Propuesta a ratificar: máximo de 60 fotografías DISPONIBLE por inspección. Una retirada libera una plaza; documentos y fotos históricas retiradas no consumen capacidad. Si la historia exige contar todas las cargas históricas, debe cambiarse esta interpretación antes de integrar.

Una nueva carga con 59 puede dejar 60; con 60 se rechaza antes de asociar. La validación definitiva se ejecuta después de bloquear la inspección, contando de nuevo dentro de la transacción de confirmación. Dos cargas concurrentes no pueden ambas aprobar el conteo 59. La retirada toma el mismo bloqueo. Un reintento idempotente ya confirmado reproduce su resultado y no pasa como carga nueva por el control de capacidad.

El frontend puede advertir el límite, pero no es autoridad. Si se escribieron objetos antes del rechazo final, HU-028 compensa exclusivamente los objetos de ese intento. Un archivo no se marca DISPONIBLE para reservar plaza antes de completar sus bytes y metadatos.

### Seguridad y eliminación por estado

La matriz siguiente es una propuesta conservadora hasta recuperar la historia; los permisos de carga autor/BORRADOR coinciden con la propuesta HU-028. La lectura y la retirada requieren ratificación específica.

| Estado de inspección propia no eliminada | Nueva carga | Lectura de DISPONIBLE | Retirada |
|---|---|---|---|
| BORRADOR | Permitida, con capacidad y contenido válidos | Permitida | Permitida |
| ENVIADA | 409 | Permitida al autor | 409 |
| EN_REVISION | 409 | Permitida al autor | 409 |
| CAMBIOS_SOLICITADOS | 409 | Permitida al autor | 409 |
| PUBLICADA | 409 | Permitida al autor | 409 |
| RECHAZADA | 409 | Permitida al autor | 409 |

Otro autor recibe 404 antes de revelar estado o existencia de fotos. No conceder acceso automático a administrador, estudiante o revisor únicamente por su rol. Si HU-029 requiere lectura de revisores o evidencia pública de inspecciones publicadas, acordar las relaciones y condiciones exactas antes de ampliar la política. El catálogo público del puente no convierte sus archivos en públicos. Los cambios de rol/verificación posteriores al inicio también requieren coordinación con HU-024; no copiar sin análisis su autorización de creación.

La eliminación propuesta es retirada lógica usando V10, no DELETE SQL ni borrado físico. Conservar asociación y metadatos, excluirla del conteo y denegar lectura posterior. RETIRADO repetido devuelve 204 solo si el solicitante sigue autorizado y el estado sigue permitiendo mutar; PURGADO devuelve 404. `motivo_retiro` es opcional en el esquema: no añadir obligatoriedad sin requisito. La purga física, plazos de retención y recuperación operativa no forman parte de este caso de uso.

Original y miniatura deben autorizar el par inspección/foto, estado del archivo y sesión antes de abrir el objeto. Nunca aceptar claves o rutas del cliente. Lectura por backend con `Cache-Control: private, no-store`, MIME calculado y `nosniff`; nombres de respuesta generados y escapados por el servidor. HU-028 implementa estas cabeceras en sus endpoints; HU-029 prueba el contrato. No añadir URLs firmadas ni servir un directorio/bucket público. Una descarga ya iniciada no puede recuperar los bytes ya enviados; la retirada impide solicitudes posteriores.

## 6. Trabajo independiente inmediato para Mushin

Tras acordar el pequeño contrato de tipos, Mushin puede crear `PoliticaArchivosInspeccion`, `ReglasEvidenciaFotografica` y sus DTO, sin almacenamiento ni Spring para las reglas puras. Puede probar todas las categorías, mínimos suministrados, faltantes, cero fotos, 59/60/61, exclusión de RETIRADO/PURGADO y matriz autor/estado. Hasta confirmar mínimos y permisos definitivos, esas partes se implementan como propuestas parametrizadas y no se declaran cerradas.

`EvidenciaFotograficaService` y `RetiradaFotografiaService` se prueban con Mockito: inspección inexistente/eliminada, inventario simulado, vínculo a otro destino, orden de autorización, fallo del registro y repetición sin cambiar fecha. Usar `Clock` fijo y los repositorios existentes simulados; no levantar MinIO ni implementar una base de datos alternativa.

`ValidadorContenidoFotografico` se prueba simulando `InspectorImagenes`: JPEG/WebP reales reconocidos por el contrato, MIME diferente, dimensiones inválidas y excepción del decoder. Estos tests comprueban la política y la traducción de errores; no demuestran que bytes reales se decodifiquen. Las pruebas de contenido real quedarán pendientes del decoder HU-028.

Mushin también puede escribir los controladores exclusivos y pruebas MockMvc de sesión/errores con servicios simulados. Para los endpoints propiedad HU-028, puede preparar casos de seguridad en un archivo de pruebas separado, pero solo podrán ejecutarse contra los controladores reales cuando estén disponibles.

Los dobles se ubican exclusivamente en `src/test`. No crear controladores falsos, clientes S3 simulados ni implementaciones permisivas en `src/main`. Si todavía no está el contrato mínimo compartido, comenzar por las reglas y sus pruebas; no copiar tipos provisionales con el mismo nombre en ambas ramas.

## 7. Integración con HU-028 y HU-026

### Confirmación de carga HU-028

HU-028 autoriza temprano con la política HU-029, recibe y valida categoría/GPS/fecha/anclaje, invoca `ValidadorContenidoFotografico` y procesa/escribe original y miniatura con su infraestructura. Al confirmar, reserva o comprueba la operación idempotente conforme a su protocolo, bloquea inspección, vuelve a autorizar, cuenta DISPONIBLE y llama a `validarCarga`. Guarda archivo, asociación y resultado idempotente juntos. La reserva duradera puede ocurrir antes de escribir objetos; el orden exacto lo define HU-028, conservando estas garantías.

No mantener el bloqueo de inspección durante la transferencia al proveedor. El control temprano ahorra trabajo, pero no sustituye el control final. HU-028 incorpora las invocaciones a las políticas en sus propios archivos: Mushin no modifica en paralelo el servicio de carga.

### Lectura y retirada

HU-028 aplica `validarLectura` y busca por ambos IDs antes de entregar listado, original o miniatura. HU-029 añade solo el DELETE mediante `RetiradaFotografiaController`, delegando todo en su servicio. Ambos usan el mismo registro persistente y bloqueo. No crear otra ruta DELETE en el controlador HU-028.

### Publicación HU-026

El responsable HU-026 incorpora `EvidenciaFotograficaService.exigirParaPublicar(inspeccionId)` en el flujo real, después de autorizar y bloquear primero puente y luego inspección, **antes de establecer PUBLICADA**. La validación de evidencia usa el inventario confirmado de esa transacción. Si falla, no se modifican estado, historial, IC ni puente.

Luego HU-026 guarda PUBLICADA/publicado_en/historial e invoca el servicio existente `PublicacionIndiceCondicionService.integrarPublicacion(inspeccionId, usuarioId)`, manteniendo la transacción y propagando excepciones. No mover la validación a un evento después del commit ni modificar el cálculo IC para incluir fotografías. La inspección bloqueada impide que una retirada o nueva asociación concurra entre validar y publicar, siempre que todos los escritores respeten ese bloqueo.

El GET diagnóstico no autoriza publicar y su resultado puede quedar desactualizado: HU-026 siempre revalida. Actualmente solo puede probarse esta integración con un llamador transaccional de prueba; la verificación del flujo real depende de la entrega HU-026.

## 8. Archivos y desarrollo sin conflictos de Git

### Archivos exclusivos HU-029

Con base Java indicada en la sección 2:

| Archivo nuevo | Responsabilidad |
|---|---|
| `inspeccion/service/PoliticaArchivosInspeccion.java` | Acceso, mutación y máximo |
| `inspeccion/service/ReglasEvidenciaFotografica.java` | Evaluación pura de evidencia obligatoria |
| `inspeccion/service/EvidenciaFotograficaService.java` | Consulta autorizada y puerta de validación para HU-026 |
| `inspeccion/service/RetiradaFotografiaService.java` | Retirada transaccional |
| `inspeccion/controller/EvidenciaFotograficaController.java` | GET diagnóstico |
| `inspeccion/controller/RetiradaFotografiaController.java` | DELETE de retirada |
| `inspeccion/dto/ValidacionEvidenciaResponse.java` | Resultado y total |
| `inspeccion/dto/FaltanteEvidenciaResponse.java` | Faltante por categoría |
| `archivo/validation/ValidadorContenidoFotografico.java` | Política sobre contenido real inspeccionado |

Pruebas propias bajo `sgp-api/src/test/java/gt/usac/cunoc/sgp/`: clases `PoliticaArchivosInspeccionTest`, `ReglasEvidenciaFotograficaTest`, `EvidenciaFotograficaServiceTest`, `RetiradaFotografiaServiceTest`, `ValidadorContenidoFotograficoTest`, `EvidenciaFotograficaControllerSecurityTest` y `RetiradaFotografiaControllerSecurityTest`, en sus paquetes equivalentes. Tras integrar, `integration/EvidenciaFotograficaIntegrationTest.java` cubre conteo concurrente, retirada/publicación y rollback con PostGIS. Una clase separada `inspeccion/controller/FotografiaAccesoSecurityTest.java` cubre los endpoints HU-028 sin editar sus pruebas.

### Archivos compartidos con un único responsable de edición

| Responsable | Archivos que publica para consumo del otro |
|---|---|
| HU-028 | `archivo/model/{CategoriaFoto,EstadoArchivo,FotografiaRegistrada,ImagenInspeccionada}.java`, `inspeccion/model/ContextoArchivosInspeccion.java`, `archivo/service/{RegistroFotografiasInspeccion,InspectorImagenes}.java` y sus implementaciones |
| HU-028 | Entidades/repositorios de archivos, `InspeccionRepository.java`, DTO `FotografiaResponse`, controlador/servicio de carga y lectura |
| HU-028 | `pom.xml`, configuración de almacenamiento, `ApiExceptionHandler.java`, cambios idempotentes mínimos de HU-024 y módulo `sync` |
| HU-028 | Panel Angular, `inspeccion-api.service.ts`, modelos frontend, Dexie y traducciones; consume errores, GET diagnóstico y DELETE acordados |
| HU-029 | Políticas, servicios, dos DTO y dos controladores exclusivos de la tabla anterior; HU-028 incorpora sus invocaciones |
| HU-026 | Servicio/controlador real de publicación y llamada a `exigirParaPublicar` |

La interfaz de almacenamiento no es dependencia directa de las reglas HU-029: solo el registro y el inspector de contenido lo son. HU-028 decide las firmas físicas de `AlmacenamientoArchivos`; Mushin no necesita introducir otro adaptador para avanzar.

V10/V11 cubren este alcance; no se propone migración para HU-029. No añadir columnas para mínimos ni otra tabla de fotografías. Cualquier necesidad nueva demostrada se coordinaría por separado, sin modificar migraciones existentes. Las dependencias de decoder/objetos se resuelven una sola vez en HU-028.

Para trabajar en paralelo se recomienda primero una entrega mínima de contratos compartidos, sin adaptadores, escrita por HU-028 e incorporada como base común por ambos responsables. Con esas firmas acordadas, HU-029 puede compilar y probar contra mocks mientras HU-028 desarrolla almacenamiento. No se deben crear las mismas interfaces en dos ramas ni integrar dobles de producción. Esta entrega es una recomendación de coordinación, no una instrucción para hacer commit, merge o rebase durante esta preparación.

## 9. Fases pequeñas y dependencias de cierre

| Fase | Trabajo | Evidencia verificable | Dependencia |
|---|---|---|---|
| 1. Acuerdo mínimo | Recuperar historia y ratificar mínimos, conteo de 60, permisos/estados, firmas y propietarios | Contrato único con decisiones explícitas; no asumir implementaciones | Ambos responsables; mínimos/permisos bloquean cierre de reglas definitivas |
| 2. Reglas HU-029 | Modelos de resultado, políticas y evaluación con fixtures | Tests puros de categorías, capacidad y autor/estado | Sin almacenamiento; tipos compartidos acordados |
| 3. Servicios simulados | Orquestación, retirada, controladores y política de contenido con mocks | Tests de errores, sesión, destino correcto y repetición | Contratos mínimos; sin S3/MinIO ni entidades de archivo completas |
| 4. Integración HU-028 | Conectar registro, decoder y llamadas en carga/lectura; probar retirada persistente | JPEG/WebP reales, contenido falso/corrupto, acceso a original/miniatura, 59→60 concurrente, exclusión tras retiro y rollback | Entidades, repositorios, decoder, endpoints e idempotencia HU-028 |
| 5. Integración HU-026 | Incorporar validación antes de publicación dentro de la transacción común | Evidencia insuficiente revierte; publicación válida; carrera con carga/retirada serializada | Flujo real HU-026 y mínimos definitivos |

En fases 2–3 Mushin puede demostrar las reglas y la interacción esperada, pero no la decodificación real, persistencia ni autorización efectiva de endpoints aún inexistentes. La lectura privada y el ciclo carga→retiro→conteo requieren fase 4. El cierre de publicación requiere fase 5. La falta de editor/repetibles persistidos afecta los recorridos de anclajes de HU-028, no las reglas generales por categoría de HU-029.

Al implementar se validará Java 21 mediante `java -version` y `mvn -version`, y se ejecutarán las pruebas aplicables, `mvn test`/`mvn verify` y revisión de diff conforme a AGENTS.md. La verificación de infraestructura usará entornos aislados y el mismo proveedor configurado por HU-028. Este análisis no ejecutó builds ni pruebas de aplicación y no afirma que los contratos propuestos ya funcionen.
