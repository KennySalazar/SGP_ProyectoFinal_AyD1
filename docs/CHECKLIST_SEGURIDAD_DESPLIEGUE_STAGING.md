# Lista de Comprobación de Seguridad de Despliegue en Staging (Parte B — HU-073)

Este documento certifica el cumplimiento de las directrices de seguridad de despliegue para los ambientes de Staging y Producción del Sistema de Gestión de Puentes (SGP), conforme a los requerimientos técnicos DT-SEC-06, DT-SEC-10, DT-SEC-11, DT-SEC-12, DT-DEP-09 y DT-DEP-10.

---

## 1. Matriz de Comprobación de Seguridad

| ID Requerimiento | Directriz / Control | Implementación en Repositorio | Verificación Staging | Estado |
| :--- | :--- | :--- | :--- | :---: |
| **DT-SEC-10** | Redirección HTTP a HTTPS y Encabezado HSTS | `infra/staging/https/nginx-https.conf.template`<br>`SecurityConfiguration.java` | Redirección 301 en puerto 80 hacia HTTPS. Encabezado `Strict-Transport-Security: max-age=31536000; includeSubDomains` presente en respuestas HTTPS. | **CONFORME** |
| **DT-SEC-11** | Encabezados de Seguridad HTTP | `infra/staging/https/nginx-https.conf.template`<br>`SecurityConfiguration.java`<br>`sgp-client/nginx.conf` | Respuestas devuelven: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, y `Content-Security-Policy`. | **CONFORME** |
| **DT-DEP-10** | Actuator Restringido a Red Interna | `infra/staging/https/nginx-https.conf.template`<br>`sgp-client/nginx.conf` | El reverse proxy rechaza con `403 Forbidden` peticiones externas a `/actuator/*`. Solo se autoriza tráfico desde `127.0.0.1`, `::1` y subredes RFC 1918 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`). | **CONFORME** |
| **DT-SEC-06** | CORS Explícito sin Comodines | `SecurityConfiguration.java` | Se rechaza explícitamente el uso de comodín `*` en orígenes autorizados lanzando `IllegalArgumentException`. Solo se configuran dominios explícitos vía `app.cors.allowed-origins`. | **CONFORME** |
| **DT-DEP-09** | Bitácoras Estructuradas en JSON con Retención >= 90 Días | `logback-spring.xml`<br>`MaskingJsonLayout.java`<br>`infra/staging/compose.yml` | Formato JSON en `stdout` capturado por Docker daemon (`driver: json-file`) con rotación de `max-size: 50m` y retención de `max-file: 90` (conservación >= 90 días en host, compatible con contenedores no-root y `read_only: true`). | **CONFORME** |
| **DT-SEC-12** | Ocultamiento de Credenciales y Datos Sensibles en Bitácoras | `MaskingJsonLayout.java`<br>`LoginRequest.java`<br>`RegisterRequest.java` | DTOs de autenticación ofuscan `password` y tokens en `toString()`. El layout de bitácoras sustituye passwords y tokens de cabeceras/URL/JSON por `[PROTEGIDO]`. | **CONFORME** |

---

## 2. Evidencia de Verificación y Comandos de Prueba

### 2.1 Verificación de Redirección HTTP y HSTS (DT-SEC-10)

```bash
# Petición HTTP al puerto 80 (debe retornar 301 con Location a https)
curl -I http://staging.sgp.cunoc.edu.gt/
# Salida esperada:
# HTTP/1.1 301 Moved Permanently
# Location: https://staging.sgp.cunoc.edu.gt/

# Petición HTTPS (debe contener encabezado HSTS)
curl -I https://staging.sgp.cunoc.edu.gt/
# Salida esperada:
# Strict-Transport-Security: max-age=31536000; includeSubDomains
```

*Prueba automatizada asociada:* `DeploymentSecurityHeadersTest.hstsPresenteEnRespuestas()` (Aprobada).

### 2.2 Verificación de Encabezados de Seguridad (DT-SEC-11)

```bash
curl -I https://staging.sgp.cunoc.edu.gt/api/v1/puentes
# Salida esperada:
# HTTP/1.1 200 OK
# X-Content-Type-Options: nosniff
# X-Frame-Options: DENY
# Referrer-Policy: strict-origin-when-cross-origin
# Strict-Transport-Security: max-age=31536000; includeSubDomains
```

*Prueba automatizada asociada:* `DeploymentSecurityHeadersTest.encabezadosSeguridadPresentes()` (Aprobada).

### 2.3 Verificación de Aislamiento de Actuator (DT-DEP-10)

```bash
# Intento de acceso externo desde Internet
curl -I https://staging.sgp.cunoc.edu.gt/actuator/health
# Salida esperada:
# HTTP/1.1 403 Forbidden

# Acceso interno desde el host o red privada (Docker network / loopback)
curl -I http://127.0.0.1:8080/actuator/health
# Salida esperada:
# HTTP/1.1 200 OK
# Content-Type: application/vnd.spring-boot.actuator.v3+json
```

### 2.4 Verificación de CORS Restringido (DT-SEC-06)

*Prueba automatizada asociada:* `DeploymentSecurityHeadersTest.corsRechazaComodin()` y `corsAceptaOrigenesExplicitos()` (Aprobadas).
- Si `app.cors.allowed-origins` contiene `*`, la inicialización del bean falla con `IllegalArgumentException: "CORS no permite el comodin '*' por directriz de seguridad DT-SEC-06"`.

### 2.5 Verificación de Formato de Bitácoras y Retención (DT-DEP-09 y DT-SEC-12)

*Prueba automatizada asociada:* `BitacoraSeguridadTest` (4/4 pruebas aprobadas).
1. `bitacoraEstructuradaEnJson()`: Valida que cada entrada sea un objeto JSON válido con campos `timestamp`, `level`, `thread`, `logger`, `message`.
2. `bitacoraEnmascaraContrasenaEnMensaje()`: Valida que cadenas con contraseñas en texto queden reemplazadas por `[PROTEGIDO]`.
3. `bitacoraEnmascaraBearerToken()`: Valida que tokens de autorización en cabeceras sean redactados a `Bearer [PROTEGIDO]`.
4. `loginRequestNoImprimeContrasenaEnToString()`: Valida que ningún DTO imprima la contraseña o token en texto plano durante su serialización o trazabilidad.

---

## 3. Conclusión de la Auditoría

Los controles de cumplimiento de seguridad del despliegue (Parte B) quedan plenamente implementados en la configuración de la infraestructura y respaldados por pruebas automatizadas sin fallos, cumpliendo a cabalidad con la especificación de la HU-073 (SCRUM-96).
