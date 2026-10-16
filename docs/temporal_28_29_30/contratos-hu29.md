# HU-029 — Contratos de integración, fase 1

Fecha: 16 de octubre de 2026. Código revisado: `c62fe92e377310063ff99890122b9dfeec148fc5`.
Rama de preparación: `feature/hu-029-impl-contratos-asignacion`.

Este documento concreta la preparación técnica de la fase 1 del [plan HU-029](implementacion-hu29.md). Las firmas futuras se implementarán en las fases indicadas. Los contratos existentes se comprobaron en el código. Las decisiones de trabajo se distinguen de la ratificación académica y de la coordinación todavía pendiente con otros responsables; no se afirma haber obtenido su aprobación.

Actualización: fase 2 implementada sobre `022d2e9`, con autorización posterior del usuario. Las firmas de política/acceso y las propiedades de mínimos ya existen. Las firmas de fase 3 y las integraciones de fases 4/5 permanecen futuras. La validación ejecutada se registra en el plan.

Actualización posterior sobre `58a2976`: fase 3 implementada. Diagnóstico, retirada, excepción informativa y validación interna están disponibles. Fases 4/5 permanecen pendientes. Las pruebas nuevas se describen y ejecutan en Gherkin; véase la [guía de pruebas](../../sgp-api/src/test/resources/features/README.md).

## 1. Contratos disponibles y responsabilidad

Base Java: `sgp-api/src/main/java/gt/usac/cunoc/sgp/`.

| Contrato existente | Firma o contenido | Responsabilidad |
|---|---|---|
| `archivo/model/CategoriaFoto` | ACCESO, SUPERESTRUCTURA, SUBESTRUCTURA, CAUCE, OTRA | HU-028; HU-029 consume |
| `archivo/model/EstadoArchivo` | DISPONIBLE, RETIRADO, PURGADO | HU-028; HU-029 consume |
| `archivo/model/FotografiaRegistrada` | ID, inspección, categoría, estado, referencia e instancia | HU-028; inventario interno |
| `inspeccion/model/ContextoArchivosInspeccion` | ID, autor, estado y eliminación | HU-028; no se amplía para autorización académica |
| `archivo/service/RegistroFotografiasInspeccion` | `consultar(UUID)`, `buscar(UUID, UUID)`, `retirar(UUID, UUID, Instant)` | HU-028; implementación JPA existente |
| `inspeccion/repository/InspeccionRepository` | `findByIdAndEliminadoEnIsNullForUpdate(UUID)` | HU-028; consulta y bloqueo existentes |
| `archivo/service/InspectorImagenes` | `ImagenInspeccionada inspeccionar(Path)` | HU-028; decodificación existente |
| `inspeccion/service/ReglasEvidenciaFotografica` | `evaluar(Map<String, Long>, Map<String, Integer>)`, `validarCapacidadParaNuevaFotografia(long)` | HU-029; conservar implementación |
| DTO de validación | `FaltanteEvidenciaResponse` y `ValidacionEvidenciaResponse` | HU-029; conservar JSON y lista inmutable |

La consulta del registro incluye estados históricos; el consumidor filtra DISPONIBLE y cuenta únicamente FOTO_INSPECCION del destino. Los códigos se obtienen mediante `CategoriaFoto.name()`. El registro debe producir IDs únicos por inspección. Un error técnico de consulta se propaga y no se sustituye por una lista vacía.

La retirada del registro exige una transacción existente, no autoriza al actor y no toma por sí sola el bloqueo de inspección. Conserva fecha ante repetición y rechaza PURGADO. El caso de uso HU-029 garantiza autorización y bloqueo antes de invocarlo.

## 2. Firmas para las fases siguientes

Se fija como contrato de trabajo de HU-029, sin crear interfaces adicionales:

```java
// Fase 2: inspeccion/service/PoliticaArchivosInspeccion
void validarCarga(ContextoArchivosInspeccion contexto, UUID actorId, long disponibles);
void validarLectura(ContextoArchivosInspeccion contexto, UUID actorId,
                    boolean esCatedraticoAsignado);
void validarRetirada(ContextoArchivosInspeccion contexto, UUID actorId);

// Fase 2: inspeccion/service/AccesoFotografiasInspeccionService
void validarLectura(Inspeccion inspeccion, UUID actorId);

// Fase 3: inspeccion/service/EvidenciaFotograficaService
ValidacionEvidenciaResponse consultar(UUID inspeccionId, UUID actorId);
void exigirParaTransicion(UUID inspeccionId);

// Fase 3: inspeccion/service/RetiradaFotografiaService
void retirar(UUID inspeccionId, UUID fotografiaId, UUID actorId);
```

`PoliticaArchivosInspeccion` no consulta BD, sesión ni almacenamiento. Delega capacidad en las reglas existentes. `AccesoFotografiasInspeccionService` resuelve la relación académica y llama a esa política, sin exponer el booleano mediante HTTP. Recibe la inspección ya cargada por el caso de uso para evitar otra lectura del mismo agregado.

La identidad siempre procede de `JwtData`. Ningún request contiene autor autorizado, permiso de lectura o mínimos del servidor. Se mantiene controller → service → repository y DTO en la frontera HTTP.

## 3. Fuente y vigencia de la asignación

Decisión técnica de trabajo: usar exclusivamente `Inspeccion.getAsignacionPuenteId()` y la relación académica persistida. Para un lector distinto del autor, resolver:

```java
asignaciones.findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull(
    inspeccion.getAsignacionPuenteId(), actorId);
```

No se concede acceso adicional si el ID es nulo, la consulta no encuentra la relación o esta fue revocada. Tampoco se deduce asignación por compartir puente, por `asignado_por_id`, por el acompañante o únicamente por el rol. El autor conserva su vía de acceso independiente.

La relación se consulta en cada solicitud de lectura; no se copia a JWT, frontend ni al contexto compartido. El permiso resultante sirve para el borrador y no concede carga ni retirada. El acceso del autor fuera de BORRADOR conserva el comportamiento existente; la ampliación a otros lectores fuera de BORRADOR queda fuera del contrato actual.

**Dependencia real:** el request, constructor y servicio de inicio actuales no fijan `asignacionPuenteId`. La fase 2 puede autorizar relaciones ya persistidas y probarlas con fixtures, pero el recorrido real de creación académica requiere que su responsable establezca el vínculo correcto. Esta fase no crea una asignación, modifica HU-024 ni inventa un endpoint para fijarla.

**Ratificación pendiente:** efecto de curso inactivo, inscripción retirada y cambio posterior de rol del Catedrático. La consulta actual solo verifica pertenencia al Catedrático y ausencia de revocación. No se afirma que resuelva esas reglas adicionales. Si se requieren, deberá ampliarse de forma acotada el control antes de cerrar ese recorrido.

## 4. Permisos, estados y repetición

| Operación | Decisión de trabajo | Rechazo |
|---|---|---|
| Carga nueva | Autor, no eliminada, BORRADOR y menos de 60 disponibles | Actor ajeno/ausente en BD: 404; estado: 409; capacidad: 422 |
| Lectura de BORRADOR | Autor o Catedrático relacionado según sección 3 | 404 para otro actor autenticado |
| Lectura del autor en otros estados | Conservar el acceso existente | Inspección eliminada: 404 |
| Retirada | Autor, no eliminada y BORRADOR | Ajeno: 404; otro estado: 409 |
| Lectura de fotografía | Vínculo del par inspección/foto y DISPONIBLE | 404 para inexistente, ajena, RETIRADO o PURGADO |
| Repetición de retirada | Reautorizar, comprobar estado, conservar fecha y devolver 204 | PURGADO: 404; nuevo estado incompatible: 409 |
| Solicitud sin sesión | Rechazar antes del acceso a datos | 401 |

Los permisos se evalúan antes de revelar estado. Un Administrador no adquiere acceso fotográfico por el permiso que tiene sobre actas PDF. La propuesta de retirada exclusivamente en BORRADOR concreta el diseño conservador del plan; los escenarios de la historia acreditan BORRADOR y PUBLICADA, y los demás estados conservan ratificación pendiente.

Resolver un POST idempotente ya confirmado no consume capacidad ni vuelve a exigir el estado de una carga nueva. La autorización de acceso sigue vigente. Existe una prueba HU-028 que reproduce después de pasar a ENVIADA y debe conservarse. Repetir un POST después de una retirada no reactiva el archivo; el listado actualizado sigue excluyéndolo.

## 5. Fuente única de mínimos

Implementado en fase 2: propiedades de servidor bajo `app.evidencia-fotografica.minimos`, consumidas por una única clase `inspeccion/config/EvidenciaFotograficaProperties`, con `@ConfigurationProperties` y validación al inicializar. Las propiedades de trabajo están en `application.properties` y los mapas expuestos son inmutables.

Configuración de trabajo incorporada en fase 2, usando el formato `.properties` del backend:

```properties
# Política de desarrollo pendiente de ratificación académica.
app.evidencia-fotografica.minimos.ACCESO=1
app.evidencia-fotografica.minimos.SUPERESTRUCTURA=1
app.evidencia-fotografica.minimos.SUBESTRUCTURA=1
app.evidencia-fotografica.minimos.CAUCE=1
```

El binding utiliza el enum compartido, no otro catálogo. La clase expondrá una copia inmutable mediante `Map<String, Integer> minimosPorCategoria()` para las reglas existentes. Los mínimos deben ser positivos y la política no puede quedar vacía. La carga de configuración debe verificar las cuatro categorías obligatorias; su ausencia debe impedir el arranque, sin introducir un valor implícito que permita publicar. No se exige mínimo OTRA en la propuesta actual.

Las cantidades permanecen parametrizadas y son modificables únicamente por configuración del servidor; no desde requests o interfaz. La cantidad académica 1 sigue pendiente de ratificación. El máximo 60 permanece concentrado en `ReglasEvidenciaFotografica`; no se añade una segunda constante configurable para el mismo límite.

El conteo usa solo fotografías confirmadas DISPONIBLE. Retirar libera una plaza según la interpretación de trabajo autorizada previamente; no se presenta como ratificación académica definitiva.

## 6. Contrato HTTP y error informativo

Se conservan POST/listado/contenido/miniatura de HU-028. HU-029 añade en fase 3:

- GET `/api/v1/inspecciones/{id}/evidencia-fotografica`: 200 con el DTO existente, aunque falten fotos.
- DELETE `/api/v1/inspecciones/{id}/fotografias/{fotoId}`: 204 mediante retirada lógica.

Decisión técnica para envío/publicación: respuesta `application/problem+json`, 422 y código `evidencia_fotografica_incompleta`, conservando el formato de `ProblemDetails`. Se añaden `faltantes`, `totalDisponibles` y `maximoPermitido`. Ejemplo de cuerpo futuro:

```json
{
  "type": "https://sgp.cunoc.usac.edu.gt/errores/evidencia_fotografica_incompleta",
  "title": "Evidencia fotográfica incompleta",
  "status": 422,
  "detail": "Falta evidencia en las categorías indicadas.",
  "instance": "/api/v1/inspecciones/01900000-0000-7000-8000-000000000001/publicar",
  "code": "evidencia_fotografica_incompleta",
  "totalDisponibles": 3,
  "maximoPermitido": 60,
  "faltantes": [{"categoria": "CAUCE", "requeridas": 1, "disponibles": 0}]
}
```

`instance` será la URI real de la solicitud; `/publicar` solo ilustra una transición futura y no define una ruta HU-026 existente.

Implementado en fase 3: `EvidenciaFotograficaIncompletaException` conserva una `ValidacionEvidenciaResponse` y un handler específico la serializa sin alterar el manejo de las demás `ApiException`. Si el inventario excede 60, se utiliza 422 `limite_fotografias_excedido`, sin inventar categorías faltantes. El GET diagnóstico devuelve `valida=false` y total real en ese caso.

## 7. Transacciones e integración con HU-028/HU-026

- Carga: mantener autorización temprana, idempotencia y compensación. En la confirmación `TransactionTemplate` existente, bloquear inspección, revalidar autor/estado/anclaje, contar DISPONIBLE y controlar capacidad antes de guardar. No transferir objetos bajo bloqueo.
- Retirada: caso de uso `@Transactional`, mismo bloqueo de inspección, autorización y registro `MANDATORY`. Conservar asociación, bytes e historial de retirada.
- Transición: `exigirParaTransicion` exige transacción existente e inspección bloqueada por el llamador. Antes de modificar el estado, evaluar inventario confirmado. MANDATORY no demuestra por sí solo el bloqueo.
- Publicación: orden puente → inspección; evaluar evidencia, guardar estado/historial y llamar a `PublicacionIndiceCondicionService.integrarPublicacion` en la misma transacción. Propagar excepciones.
- Seguridad: listado, original, miniatura y diagnóstico usarán la misma resolución de lectura. El DELETE utiliza autorización de mutación.

## 8. Archivos y responsables de edición

| Responsable técnico | Archivos/capacidad | Momento |
|---|---|---|
| HU-029 | Política, acceso académico y propiedades de mínimos con pruebas | Fase 2 |
| Integración HU-029 sobre HU-028 | Edición acotada de `FotografiaService` y pruebas para permisos/conteo | Fase 2; coordinar antes de editar simultáneamente |
| HU-029 | Diagnóstico, retirada, controladores y excepción de evidencia | Fase 3 |
| Integración HU-029 | Ampliación puntual de `ApiExceptionHandler` para error específico | Fase 3; un solo editor |
| Integración frontend HU-029/HU-028 | Servicio API, modelos, reporte fotográfico y traducciones | Fase 4; un solo editor por archivo |
| Responsable académico | Establecer relación inspección/asignación y validar su ciclo de vida | Dependencia externa para recorrido completo |
| Responsable de envío/HU-026 | Invocar validador antes de las transiciones reales | Fase 5 |

Los modelos, repositorios y adaptadores entregados por HU-028 se reutilizan. Esta preparación no atribuye a HU-029 su creación. No se requieren migraciones para estos contratos. La coordinación de edición está delimitada aquí; no se ha enviado ningún mensaje ni se acredita un acuerdo externo.

## 9. Cierre de la preparación

- [x] Contratos existentes contrastados con código y responsabilidades delimitadas.
- [x] Firmas de trabajo y precondiciones para fases 2/3 especificadas.
- [x] Fuente técnica de asignación y comportamiento ante relación ausente/revocada documentados.
- [x] Fuente parametrizada de mínimos y validación de configuración definidas.
- [x] Error 422 estructurado y repetición idempotente definidos.
- [x] Orden de bloqueos y puntos de integración precisados.
- [ ] Ratificación académica de mínimos y reglas adicionales de asignación/estados.
- [ ] Coordinación de edición con HU-028 y entrega del vínculo por el flujo académico.
- [x] Implementación y verificación backend de fase 2 (87 pruebas seleccionadas aprobadas, incluidas integración PostGIS y regresión HU-028).
- [x] Implementación de fase 3 y 36 escenarios Gherkin aprobados con JWT y PostGIS reales.
- [x] Regresión completa posterior: 932 pruebas aprobadas, empaquetado y controles de cobertura cumplidos (`mvn verify`).
- [ ] Implementación y verificación de fases 4/5.

La entrega técnica documental de fase 1 está preparada para revisión. Las casillas pendientes conservan explícitamente las dependencias externas; no se declaran acuerdos que no han ocurrido. Puede iniciarse fase 2 con los contratos de trabajo y mantener esas dependencias como condiciones de cierre del recorrido académico.

Validación original de fase 1: firmas y rutas contrastadas con el código, enlaces locales y `git diff --check`; entrega documental sin pruebas de aplicación. La validación posterior de los servicios implementados en fase 2 se registra en el plan. Ninguno de esos resultados valida las fases aún pendientes.
