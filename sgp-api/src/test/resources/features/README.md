# Pruebas nuevas en Gherkin

Por instrucción del usuario, las pruebas nuevas se describen en archivos `.feature` mediante escenarios Gherkin. Las clases Java contienen los pasos y la preparación necesaria para ejecutarlos con Cucumber; no se sustituyen por comentarios «Dado/Cuando/Entonces» dentro de tests JUnit. Las pruebas anteriores permanecen disponibles para regresión.

## Ejecutar HU-029, fase 3

Desde `sgp-api`, con Maven sobre Java 21 y acceso a Docker:

```bash
mvn -Dtest=Hu029Fase3Test test
```

La suite [Hu029Fase3Test](../../java/gt/usac/cunoc/sgp/inspeccion/bdd/Hu029Fase3Test.java) selecciona únicamente `features/hu029/fase3`. Los escenarios usan español mediante `# language: es`:

- [Diagnóstico y validación transaccional](hu029/fase3/evidencia.feature).
- [Retirada, permisos, rollback e idempotencia](hu029/fase3/retirada.feature).

Reportes generados, fuera del control de versiones:

- `target/cucumber/hu029-fase3.html`.
- `target/cucumber/hu029-fase3.json`.
- `target/surefire-reports/`.

## Alcance y aislamiento

La configuración selecciona explícitamente los componentes reales necesarios: controladores, servicios, repositorios, seguridad JWT y almacenamiento local en un directorio temporal. Se levanta un PostGIS temporal con Testcontainers y las migraciones existentes se aplican solo a esa base. La limpieza del contenedor queda a cargo del mecanismo Ryuk de Testcontainers al finalizar la JVM.

Cada escenario crea identidades, inspecciones y archivos propios. La mayoría de escenarios prepara metadatos de inventario; los que verifican bytes o reintentos realizan una carga real por HU-028. No se usan dobles de servicios de negocio ni repositorios en estos escenarios. Las cantidades mínimas se obtienen de la configuración parametrizada del servidor.

El endpoint `/prueba/inspecciones/{id}/validar-transicion` pertenece exclusivamente al contexto de test. Bloquea la inspección y llama al validador dentro de una transacción para comprobar errores y rollback; no acredita la implementación del flujo real de envío/publicación HU-026. Las configuraciones y el controlador de prueba están marcados como componentes de test para excluirlos del escaneo normal de la aplicación.

Los escenarios no prueban MinIO real, navegación del frontend ni la creación académica del vínculo de asignación. El vínculo del Catedrático se persiste como fixture, igual que en las pruebas anteriores.

## Añadir nuevos escenarios

1. Escribir `.feature` con `Característica`, `Escenario`/`Esquema del escenario` y pasos `Dado`, `Cuando`, `Entonces`.
2. Implementar o reutilizar pasos en el paquete seleccionado por la suite; usar beans reales para pruebas de integración y mantener las utilidades exclusivamente bajo `src/test`.
3. Evitar escenarios que dependan de ejecutar primero otro escenario. Reutilizar el catálogo y contratos existentes, sin tipos alternativos de producción.
4. Si se crea otra suite, darle recursos y paquete de pasos explícitos para evitar ejecución duplicada o mezclar contextos Spring.
5. Ejecutar los escenarios nuevos y la regresión relacionada. `mvn verify` ejecuta también las pruebas anteriores y las comprobaciones de cobertura configuradas.

Se añadieron únicamente dependencias de alcance `test`: Cucumber Java, Spring y JUnit Platform Engine 7.20.1, y JUnit Platform Suite con la versión administrada por Spring Boot. La integración utiliza el mecanismo de suites descrito en la [documentación oficial de Cucumber](https://github.com/cucumber/cucumber-jvm/blob/v7.20.1/cucumber-junit-platform-engine/README.md).
