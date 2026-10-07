# ADR-0001: Base arquitectonica del SGP

## Estado

Aceptada como punto de partida del proyecto.

## Contexto

El enunciado exige una SPA Angular desacoplada de una API REST Spring Boot, organizacion por dominio, PostgreSQL con PostGIS, migraciones Flyway, autenticacion JWT stateless, operacion PWA/offline y despliegue contenedorizado.

## Decision

Se parte de una base limpia con:

- `sgp-api`: Java 21, Spring Boot 3.3.x y Maven.
- `sgp-client`: Angular 21, componentes standalone, TypeScript strict y signals.
- PostgreSQL 16 + PostGIS, con usuario de migracion separado del usuario de aplicacion.
- MinIO reservado para almacenamiento de objetos.
- Nginx como unico punto de entrada del `docker-compose`.
- Modulo `usuario` como unica funcionalidad heredada: autenticacion, 2FA y recuperacion de contrasena, adaptado a los roles del SGP.
- Los modulos funcionales del SGP se dejan vacios para desarrollarse por ramas `feature/*`.


