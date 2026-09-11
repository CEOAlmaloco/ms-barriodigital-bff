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

## Arranque local sin Azure (solo desarrollo)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Con `dev` no se exige JWT. **No usar `dev` en la demo de EP1.**

## Arranque con JWT (EP1-12 / EP1-13)

```powershell
.\mvnw.cmd spring-boot:run
```

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
| Issuer / firma / exp / audience | EP1-12 (`JwtValidationConfig`) |
| Claim `roles` → `ROLE_*` | `EntraRolesJwtConverter` |
| `/api/admin/**` | `hasRole("Admin")` → 403 JSON si no |

## Tests

```powershell
.\mvnw.cmd test
```

## Qué NO está aún

- Orquestación a `ms-requests` (EP1-15)
- Dockerfile (EP1-24)
