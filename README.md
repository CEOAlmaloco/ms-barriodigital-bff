# ms-barriodigital-bff

BFF de **BarrioDigital**: valida el JWT de Azure AD y, más adelante, llama a requests/catalog.

Puerto: **8080**

## Arranque local (sin Azure todavía)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Probar:

```powershell
curl http://localhost:8080/api/ping
curl http://localhost:8080/actuator/health
```

Con perfil `dev` el ping responde sin token (solo para desarrollar). En la demo real hay que usar el perfil por defecto + token Azure.

## Con Azure AD (cuando exista EP1-01)

```powershell
$env:AZURE_ISSUER_URI = "https://login.microsoftonline.com/<TENANT_ID>/v2.0"
.\mvnw.cmd spring-boot:run
```

Sin Bearer → **401**. Con JWT válido → **200** y el ping muestra `subject` / `roles`.

## Qué NO está aún (a propósito)

- Orquestación a `ms-requests` (EP1-15)
- Autorización fina por rol Admin/Operador (EP1-13)
- Tests MockMvc (EP1-22)
- Dockerfile (EP1-24)
