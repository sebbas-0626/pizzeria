# Requisitos del sistema — Backend Pizzería

- **Versión:** 1.0
- **Estado:** ✅ **Aprobado** el 2026-10-17 · **congelado**
- **Fecha:** 2026-10-11 (redactado) · 2026-10-17 (aprobado)
- **Ámbito:** backend de una pizzería con una única sede, pedidos para recogida presencial y pago en efectivo
- **Documento fuente de verdad nº 1** (ver [`AGENTS.md`](../../AGENTS.md) §1)

> **Este documento está congelado.** Las 14 decisiones pendientes se ratificaron con sus valores por defecto el
> 2026-10-17 (§11 y §12), de modo que **ya no queda ninguna marca ⚙️**. Cualquier cambio posterior a esta fecha
> **no se edita aquí**: genera una `requirements-v1.1.md` con su registro de cambios, según la regla
> anti-contradicción de `AGENTS.md` §1.

---

## 1. Objetivo del sistema

Permitir que los clientes de una pizzería consulten el catálogo, creen pedidos para recoger en el
establecimiento y gestionen esos pedidos, y que el personal pueda administrar el catálogo, confirmar y
entregar pedidos y registrar el cobro en efectivo, **garantizando que los importes los calcula siempre el
servidor y que cada cobro queda registrado de forma atómica con la entrega**.

## 2. Glosario

| Término | Significado |
|---|---|
| **Pizza** | Producto del catálogo. No tiene precio propio: el precio está en sus variantes |
| **Variante** | Combinación de una pizza con un tamaño. Tiene precio propio y disponibilidad propia |
| **Tamaño** | `SMALL`, `MEDIUM` o `LARGE`. No todas las pizzas ofrecen los tres |
| **Pedido** | Conjunto de líneas pedido por un cliente para recogida |
| **Línea de pedido** | Una variante con una cantidad. Guarda la foto histórica de sus datos comerciales |
| **Recogida** | Entrega presencial del pedido en el establecimiento (`PICKUP`) |
| **Foto histórica** | Copia de los datos comerciales en el momento del pedido: nombre, tamaño, precio unitario, cantidad e importe |
| **Terminal** | Estado del que un pedido no puede salir |
| **Cobro** | Registro del pago en efectivo recibido al entregar el pedido |
| **Fulfillment type** | Modalidad de entrega. En el sistema sólo existe `PICKUP` |

---

## 3. Actores y permisos

| Actor | Descripción | Puede |
|---|---|---|
| **Visitante** | Sin autenticar | Consultar el catálogo público |
| **Cliente** (`CUSTOMER`) | Usuario registrado | Consultar el catálogo, crear pedidos, ver los suyos, cancelar los suyos mientras estén `PENDING` |
| **Administrador** (`ADMIN`) | Personal de la pizzería | Todo lo del cliente, más gestionar el catálogo, listar todos los pedidos, confirmar, cancelar y registrar la recogida con el cobro |

---

## 4. Requisitos funcionales

### 4.1 Módulo de identidad — RF-ID

| ID | Requisito | Historia |
|---|---|---|
| **RF-ID-01** | El sistema permite registrar un cliente con email, nombre y contraseña | US-020 |
| **RF-ID-02** | El email es único, con independencia de mayúsculas y minúsculas | US-020 |
| **RF-ID-03** | La contraseña se almacena cifrada con un algoritmo de hashing con sal; nunca en texto plano ni de forma reversible | US-020 |
| **RF-ID-04** | El registro **ignora cualquier rol enviado por el cliente** y fuerza siempre `CUSTOMER` | US-020 |
| **RF-ID-05** | El sistema permite autenticar con email y contraseña y devolver un token de acceso | US-021 |
| **RF-ID-06** | El token es un JWT firmado, con expiración, y contiene el identificador del cliente y sus roles | US-021 |
| **RF-ID-07** | El cliente puede consultar su propio perfil con su token | US-023 |
| **RF-ID-08** | El perfil nunca incluye la contraseña ni su hash | US-023 |
| **RF-ID-09** | Las credenciales incorrectas producen un error genérico que no revela si el email existe | US-021 |

### 4.2 Módulo de catálogo — RF-CA

| ID | Requisito | Historia |
|---|---|---|
| **RF-CA-01** | Cualquier visitante puede listar las pizzas activas del catálogo, con sus variantes disponibles | US-010 |
| **RF-CA-02** | Cualquier visitante puede consultar el detalle de una pizza con sus variantes, tamaños y precios | US-010 |
| **RF-CA-03** | El listado es paginado | US-010 |
| **RF-CA-04** | El administrador puede crear una pizza con nombre y descripción | US-011 |
| **RF-CA-05** | El administrador puede modificar parcialmente una pizza | US-011 |
| **RF-CA-06** | El administrador puede añadir una variante a una pizza con tamaño, precio y disponibilidad | US-012 |
| **RF-CA-07** | El administrador puede modificar precio y disponibilidad de una variante | US-012 |
| **RF-CA-08** | El administrador puede desactivar una pizza o una variante | US-013 |
| **RF-CA-09** | El catálogo **no ofrece eliminación física**: sólo desactivación lógica | US-013 |

### 4.3 Módulo de pedidos — RF-PE

| ID | Requisito | Historia |
|---|---|---|
| **RF-PE-01** | El cliente puede crear un pedido para recogida indicando variantes y cantidades | US-030 |
| **RF-PE-02** | El pedido se crea siempre en estado `PENDING`, con pago `CASH` en estado `UNPAID` | US-030 |
| **RF-PE-03** | El cliente puede listar sus propios pedidos, de forma paginada y ordenados por fecha descendente | US-031 |
| **RF-PE-04** | El cliente puede consultar el detalle de uno de sus pedidos | US-032 |
| **RF-PE-05** | El cliente puede cancelar un pedido propio mientras esté en `PENDING` | US-033 |
| **RF-PE-06** | El administrador puede listar todos los pedidos, con filtro por estado y paginación | US-034 |
| **RF-PE-07** | El administrador puede confirmar un pedido en `PENDING`, que pasa a `CONFIRMED` | US-035 |
| **RF-PE-08** | El administrador puede cancelar un pedido en `PENDING` o `CONFIRMED` | US-036 |
| **RF-PE-09** | El administrador puede registrar la recogida y el cobro en efectivo de un pedido confirmado | US-037 |
| **RF-PE-10** | El sistema conserva el historial de cambios de estado con actor, fecha y motivo | US-038 |

### 4.4 Módulo de pagos — RF-PA

| ID | Requisito | Historia |
|---|---|---|
| **RF-PA-01** | El único método de pago del sistema es `CASH` | US-037 |
| **RF-PA-02** | Un pago tiene exactamente dos estados: `UNPAID` y `PAID` | US-037 |
| **RF-PA-03** | La creación y la confirmación de un pedido **no** implican que esté pagado | US-035, US-037 |
| **RF-PA-04** | El cobro registra el importe, la fecha y el administrador que lo cobró | US-037 |
| **RF-PA-05** | El cobro registra siempre al administrador autenticado, nunca un valor recibido en el cuerpo de la petición | US-037 |
| **RF-PA-06** | El registro del cobro y el cambio de estado del pedido a `DELIVERED` se realizan de forma **atómica** | US-037 |
| **RF-PA-07** | Un pedido no puede tener más de un cobro registrado | US-037 |

---

## 5. Reglas de negocio

### 5.1 Importes y precios — RN-IM

| ID | Regla |
|---|---|
| **RN-IM-01** | El servidor calcula todos los importes. El cliente **nunca** envía ni decide precio unitario ni total |
| **RN-IM-02** | Si la petición contiene campos `price`, `unitPrice` o `total`, el sistema los ignora y aplica los del catálogo |
| **RN-IM-03** | El importe total de un pedido es la suma exacta de los importes de sus líneas |
| **RN-IM-04** | Los importes se representan con precisión decimal exacta (`BigDecimal`), nunca con números de coma flotante binaria |
| **RN-IM-05** | La moneda del sistema es el **peso colombiano (COP)**. No tiene subdivisión decimal: todos los importes se almacenan con escala **0** y `NUMERIC(12,0)` en PostgreSQL |
| **RN-IM-06** | El redondeo de importes es `UNNECESSARY`: si una operación produce un importe fraccionario, es un **error de programación** y debe rechazarse, nunca redondearse |
| **RN-IM-07** | El precio de una variante es siempre mayor que cero |

### 5.2 Foto histórica — RN-FH

| ID | Regla |
|---|---|
| **RN-FH-01** | Cada línea de pedido conserva el nombre de la pizza, el tamaño y el precio unitario **en el momento del pedido** |
| **RN-FH-02** | Cambiar el precio o desactivar una variante **no** altera los pedidos ya creados |
| **RN-FH-03** | El histórico de pedidos es inmutable. No existe ninguna operación para modificarlo ni eliminarlo |
| **RN-FH-04** | Dos líneas con la misma variante en un mismo pedido se fusionan en una única línea con la cantidad sumada |

### 5.3 Estados del pedido — RN-ES

| ID | Regla |
|---|---|
| **RN-ES-01** | Los estados posibles son `PENDING`, `CONFIRMED`, `DELIVERED` y `CANCELLED` |
| **RN-ES-02** | `DELIVERED` y `CANCELLED` son **terminales**: de ellos no se puede salir |
| **RN-ES-03** | Transiciones permitidas: `PENDING → CONFIRMED`, `PENDING → CANCELLED`, `CONFIRMED → DELIVERED`, `CONFIRMED → CANCELLED` |
| **RN-ES-04** | Cualquier transición no permitida se rechaza |
| **RN-ES-05** | La validación de transiciones reside en el dominio, no en el controlador |
| **RN-ES-06** | Cada transición se registra en el historial con estado origen, destino, actor, fecha y motivo |
| **RN-ES-07** | Confirmar un pedido **no** cambia el estado del pago, que permanece `UNPAID` |
| **RN-ES-08** | Los pedidos `PENDING` no caducan automáticamente en esta versión |

### 5.4 Cancelación — RN-CA

| ID | Regla |
|---|---|
| **RN-CA-01** | El cliente sólo puede cancelar **sus propios** pedidos |
| **RN-CA-02** | El cliente sólo puede cancelar pedidos en estado `PENDING` |
| **RN-CA-03** | El administrador puede cancelar pedidos en `PENDING` y en `CONFIRMED` |
| **RN-CA-04** | En `CONFIRMED`, el motivo de la cancelación es obligatorio |
| **RN-CA-05** | El administrador nunca puede cancelar un pedido `DELIVERED` o `CANCELLED` |
| **RN-CA-06** | Una segunda cancelación sobre un pedido ya cancelado produce `409 Conflict` |

### 5.5 Recogida y cobro — RN-PG

| ID | Regla |
|---|---|
| **RN-PG-01** | La recogida sólo se puede registrar sobre un pedido en `CONFIRMED` |
| **RN-PG-02** | El importe registrado en el cobro es exactamente el total del pedido |
| **RN-PG-03** | Si el importe recibido difiere del total, la operación se rechaza con `422` |
| **RN-PG-04** | El cobro y el paso a `DELIVERED` se confirman en una **única transacción** |
| **RN-PG-05** | Si la operación falla, **no** puede quedar el pedido `DELIVERED` sin cobro registrado |
| **RN-PG-06** | Si la operación falla, **no** puede quedar un cobro registrado sin el pedido en `DELIVERED` |
| **RN-PG-07** | Un pedido no admite más de un cobro; la base de datos lo garantiza con una restricción de unicidad |
| **RN-PG-08** | Un intento de cobro repetido o concurrente produce `409 Conflict` |

### 5.6 Disponibilidad — RF-DS *(catálogo y pedidos)*

| ID | Regla |
|---|---|
| **RN-DS-01** | No se puede ordenar una variante inactiva o no disponible |
| **RN-DS-02** | Una pizza sin ninguna variante disponible no aparece en el catálogo público |
| **RN-DS-03** | Desactivar una variante no bloquea pedidos ya creados; el histórico manda |
| **RN-DS-04** | Cada línea admite entre 1 y 20 unidades, y un pedido admite hasta 10 líneas distintas (ver D5) |

### 5.7 Seguridad — RN-SE

| ID | Regla |
|---|---|
| **RN-SE-01** | Los usuarios tienen exactamente un rol del conjunto `{CUSTOMER, ADMIN}` |
| **RN-SE-02** | Los endpoints administrativos exigen rol `ADMIN` |
| **RN-SE-03** | **No existe ninguna forma pública de obtener el rol `ADMIN`** |
| **RN-SE-04** | La propiedad de un pedido se verifica **en el servidor**, filtrando por identificador de cliente en la consulta |
| **RN-SE-05** | Un cliente no puede consultar ni modificar pedidos ajenos |
| **RN-SE-06** | El acceso a un pedido ajeno responde `404`, no `403`, para no revelar su existencia (ver D4) |
| **RN-SE-07** | No se registran contraseñas, tokens, cabeceras de autorización ni trazas de pila en los registros del sistema |
| **RN-SE-08** | El token es firmado con `RS256`, caduca a los 15 minutos y no hay token de renovación en esta versión |
| **RN-SE-09** | Ningún secreto se versiona en el repositorio |

---

## 6. Requisitos no funcionales

| ID | Requisito | Verificación |
|---|---|---|
| **RNF-01** | Atomicidad del cobro y la entrega | Prueba de integración que fuerza un fallo y comprueba que no queda el pedido entregado sin cobro |
| **RNF-02** | Aislamiento por propietario | Prueba con dos clientes reales: uno no puede leer el pedido del otro |
| **RNF-03** | Precisión monetaria exacta, sin pérdida ni fracciones | Pruebas con `0`, `12.505`, `3 × 0.335` y un caso que produzca un importe fraccionario, que debe rechazarse |
| **RNF-04** | El esquema de base de datos se aplica desde cero de forma reproducible | Migraciones de Flyway sobre base vacía |
| **RNF-05** | El sistema arranca localmente sin depender de ningún servicio de AWS | Prueba de arranque con el perfil por defecto |
| **RNF-06** | Los errores tienen un formato estable y no filtran datos sensibles | Prueba por cada tipo de error |
| **RNF-07** | El dominio y los casos de uso se prueban sin base de datos ni servidor | Las pruebas de dominio y aplicación arrancan sin contexto de Spring |
| **RNF-08** | Las capas no se violan en tiempo de compilación | Pruebas de arquitectura ArchUnit |
| **RNF-09** | El API está documentado de forma viva | OpenAPI 3.1 + prueba de contrato |
| **RNF-10** | El build se verifica con un único comando | `./mvnw verify` |
| **RNF-11** | Los registros son estructurados y correlacionables | Identificador de petición propagado y devuelto |
| **RNF-12** | Ninguna funcionalidad se entrega simulada | Sin `TODO`, `@Disabled`, stubs ni banderas que finjan comportamiento |

---

## 7. Modelo de datos (resumen)

| Tabla | Propósito | Restricciones clave |
|---|---|---|
| `customers` | Usuarios del sistema | Email único sin distinguir mayúsculas |
| `customer_roles` | Roles por usuario | Clave primaria compuesta |
| `pizzas` | Pizzas del catálogo | Baja lógica, nunca física |
| `pizza_variants` | Tamaño, precio y disponibilidad | `UNIQUE (pizza_id, size)`, `price > 0` |
| `orders` | Cabecera del pedido | Estado, total, cliente, versionado optimista |
| `order_items` | Líneas con **foto histórica** | FK restrictiva al catálogo, `line_total = unit_price × quantity` |
| `cash_payments` | Cobro en efectivo | `UNIQUE (order_id)` — impide el doble cobro |
| `order_status_history` | Auditoría de transiciones | Inmutable, sin endpoint de escritura |

Boceto del modelo en [`data-model.dbml`](../architecture/data-model.dbml). La justificación de cada tabla,
columna y restricción está en `data-model.md`, que se escribe en US-044.

---

## 8. Contrato de API (resumen)

Prefijo `/api/v1`. Formato de error `application/problem+json` (RFC 9457).

| Método | Ruta | Acceso |
|---|---|---|
| `GET` | `/pizzas` | Público |
| `GET` | `/pizzas/{pizzaId}` | Público |
| `POST` | `/admin/pizzas` | ADMIN |
| `PATCH` | `/admin/pizzas/{pizzaId}` | ADMIN |
| `POST` | `/admin/pizzas/{pizzaId}/variants` | ADMIN |
| `PATCH` | `/admin/variants/{variantId}` | ADMIN |
| `POST` | `/auth/register` | Público |
| `POST` | `/auth/login` | Público |
| `GET` | `/customers/me` | Autenticado |
| `POST` | `/orders` | Autenticado |
| `GET` | `/orders` | Autenticado (sólo los suyos) |
| `GET` | `/orders/{orderId}` | Propietario o ADMIN |
| `POST` | `/orders/{orderId}/cancel` | Propietario |
| `GET` | `/admin/orders` | ADMIN |
| `POST` | `/admin/orders/{orderId}/confirm` | ADMIN |
| `POST` | `/admin/orders/{orderId}/cancel` | ADMIN |
| `POST` | `/admin/orders/{orderId}/complete-pickup` | ADMIN |

> El endpoint `/admin/orders/{orderId}/cancel` se **añadió** durante el análisis: las reglas de negocio
> permiten al administrador cancelar, pero no estaba en el contrato inicial.

Detalle completo en [`docs/architecture/api-contract.md`](../architecture/api-contract.md).

---

## 9. Fuera de alcance

No se implementará en esta versión:

| Tema | Nota |
|---|---|
| Entregas a domicilio | El modelo ya contempla el modo de entrega para un futuro |
| Aplicación frontend | Carpeta reservada en [`frontend/`](../../frontend/README.md), sin código |
| Módulo de cocina | Pantalla de producción para el personal |
| Pasarelas de pago | Sólo efectivo |
| Múltiples sedes | Una sola sede |
| Microservicios | Monolito modular |
| Rate limiting | Se resuelve en el borde; anotado como deuda |
| Clave de idempotencia en la creación de pedidos | Anotada como deuda |
| Envío de notificaciones al cliente | Anotado como deuda |

---

## 10. Trazabilidad requisito → historia

| Módulo | Requisitos | Historias |
|---|---|---|
| Identidad | RF-ID-01 … RF-ID-09 | US-020, US-021, US-023 |
| Autorización | RN-SE-01 … RN-SE-09 | US-022 |
| Catálogo | RF-CA-01 … RF-CA-09 | US-010, US-011, US-012, US-013 |
| Pedidos | RF-PE-01 … RF-PE-10, RN-ES-*, RN-CA-* | US-030 … US-038 |
| Pagos | RF-PA-01 … RF-PA-07, RN-PG-* | US-037 |
| Importes | RN-IM-*, RN-FH-*, RN-DS-* | US-010, US-012, US-030, US-037 |
| No funcionales | RNF-01 … RNF-12 | US-001 … US-006, US-040 … US-045, US-070, US-080 |

**Toda historia del backlog debe poder rastrearse a al menos un requisito.** Si no puede, es trabajo no autorizado.

---

## 11. Decisiones ratificadas en la aprobación

Estas decisiones están registradas en [`docs/planning/decisions-pending.md`](../planning/decisions-pending.md),
que conserva el historial completo de cada una. **Todas se ratificaron el 2026-10-17 con su valor por
defecto**, que es el valor que este documento aplica.

| ID | Decisión | Requisitos afectados | Afectaba a | Decisión ratificada |
|---|---|---|---|---|
| **D1** | Moneda y número de decimales | RN-IM-05, RN-IM-06 | US-003 (esquema) | **COP, sin decimales**. `NUMERIC(12,0)`, `RoundingMode.UNNECESSARY` |
| **D2** | Reglas de cancelación del administrador | RN-CA-03, RN-CA-04 | US-036 | Admin cancela `PENDING` y `CONFIRMED`; motivo obligatorio en `CONFIRMED` |
| **D3** | Cancelación repetida | RN-CA-06 | US-033 | `409 Conflict` (no idempotente) |
| **D4** | Acceso a pedido ajeno: 404 o 403 | RN-SE-06 | US-022, US-032 | `404`, para no revelar la existencia del recurso |
| **D5** | Límites de cantidad y de líneas | RN-DS-04 | US-030 | 1–20 unidades por línea, 10 líneas distintas por pedido |
| **D6** | Variante desactivada con pedido abierto | RN-DS-03 | US-012, US-030 | No bloquea; manda el snapshot histórico |
| **D7** | Cambio de precio con pedido abierto | RN-FH-02 | US-012 | Se cobra el precio del snapshot |
| **D8** | Caducidad de pedidos `PENDING` | RN-ES-08 | Fuera del MVP | Sin caducidad automática; queda como deuda registrada |
| **D9** | Importe recibido en la recogida | RN-PG-02, RN-PG-03 | US-037 | Debe coincidir con el total; si no, `422` |
| **D10** | Ficha de cliente | RF-ID-01 | US-020 | Email + nombre + contraseña (≥ 10). Rol forzado a `CUSTOMER` |
| **D11** | Si el registro devuelve token | RF-ID-05 | US-020 | `201` con el recurso del cliente, **sin** token |
| **D12** | Algoritmo, caducidad y renovación del token | RN-SE-08 | US-021 | RS256, 15 minutos, sin refresh token |
| **D13** | Visibilidad del historial para el cliente | RF-PE-10 | US-032 | Historial completo en el detalle del pedido |
| **D14** | Autorización y presupuesto de AWS | — | US-072, US-081, US-082 | **Ningún recurso de AWS sin autorización escrita.** US-072, US-081 y US-082 siguen bloqueadas |
| **D15** | Dónde vive el estado del pago | — | US-037 | Columnas `payment_status` y `payment_method` en `orders`; `cash_payments` es el registro del cobro |

> D15 se detectó al implementar US-003 y se aplicó con su valor por defecto antes de esta aprobación. La tabla
> `payments` **no existe**: el estado del pago son columnas de `orders`. Ver `decisions-pending.md` §D15.

---

## 12. Aprobación

| | |
|---|---|
| Versión | 1.0 |
| Estado | ✅ **Aprobado** |
| Decisiones pendientes | **0** — las 15 de §11 ratificadas el 2026-10-17 |
| Responsable | Responsable del proyecto |
| Fecha de aprobación | **2026-10-17** |

**Al aprobar este documento:** ✅ hecho

1. ✅ Se han eliminado las marcas ⚙️ y las decisiones definitivas constan en §11.
2. ✅ Registrada la fecha en este apartado; el estado pasa a **Aprobado**.
3. ✅ El documento queda **congelado**: cualquier cambio posterior genera `requirements-v1.1.md`.
4. ✅ Sprint 1 desbloqueado (ejecutado y cerrado el 2026-10-11).