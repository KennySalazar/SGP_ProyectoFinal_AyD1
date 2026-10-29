# Requisitos de hardware y software

## Software

| Componente | Requisito configurado | Observación |
|---|---|---|
| Backend local | JDK 21 y Maven | No hay Maven Wrapper; la imagen de compilación utiliza Maven 3.9.9. |
| Cliente local | Node.js compatible con Angular; Node 24 recomendado | CI y Docker usan Node 24. npm instala desde `package-lock.json`. |
| Base de datos local | Docker y Docker Compose, o PostgreSQL 16 con PostGIS 3.5 | Compose publica PostgreSQL en `localhost:55432`. |
| Aplicación completa en contenedores | Docker con soporte de Compose | El cliente se sirve por Nginx en `localhost:8088`. |
| Pruebas de integración | Docker accesible al proceso de pruebas | Testcontainers levanta PostgreSQL/PostGIS para pruebas de integración. |
| Navegador | Navegador moderno con soporte para PWA, IndexedDB y service workers | Necesario para las funciones locales del cliente. |

Para ejecutar exclusivamente el cliente, se requieren Node.js y npm. Para iniciar la API localmente se necesita Java, Maven, una base PostgreSQL/PostGIS accesible y las variables requeridas. Docker simplifica la base local y las pruebas de integración.

## Hardware

El proyecto no fija cantidades mínimas de CPU, memoria o almacenamiento para equipos de desarrollo o servidores. Los recursos necesarios dependen del volumen de datos, cantidad de usuarios, imágenes y procesos concurrentes. En desarrollo, el equipo debe disponer de recursos suficientes para las herramientas locales y los contenedores que se ejecuten simultáneamente.
