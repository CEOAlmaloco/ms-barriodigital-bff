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
| `GET /api/requests/{id}` | JWT | detalle |

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

Body de alta (sin `title`): `description`, `procedureType`, `address`.  
El BFF manda `X-User-Id` (oid/sub del JWT) y `X-User-Roles` a requests.  
Filtros `from`/`to`: fecha `yyyy-MM-dd`.

## Arranque local sin Azure (solo desarrollo)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Con `dev` no se exige JWT. **No usar `dev` en la demo de EP1.**

## Arranque con validación JWT (EP1-12)

Los defaults de `application.yml` ya apuntan al tenant del equipo. Se puede sobreescribir:

```powershell
$env:AZURE_ISSUER_URI = "https://login.microsoftonline.com/ce6e98c4-63f0-4b79-83e5-20925b5fada2/v2.0"
$env:AZURE_AUDIENCES  = "api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3"

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
   - `aud` = `api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3`
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

## Qué NO está aún (a propósito)

- Autorización por rol → 403 (EP1-13) — en PR aparte
- Orquestación a `ms-requests` (EP1-15)
- Dockerfile (EP1-24)
