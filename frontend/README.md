# Frontend — Placeholder

> **Estado:** vacío y reservado. **No forma parte del MVP.**

---

## 1. Qué es y qué no es

| | |
|---|---|
| **Existe** | Esta carpeta, para que el monorepo tenga desde el principio la ubicación del cliente |
| **No existe** | Código, configuración de build, dependencias ni ningún artefacto |
| **Motivo** | El proyecto se centra en el backend. Añadir un frontend vacío sólo produciría ruido |

## 2. Por qué se crea ahora

1. **El monorepo se define completo desde el principio.** La estructura del repositorio no debería cambiar de forma cuando aparezca el frontend.
2. **Separa responsabilidades.** Backend, infraestructura, diagramas y decisiones se mantienen en carpetas distintas.
3. **Deja clara una frontera de arquitectura:** aquí irá lo que consume la API `/api/v1`, sin que el backend dependa de ella.

## 3. Cuándo se rellenará

Sólo cuando exista una decisión explícita sobre:

- [ ] Framework y versión (React, Vue, otro).
- [ ] Tipo de proyecto: SPA o generado.
- [ ] Si se queda en el monorepo o se extrae a otro repositorio.
- [ ] Si se añade autenticación con el mismo JWT del backend o con un proveedor externo.
- [ ] Dónde se despliega (S3 + CloudFront, ver US-083) y con qué CDN.
- [ ] Si se escribe end-to-end testing y dónde vive.

Nada de esto bloquea el desarrollo del backend.

## 4. Reglas para cuando llegue el momento

- El frontend **consume** la API. Nunca se modifica `backend/` desde aquí.
- El contrato es [`docs/architecture/api-contract.md`](../docs/architecture/api-contract.md) y su snapshot `openapi.json`.
- Ningún secreto del backend se replica en el cliente.
- El despliegue del frontend en AWS requiere autorización, igual que el backend (ver [`docs/planning/decisions-pending.md`](../docs/planning/decisions-pending.md) D14).

## 5. Historia relacionada

- **US-083** — Frontend estático en S3 + CloudFront. Marcada como **fuera del MVP** en [`docs/planning/backlog.md`](../docs/planning/backlog.md) §3.9.

---

> **Nota para el responsable:** si en algún momento decides no hacer frontend, esta carpeta puede
> eliminarse sin ningún impacto. No hay nada que deshacer.