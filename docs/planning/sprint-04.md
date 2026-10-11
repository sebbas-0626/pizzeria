# Sprint 04 — Identidad, autorización y recogida

> **Estado:** Planificado
> **Objetivo:** API completa y segura de extremo a extremo, con el cobro atómico.
> **Historias:** US-020, US-021, US-022, US-023, US-030 (capa web), US-031, US-032, US-033, US-034, US-035, US-036, US-037, US-038
> **Puntos:** 45 · **El sprint más cargado del proyecto**
> **Documento efímero:** al terminar, lo relevante pasa al backlog y a `api-contract.md`.

---

## 1. Advertencia de carga

45 puntos en un sprint es demasiado para un sprint cómodo. Si hay que recortar, el orden de sacrificio es:

1. **US-023** (perfil del cliente) — es trivial y puede ir a S5.
2. **US-032** (detalle del pedido) — puede esperar si US-031 está.
3. **US-038** (historial) — la escritura ya existe desde S3.

Lo que **no** se sacrifica bajo ninguna circunstancia: **US-037** (cobro atómico), **US-022**
(autorización) y las pruebas de rollback y doble cobro.

---

## 2. Historias del sprint

| ID | Título | Puntos | Riesgo principal |
|---|---|---|---|
| US-020 | Registro de cliente | 5 | Bypass del rol ADMIN |
| US-021 | Autenticación y emisión de JWT | 8 | Gestión de claves, expiración |
| US-022 | Autorización por roles y propiedad | 5 | Fuga de datos entre clientes |
| US-023 | Perfil del cliente | 2 | Exposición del hash de contraseña |
| US-030 (resto) | Crear pedido — capa web | 5 | Aceptar precios del cliente |
| US-031 | Listar mis pedidos | 3 | Devolver pedidos ajenos |
| US-032 | Ver detalle de un pedido | 3 | IDOR (referencia directa insegura) |
| US-033 | Cancelar mi pedido pendiente | 5 | Cancelar estados no permitidos |
| US-034 | Listar pedidos (admin) | 3 | Exposición a no administradores |
| US-035 | Confirmar pedido | 3 | Confirme que cobre dinero |
| US-036 | Cancelar pedido (admin) | 3 | Motivo no obligatorio en `CONFIRMED` |
| US-037 | Registro de recogida y cobro | 13 | Atomicidad y doble cobro |
| US-038 | Historial de cambios de estado | 2 | Historial mutable |

---

## 3. US-020 — Registro de cliente

### Tareas

- [ ] `identity/domain/Customer.java`, `Role.java`, `Password.java`
- [ ] `identity/application/RegisterCustomerUseCase`
- [ ] `identity/adapters/in/web/AuthController.java`, `RegisterRequest`, `CustomerResponse`
- [ ] `PasswordEncoder` con BCrypt vía `DelegatingPasswordEncoder`
- [ ] Restricción de email único sin distinguir mayúsculas
- [ ] Test que captura el log y comprueba que no aparece la contraseña

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | `POST /api/v1/auth/register` → 201 con id, email, nombre y rol; **sin** contraseña |
| 2 | Email único, insensible a mayúsculas (`LUCÍA@EXAMPLE.COM` = `lucia@example.com`) → 409 |
| 3 | Contraseña de 10 caracteres o más; más corta → 400 |
| 4 | Hash BCrypt almacenado; nunca texto plano ni reversible |
| 5 | **`role: "ADMIN"` en el cuerpo → 400.** El servidor fuerza `CUSTOMER` |
| 6 | El log de registro no contiene la contraseña |
| 7 | ⚙️ Sin token en la respuesta (D11 por defecto: hay que hacer login después) |

> El criterio 5 es **RN-SE-03**, «no existe forma pública de obtener el rol ADMIN». Se escribe el test
> antes que la implementación, para que la prueba no se adapta al código.

---

## 4. US-021 — Autenticación y emisión de JWT

### Tareas

- [ ] Puerto `JwtTokenIssuer` en `identity/application/port/out/`
- [ ] Adaptador con Nimbus JOSE JWT, firma **RS256**
- [ ] Generación de claves en local; claves por variable de entorno
- [ ] `SecurityFilterChain` con `oauth2ResourceServer(jwt)`
- [ ] Filtro `OncePerRequestFilter` que construye el principal
- [ ] `CustomerPrincipal` con id y roles
- [ ] Configuración de emisor (`iss`) y caducidad (`exp`)
- [ ] Script de generación de claves en `scripts/`

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | `POST /api/v1/auth/login` → 200 con `accessToken`, `tokenType: Bearer`, `expiresIn` |
| 2 | Credenciales incorrectas → 401 con **mensaje genérico** (no dice si el email existe) |
| 3 | El token contiene `sub` (UUID del cliente), `roles`, `iss`, `iat`, `exp` |
| 4 | ⚙️ Caducidad de 15 minutos (D12) |
| 5 | ⚙️ Firma `RS256` (D12) |
| 6 | La clave privada **no** está en el repositorio |
| 7 | Token alterado → 401 · Token caducado → 401 |

### Pruebas

| Prueba | Tipo |
|---|---|
| `JwtTokenIssuerTest` | Unitario con `Clock.fixed()`: verifica firma, claims y expiración |
| `AuthControllerTest` | `@WebMvcTest`: login válido, inválido, email inexistente |
| `SecurityIntegrationIT` | Integración: token válido, alterado, caducado, de firma distinta |

---

## 5. US-022 — Autorización por roles y propiedad del recurso

### Tareas

- [ ] Reglas por ruta en `SecurityFilterChain` (`/api/v1/admin/**` → `hasRole("ADMIN")`)
- [ ] Endpoints públicos explícitos: `/api/v1/pizzas`, `/api/v1/pizzas/**`, `/api/v1/auth/**`
- [ ] Filtros por `customerId` **en la consulta** en todos los puertos de pedido
- [ ] Prueba de matriz de seguridad parametrizada

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | Sin token en `/api/v1/admin/**` → 401 |
| 2 | Con rol `CUSTOMER` en `/api/v1/admin/**` → 403 |
| 3 | Con rol `ADMIN` → permitido |
| 4 | Cliente B pide `GET /orders/{idDeA}` → **404** (⚙️ D4) |
| 5 | Cliente B pide `POST /orders/{idDeA}/cancel` → **404** |
| 6 | El filtrado por propietario ocurre en la consulta, no comparando después |
| 7 | `ArchitectureRulesTest` impide que `domain` importe Spring |

> El criterio 4 es el que más se olvida. La prueba usa **dos clientes reales** en Testcontainers,
> no un mock: un doble mal configurado haría pasar el test sin comprobar nada.

---

## 6. US-030 (resto) — Crear pedido por HTTP

### Tareas

- [ ] `OrderController`, `CreateOrderRequest` (`record`), `OrderResponse`, `OrderMapper`
- [ ] Traducción de excepciones de dominio a códigos HTTP
- [ ] Cabecera `Location` en el 201
- [ ] Prueba específica: el cuerpo **no** acepta precios

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | `POST /api/v1/orders` → 201 + `Location`, con `orderId` y `orderNumber` |
| 2 | El cuerpo sólo acepta `variantId` y `quantity` |
| 3 | **`unitPrice`, `total` o `price` en el cuerpo → el servidor los ignora.** Test explícito |
| 4 | Sin token → 401 · Con rol insuficiente → 403 |
| 5 | Variante no disponible → 422 `CATALOG_VARIANT_NOT_AVAILABLE` |
| 6 | Pedido vacío → 422 `ORDERS_EMPTY` |
| 7 | Cantidad fuera de rango → 400 |
| 8 | El total devuelto coincide con el calculado por el servidor |

> El criterio 3 es la demostración de RN-IM-01 a nivel HTTP. Según cómo se serialice el JSON, las
> opciones son ignorar campos desconocidos o rechazarlos con 400; **ambas** son válidas, pero la elegida
> se documenta en `api-contract.md` y se prueba.

---

## 7. US-031 a US-036 — Gestión de pedidos

| Historia | Endpoint | Criterios clave |
|---|---|---|
| **US-031** | `GET /orders?page&size` | Sólo los del cliente · paginado · orden `placedAt DESC` · `size` máx. 100 · vacío → 200 con `content: []` |
| **US-032** | `GET /orders/{id}` | 200 con cabecera, líneas con foto histórica, estado del pago e historial · ajeno → 404 |
| **US-033** | `POST /orders/{id}/cancel` | 204 si `PENDING` · ajeno → 404 · `CONFIRMED`/`DELIVERED`/`CANCELLED` → 409 · ⚙️D3 repetido → 409 · motivo opcional · entrada en historial |
| **US-034** | `GET /admin/orders?status&page` | Sólo ADMIN · filtro por estado · orden `placedAt ASC` (lo más antiguo primero) |
| **US-035** | `POST /admin/orders/{id}/confirm` | 204 sólo desde `PENDING` · otros → 409 · **el pago sigue `UNPAID`** (test explícito) |
| **US-036** | `POST /admin/orders/{id}/cancel` | 204 desde `PENDING` o `CONFIRMED` · ⚙️ en `CONFIRMED` el motivo es obligatorio → 400 · `DELIVERED`/`CANCELLED` → 409 · motivo persistido |

> **US-036 introduce un endpoint que no existía en el contrato inicial.** Las reglas de negocio permiten
> al administrador cancelar, pero la ruta faltaba. Está documentado en `requirements-v1.0.md` §8.

---

## 8. US-037 — Registro de recogida y cobro en efectivo

> **La historia más delicada del proyecto.** Requiere dos invariantes que deben quedar demostradas:
> **RN-PG-05** (no queda entregado sin cobro) y **RN-PG-06** (no queda cobro sin entrega).

### Tareas

- [ ] `payments/domain/Payment.java`, `PaymentStatus.java`, `CashPayment.java`
- [ ] `payments/application/port/out/PaymentRepository.java`
- [ ] `CompletePickupUseCase` con `@Transactional` — **una sola transacción** para cobro + pedido + historial
- [ ] `OrderJpaEntity` + campo `version` para bloqueo optimista
- [ ] `Order` cuenta con la transición `CONFIRMED → DELIVERED`
- [ ] Restricción `UNIQUE (order_id)` en `cash_payments` (ya creada en S1)
- [ ] `received_by` tomado del principal autenticado, **nunca** del cuerpo
- [ ] `OrderMapper` para lectura: estado de pago visible en el detalle del pedido

### Criterios de aceptación

| # | Criterio | Prueba |
|---|---|---|
| 1 | `POST /admin/orders/{id}/complete-pickup` → 204 | Integración |
| 2 | ⚙️ Sólo desde `CONFIRMED`; otro estado → 409 | Unitario |
| 3 | Inserta `cash_payments` con `amount = orders.total_amount` | Integración |
| 4 | Pago `PAID` **y** pedido `DELIVERED` en la misma transacción | Integración |
| 5 | **Fallo forzado entre ambas escrituras → rollback completo** | Integración con excepción inyectada |
| 6 | **Dos llamadas concurrentes → exactamente un 204 y un 409** | Integración con dos hilos |
| 7 | `received_by` es el admin autenticado | Integración con dos admins distintos |
| 8 | ⚙️ Importe ≠ total → 422 (D9) | Integración |
| 9 | Entrada de historial con actor | Integración |
| 10 | Tras recoger, el pedido no admite otro cobro | Integración |

### Cómo se prueban los criterios 5 y 6

No se prueban «a ojo». Se prueban así:

**Rollback (criterio 5).** En la prueba de integración se inyecta un fallo controlado **después** de
insertar el pago y **antes** de cambiar el estado del pedido. Se comprueba, con consultas directas a la
base de datos, que:

- no existe ninguna fila en `cash_payments` para ese pedido;
- el pedido sigue en `CONFIRMED`, no en `DELIVERED`.

**Concurrencia (criterio 6).** Dos hilos llaman a `complete-pickup` sobre el mismo pedido al mismo tiempo,
con una barrera de sincronización para que ambos intenten a la vez. El resultado esperado es exactamente
un 204 y un 409, y **una sola fila** en `cash_payments`.

> La barrera es imprescindible: sin ella, el segundo hilo llega después de que el primero terminó y la
> prueba pasa sin haber probado nada.

### Verificación complementaria

Una consulta a la base de datos que compruebe que **no existe ningún pedido `DELIVERED` sin fila en
`cash_payments`**. Es la comprobación que expresa RN-PG-05 de forma directa y que puede ejecutarse
como prueba tras cualquier cambio futuro en el flujo.

---

## 9. US-038 — Historial de cambios de estado

- [ ] `GET` del historial dentro del detalle del pedido
- [ ] Cada fila: `fromStatus`, `toStatus`, `changedBy`, `reason`, `changedAt`
- [ ] Sin endpoint de escritura (verificado por test de rutas)
- [ ] ⚙️ D13: historial completo visible para el cliente

**Prueba de referencia:** el recorrido `PENDING → CONFIRMED → DELIVERED` deja exactamente **tres** filas,
la primera con `fromStatus = null`.

---

## 10. Diagrama a producir

`08-sequence-complete-pickup.drawio` — secuencia con la sección de transacción que abarca las tres
escrituras, el punto de rollback y la anotación de la carrera entre dos administradores.

---

## 11. Criterio de salida del sprint

- [ ] Recorrido completo verificado: registro → login → crear pedido → confirmar → recoger con cobro
- [ ] Matriz de autorización verde: ningún endpoint filtra datos de otro usuario
- [ ] Prueba de rollback verde: no queda `DELIVERED` sin cobro
- [ ] Prueba de concurrencia verde: un 204 y un 409
- [ ] `received_by` es siempre el administrador autenticado
- [ ] Registro con `role: ADMIN` rechazado
- [ ] Confirmar un pedido **no** cobra (test explícito en verde)
- [ ] `api-contract.md` refleja los 17 endpoints
- [ ] `ArchitectureRulesTest` verde
- [ ] `./mvnw verify` en verde con la salida reportada
- [ ] US-020…US-038 marcadas en el backlog

---

## 12. Dependencias y riesgos

| | |
|---|---|
| **Depende de** | Sprint 03 (agregado y persistencia del pedido) |
| **Bloquea a** | Sprint 05 |
| **Riesgo crítico** | Perder atomicidad si la transacción se abre en varias capas → una sola transacción, en el caso de uso |
| **Riesgo crítico** | Confiar en la lógica para evitar el doble cobro → la garantía vive en `UNIQUE (order_id)` |
| **Riesgo** | Filtrar el propietario comparando en memoria en lugar de en la consulta |
| **Riesgo** | Guardar el hash de contraseña en el DTO de perfil |
| **Decisiones abiertas** | ⚙️ D2, D3, D4, D5, D9, D10, D11, D12, D13 afectan a criterios de aceptación concretos |

---

## 13. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| — | — | — | Sprint no iniciado |