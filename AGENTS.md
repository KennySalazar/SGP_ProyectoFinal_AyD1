# AGENTS.md

## Contexto obligatorio

Antes de realizar cambios:

1. Revisar `README.md`.
2. Revisar `docs/ARCHITECTURE.md`.
3. Revisar `docs/UI_GUIDELINES.md` si la tarea modifica frontend o interfaz.
4. Revisar las migraciones Flyway existentes si la tarea modifica persistencia o base de datos.
5. Revisar `git status` y el diff actual antes de editar archivos.

La implementación existente es la fuente de verdad. No asumir que funcionalidades futuras ya están implementadas.

## Reglas de trabajo

- Trabajar únicamente sobre el alcance solicitado.
- No modificar código, configuración o documentación no relacionada.
- No crear capas, abstracciones, dependencias o carpetas “por si acaso”.
- Respetar la arquitectura definida en `docs/ARCHITECTURE.md`.
- No introducir cambios arquitectónicos relevantes sin justificarlos y documentarlos mediante ADR.
- Mantener los endpoints funcionales bajo `/api/v1`.
- No exponer entidades JPA directamente mediante la API; utilizar DTO.
- Mantener la lógica de negocio en servicios.
- No acceder desde `controller` directamente a `repository`.
- Mantener `common/` únicamente para elementos realmente transversales.
- Utilizar MapStruct para los mapeos entre entidades y DTO cuando corresponda.
- Crear únicamente las carpetas que cada dominio realmente necesite.
- En Angular, las páginas y componentes no deben realizar llamadas HTTP directas.
- Centralizar las llamadas HTTP en servicios de feature o servicios transversales de `core/`.
- Mantener las rutas específicas dentro de cada feature y conservar lazy loading.
- Respetar los lineamientos de `docs/UI_GUIDELINES.md` al modificar interfaces.
- No acceder directamente a IndexedDB/Dexie desde las features; utilizar la capa `offline/`.
- No modificar migraciones Flyway ya aplicadas.
- Antes de crear una migración, revisar cuál es la siguiente versión disponible.
- No versionar `.env`, contraseñas, tokens, claves ni otros secretos.
- No guardar el access token en `localStorage`.
- No añadir ni actualizar dependencias salvo que la tarea lo requiera explícitamente.
- No modificar archivos ajenos al alcance únicamente para reorganizar, formatear o “mejorar” código existente.
- No hacer commit, push, merge o rebase salvo que se solicite explícitamente.

## Entorno

El backend del proyecto utiliza Java 21.

Antes de validar cambios del backend, confirmar que Maven se esté ejecutando con Java 21:

```bash
java -version
mvn -version
```

No asumir que una herramienta está disponible únicamente porque aparezca mencionada en documentación histórica. Utilizar solo herramientas y comandos configurados actualmente en el repositorio.

## Verificación

Ejecutar únicamente las verificaciones aplicables al cambio realizado.

### Backend

```bash
cd sgp-api
mvn test
mvn verify
```

### Frontend

```bash
cd sgp-client
npm run lint
npm run build
npm run format:check
```

### Revisión final

```bash
git diff --check
git status
```

Antes de considerar terminada una tarea:

- Revisar el diff final.
- Confirmar que no existan cambios accidentales o fuera del alcance.
- No afirmar que una prueba o comando pasó si no fue ejecutado.
- Si una verificación no puede ejecutarse por una limitación existente del proyecto, reportarlo claramente.
- No modificar configuración o dependencias únicamente para ocultar una verificación fallida.
- Mantener el comportamiento existente salvo que la tarea solicite explícitamente cambiarlo.

## Criterio general
Priorizar cambios pequeños, claros y fáciles de revisar.

Cuando exista una duda entre introducir una solución nueva o seguir un patrón ya utilizado correctamente en el proyecto, seguir el patrón existente.