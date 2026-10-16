# ms-barriodigital-bff

BFF de **BarrioDigital**: valida el JWT de Microsoft Entra ID y autoriza por App Roles.

Puerto: **8080**

Contrato: `barriodigital-infra` → `docs/decisiones.md`.

## Endpoints

| Ruta | Auth | Rol |
|------|------|-----|
| `GET /actuator/health` | público | — |
| `GET /api/ping` | JWT | cualquier rol autenticado |
| `GET /api/admin/ping` | JWT | **Admin** (si no → **403**) |
| `POST /api/requests` | JWT | cualquier rol autenticado → proxy a requests |
| `GET /api/requests` | JWT | listado (filtros `status`, `from`, `to`) |
| `GET /api/requests/meta/transitions` | JWT | proxy del mapa de transiciones |
| `GET /api/requests/{id}` | JWT | detalle |
| `GET /api/catalog/procedures` | JWT | cualquier rol autenticado → proxy a catalog |
| `POST /api/catalog/procedures` | JWT | **Admin** (si no → **403**) |
| `PUT /api/catalog/procedures/{value}` | JWT | **Admin** (si no → **403**) |
| `PUT /api/requests/{id}/status` | JWT | **Funcionario** (si no → **403**). Proxy a requests. |

## Proxy a requests (EP1-15)

El BFF valida el JWT y reenvía alta/listado/detalle a `ms-barriodigital-requests`.

Variable: `REQUESTS_BASE_URL` (default `http://localhost:8081`). Ver `.env.example`.

1. Levanta requests en el puerto **8081** (con Oracle).
2. Levanta el BFF en **8080**.
3. Prueba local sin Azure (perfil `dev`):

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

```powershell
curl.exe -i -X POST http://localhost:8080/api/requests `
  -H "Content-Type: application/json" `
  -d "{\"title\":\"Bache\",\"description\":\"Hueco\",\"procedureType\":\"bache\"}"

curl.exe -i "http://localhost:8080/api/requests?status=INGRESADO"
```

Con JWT real (sin perfil `dev`):

```powershell
curl.exe -i -X POST http://localhost:8080/api/requests `
  -H "Authorization: Bearer PEGA_EL_ACCESS_TOKEN" `
  -H "Content-Type: application/json" `
  -d "{\"title\":\"Bache\",\"description\":\"Hueco\",\"procedureType\":\"bache\"}"
```

Si requests responde 400 o 404, el BFF **conserva** ese status (no lo tapa con 500).

### Tipo de trámite validado contra catalog

Antes de reenviar `POST /api/requests`, el BFF pide `GET /api/catalog/procedures` y busca el `procedureType` del body (recortado y en minúsculas) en el campo `value`. El body que llega a requests no se modifica. El resto de rutas de requests no consulta catalog.

| Caso | Respuesta | `message` |
|------|-----------|-----------|
| Existe y `active` es true | se reenvía a requests como siempre | |
| No existe en catalog | **400** | `procedureType invalido` |
| Existe pero `active` es false | **400** | `El tipo de tramite no esta disponible` |
| Catalog sin conexión, sin respuesta a tiempo, con status no 2xx o con cuerpo ilegible | **503** | `El catalogo de tipos de tramite no esta disponible. Intenta nuevamente en unos minutos.` |
| `procedureType` vacío o ausente | **400** de validación del BFF, sin consultar catalog | |

En los tres errores de la tabla requests no se llama, y el cuerpo tiene la misma forma que los 401 y 403:

```json
{"status":400,"error":"Bad Request","message":"procedureType invalido","path":"/api/requests"}
```

El cliente hacia catalog corta a los **2 s** si no conecta y a los **3 s** si no responde.

## Proxy a catalog (EP1.5-13)

El BFF vuelve a validar el JWT, igual que en requests. `GET /api/catalog/procedures` lo puede llamar cualquier rol autenticado. `POST` y `PUT` exigen Admin: otro rol responde **403** y no llega a catalog.

Variable: `CATALOG_BASE_URL` (default `http://localhost:8082`).

El BFF arma el header `X-User-Roles` desde el claim `roles` del token y se lo manda a catalog. Catalog no lee el JWT.

El proxy de catálogo conserva los estados HTTP, pero reemplaza el cuerpo de cualquier 5xx por un mensaje genérico: no reenvía SQL, excepciones ni trazas del servicio interno. Los errores de negocio 400/403/404/409 mantienen su contrato.
Body de alta (sin `title`): `description`, `procedureType`, `address`.  
El BFF manda `X-User-Id` (oid/sub del JWT) y `X-User-Roles` a requests.
En el POST de alta manda además `X-User-Name`, leído del claim `name` del JWT y, si no viene, de `preferred_username`. El nombre no va en el body. GET, listado y cambio de estado no llevan ese header: requests solo lo necesita al crear el snapshot.  
En ese mismo POST manda `X-User-Email`, leído del claim `email` y, si no viene, de `preferred_username`. Un email enviado en el body se ignora. Sin ninguno de los dos claims el header va vacío y requests guarda `null`.  
Filtros `from`/`to`: fecha `yyyy-MM-dd`.

Cambio de estado (EP1.5-06). Vecino/Admin/Auditor → **403** en el BFF (no llega a requests).  
200 / 400 / 404 / 409 se **copian** del micro de requests; al admitir, los rechazos reconocidos se devuelven después de confirmar la compensación de cupo (EP1.5-18).

```powershell
curl.exe -i -X PUT http://localhost:8080/api/requests/ID/status `
  -H "Authorization: Bearer TOKEN_FUNCIONARIO" `
  -H "Content-Type: application/json" `
  -d "{\"status\":\"ADMITIDO\"}"
```

## Arranque local sin Azure (solo desarrollo)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Con `dev` no se exige JWT. **No usar `dev` en la demo de EP1.**

## Arranque con validación JWT (EP1-12)

Los defaults de `application.yml` ya apuntan al tenant del equipo. Se puede sobreescribir:

```powershell
$env:AZURE_ISSUER_URI = "https://login.microsoftonline.com/ce6e98c4-63f0-4b79-83e5-20925b5fada2/v2.0"
$env:AZURE_AUDIENCES  = "api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3,a0773f3e-abc6-4b53-86fc-9d33a2eddef3"

.\mvnw.cmd spring-boot:run
```

## CORS para Angular (EP1-16)

El front corre en `http://localhost:4200` y el BFF en `8080`. Sin CORS, el navegador bloquea las llamadas aunque el JWT sea válido.

- Origen permitido: `http://localhost:4200` (configurable con `CORS_ALLOWED_ORIGINS`)
- Métodos: GET, POST, PUT, DELETE, PATCH, OPTIONS
- Headers: `Authorization`, `Content-Type`
- No se usa origen `*` porque se envía el Bearer

Prueba de preflight:

```powershell
curl.exe -i -X OPTIONS http://localhost:8080/api/ping `
  -H "Origin: http://localhost:4200" `
  -H "Access-Control-Request-Method: GET" `
  -H "Access-Control-Request-Headers: Authorization, Content-Type"
```

Debe responder **200** con `Access-Control-Allow-Origin: http://localhost:4200`.

El API Gateway con CORS de borde queda para EP2; en EP1 el BFF atiende al Angular directo.

## Laboratorio Postman / curl (criterio EP1-12)

### 1) Sin token → 401

```powershell
curl.exe -i http://localhost:8080/api/ping
```

### 2) Token válido de Entra → 200

1. Entrá con un usuario de prueba (MSAL / https://jwt.ms).
2. El access token debe tener:
   - `iss` = `https://login.microsoftonline.com/ce6e98c4-63f0-4b79-83e5-20925b5fada2/v2.0`
   - `aud` = `api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3` **o** solo el GUID `a0773f3e-abc6-4b53-86fc-9d33a2eddef3`  
     (Entra suele emitir el GUID; ambos son válidos. El BFF acepta los dos vía `AZURE_AUDIENCES`.)
   - `roles` (App Roles): Admin | Funcionario | Vecino | Auditor
3. Llamá:

```powershell
curl.exe -i http://localhost:8080/api/ping -H "Authorization: Bearer PEGA_EL_ACCESS_TOKEN"
```

Esperado: **200** con `subject` y `roles`.
## Arranque con JWT (EP1-12 / EP1-13)

```powershell
.\mvnw.cmd spring-boot:run
```

Usá un token de otra app (otro `aud`) o fuerza `AZURE_AUDIENCES=api://audience-que-no-es` y reutilizá un token bueno: el BFF responde **401**.
Defaults en `application.yml` (issuer + audience del tenant).

## Laboratorio Postman

### EP1-12 — autenticación

1. Sin Bearer → `/api/ping` → **401**
2. Token Entra válido → `/api/ping` → **200**
3. Audience / firma / `exp` mal → **401**

### EP1-13 — autorización

Mismo URL `/api/admin/ping`, dos tokens:

```powershell
# Token de Admin (claim roles: ["Admin"]) → 200
curl.exe -i http://localhost:8080/api/admin/ping -H "Authorization: Bearer TOKEN_ADMIN"

# Token de Vecino (claim roles: ["Vecino"]) → 403
curl.exe -i http://localhost:8080/api/admin/ping -H "Authorization: Bearer TOKEN_VECINO"
```

El access token debe traer `roles` (App Roles de Entra): `Admin` | `Funcionario` | `Vecino` | `Auditor`.

## Qué valida el BFF

| Check | Cómo |
|-------|------|
| Issuer | `issuer-uri` + `JwtValidators.createDefaultWithIssuer` |
| Firma | JWKS de Entra (`fromIssuerLocation`) |
| Expiración (`exp`) | validador default de Spring |
| Audience | `AudienceValidator` vs `AZURE_AUDIENCES` / yml |
| CORS | origen `localhost:4200`, sin `*` (EP1-16) |
| Issuer / firma / exp / audience | EP1-12 (`JwtValidationConfig`) |
| Claim `roles` → `ROLE_*` | `EntraRolesJwtConverter` |
| `/api/admin/**` | `hasRole("Admin")` → 403 JSON si no |

## Tests

```powershell
.\mvnw.cmd test
```

## Git / secretos (EP1-19)

Ignorados: `target/`, `.env`, wallets (`*.pem`, `wallet/`), `application-local.yml`.  
Usá `.env.example` como plantilla. Tras `.\mvnw.cmd package`, `git status` no debe listar `target/`.

## Docker (EP1-24)

La imagen no incluye secretos. Entra y la URL de requests van por env.

| Variable | Ejemplo |
|----------|---------|
| `AZURE_ISSUER_URI` | `https://login.microsoftonline.com/<tenant>/v2.0` |
| `AZURE_AUDIENCES` | `api://a0773f3e-...` y/ o el GUID `a0773f3e-...` (coma-separados) |
| `REQUESTS_BASE_URL` | `http://host.docker.internal:8081` (local) o `http://requests:8081` (compose) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |

```powershell
docker build -t bd-bff .

docker run --rm -p 8080:8080 `
  -e AZURE_ISSUER_URI="https://login.microsoftonline.com/ce6e98c4-63f0-4b79-83e5-20925b5fada2/v2.0" `
  -e AZURE_AUDIENCES="api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3,a0773f3e-abc6-4b53-86fc-9d33a2eddef3" `
  -e REQUESTS_BASE_URL="http://host.docker.internal:8081" `
  -e CORS_ALLOWED_ORIGINS="http://localhost:4200" `
  bd-bff
```

Health: `http://localhost:8080/actuator/health`  
`/api/ping` sin Bearer → **401** (esperado). Compose con requests: `barriodigital-infra/apps/`.

## Qué NO está aún (a propósito)

- GET `/api/requests/meta/transitions` (EP1.5-07)
- Rabbit / notify / Kafka (EP3)

## Admisión y cupo (EP1.5-18)

`PUT /api/requests/{id}/status` mantiene URL, payload (`status`, `rejectionReason`) y recurso completo en el 200 normal. Solo Funcionario puede cambiar estados; Admin sin Funcionario no tiene override. El controller propaga las autoridades que autorizó Spring Security, también cuando el converter aceptó `role` o `ROLE_*`. En dev sin JWT sigue usando Vecino y la admisión se rechaza antes de consultar Catalog.

`RequestStatusOrchestrationService` coordina únicamente el destino `ADMITIDO`, comparando después de `trim()` sin convertir mayúsculas. ` ADMITIDO ` reserva; `admitido` sigue llegando a la validación de Requests como inválido. El payload original se conserva. Los demás destinos usan el proxy anterior, sin consulta adicional ni llamadas a Catalog; rechazar no devuelve cupo.

Orden: GET del trámite con identidad real → POST reserve del `procedureType` persistido → PUT de estado. No se confía en un tipo del frontend, no se consulta `remainingQuota` para autorizar y no se omite reserve por cupo ilimitado. Catalog tampoco prohíbe reservar un tipo inactivo. El GET no bloquea el trámite ni garantiza su estado posterior.

Requests conserva la autoridad sobre las transiciones y debe desplegarse con la protección de `fix/request-status-concurrency`: bloqueo pesimista de la fila durante lectura, validación y guardado de **todas** las transiciones. La transacción de Requests no incluye Catalog. El frontend coordinado, `fix/frontend-admission-errors`, interpreta `code` y evita reenviar desde el diálogo después de incertidumbre.

### Errores nuevos de coordinación

Son decisiones de EP1.5-18. El cuerpo conserva `status`, `error`, `message`, `path` y añade `code`:

| Fase / situación | HTTP | code | message |
|---|---|---|---|
| Reserva: sin cupo | 409 | QUOTA_EXHAUSTED | No queda cupo disponible para este tipo de trámite. |
| Reserva: tipo inexistente | 409 | PROCEDURE_TYPE_UNAVAILABLE | El tipo de trámite asociado ya no está disponible en el catálogo. |
| Consulta: cuerpo inválido o sin tipo válido | 502 | ADMISSION_DEPENDENCY_INVALID_RESPONSE | No pudimos obtener información válida del trámite. |
| Consulta: transporte o 5xx | 503 | ADMISSION_DEPENDENCY_UNAVAILABLE | No pudimos consultar el trámite. Intenta consultar el listado más tarde. |
| Reserva: transporte o HTTP distinto de 200/404/409 | 502 | QUOTA_RESERVATION_UNCONFIRMED | No pudimos confirmar la reserva de cupo. Cierra y consulta el listado; la operación requiere revisión. |
| PUT: sin respuesta o sin rechazo reconocible | 502 | ADMISSION_RESULT_UNCONFIRMED | No pudimos confirmar si el cambio se guardó. Cierra y consulta el listado antes de realizar otra operación. |
| PUT: 200 observado, recurso ilegible o incompatible | 502 | ADMISSION_RESPONSE_UNAVAILABLE | El cambio se guardó, pero no pudimos obtener su respuesta completa. Cierra y actualiza el listado. |
| Release: transporte o HTTP distinto de 200 | 502 | QUOTA_COMPENSATION_UNCONFIRMED | El cambio fue rechazado y no pudimos confirmar la devolución del cupo. La operación requiere revisión. |

Un error de negocio reconocible del GET se conserva sin reservar. Un rechazo del PUT se reconoce por HTTP, `status` del cuerpo, `path` de esa operación y mensaje controlado de Requests (400/403/404/409). Solo entonces se intenta **un** release. Si confirma 200, se devuelve el rechazo original de Requests. Si no confirma, se devuelve QUOTA_COMPENSATION_UNCONFIRMED y se registran ambos resultados mediante un identificador local del intento. Ese identificador no es una clave de idempotencia.

Un 5xx, timeout, desconexión o rechazo ilegible del PUT no demuestra rollback: no libera. Un 200 del PUT seguido de un cuerpo ilegible tampoco libera ni fabrica un recurso. Reserve y release se clasifican por su HTTP; sus cuerpos no se deserializan. No se usa un GET posterior como prueba de qué intento produjo el estado observado.

### Timeouts y operación

RestClient síncrono, sin reintentos de aplicación. Requests usa JDK HttpClient con `barriodigital.requests.connect-timeout` (`REQUESTS_CONNECT_TIMEOUT`, default `2s`) y `barriodigital.requests.read-timeout` (`REQUESTS_READ_TIMEOUT`, default `3s`), siguiendo el cliente de Catalog (2s/3s). Son límites cortos para operaciones internas pequeñas; pueden ajustarse según latencia real. Limitar la espera no cancela ni prueba la reversión de una escritura. No se garantiza una única transmisión física del transporte JDK.

`CATALOG_BASE_URL` configura el cliente existente (default local, puerto 8082). Los Compose inspeccionados de Infra todavía no incluyen Catalog ni esa variable para el BFF. Debe estar conectado al servicio correcto antes de una prueba integrada; dentro del contenedor, localhost no identifica otro contenedor. No se modificó infraestructura.

### Límites y verificación

Reserve/release no reciben identificador de trámite, reserva u operación. Repetirlos puede modificar otra vez el contador; el tope de release no aporta idempotencia. Una caída del BFF después de reservar puede dejar coordinación incompleta. Respuestas perdidas y compensaciones fallidas no tienen recuperación durable automática.

Release no identifica la jornada original ni aplica reset: una compensación de ayer puede afectar el contador de hoy si ya hubo reset. Cambios administrativos del límite, y GET/PUT de Catalog con lecturas sin el mismo bloqueo, también limitan la coordinación. Corregir la concurrencia de Requests no resuelve esos casos. No es una transacción distribuida ni una garantía de exactamente una ejecución o de recuperación de todo cupo.

Los logs de coordinación contienen fase, id de trámite, tipo, intento local, HTTP conocido, categoría y resultado de compensación. No incluyen tokens, headers completos, payload, solicitante, dirección ni motivo. Los logs no recuperan cupos.

Pruebas: `.\mvnw.cmd verify`, con Mockito, MockMvc y MockRestServiceServer. Los mocks comprueban orden y clasificación, no integración real. La concurrencia de Requests se prueba con transacciones independientes en H2; falta validarla en Oracle desplegado. El documento compartido `barriodigital-infra/docs/decisiones.md` requiere una actualización coordinada sobre rechazos confirmados, incertidumbre, códigos y límites; no se modifica en esta tarea.
