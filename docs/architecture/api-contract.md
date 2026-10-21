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
> Última actualización: **2026-10-17** (US-010). Ya hay endpoints: los dos públicos del catálogo.

---

## 1. Convenciones globales

| Regla | Valor |
|---|---|
| Base | `/api/v1` |
| Formato de petición y respuesta | `application/json`, salvo `multipart/form-data` en las subidas de imagen |
| Formato de error | `application/problem+json` (RFC 9457) |
| Moneda | **COP** (peso colombiano). Importes enteros, **sin decimales** |
| Fechas | ISO-8601 con zona, siempre en UTC: `2026-10-11T18:30:50Z` |
| Identificadores | UUID v4 en clientes, pedidos y pagos; **BIGINT en el catálogo** (migración V2). Los genera la aplicación en el servidor |
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
| Roles | `CUSTOMER`, `EMPLOYEE` o `ADMIN`. El primero es el único al que llega el registro público; los otros dos los crea el responsable por dentro ([`requirements-v1.1.md`](../requirements/requirements-v1.1.md)) |
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
| `GET` | `/api/v1/pizzas` | Público | US-010 | ✅ **Hecho** |
| `GET` | `/api/v1/pizzas/{pizzaId}` | Público | US-010 | ✅ **Hecho** |
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
| `GET` | `/api/v1/employee/orders` | `ADMIN`, `EMPLOYEE` | US-034 | Pendiente |
| `POST` | `/api/v1/employee/orders/{id}/confirm` | `ADMIN`, `EMPLOYEE` | US-035 | Pendiente |
| `POST` | `/api/v1/employee/orders/{id}/cancel` | `ADMIN`, `EMPLOYEE` | US-036 | Pendiente |
| `POST` | `/api/v1/employee/orders/{id}/complete-pickup` | `ADMIN`, `EMPLOYEE` | US-037 | Pendiente |
| `POST` | `/api/v1/admin/pizzas/{id}/image` | `ADMIN` | US-052 | Pendiente |

---

## 4.0 Qué entra por dónde

| Prefijo | Quién entra | Qué hay dentro |
|---|---|---|
| `/api/v1/pizzas/**` | **Público**, sin token | Leer el catálogo |
| `/api/v1/admin/**` | `ADMIN` | **El catálogo**: crear pizzas, variantes, precios y disponibilidad (US-011, US-012, US-013, US-052) |
| `/api/v1/employee/**` | `ADMIN` o `EMPLOYEE` | **Los pedidos del mostrador**: listar todos, confirmar, cancelar y registrar la recogida con el cobro (US-034…US-037) |
| `/api/v1/auth/**` | Público | Registrarse y hacer login |
| `/api/v1/orders/**` | Autenticado | Los pedidos **del propio cliente** |

La separación no es cosmetics: **`/admin/**` es lo que cuesta dinero y `/employee/**` es lo que se hace
todos los días**. Por eso un `EMPLOYEE` no entra en `/admin/**` aunque sus rutas sean de pedidos
(requisitos v1.1 §2.1), y un `ADMIN` entra en `/employee/**` porque puede hacerlo todo.

---

## 4.1 El catálogo público

Las dos rutas de lectura son **públicas**: no llevan token y no se les pide uno. Lo protegido en S2 es
`/api/v1/admin/**`, que llega con US-011.

### `GET /api/v1/pizzas`

Lista el catálogo en páginas.

| Parámetro | Tipo | Por defecto | Valores | Regla |
|---|---|---|---|---|
| `availableOnly` | booleano | **`true`** | `true`, `false` | `true` muestra **sólo lo vendible** (RN-DS-02). `false` añade lo dado de baja y lo agotado, para la vista previa del administrador |
| `page` | entero | `0` | ≥ 0 | Índice de página, empezando en 0 |
| `size` | entero | `20` | 1 – 100 | Filas por página. Fuera de rango → **400** |

Respuesta `200`:

```json
{
  "content": [
    {
      "id": 1,
      "name": "Margarita",
      "description": "Tomate, mozzarella y albahaca",
      "variants": [
        {
          "id": 2,
          "size": "SMALL",
          "price": 9900,
          "available": true
        }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

Reglas del contrato:

| Regla | Detalle |
|---|---|
| **Orden** | Por `name`, sin distinguir mayúsculas: `Bufala, Diavola, Margarita`. **No** por fecha ni por precio |
| **`price`** | Número JSON, **no texto**, y **sin decimales**: `12500`, nunca `"12500.00"` ni `12500.00` (RN-IM-05) |
| **`size`** | `SMALL` \| `MEDIUM` \| `LARGE`. Es un conjunto cerrado: el esquema lo restringe |
| **Orden de `variants`** | De menor a mayor tamaño |
| **Catálogo vacío** | `200` con `content: []`, `totalElements: 0` y `totalPages: 0`. Nunca `404` |
| **Página fuera de rango** | `200` con `content: []` y el `totalElements` correcto. Un `404` obligaría al cliente a distinguir dos casos indistinguibles desde fuera |
| **`description` ausente** | `null`, nunca `""` |
| **`totalElements` y `totalPages`** | Siempre con el **mismo filtro** que el contenido. Si no coinciden, el cliente pagina sobre una cuenta que no ve |

**Por qué `availableOnly` viene en `true` por defecto.** El catálogo público muestra lo que se puede pedir.
Una pizza sin ninguna variante disponible (RN-DS-02) no aparece, aunque esté activa: anunciarla sería una
entrada que nadie puede convertir en pedido.

### `GET /api/v1/pizzas/{pizzaId}`

Detalle de una pizza, mismo cuerpo que un elemento de la lista.

| Situación | Respuesta |
|---|---|
| Existe y se puede pedir | `200` con `id`, `name`, `description` y `variants` |
| No existe | `404` `CATALOG_PIZZA_NOT_FOUND` |
| Está dada de baja | `404` `CATALOG_PIZZA_NOT_FOUND` |
| No tiene ninguna variante disponible | `404` `CATALOG_PIZZA_NOT_FOUND` |
| `pizzaId` no es un número entero | `400` `MALFORMED_REQUEST` |

Las tres filas de 404 son **la misma respuesta a propósito**: el catálogo público no enseña lo dado de baja,
así que confirmar por el mismo camino que buscar sería filtrar información. Es el mismo criterio que aplica
D4 a los pedidos ajenos.

### 4.3 Identidad

#### `POST /api/v1/auth/register`

Registra un cliente. **Pública**: no lleva token.

Cuerpo:

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `email` | texto | sí | Formato válido, 254 caracteres como máximo. Se guarda **en minúsculas** |
| `password` | texto | sí | 10 caracteres como mínimo. Se guarda **codificada con BCrypt**, nunca en claro |
| `fullName` | texto | sí | 1 a 120 caracteres |
| `phone` | texto | no | 30 caracteres como máximo. Ausente sale como `null` |

> **No hay campo de rol, y se note.** Un `"role": "ADMIN"` —o un `"roles"`, o cualquier otro campo que no
> esté en la tabla— devuelve **400** `MALFORMED_REQUEST` diciendo cuál sobra. La regla es global
> (`spring.jackson.deserialization.fail-on-unknown-properties=true`): **la API rechaza los campos que no
> declara**, y el rol nunca se toma del cuerpo (RF-ID-04). El registro **siempre** crea `CUSTOMER`.

Respuesta `201`:

```json
{
  "id": "6f1c2b4e-0a3d-4c5e-9b7a-8d9e0f1a2b3c",
  "email": "lucia@pizzeria.co",
  "fullName": "Lucia Perez",
  "phone": "+573001234567",
  "role": "CUSTOMER",
  "createdAt": "2026-10-18T15:00:00Z"
}
```

**No devuelve token** (D11, ratificada): quien se registra hace login después. **No devuelve la contraseña
ni su hash**: son dos formas de filtrar el mismo secreto.

| Situación | Respuesta |
|---|---|
| Falta `email`, `fullName`, o la contraseña no llega a 10 caracteres | `400` `REQUEST_VALIDATION_FAILED`, con la lista de campos |
| El cuerpo trae un campo que no existe (un `role`, una errata) | `400` `MALFORMED_REQUEST`, diciendo el nombre del campo |
| El email ya está registrado | `409` `IDENTITY_EMAIL_ALREADY_REGISTERED` |

> **El 409 permite enumerar correos.** Está aceptado en el MVP y anotado como deuda en `backlog.md`: la
> alternativa —responder «registrado» siempre— deja al usuario sin saber por qué falla su alta.

### 4.4 Lo que todavía no está en el catálogo

| Ausente | Llega con |
|---|---|
| `imageUrl` en la respuesta | US-052. Se publica una URL prefirmada generada por el servidor, nunca una clave de almacenamiento |
| Alta, edición y baja de pizzas | US-011, US-012, US-013 |
| Cualquier campo de precio en la entrada | Nunca: el cliente no manda precios (RN-IM-01) |

### 4.2 No existe ningún `DELETE` en el catálogo

El catálogo se desactiva, no se borra (RF-CA-09). Desactivar es un `PATCH` con `active=false`. Si algún
día apareciera un `DELETE` en el catálogo, sería un error de diseño, no una mejora. US-013 lo verifica con
un test que enumera las rutas registradas y falla si aparece cualquiera.

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
| Endpoint nuevo o modificado | La tabla de §4, su sección de detalle y el OpenAPI, en el mismo cambio |
| Código o código HTTP nuevo | §3.2 y `ErrorCode` |
| Regla de negocio que afecta al contrato | §1, §2 o §3 y `requirements-v1.0.md` |
| Razón por la que un 404 no es un 403 | §3.3 |
| Parámetro, orden o límite de un listado | Su sección de detalle, con el valor exacto |

Una historia **no** se cierra con este documento desfasado. Si el OpenAPI y `api-contract.md` no coinciden,
la historia no está terminada (criterio 3 de US-005 y definición de terminado).

> El contraste automático entre el OpenAPI generado y `openapi.json` llega en **US-045**. Hasta entonces,
> la alineación es responsabilidad de quien revisa.