# Contrato de la API

> Contrato de la API de la pizzería. **Fuente de verdad: [`requirements-v1.0.md`](../requirements/requirements-v1.0.md).**
> Especificación legible por máquina (la que Spring genera del código):
> <http://localhost:8080/v3/api-docs> en local, y su copia versionada en
> [`openapi.json`](openapi.json) desde **US-045**.
>
> Qué es cada cosa: **Swagger** es la documentación viva y ejecutable. Este documento es el contrato
> con las reglas que no se deducen del código: qué es un 404, por qué un 409, qué está fuera de
> alcance. No duplica la lista de endpoints; para eso está el OpenAPI.
>
> Última actualización: **2026-10-11** (US-005). **Todavía no hay endpoints**: el primero llega con
> US-010.

---

## 1. Convenciones globales

| Regla | Valor |
|---|---|
| Base | `/api/v1` |
| Formato de petición y respuesta | `application/json`, salvo `multipart/form-data` en las subidas de imagen |
| Formato de error | `application/problem+json` (RFC 9457) |
| Moneda | **COP** (peso colombiano). Importes enteros, **sin decimales** |
| Fechas | ISO-8601 con zona, siempre en UTC: `2026-10-11T18:30:50Z` |
| Identificadores | UUID v4, generados por el cliente de la API en el servidor |
| Correlación | Toda respuesta lleva `X-Trace-Id`. Enviarlo de vuelta es opcional pero útil |

### 1.1 Los importes se calculan en el servidor

**El cliente nunca envía ni decide un precio.** Si una petición incluye `price`, `unitPrice` o `total`,
**se ignoran** y se aplican los del catálogo (RN-IM-01, RN-IM-02).

Es la regla que más repetir aparece en el backlog, y la razón es concreta: si el cliente pudiera
mandar el total, podría mandar `1`. El servidor calcula; el cliente sólo dice **qué** pide y **cuánto**.

---

## 2. Autenticación

| Aspecto | Regla |
|---|---|
| Mecanismo | **Bearer** (JWT), cabecera `Authorization: Bearer <token>` |
| Firma | **RS256** |
| Caducidad | **15 minutos**. No hay token de renovación: al caducar, hay que iniciar sesión otra vez |
| Claims | `sub` (UUID del cliente), `roles`, `iss`, `iat`, `exp` |
| Roles | Exactamente uno por usuario: `CUSTOMER` o `ADMIN` |
| Obtener `ADMIN` | **Imposible por vía pública.** El registro siempre fuerza `CUSTOMER` (RN-SE-03) |

Se documenta en el contrato con el esquema `bearerAuth`, declarado en
[`OpenApiConfig`](../../backend/src/main/java/com/pizzeria/infrastructure/config/OpenApiConfig.java).

**El esquema se declara, pero no se aplica a todas las operaciones.** Los endpoints públicos no llevan
autenticación; cada endpoint protegido declara el suyo. Aplicarlo globalmente marcaría como privados
endpoints que no lo son.

---

## 3. Errores

Todo error sale como `application/problem+json` con esta forma:

```json
{
  "type": "https://pizzeria.local/errors/ORDERS_EMPTY",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "El pedido no puede estar vacío",
  "instance": "/api/v1/orders",
  "code": "ORDERS_EMPTY",
  "timestamp": "2026-10-11T18:30:50.102439Z",
  "traceId": "b7c432a7-b5c8-40ce-8c12-0cab6d03d91d"
}
```

Un error de validación **añade** la lista de campos:

```json
{
  "code": "REQUEST_VALIDATION_FAILED",
  "status": 400,
  "errors": [
    { "campo": "items[0].quantity", "mensaje": "debe ser menor o igual que 20" }
  ]
}
```

### 3.1 Cómo se consume un error

**Compara el `code`, nunca el `detail`.** El `detail` está en español y puede cambiar; el `code` es
contrato y no cambia nunca.

### 3.2 Catálogo de códigos

| Código | HTTP | Cuándo |
|---|---|---|
| `REQUEST_VALIDATION_FAILED` | 400 | El cuerpo o un parámetro no supera la validación. Incluye `errors` |
| `MALFORMED_REQUEST` | 400 | El JSON no se puede deserializar, o un parámetro no tiene el tipo esperado |
| `RESOURCE_NOT_FOUND` | 404 | La ruta no existe en la API |
| `ORDERS_NOT_FOUND` | 404 | El pedido no existe **o es de otro cliente** |
| `CATALOG_PIZZA_NOT_FOUND` | 404 | La pizza no existe |
| `CATALOG_VARIANT_NOT_FOUND` | 404 | La variante no existe |
| `IDENTITY_CUSTOMER_NOT_FOUND` | 404 | El cliente no existe |
| `IDENTITY_EMAIL_ALREADY_REGISTERED` | 409 | El email ya está registrado |
| `ORDERS_INVALID_TRANSITION` | 409 | La transición de estado no está permitida desde el estado actual |
| `PAYMENTS_ALREADY_REGISTERED` | 409 | El pedido ya tiene un cobro |
| `ORDERS_EMPTY` | 422 | Se intentó crear un pedido sin líneas |
| `CATALOG_VARIANT_NOT_AVAILABLE` | 422 | La variante está inactiva o no disponible |
| `PAYMENTS_AMOUNT_MISMATCH` | 422 | El importe recibido no coincide con el total |
| `INTERNAL_ERROR` | 500 | Fallo no previsto. El mensaje es **siempre** genérico |

El catálogo vive en
[`ErrorCode`](../../backend/src/main/java/com/pizzeria/domain/exception/ErrorCode.java); es la única
fuente y cada código se mapea a un estado en
[`GlobalExceptionHandler`](../../backend/src/main/java/com/pizzeria/shared/exception/GlobalExceptionHandler.java).

### 3.3 Por qué 404 y no 403

Un cliente que pide el pedido de **otro** recibe **404**, no 403 (RN-SE-06).

403 confirmaría que ese pedido existe: bastaría con probarlo con identificadores ajenos para enumerar
pedidos reales. El 404 no revela nada. La propiedad del recurso se verifica **en la consulta**
(`findByIdAndCustomerId`), nunca comparando después.

---

## 4. Endpoints

| Método | Ruta | Acceso | Historia | Estado |
|---|---|---|---|---|
| `GET` | `/api/v1/pizzas` | Público | US-010 | Pendiente |
| `GET` | `/api/v1/pizzas/{pizzaId}` | Público | US-010 | Pendiente |
| `POST` | `/api/v1/admin/pizzas` | `ADMIN` | US-011 | Pendiente |
| `PATCH` | `/api/v1/admin/pizzas/{pizzaId}` | `ADMIN` | US-011 | Pendiente |
| `POST` | `/api/v1/admin/pizzas/{pizzaId}/variants` | `ADMIN` | US-012 | Pendiente |
| `PATCH` | `/api/v1/admin/variants/{variantId}` | `ADMIN` | US-012 | Pendiente |
| `POST` | `/api/v1/auth/register` | Público | US-020 | Pendiente |
| `POST` | `/api/v1/auth/login` | Público | US-021 | Pendiente |
| `GET` | `/api/v1/customers/me` | Autenticado | US-023 | Pendiente |
| `POST` | `/api/v1/orders` | Autenticado | US-030 | Pendiente |
| `GET` | `/api/v1/orders` | Autenticado | US-031 | Pendiente |
| `GET` | `/api/v1/orders/{orderId}` | Autenticado | US-032 | Pendiente |
| `POST` | `/api/v1/orders/{orderId}/cancel` | Autenticado | US-033 | Pendiente |
| `GET` | `/api/v1/admin/orders` | `ADMIN` | US-034 | Pendiente |
| `POST` | `/api/v1/admin/orders/{id}/confirm` | `ADMIN` | US-035 | Pendiente |
| `POST` | `/api/v1/admin/orders/{id}/cancel` | `ADMIN` | US-036 | Pendiente |
| `POST` | `/api/v1/admin/orders/{id}/complete-pickup` | `ADMIN` | US-037 | Pendiente |
| `POST` | `/api/v1/admin/pizzas/{id}/image` | `ADMIN` | US-052 | Pendiente |

> **Todos pendientes.** Sprint 1 construye los cimientos: la API todavía **no expone ningún endpoint**.
> Por eso el `paths` del OpenAPI sale vacío. Es lo esperado, no un fallo.

### 4.1 No existe ningún `DELETE` en el catálogo

El catálogo se desactiva, no se borra (RF-CA-09). Desactivar es un `PATCH` con `active=false`. Si algún
día apareciera un `DELETE` en el catálogo, sería un error de diseño, no una mejora.

---

## 5. Fuera de alcance

Decidido antes de escribir código, para que no se lea como un olvido:

| No existe | Por qué |
|---|---|
| Envíos a domicilio | Fuera del alcance acordado |
| Pasarelas de pago | Sólo efectivo en el MVP |
| Módulo de cocina | Fuera del alcance |
| Múltiples sedes | Una sola sede |
| Token de renovación | Fuera de alcance en esta versión |
| Caducidad automática de `PENDING` | Sin módulo de temporizadores; anotado como deuda |

---

## 6. Cómo se mantiene este documento

| Cambio | Qué hay que actualizar aquí |
|---|---|
| Endpoint nuevo o modificado | La tabla de §4 **y** el OpenAPI, en el mismo cambio |
| Código o código HTTP nuevo | §3.2 y `ErrorCode` |
| Regla de negocio que afecta al contrato | §1, §2 o §3 y `requirements-v1.0.md` |
| Razón por la que un 404 no es un 403 | §3.3 |

Una historia **no** se cierra con este documento desfasado. Si el OpenAPI y `api-contract.md` no coinciden,
la historia no está terminada (criterio 3 de US-005 y definición de terminado).

> El contraste automático entre el OpenAPI generado y `openapi.json` llega en **US-045**. Hasta entonces,
> la alineación es responsabilidad de quien revisa.