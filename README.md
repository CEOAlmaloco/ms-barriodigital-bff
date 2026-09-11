# ms-barriodigital-bff

BFF de **BarrioDigital**: valida el JWT de Microsoft Entra ID (issuer, audience, firma, `exp`) y expone `/api/ping`.

Puerto: **8080**

Valores del contrato: `barriodigital-infra` → `docs/decisiones.md` (EP1-03).

## Arranque local sin Azure (solo desarrollo)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
curl http://localhost:8080/api/ping
```

Con `dev` no se exige JWT. **No usar `dev` en la demo de EP1.**

## Arranque con validación JWT (EP1-12)

Los valores por defecto de `application.yml` ya apuntan al tenant del equipo. Si necesita cambiarlos:

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

### 3) Audience incorrecto → 401

Usá un token de otra app (otro `aud`) o forzá `AZURE_AUDIENCES=api://audience-que-no-es` y reutilizá un token bueno: el BFF responde **401**.

### 4) Token expirado → 401

Esperá a que venza el access token (o alterá `exp` en jwt.io sin poder re-firmar: firma inválida → también 401).

### Health (público)

```powershell
curl.exe http://localhost:8080/actuator/health
```

## Qué valida el BFF hoy

| Check | Cómo |
|-------|------|
| Issuer | `issuer-uri` + `JwtValidators.createDefaultWithIssuer` |
| Firma | JWKS de Entra (`fromIssuerLocation`) |
| Expiración (`exp`) | validador default de Spring |
| Audience | `AudienceValidator` vs `AZURE_AUDIENCES` / yml |
| CORS | origen `localhost:4200`, sin `*` (EP1-16) |

## Tests

```powershell
.\mvnw.cmd test
```

## Qué todavía no está (a propósito)

- Autorización por rol → 403 (EP1-13, otro PR)
- Orquestación a `ms-requests` (EP1-15)
- Dockerfile (EP1-24)
