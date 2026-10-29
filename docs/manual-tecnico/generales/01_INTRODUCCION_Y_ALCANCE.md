# Introducción y alcance

## Propósito

El Sistema de Gestión de Puentes (SGP) es una aplicación web progresiva para administrar el inventario de puentes y apoyar su inspección técnica, revisión académica, colaboración y mantenimiento. Está dirigido a administradores, catedráticos, estudiantes y profesionales externos, con permisos asociados al rol y al contexto de trabajo.

## Alcance funcional

El sistema relaciona puentes y su ubicación territorial con formularios de inspección versionados, evidencias, resultados del índice de condición y acciones de mantenimiento. Para el trabajo de campo dispone de preparación de salidas y persistencia local que permite continuar capturas con conectividad limitada y sincronizarlas posteriormente.

La solución se compone de un cliente Angular PWA, una API REST Spring Boot y PostgreSQL con PostGIS. La API concentra validaciones y reglas de negocio; el cliente presenta las funciones y se comunica con ella. Los documentos generales describen el contexto, entorno y prácticas transversales. Los capítulos M01–M14 desarrollan arquitectura y dominios concretos.

## Límites

El SGP registra información y apoya procesos técnicos y académicos. La publicación de un resultado o una propuesta de mantenimiento depende de las reglas configuradas y de las acciones autorizadas en el sistema. Este manual describe el comportamiento implementado; no establece criterios de ingeniería civil externos ni afirma que un despliegue particular esté activo.
