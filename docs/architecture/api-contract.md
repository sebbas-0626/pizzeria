# Contrato de la API

> Contrato de la API de la pizzerÃ­a. **Fuente de verdad: [`requirements-v1.0.md`](../requirements/requirements-v1.0.md).**
> EspecificaciÃ³n legible por mÃ¡quina (la que Spring genera del cÃ³digo):
> <http://localhost:8080/v3/api-docs> en local, y su copia versionada en
> [`openapi.json`](openapi.json) desde **US-045**.
>
> QuÃ© es cada cosa: **Swagger** es la documentaciÃ³n viva y ejecutable. Este documento es el contrato
> con las reglas que no se deducen del cÃ³digo: quÃ© es un 404, por quÃ© un 409, quÃ© estÃ¡ fuera de
> alcance. No duplica la lista de endpoints; para eso estÃ¡ el OpenAPI.
>
> Ãšltima actualizaciÃ³n: **2026-10-17** (US-010). Ya hay endpoints: los dos pÃºblicos del catÃ¡logo.

---

## 1. Convenciones globales

| Regla | Valor |
|---|---|
| Base | `/api/v1` |
| Formato de peticiÃ³n y respuesta | `application/json`, salvo `multipart/form-data` en las subidas de imagen |
| Formato de error | `application/problem+json` (RFC 9457) |
| Moneda | **COP** (peso colombiano). Importes con **2 decimales** |
| Fechas | ISO-8601 con zona, siempre en UTC: `2026-10-11T18:30:50Z` |
| Identificadores | Enteros (BIGINT) en todas las tablas. Los genera la aplicaciÃ³n en el servidor |
| CorrelaciÃ³n | Toda respuesta lleva `X-Trace-Id`. Enviarlo de vuelta es opcional pero Ãºtil |

### 1.1 Los importes se calculan en el servidor

**El cliente nunca envÃ­a ni decide un precio.** Si una peticiÃ³n incluye `price`, `unitPrice` o `total`,
**se ignoran** y se aplican los del catÃ¡logo (RN-IM-01, RN-IM-02).

Es la regla que mÃ¡s repetir aparece en el backlog, y la razÃ³n es concreta: si el cliente pudiera
mandar el total, podrÃ­a mandar `1`. El servidor calcula; el cliente sÃ³lo dice **quÃ©** pide y **cuÃ¡nto**.

---

## 2. AutenticaciÃ³n

| Aspecto | Regla |
|---|---|
| Mecanismo | **Bearer** (JWT), cabecera `Authorization: Bearer <token>` |
| Firma | **RS256** |
| Caducidad | **15 minutos**. No hay token de renovaciÃ³n: al caducar, hay que iniciar sesiÃ³n otra vez |
| Claims | `sub` (UUID del cliente), `roles`, `iss`, `iat`, `exp` |
| Roles | `CLIENTE`, `COOK` o `ADMIN`. El primero es el Ãºnico al que llega el registro pÃºblico; los otros dos los crea el responsable por dentro |
| Obtener `ADMIN` | **Imposible por vÃ­a pÃºblica.** El registro siempre fuerza `CUSTOMER` (RN-SE-03) |

Se documenta en el contrato con el esquema `bearerAuth`, declarado en
[`OpenApiConfig`](../../backend/src/main/java/com/pizzeria/infrastructure/config/OpenApiConfig.java).

**El esquema se declara, pero no se aplica a todas las operaciones.** Los endpoints pÃºblicos no llevan
autenticaciÃ³n; cada endpoint protegido declara el suyo. Aplicarlo globalmente marcarÃ­a como privados
endpoints que no lo son.

---

## 3. Errores

Todo error sale como `application/problem+json` con esta forma:

```json
{
  "type": "https://pizzeria.local/errors/ORDERS_EMPTY",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "El pedido no puede estar vacÃ­o",
  "instance": "/api/v1/orders",
  "code": "ORDERS_EMPTY",
  "timestamp": "2026-10-11T18:30:50.102439Z",
  "traceId": "b7c432a7-b5c8-40ce-8c12-0cab6d03d91d"
}
```

Un error de validaciÃ³n **aÃ±ade** la lista de campos:

```json
{
  "code": "REQUEST_VALIDATION_FAILED",
  "status": 400,
  "errors": [
    { "campo": "items[0].quantity", "mensaje": "debe ser menor o igual que 20" }
  ]
}
```

### 3.1 CÃ³mo se consume un error

**Compara el `code`, nunca el `detail`.** El `detail` estÃ¡ en espaÃ±ol y puede cambiar; el `code` es
contrato y no cambia nunca.

### 3.2 CatÃ¡logo de cÃ³digos

| CÃ³digo | HTTP | CuÃ¡ndo |
|---|---|---|
| `REQUEST_VALIDATION_FAILED` | 400 | El cuerpo o un parÃ¡metro no supera la validaciÃ³n. Incluye `errors` |
| `MALFORMED_REQUEST` | 400 | El JSON no se puede deserializar, o un parÃ¡metro no tiene el tipo esperado |
| `RESOURCE_NOT_FOUND` | 404 | La ruta no existe en la API |
| `ORDERS_NOT_FOUND` | 404 | El pedido no existe **o es de otro cliente** |
| `CATALOG_PIZZA_NOT_FOUND` | 404 | La pizza no existe |
| `CATALOG_VARIANT_NOT_FOUND` | 404 | La variante no existe |
| `IDENTITY_CUSTOMER_NOT_FOUND` | 404 | El cliente no existe |
| `IDENTITY_EMAIL_ALREADY_REGISTERED` | 409 | El email ya estÃ¡ registrado |
| `ORDERS_INVALID_TRANSITION` | 409 | La transiciÃ³n de estado no estÃ¡ permitida desde el estado actual |
| `PAYMENTS_ALREADY_REGISTERED` | 409 | El pedido ya tiene un cobro |
| `ORDERS_EMPTY` | 422 | Se intentÃ³ crear un pedido sin lÃ­neas |
| `CATALOG_VARIANT_NOT_AVAILABLE` | 422 | La variante estÃ¡ inactiva o no disponible |
| `PAYMENTS_AMOUNT_MISMATCH` | 422 | El importe recibido no coincide con el total |
| `INTERNAL_ERROR` | 500 | Fallo no previsto. El mensaje es **siempre** genÃ©rico |

El catÃ¡logo vive en
[`ErrorCode`](../../backend/src/main/java/com/pizzeria/domain/exception/ErrorCode.java); es la Ãºnica
fuente y cada cÃ³digo se mapea a un estado en
[`GlobalExceptionHandler`](../../backend/src/main/java/com/pizzeria/shared/exception/GlobalExceptionHandler.java).

### 3.3 Por quÃ© 404 y no 403

Un cliente que pide el pedido de **otro** recibe **404**, no 403 (RN-SE-06).

403 confirmarÃ­a que ese pedido existe: bastarÃ­a con probarlo con identificadores ajenos para enumerar
pedidos reales. El 404 no revela nada. La propiedad del recurso se verifica **en la consulta**
(`findByIdAndCustomerId`), nunca comparando despuÃ©s.

---

## 4. Endpoints

| MÃ©todo | Ruta | Acceso | Historia | Estado |
|---|---|---|---|---|
| `GET` | `/api/v1/pizzas` | PÃºblico | US-010 | âœ… **Hecho** |
| `GET` | `/api/v1/pizzas/{pizzaId}` | PÃºblico | US-010 | âœ… **Hecho** |
| `POST` | `/api/v1/admin/pizzas` | `ADMIN` | US-011 | Pendiente |
| `PATCH` | `/api/v1/admin/pizzas/{pizzaId}` | `ADMIN` | US-011 | Pendiente |
| `POST` | `/api/v1/admin/pizzas/{pizzaId}/variants` | `ADMIN` | US-012 | Pendiente |
| `PATCH` | `/api/v1/admin/variants/{variantId}` | `ADMIN` | US-012 | Pendiente |
| `POST` | `/api/v1/auth/register` | PÃºblico | US-020 | Pendiente |
| `POST` | `/api/v1/auth/login` | PÃºblico | US-021 | Pendiente |
| `GET` | `/api/v1/customers/me` | Autenticado | US-023 | Pendiente |
| `POST` | `/api/v1/orders` | Autenticado | US-030 | ✅ **Hecho** |
| `GET` | `/api/v1/orders` | Autenticado | US-031 | ✅ **Hecho** |
| `GET` | `/api/v1/orders/{orderId}` | Autenticado | US-032 | ✅ **Hecho** |
| `POST` | `/api/v1/orders/{orderId}/cancel` | Autenticado | US-033 | ✅ **Hecho** |
| `GET` | `/api/v1/employee/orders` | `ADMIN`, `EMPLOYEE` | US-034 | ✅ **Hecho** |
| `POST` | `/api/v1/employee/orders/{id}/confirm` | `ADMIN`, `EMPLOYEE` | US-035 | ✅ **Hecho** |
| `POST` | `/api/v1/employee/orders/{id}/cancel` | `ADMIN`, `EMPLOYEE` | US-036 | ✅ **Hecho** |
| `POST` | `/api/v1/employee/orders/{id}/complete-pickup` | `ADMIN`, `EMPLOYEE` | US-037 | ✅ **Hecho** |
| `POST` | `/api/v1/admin/pizzas/{id}/image` | `ADMIN` | US-052 | Pendiente |

---

## 4.0 QuÃ© entra por dÃ³nde

| Prefijo | QuiÃ©n entra | QuÃ© hay dentro |
|---|---|---|
| `/api/v1/pizzas/**` | **PÃºblico**, sin token | Leer el catÃ¡logo |
| `/api/v1/admin/**` | `ADMIN` | **El catÃ¡logo**: crear pizzas, variantes, precios y disponibilidad (US-011, US-012, US-013, US-052) |
| `/api/v1/employee/**` | `ADMIN` o `EMPLOYEE` | **Los pedidos del mostrador**: listar todos, confirmar, cancelar y registrar la recogida con el cobro (US-034â€¦US-037) |
| `/api/v1/auth/**` | PÃºblico | Registrarse y hacer login |
| `/api/v1/orders/**` | Autenticado | Los pedidos **del propio cliente** |

La separaciÃ³n no es cosmetics: **`/admin/**` es lo que cuesta dinero y `/employee/**` es lo que se hace
todos los dÃ­as**. Por eso un `EMPLOYEE` no entra en `/admin/**` aunque sus rutas sean de pedidos
(requisitos v1.1 Â§2.1), y un `ADMIN` entra en `/employee/**` porque puede hacerlo todo.

---

## 4.1 El catÃ¡logo pÃºblico

Las dos rutas de lectura son **pÃºblicas**: no llevan token y no se les pide uno. Lo protegido en S2 es
`/api/v1/admin/**`, que llega con US-011.

### `GET /api/v1/pizzas`

Lista el catÃ¡logo en pÃ¡ginas.

| ParÃ¡metro | Tipo | Por defecto | Valores | Regla |
|---|---|---|---|---|
| `availableOnly` | booleano | **`true`** | `true`, `false` | `true` muestra **sÃ³lo lo vendible** (RN-DS-02). `false` aÃ±ade lo dado de baja y lo agotado, para la vista previa del administrador |
| `page` | entero | `0` | â‰¥ 0 | Ãndice de pÃ¡gina, empezando en 0 |
| `size` | entero | `20` | 1 â€“ 100 | Filas por pÃ¡gina. Fuera de rango â†’ **400** |

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
| **Orden** | Por `name`, sin distinguir mayÃºsculas: `Bufala, Diavola, Margarita`. **No** por fecha ni por precio |
| **`price`** | NÃºmero JSON, **no texto**, y **sin decimales**: `12500`, nunca `"12500.00"` ni `12500.00` (RN-IM-05) |
| **`size`** | `SMALL` \| `MEDIUM` \| `LARGE`. Es un conjunto cerrado: el esquema lo restringe |
| **Orden de `variants`** | De menor a mayor tamaÃ±o |
| **CatÃ¡logo vacÃ­o** | `200` con `content: []`, `totalElements: 0` y `totalPages: 0`. Nunca `404` |
| **PÃ¡gina fuera de rango** | `200` con `content: []` y el `totalElements` correcto. Un `404` obligarÃ­a al cliente a distinguir dos casos indistinguibles desde fuera |
| **`description` ausente** | `null`, nunca `""` |
| **`totalElements` y `totalPages`** | Siempre con el **mismo filtro** que el contenido. Si no coinciden, el cliente pagina sobre una cuenta que no ve |

**Por quÃ© `availableOnly` viene en `true` por defecto.** El catÃ¡logo pÃºblico muestra lo que se puede pedir.
Una pizza sin ninguna variante disponible (RN-DS-02) no aparece, aunque estÃ© activa: anunciarla serÃ­a una
entrada que nadie puede convertir en pedido.

### `GET /api/v1/pizzas/{pizzaId}`

Detalle de una pizza, mismo cuerpo que un elemento de la lista.

| SituaciÃ³n | Respuesta |
|---|---|
| Existe y se puede pedir | `200` con `id`, `name`, `description` y `variants` |
| No existe | `404` `CATALOG_PIZZA_NOT_FOUND` |
| EstÃ¡ dada de baja | `404` `CATALOG_PIZZA_NOT_FOUND` |
| No tiene ninguna variante disponible | `404` `CATALOG_PIZZA_NOT_FOUND` |
| `pizzaId` no es un nÃºmero entero | `400` `MALFORMED_REQUEST` |

Las tres filas de 404 son **la misma respuesta a propÃ³sito**: el catÃ¡logo pÃºblico no enseÃ±a lo dado de baja,
asÃ­ que confirmar por el mismo camino que buscar serÃ­a filtrar informaciÃ³n. Es el mismo criterio que aplica
D4 a los pedidos ajenos.

### 4.3 Identidad

#### `POST /api/v1/auth/register`

Registra un cliente. **PÃºblica**: no lleva token.

Cuerpo:

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `email` | texto | sÃ­ | Formato vÃ¡lido, 254 caracteres como mÃ¡ximo. Se guarda **en minÃºsculas** |
| `password` | texto | sÃ­ | 10 caracteres como mÃ­nimo. Se guarda **codificada con BCrypt**, nunca en claro |
| `fullName` | texto | sÃ­ | 1 a 120 caracteres |
| `phone` | texto | no | 30 caracteres como mÃ¡ximo. Ausente sale como `null` |

> **No hay campo de rol, y se note.** Un `"role": "ADMIN"` â€”o un `"roles"`, o cualquier otro campo que no
> estÃ© en la tablaâ€” devuelve **400** `MALFORMED_REQUEST` diciendo cuÃ¡l sobra. La regla es global
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

**No devuelve token** (D11, ratificada): quien se registra hace login despuÃ©s. **No devuelve la contraseÃ±a
ni su hash**: son dos formas de filtrar el mismo secreto.

| SituaciÃ³n | Respuesta |
|---|---|
| Falta `email`, `fullName`, o la contraseÃ±a no llega a 10 caracteres | `400` `REQUEST_VALIDATION_FAILED`, con la lista de campos |
| El cuerpo trae un campo que no existe (un `role`, una errata) | `400` `MALFORMED_REQUEST`, diciendo el nombre del campo |
| El email ya estÃ¡ registrado | `409` `IDENTITY_EMAIL_ALREADY_REGISTERED` |

> **El 409 permite enumerar correos.** EstÃ¡ aceptado en el MVP y anotado como deuda en `backlog.md`: la
> alternativa â€”responder Â«registradoÂ» siempreâ€” deja al usuario sin saber por quÃ© falla su alta.

### 4.4 Lo que todavÃ­a no estÃ¡ en el catÃ¡logo

| Ausente | Llega con |
|---|---|
| `imageUrl` en la respuesta | US-052. Se publica una URL prefirmada generada por el servidor, nunca una clave de almacenamiento |
| Alta, ediciÃ³n y baja de pizzas | US-011, US-012, US-013 |
| Cualquier campo de precio en la entrada | Nunca: el cliente no manda precios (RN-IM-01) |

### 4.2 No existe ningÃºn `DELETE` en el catÃ¡logo

El catÃ¡logo se desactiva, no se borra (RF-CA-09). Desactivar es un `PATCH` con `active=false`. Si algÃºn
dÃ­a apareciera un `DELETE` en el catÃ¡logo, serÃ­a un error de diseÃ±o, no una mejora. US-013 lo verifica con
un test que enumera las rutas registradas y falla si aparece cualquiera.

---

## 5. Fuera de alcance

Decidido antes de escribir cÃ³digo, para que no se lea como un olvido:

| No existe | Por quÃ© |
|---|---|
| EnvÃ­os a domicilio | Fuera del alcance acordado |
| Pasarelas de pago | SÃ³lo efectivo en el MVP |
| MÃ³dulo de cocina | Fuera del alcance |
| MÃºltiples sedes | Una sola sede |
| Token de renovaciÃ³n | Fuera de alcance en esta versiÃ³n |
| Caducidad automÃ¡tica de `PENDING` | Sin mÃ³dulo de temporizadores; anotado como deuda |

---

## 6. CÃ³mo se mantiene este documento

| Cambio | QuÃ© hay que actualizar aquÃ­ |
|---|---|
| Endpoint nuevo o modificado | La tabla de Â§4, su secciÃ³n de detalle y el OpenAPI, en el mismo cambio |
| CÃ³digo o cÃ³digo HTTP nuevo | Â§3.2 y `ErrorCode` |
| Regla de negocio que afecta al contrato | Â§1, Â§2 o Â§3 y `requirements-v1.0.md` |
| RazÃ³n por la que un 404 no es un 403 | Â§3.3 |
| ParÃ¡metro, orden o lÃ­mite de un listado | Su secciÃ³n de detalle, con el valor exacto |

Una historia **no** se cierra con este documento desfasado. Si el OpenAPI y `api-contract.md` no coinciden,
la historia no estÃ¡ terminada (criterio 3 de US-005 y definiciÃ³n de terminado).

> El contraste automÃ¡tico entre el OpenAPI generado y `openapi.json` llega en **US-045**. Hasta entonces,
> la alineaciÃ³n es responsabilidad de quien revisa.
