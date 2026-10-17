# ADR-0004: Despliegue automático de producción desde `main`

## Estado

Aceptada. Reemplaza la promoción manual de producción descrita en ADR-0002
(decisiones 9 a 12) y en `GUIA_IMPLEMENTACION_PRODUCCION.md`. Las demás
decisiones de ADR-0002 se mantienen.

## Contexto

ADR-0002 definió que solo `develop` publica imágenes y despliega staging, y que
producción se actualizaría manualmente con los digests aprobados en staging. El
primer despliegue de producción se hizo así el 17 de octubre de 2026, con la
revisión `61dae8a43bbaa064159d03b15872f6b8eceb7d4f`.

El procedimiento manual requiere copiar digests y ejecutar comandos en Session
Manager en cada liberación. Eso es propenso a errores (en el primer despliegue
se pegó dos veces el prefijo de la imagen) y deja la trazabilidad en manos del
operador. Además, `main` ya recibe integraciones directas de ramas `feature/*`
(por ejemplo, el Pull Request 27), por lo que los digests de `develop` no
siempre representan el contenido de `main`.

## Decisión

1. Un `push` a `main` ejecuta el mismo workflow `CI`: valida backend y frontend,
   publica imágenes y despliega producción.
2. Los jobs de publicación construyen para la rama que recibió el `push` y
   etiquetan `sha-<SHA>` y `develop` o `main`. Producción se despliega con los
   digests publicados por ese mismo run, que corresponden exactamente al commit
   de `main` validado por las pruebas.
3. El job `deploy-production` replica `deploy-staging`: asume por OIDC el rol
   `sgp-prod-github-actions-role`, envía `infra/production/compose.yml` y
   `infra/production/deploy.sh` mediante Systems Manager y espera el resultado.
   `deploy.sh` conserva el bloqueo, las comprobaciones de salud y el rollback.
4. La confianza del rol se limita al sujeto inmutable de `refs/heads/main`, y
   su política solo permite `AWS-RunShellScript` sobre la instancia de
   producción (`infra/production/iam/`).
5. El job requiere las variables de repositorio `PRODUCTION_ENABLED`,
   `PRODUCTION_ROLE_ARN` y `PRODUCTION_INSTANCE_ID`, además de `AWS_REGION`.
   `PRODUCTION_ENABLED=false` suspende los despliegues sin retirar OIDC.
6. El control de cambios pasa a ser el Pull Request hacia `main`: su revisión y
   las validaciones de CI son la aprobación de la liberación. Se recomienda
   proteger `main` para exigir Pull Request y checks exitosos.

## Consecuencias

### Positivas

- Cada liberación queda ligada a un run de GitHub Actions, un commit de `main` y
  dos digests, sin pasos manuales.
- Producción refleja exactamente `main`, incluso cuando recibe integraciones que
  no pasaron por `develop`.
- El procedimiento manual de `PRODUCCION_AWS.md` sigue disponible como
  contingencia y para restaurar una versión anterior por digest.

### Negativas y riesgos

- La imagen de producción se construye de nuevo en `main`; no es el mismo
  binario que se probó en staging. Si `main` y `develop` tienen el mismo
  contenido, el código fuente es idéntico y las pruebas del run se ejecutan
  sobre ese commit, pero el despliegue en staging no valida la imagen exacta.
- Sin protección de rama, un `push` directo a `main` desplegaría producción.
- Un error de una integración llega a producción sin una aprobación adicional;
  el rollback automático de `deploy.sh` solo cubre fallos de arranque y salud.
