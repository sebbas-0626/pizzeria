# Sprint 07 — Integración con SQS (mensajería asíncrona)

> **Estado:** Planificado
> **Objetivo:** que el sistema emita hechos del negocio y los publique de forma fiable.
> **Historias:** US-060, US-061, US-062
> **Puntos:** 26
> **Documento efímero:** al terminar, lo relevante pasa al backlog y a `architecture.md`.

---

## 1. Por qué SQS y no otra cosa

SQS introduce el primer problema genuinamente difícil del proyecto: **coherencia entre la base de datos y
un sistema de mensajes externo**. Es exactamente lo que se quiere aprender, y es donde más código
ingenuo se escribe en la vida profesional. Por eso el sprint insiste en el patrón *outbox* en lugar de
publicar directamente.

---

## 2. Historias del sprint

| ID | Título | Puntos | Riesgo |
|---|---|---|---|
| US-060 | Eventos de dominio y puerto de publicación | 8 | Acoplar el dominio a la mensajería |
| US-061 | Outbox transaccional y publicador SQS | 13 | **Perder o duplicar mensajes** |
| US-062 | Consumidor de ejemplo y prueba contra emulador | 5 | Procesar dos veces el mismo evento |

---

## 3. US-060 — Eventos de dominio y puerto de publicación

### Tareas

- [ ] Eventos inmutables: `OrderPlaced`, `OrderConfirmed`, `OrderCancelled`, `OrderPickedUp`
- [ ] Puerto `DomainEventPublisher` en `orders.application.port.out`
- [ ] El agregado **recoge** los eventos; no los publica (no conoce el mecanismo)
- [ ] El caso de uso los publica **dentro de la transacción**
- [ ] Adaptador con `ApplicationEventPublisher` de Spring
- [ ] Test ArchUnit que prohíbe el SDK de AWS en `domain`

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | Los eventos son inmutables y contienen el identificador del pedido | Inspección y test |
| 2 | El agregado los recoge sin saber que existen colas | Test del agregado sin Spring |
| 3 | El SDK de AWS no aparece en `domain` ni en `application` | Test ArchUnit |
| 4 | Confirmar un pedido produce `OrderConfirmed` | Test del caso de uso |

### El patrón que se instala aquí

```text
Order.confirm(...)   →  order.recorderOrderConfirmed(...)   ← el dominio no publica
PlaceOrderUseCase    →  orderRepository.save(order)
                        order.pullEvents().forEach(publisher::publish)   ← dentro de @Transactional
```

El dominio dice **qué** pasó. La aplicación decide **cómo** se comunica. Esa separación es la razón de ser
de todo el ejercicio.

---

## 4. US-061 — Outbox transaccional y publicador SQS

### El problema que se resuelve

Si el publicador escribe en SQS **después** del `COMMIT` y el proceso muere en medio, el pedido queda
confirmado y el evento **nunca se publica**. Si se escribe **antes**, puede publicarse un evento de un
pedido que luego no se guardó. Ninguna de las dos opciones es aceptable.

**Solución: transactional outbox.** Los eventos se escriben en una tabla `outbox` **en la misma
transacción** que el cambio de estado. Un publicador aparte los envía a SQS y los marca.

### Tareas

- [ ] Migración `V*` con la tabla `outbox` (`id`, `event_type`, `payload`, `occurred_at`, `published_at`, `attempts`)
- [ ] Escritura del evento en la tabla dentro de la misma transacción que el cambio de estado
- [ ] `OutboxPublisher` programado que envía a SQS y marca `published_at`
- [ ] Reintentos con espera creciente y marca de fallo tras N intentos
- [ ] Endpoint y cola creados idempotentemente en el perfil local
- [ ] Métrica de eventos pendientes de publicar

### Criterios de aceptación

| # | Criterio | Expresa |
|---|---|---|
| 1 | El evento se escribe en `outbox` **en la misma transacción** que el cambio de estado | Integración |
| 2 | Si el pedido se confirma pero SQS está caído, **el pedido se confirma igual** | Integración con emulador parado |
| 3 | Cuando SQS vuelve, el evento se publica sin intervención manual | Integración |
| 4 | Un evento no se publica dos veces en condiciones normales | Integración |
| 5 | Tras N intentos fallidos, el evento queda marcado como fallido y con métrica visible | Unitario |
| 6 | La tabla `outbox` no crece sin límite: los eventos publicados se limpian | Documentado con política de retención |

### La prueba clave

```text
1. Arrancar el emulador
2. Confirmar un pedido          → 204, evento en outbox, mensaje en la cola
3. Parar el emulador
4. Confirmar otro pedido        → 204, evento en outbox, sin mensaje (aún)
5. Arrancar el emulador
6. Esperar al publicador        → mensaje en la cola, published_at informado
7. Verificar que en el paso 4 el pedido quedó confirmado igualmente
```

Sin el paso 7, la prueba pasa aunque el sistema pierde pedidos. **Ese paso es el que demuestra el valor
del patrón.**

---

## 5. US-062 — Consumidor de ejemplo

### Tareas

- [ ] Consumidor de `OrderConfirmed`
- [ ] Registro estructurado del evento recibido
- [ ] Fila de auditoría en una tabla de lectura
- [ ] Prueba de integración completa: producir → consumir → verificar
- [ ] Documentación de idempotencia: clave = identificador del evento

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | Un evento publicado llega al consumidor | Prueba de integración |
| 2 | El consumidor deja rastro verificable | Integración |
| 3 | Recibir el mismo evento dos veces **no** duplica el efecto | Prueba explícita |
| 4 | El arranque **sin** SQS no falla | Prueba de humo del perfil por defecto |

### Sobre la entrega «al menos una vez»

SQS entrega **al menos una vez**, no exactamente una vez. El consumidor **debe** ser idempotente.
Esto no es un defecto del diseño: es la característica del sistema y hay que diseñarlo así. Se documenta
en el ADR correspondiente.

---

## 6. Criterio de salida del sprint

- [ ] Confirmar un pedido produce un evento y llega al consumidor
- [ ] Con SQS caído, el pedido **se confirma igualmente** y el evento sale después
- [ ] El consumidor es idempotente (probado con el mismo evento dos veces)
- [ ] Test ArchUnit verde: sin SDK de AWS en el dominio
- [ ] El backend arranca sin SQS
- [ ] Prueba de integración con el emulador en verde
- [ ] `./mvnw verify` en verde
- [ ] US-060, US-061 y US-062 en `Hecho`

---

## 7. Dependencias y riesgos

| | |
|---|---|
| **Depende de** | Sprint 06 (mismo patrón de puerto y adaptador) |
| **Bloquea a** | Sprint 08 |
| **Riesgo crítico** | Publicar directamente sin outbox y perder eventos en un fallo → la prueba del paso 7 lo detecta |
| **Riesgo** | Asumir entrega «exactamente una vez» → documentar «al menos una vez» y hacer el consumidor idempotente |
| **Riesgo** | El publicador se ejecuta dentro de la transacción → lo bloquea; va fuera |
| **Riesgo de tiempo** | US-061 es la historia más ambiciosa. Si no da, se entrega publicación directa y **el ADR registra la deuda**; nunca se presenta como terminada |
| **Decisión abierta** | ⚙️ D14. **Este sprint NO crea ningún recurso real de AWS** |

---

## 8. Criterio de abandono

Si US-061 no puede completarse con calidad, la decisión responsable es:

1. Entregar US-060 y US-062 (puerto, eventos y consumidor) sin outbox.
2. Documentar en el ADR que la publicación es directa y **dónde** se pierde el mensaje.
3. Registrar la deuda en el backlog con su historia propia.

Lo que **no** se hace: marcar US-061 como completada sin el patrón, o dejar un publicador «para
terminar luego» sin decir que es una entrega parcial.

---

## 9. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| — | — | — | Sprint no iniciado |