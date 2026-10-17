# Sprint 03 — Pedidos: dominio y persistencia

> **Estado:** Planificado
> **Objetivo:** construir y verificar el corazón del negocio **sin** exponerlo por HTTP todavía.
> **Historias:** US-030 (dominio + aplicación + persistencia), US-038 (parcial)
> **Puntos:** 8 de los 13 de US-030 (el resto se entrega en el Sprint 04)
> **Documento efímero:** al terminar, lo relevante pasa al backlog y a la documentación de arquitectura.
>
> **Estructura de paquetes: capas globales**, según [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md).
> Este documento se planificó con la estructura por **módulo** (`orders/domain/…`, `orders/application/port/out/…`)
> y se corrigió el 2026-10-17: ya no existen paquetes `orders/`, y los puertos viven en `domain/port/out`.

---

## 1. Por qué no se exponen endpoints en este sprint

Podríamos entregar la API de pedidos aquí, pero **no hay identidad todavía**: sin autenticación no existe
«cliente propietario» de un pedido, y una API de pedidos abierta o simulada con un principal fijo daría
una falsa sensación de seguridad que luego habría que tirar.

La división elegida:

| Sprint | Qué entrega | Por qué |
|---|---|---|
| **S3** | Agregado, invariantes, cálculo de precios, persistencia e historial | El negocio se puede verificar entero sin HTTP |
| **S4** | JWT, autorización y endpoints de pedidos y recogida | La capa web llega cuando hay propietario real |

Es una duración total mayor a cambio de no escribir nunca código de seguridad "de mentira".

---

## 2. Historias del sprint

| ID | Título | Puntos | Riesgo |
|---|---|---|---|
| US-030 (parcial) | Crear pedido — dominio, aplicación y persistencia | 8 | Redondeo monetario y snapshot |
| US-038 (parcial) | Historial de cambios de estado — escritura | 2 | Inmutabilidad |

---

## 3. Modelo de dominio

### 3.1 Objetos de valor

| Tipo | Responsabilidad |
|---|---|
| `Money` | Importe en COP con escala 0 y `RoundingMode.UNNECESSARY`. Suma, multiplicación por entero, comparación y validación de moneda |
| `Quantity` | Cantidad validada (⚙️ 1–20) |
| `VariantSize` | `SMALL` \| `MEDIUM` \| `LARGE` |
| `OrderNumber` | Identificador legible de pedido |

### 3.2 Entidades

```text
Order (agregado raíz)
├── id, customerId, orderNumber, fulfillmentType
├── status: OrderStatus
├── total: Money                     ← derivado de las líneas, nunca recibido
├── items: List<OrderItem>           ← inmutables una vez creados
├── cancellation: Cancelación?       ← motivo y actor
└── historial: List<CambioEstado>    ← sólo append
```

**Invariantes del agregado** (cada uno con su prueba):

| # | Invariante |
|---|---|
| I-01 | Un pedido nuevo nace en `PENDING` |
| I-02 | Un pedido tiene al menos una línea |
| I-03 | ⚙️ Un pedido tiene como máximo 10 líneas distintas |
| I-04 | ⚙️ Cada línea tiene entre 1 y 20 unidades |
| I-05 | El total es siempre la suma exacta de las líneas |
| I-06 | El pago nasce `CASH`/`UNPAID` |
| I-07 | El modo de entrega es siempre `PICKUP` |
| I-08 | Un pedido `DELIVERED` o `CANCELLED` no puede cambiar de estado |
| I-09 | Las líneas no se modifican después de crear el pedido |

### 3.3 Máquina de estados

| Desde | Transición a | Actor | Efectos |
|---|---|---|---|
| `PENDING` | `CONFIRMED` | ADMIN | Ninguno sobre el pago |
| `PENDING` | `CANCELLED` | Propietario o ADMIN | Motivo opcional (obligatorio si venía de `CONFIRMED`) |
| `CONFIRMED` | `DELIVERED` | ADMIN | Se registra el cobro (Sprint 04) |
| `CONFIRMED` | `CANCELLED` | ADMIN | Motivo **obligatorio** |
| `DELIVERED` | — | — | **Terminal** |
| `CANCELLED` | — | — | **Terminal** |

La tabla vive en `OrderStatus` como **única fuente de verdad**. El diagrama `02-order-state-machine`
se deriva de ella, no al contrario.

---

## 4. Tareas

### 4.1 Dominio

- [ ] `domain/model/vo/Money.java` — objeto de valor del dinero (COP, escala 0, `RoundingMode.UNNECESSARY`); implementa RN-IM-06
- [ ] `domain/model/Order.java` (agregado raíz)
- [ ] `domain/model/OrderItem.java` (con la foto histórica)
- [ ] `domain/model/OrderStatus.java` (tabla de transiciones)
- [ ] `domain/model/Quantity.java`, `OrderNumber.java`, `FulfillmentType.java`
- [ ] `domain/exception/` — `OrderNotFoundException`, `InvalidOrderTransitionException`, `EmptyOrderException`, `TooManyItemsException`
- [ ] `Order.reconstitute(...)` para reconstruir desde persistencia

### 4.2 Aplicación

- [ ] `domain/port/out/OrderRepositoryPort.java`
- [ ] `domain/port/out/PricingCatalogPort.java` — **puerto** que devuelve precio y nombre del servidor
- [ ] `application/usecase/PlaceOrderUseCase.java` + `application/service/PlaceOrderService.java` (`@Transactional`)
- [ ] Comando `PlaceOrderCommand` en `application/dto` (sólo `variantId` + `quantity`)
- [ ] Fusión de líneas duplicadas antes de persistir

### 4.3 Persistencia

- [ ] `infrastructure/adapter/out/persistence/entity/` — `OrderJpaEntity`, `OrderItemJpaEntity`, `OrderStatusHistoryJpaEntity`
- [ ] Repositorios Spring Data en `…/persistence/repository/` (internos al adaptador)
- [ ] `OrderPersistenceAdapter` en `…/persistence/` que implementa `OrderRepositoryPort`
- [ ] `OrderPersistenceMapper` y `OrderItemMapper` en `…/persistence/mapper/` (manuales)
- [ ] `CatalogPricingAdapter` en `…/persistence/` que implementa `PricingCatalogPort`

> `PageRequest` y `PageResponse` **no se crean aquí**: llegan de S2, en `application/dto`.

### 4.4 Persistencia de una transición

- [ ] Método del agregado para registrar la transición y añadir la entrada de historial en la misma operación

---

## 5. Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | ⚙️ Un pedido con una variante inexistente, inactiva o no disponible se rechaza con 422 | Prueba unitaria del caso de uso |
| 2 | ⚙️ Pedido vacío → 422 | Prueba unitaria |
| 3 | ⚙️ Cantidad fuera de rango → error de validación | Prueba unitaria |
| 4 | El total es la suma exacta de las líneas | Prueba unitaria con `BigDecimal` |
| 5 | El total se calcula con los precios **del servidor** | Prueba que pasa precios falsos en el comando y verifica que se ignoran |
| 6 | Las líneas conservan nombre, tamaño y precio unitario | Prueba de integración tras cambiar el precio en catálogo |
| 7 | Se escribe una fila de historial al crear el pedido | Prueba de integración |
| 8 | Transiciones ilegales lanzan `InvalidOrderTransitionException` | Prueba parametrizada de **todas** las combinaciones |
| 9 | `DELIVERED` y `CANCELLED` son terminales | Prueba explícita |
| 10 | El pedido se persiste con total, estado y líneas coherentes | Prueba de integración |
| 11 | ⚙️ Duplicados de la misma variante se fusionan | Prueba unitaria |
| 12 | Un importe fraccionario (por ejemplo `12.505`) se **rechaza**, no se redondea | Prueba unitaria de `Money` |

> Prueba de referencia (RN-IM-01): el caso de uso recibe **sólo** identificadores y cantidades. Si el
> comando tuviera un campo de precio, el test debe demostrar que se ignora. En esta fase el test se hace
> directamente contra el caso de uso; en el S4 se repite contra el endpoint.

---

## 6. Pruebas requeridas

| Prueba | Tipo | Nota |
|---|---|---|
| `OrderTest` | Unitario puro | Todos los invariantes y todas las transiciones, incluidas las ilegales |
| `MoneyTest` | Unitario puro | Escala, redondeo, comparación con `compareTo`, moneda distinta |
| `QuantityTest` | Unitario puro | Límites 1 y 20, y los valores fuera |
| `PlaceOrderServiceTest` | Unitario con dobles | Variante no disponible, vacío, duplicados, precios falsos |
| `OrderPersistenceAdapterIT` | Integración con Testcontainers | Persistencia real, tipos monetarios exactos, historial |
| `CatalogPricingAdapterIT` | Integración | Filtra por `active` y `available` |
| `ArchitectureRulesTest` | ArchUnit | `domain` sigue sin Spring ni JPA |

**Cobertura exigida al dominio:** ≥ 80 %. En este sprint, el agregado debe superar holgadamente ese
umbral, porque es donde vive el riesgo del proyecto.

---

## 7. Diagrama a producir

`07-sequence-place-order.drawio` — secuencia del caso de uso crítico, con los caminos de error
(401/403/404/422) y el punto exacto donde el servidor obtiene el precio.

Se genera **en este sprint**, no después, porque el diagrama ajuda a detectar huecos de diseño
antes de escribir los tests.

---

## 8. Criterio de salida del sprint

- [ ] Todos los invariantes probados, incluidas las transiciones ilegales
- [ ] Un test de integración crea un pedido completo contra PostgreSQL real
- [ ] El precio del snapshot sobrevive a un cambio de precio en catálogo (probado)
- [ ] Existe una fila de historial por cada creación
- [ ] `./mvnw verify` en verde con la salida reportada
- [ ] `ArchitectureRulesTest` verde: el dominio no importa nada de Spring ni de JPA
- [ ] Diagrama `07-sequence-place-order.drawio` creado
- [ ] `data-model.md` y `api-contract.md` (sección de creación de pedido) actualizados

---

## 9. Dependencias y riesgos

| | |
|---|---|
| **Depende de** | Sprint 02 (catálogo y precios disponibles) |
| **Bloquea a** | Sprint 04 (los endpoints de pedidos) |
| **Riesgo** | Relajarse en los redondeos: `Money` debe concentrarlos todos, no cada línea |
| **Riesgo** | Filtrar precio «si viene» desde el cliente. Nunca: el puerto `PricingCatalogPort` es la única fuente |
| **Riesgo** | Reconstruir el agregado desde la entidad JPA y saltarse invariantes → `reconstitute` documentado y testeado |
| **Decisión abierta** | ⚙️ D5 (límites) y ⚙️ D6/D7 (variante desactivada y cambio de precio) afectan a los invariantes I-03, I-04 y al comportamiento del caso de uso |

---

## 10. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| 2026-10-17 | — | — | Rutas de tareas corregidas a capas globales ([ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md), [ADR-0005](../adr/0005-los-puertos-se-declaran-en-el-dominio.md)): `orders/domain/…` → `domain/model/…`, `orders/application/port/out/…` → `domain/port/out/…`, y los puertos pasan a nombre `…Port`. Sin historia empezada. |
| — | — | — | Sprint no iniciado |