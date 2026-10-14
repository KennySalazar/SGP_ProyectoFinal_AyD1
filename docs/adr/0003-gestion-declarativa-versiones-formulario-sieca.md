# ADR-0003: Gestion declarativa de versiones del formulario SIECA mediante JSON Schema

## Estado

Aceptada.

## Contexto

La regla de negocio RN-FRM-01 y las directrices técnicas DT-FE-07 y DT-BD-02 exigen que la estructura del formulario de evaluación técnica de puentes (manual SIECA) se gestione como un dato versionado (JSON Schema) en la base de datos, desacoplado del código fuente para permitir modificaciones estructurales sin redespliegues ni migraciones de esquema.

El alcance completo teórico contemplaba un editor visual interactivo ("drag-and-drop") de preguntas para el Administrador (Decisión abierta 8). Sin embargo, la complejidad de construir un editor visual completo que genere y sincronice dinámicamente JSON Schema (Draft 2020-12) con anotaciones propietarias (`x-sgp-evaluable`, `x-sgp-fuente`, vínculos a columnas tipadas y pesos del Índice de Condición) excede el valor funcional prioritario del sprint y añade riesgo de inconsistencias estructurales.

## Decision

Se aprueba el **alcance mínimo acordado** documentado en la Decisión abierta 8:

1. **Gestión declarativa del formulario**:
   - El Administrador gestiona y edita las versiones del formulario mediante estructuras estructuradas de datos: JSON Schema autoritativo, vínculos de almacenamiento relacional (`vinculos_almacenamiento`) y reglas de evaluación (`reglas_evaluacion_ic`).
   - Se provee precarga directa de la plantilla oficial inicial del manual SIECA (`sieca-v1`) con las 8 secciones, reporte fotográfico, recomendaciones y propuestas de mantenimiento (RN-FRM-07).
   - El editor visual interactivo de preguntas queda formalmente fuera del alcance de este ciclo.

2. **Validación autoritativa en backend**:
   - Todo guardado de borrador o intento de publicación valida el JSON Schema contra la especificación formal antes de persistir, retornando ruta exacta y causa del error ante inconsistencias de sintaxis o estructura.

3. **Inmutabilidad y ciclo de vida (RN-FRM-03, RN-FRM-04)**:
   - Las versiones inician en estado `BORRADOR` y pueden modificarse libremente.
   - Una vez `PUBLICADA`, la versión se congela y es estrictamente inmutable (HTTP 409 Conflict si se intenta editar).
   - Para aplicar cambios a una versión publicada, se crea una nueva versión en `BORRADOR` a partir de ella.
   - Solo puede existir exactamente una versión activa en un instante dado, garantizado a nivel de base de datos (índice único parcial) y a nivel transaccional.

4. **Declaración de correspondencia y mapeo (RN-FRM-06)**:
   - Al publicar una nueva versión que sucede a una versión previa, el Administrador debe declarar explícitamente el mapeo de cada campo (`EQUIVALENTE`, `RENOMBRADO`, `NUEVO`, `ELIMINADO`), garantizando la preservación histórica y comparabilidad de inspecciones pasadas.

## Consecuencias

- **Positivas**:
  - Cumplimiento estricto de RN-FRM-01, RN-FRM-03, RN-FRM-04, RN-FRM-06 y RN-FRM-07 sin dependencias de librerías visuales complejas o pesadas en el cliente.
  - Mayor precisión y control técnico al definir reglas de validación avanzadas (condicionales `if`/`then`/`else`, rangos y tipos estrictos de JSON Schema).
  - Presupuesto de bundle CSS/JS del cliente controlado y mantenido dentro de los límites exigidos.
- **Negativas**:
  - El Administrador edita o ajusta el esquema mediante editores de texto/JSON asistidos por validación de esquema en lugar de una interfaz gráfica de arrastrar y soltar bloques.
