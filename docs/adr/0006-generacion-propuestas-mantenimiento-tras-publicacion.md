# ADR-0006: Generación de propuestas de mantenimiento después de confirmar la publicación

## Estado

Aceptada

## Contexto

La regla RN-MTO-02 establece que al publicarse una inspección el sistema propone órdenes de mantenimiento a partir de los daños registrados, y la HU-046 lo implementa sobre el catálogo de reglas de la ADR-0005.

La publicación (HU-020) es una operación transaccional: pasa la inspección a PUBLICADA, calcula el Índice de Condición y actualiza el estado vigente del puente, y cualquier fallo revierte todo. El mantenimiento es un módulo posterior y no debe poder impedir ni deshacer una publicación ya válida, ni el módulo de inspecciones debe depender de él.

## Decisiones

### 1. Un evento interno, atendido después de la confirmación

- `TransicionInspeccionService.publicarConIndice` publica `InspeccionPublicadaEvent(inspeccionId, usuarioId)` dentro de la transacción de publicación. Lo emiten tanto la publicación directa del Catedrático o Profesional externo como la aprobación de una revisión; `usuarioId` es quien publicó o aprobó.
- El evento vive en el paquete de inspecciones. El módulo de mantenimiento lo escucha con `@TransactionalEventListener(phase = AFTER_COMMIT)`, de modo que la dependencia va de mantenimiento hacia inspecciones y nunca al revés.
- Si la publicación se revierte, el evento no llega a atenderse.

### 2. La generación corre en su propia transacción y no bloquea

- El oyente invoca `GeneradorPropuestasMantenimientoService.generarTrasPublicacion` con `REQUIRES_NEW` y atrapa cualquier excepción: un fallo se registra en el log y la publicación ya confirmada permanece.
- Un Administrador puede repetir la generación con `POST /api/v1/inspecciones/{id}/propuestas-mantenimiento/regenerar`. El endpoint devuelve cuántas propuestas se crearon, cuántas ya existían, cuántas se omitieron por tener una orden en curso y cuántas reglas no se pudieron evaluar. Responde 409 si la inspección no está publicada.
- Las inspecciones publicadas antes de esta funcionalidad no se procesan de forma retroactiva; se regeneran manualmente por ese mismo endpoint.

### 3. Evaluación de las reglas

- Se evalúan las reglas activas de la versión del formulario con que se levantó la inspección, sobre cada elemento que resuelve la `elemento_ref` de la regla (una evaluación por instancia en los elementos repetibles).
- La condición sigue el contrato de la ADR-0005 (`indicador`, `operador`, `umbral`) con los operadores `IGUAL`, `DISTINTO`, `MAYOR_O_IGUAL`, `MENOR_O_IGUAL` y `CONTIENE`. Las comparaciones de orden admiten números y la escala de severidad `NO_PRESENTA < LEVE < MODERADO < SEVERO`.
- Un indicador sin responder no dispara la regla (ausencia no es `false` ni cero). Una condición incomparable, con operador desconocido o incompleta se trata como «no se cumple» y deja una advertencia en el log. Una regla que falla al evaluarse se cuenta en `reglasConError` y no impide evaluar las demás.

### 4. Idempotencia y no duplicación

- Cada propuesta lleva `clave_generacion = codigoRegla:(instanciaId|-)`, única por inspección (índice `uq_orden_generacion`). Repetir la generación no duplica.
- No se propone si el mismo puente ya tiene una orden en curso (PROPUESTA, PROGRAMADA o EN_EJECUCION) para el mismo elemento, la misma instancia y cualquier revisión de la misma regla.
- La fila de la inspección se bloquea durante la generación para que dos ejecuciones simultáneas se serialicen.

### 5. Trazabilidad y avisos

- Toda propuesta nace en estado `PROPUESTA`, sin autor (`creado_por_id` nulo), con su primer registro de historial (sin estado anterior → `PROPUESTA`) y una entrada de auditoría `CREAR` con el proceso automático `generar-propuestas-mantenimiento`. Cuando la regenera un Administrador, el actor queda registrado.
- `fundamento_generacion` guarda el porqué en JSON: código y revisión de la regla, elemento, indicador, operador, umbral y valor encontrado.
- Se envía un aviso `MANTENIMIENTO_PROPUESTAS` por evento a los Administradores activos y a quien publicó, deduplicado con la clave `mantenimiento-propuestas:{inspeccionId}`. Sin propuestas nuevas no hay aviso.

### 6. Consulta

- `GET /api/v1/inspecciones/{id}/ordenes-mantenimiento` lista las órdenes que originó una inspección. Lo puede consultar cualquier usuario autenticado, pero solo para inspecciones publicadas: las demás responden 404 (RN-INS-05).

## Alternativas descartadas

- **Generar dentro de la misma transacción de publicación.** Un error en el mantenimiento revertiría una publicación válida y acoplaría el módulo de inspecciones al de mantenimiento.
- **Un proceso periódico que busque inspecciones sin evaluar.** Retrasa las propuestas, exige llevar un registro de lo ya evaluado y añade un mecanismo más sin una necesidad que lo justifique hoy.

## Consecuencias

### Positivas
- Publicar nunca depende del mantenimiento; el fallo de uno no afecta al otro.
- El evento `InspeccionPublicadaEvent` queda como punto de extensión para otros módulos que necesiten reaccionar a una publicación confirmada.
- La generación es repetible sin riesgo de duplicados y deja rastro completo de por qué existe cada propuesta.

### Negativas
- Entre la confirmación y la generación hay una ventana: si el proceso se detiene justo ahí, la inspección queda publicada sin propuestas hasta que un Administrador las regenere.
- La generación automática no se reintenta sola. Es una limitación de esta
  decisión: ante un fallo, un Administrador puede usar el endpoint de
  regeneración descrito arriba. Este ADR no establece un reintento automático
  como tarea pendiente.
