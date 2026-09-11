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

Los defaults de `application.yml` ya apuntan al tenant del equipo. Se puede sobreescribir:

```powershell
# Opcional si usás los defaults del yml
$env:AZURE_ISSUER_URI = "https://login.microsoftonline.com/ce6e98c4-63f0-4b79-83e5-20925b5fada2/v2.0"
$env:AZURE_AUDIENCES  = "api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3"

.\mvnw.cmd spring-boot:run
```

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

Usá un token de otra app (otro `aud`) o fuerza `AZURE_AUDIENCES=api://audience-que-no-es` y reutilizá un token bueno: el BFF responde **401**.

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
