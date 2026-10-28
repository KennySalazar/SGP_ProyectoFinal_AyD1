# ADR-0003: Gestion declarativa de versiones del formulario SIECA mediante JSON Schema

## Estado

Aceptada.

## Contexto

La regla de negocio RN-FRM-01 y las directrices técnicas DT-FE-07 y DT-BD-02 exigen que la estructura del formulario de evaluación técnica de puentes (manual SIECA) se gestione como un dato versionado (JSON Schema) en la base de datos, desacoplado del código fuente para permitir modificaciones estructurales sin redespliegues ni migraciones de esquema.

El alcance completo teórico contemplaba un editor visual interactivo ("drag-and-drop") de preguntas para el Administrador (Decisión abierta 8). Sin embargo, la complejidad de construir un editor visual completo que genere y sincronice dinámicamente JSON Schema (Draft 2020-12) con anotaciones propietarias (`x-sgp-evaluable`, `x-sgp-fuente`, vínculos a columnas tipadas y pesos del Índice de Condición) excede las prioridades funcionales del alcance previsto y añade riesgo de inconsistencias estructurales.

## Decision

Se aprueba el **alcance mínimo acordado** documentado en la Decisión abierta 8:

1. **Gestión declarativa del formulario con capa visual amigable**:
   - El Administrador gestiona y edita las versiones del formulario mediante un **editor visual interactivo** que organiza secciones y preguntas en tarjetas y tablas de fácil comprensión.
   - Mediante diálogos modales amigables, el Administrador puede crear, editar y eliminar secciones y preguntas, configurar tipos de dato (texto, párrafo multilínea, entero, decimal, fecha, sí/no, selección única con opciones dinámicas), definir restricciones (obligatoriedad, solo lectura, rangos de longitud o valor) y vincular columnas de almacenamiento relacional (`puente.*`).
   - Se mantiene un selector de modo que permite alternar bidireccionalmente entre el **Editor visual** (modo predeterminado amigable) y el modo **Código JSON Schema** (modo avanzado), garantizando sincronización en tiempo real sin pérdida de metadatos del esquema.
   - Se provee precarga directa de la plantilla oficial inicial del manual SIECA (`sieca-v1`) con las 8 secciones, reporte fotográfico, recomendaciones y propuestas de mantenimiento (RN-FRM-07).

2. **Validación autoritativa en backend**:
   - Todo guardado de borrador o intento de publicación valida el JSON Schema contra la especificación formal (Draft 2020-12) antes de persistir, retornando ruta exacta y causa del error ante inconsistencias de sintaxis o estructura.

3. **Inmutabilidad y ciclo de vida (RN-FRM-03, RN-FRM-04)**:
   - Las versiones inician en estado `BORRADOR` y pueden modificarse libremente.
   - Una vez `PUBLICADA`, la versión se congela y es estrictamente inmutable (HTTP 409 Conflict si se intenta editar).
   - Para aplicar cambios a una versión publicada, se crea una nueva versión en `BORRADOR` a partir de ella.
   - Solo puede existir exactamente una versión activa en un instante dado, garantizado a nivel de base de datos (índice único parcial) y a nivel transaccional.

4. **Declaración de correspondencia y mapeo (RN-FRM-06)**:
   - Al publicar una nueva versión que sucede a una versión previa, el Administrador debe declarar explícitamente el mapeo de cada campo (`EQUIVALENTE`, `RENOMBRADO`, `NUEVO`, `ELIMINADO`), garantizando la preservación histórica y comparabilidad de inspecciones pasadas.

## Consecuencias

- **Positivas**:
  - Cumplimiento estricto de RN-FRM-01, RN-FRM-03, RN-FRM-04, RN-FRM-06 y RN-FRM-07 ofreciendo una experiencia amigable y accesible para el Administrador según la retroalimentación recibida.
  - Sincronización transparente con el estándar JSON Schema sin dependencias externas pesadas, manteniendo el presupuesto de bundle inicial del cliente por debajo del límite de 500 kB.
  - Flexibilidad dual: interfaz gráfica intuitiva para usuarios funcionales y vista de código JSON Schema para reglas avanzadas o depuración técnica.
- **Negativas**:
  - Requiere sincronización bidireccional entre el modelo visual y el esquema JSON al cambiar de modo o guardar, lo cual se mitiga mediante validaciones sintácticas previas.
