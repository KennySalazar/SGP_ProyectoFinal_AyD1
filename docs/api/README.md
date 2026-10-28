# Especificación OpenAPI

[`openapi.json`](openapi.json) es la especificación OpenAPI 3.0 de la API REST de SGP, exportada de
Springdoc. Cubre todos los endpoints funcionales bajo `/api/v1`, con sus DTO de entrada y salida, y la
regla de autorización que documenta cada operación.

En staging y producción el documento en vivo (`/api/docs`) y Swagger UI exigen una sesión, porque
`OPENAPI_PUBLIC=false`. Este archivo permite consultar la especificación sin acceder a esos ambientes.
El campo `servers` apunta a `/`: la API se sirve en el mismo origen que el frontend.

## Regenerarla

Después de cambiar un controlador o un DTO, con PostgreSQL local (`docker compose up -d postgres`) y
`sgp-api/.env` configurado:

```bash
cd sgp-api
OPENAPI_PUBLIC=true mvn spring-boot:run
```

En otra terminal, desde la raíz del repositorio:

```bash
curl -s http://localhost:8090/api/docs | python3 -c "import json,sys; d=json.load(sys.stdin); d['servers']=[{'url':'/','description':'Mismo origen que el frontend (staging o producción); la API vive bajo /api/v1'}]; print(json.dumps(d, ensure_ascii=False, indent=2))" > docs/api/openapi.json
```

Revise el diff antes de versionarlo: debe reflejar solo los cambios hechos a la API.
