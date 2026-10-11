# Decisiones pendientes de aprobación

> **Fuente de verdad de las preguntas abiertas.** Ninguna implementación que dependa de una de estas
> decisiones se ejecuta sin respuesta. Si una decisión no tiene respuesta cuando toque implementarla,
> se aplica el **valor por defecto** marcado con ⚙️ y se anota aquí el supuesto asumido.
> Última actualización: **2026-10-11**

---

## Resumen

| ID | Decisión | Bloquea | Estado | Por defecto |
|---|---|---|---|---|
| D1 | Moneda del negocio | US-003 (esquema) | ✅ **Resuelta 2026-10-11** | **COP, sin decimales** |
| D2 | Reglas de cancelación del administrador | US-036 | ⬜ Abierta | ⚙️ Admin cancela `PENDING` y `CONFIRMED`; motivo obligatorio en `CONFIRMED` |
| D3 | Cancelación repetida | US-033 | ⬜ Abierta | ⚙️ `409 CONFLICT` (no idempotente) |
| D4 | Acceso a un pedido ajeno | US-022, US-032, US-033 | ⬜ Abierta | ⚙️ `404` (no revela existencia) |
| D5 | Límites de cantidad y líneas | US-030 | ⬜ Abierta | ⚙️ 1–20 unidades por línea, 10 líneas por pedido |
| D6 | Variante desactivada con pedido abierto | US-030, US-012 | ⬜ Abierta | ⚙️ No bloquea; manda el snapshot histórico |
| D7 | Cambio de precio con pedido abierto | US-012, US-030 | ⬜ Abierta | ⚙️ Se cobra el precio del snapshot |
| D8 | Caducidad de pedidos `PENDING` | Fuera del MVP | ⬜ Abierta | ⚙️ Sin caducidad automática; documentado como deuda |
| D9 | Importe recibido en la recogida | US-037 | ⬜ Abierta | ⚙️ Debe coincidir con el total; si no, 422 |
| D10 | Ficha de cliente en el registro | US-020 | ⬜ Abierta | ⚙️ Email + nombre + contraseña (≥10). Rol forzado a `CUSTOMER` |
| D11 | El registro devuelve token | US-020 | ⬜ Abierta | ⚙️ 201 con el recurso del cliente, sin token (hay que hacer login) |
| D12 | Emisión y caducidad del JWT | US-021 | ⬜ Abierta | ⚙️ RS256, 15 minutos, sin refresh token |
| D13 | Datos visibles en el historial de estados | US-032 | ⬜ Abierta | ⚙️ Historial completo en el detalle del pedido |
| D14 | Autorización y presupuesto para AWS | US-072, US-081, US-082 | ⬜ Abierta | ⚙️ Sin despliegue real; historias marcadas como bloqueadas |
| **D15** | **Dónde vive el estado del pago** | **US-003 (esquema)** | ⚙️ **Aplicada con el valor por defecto** (pendiente de ratificar) | Columnas `payment_status` y `payment_method` en `orders` |

---

## Detalle

### D1 · Moneda del negocio — ✅ RESUELTA (2026-10-11)

**Decisión:** la moneda es el **peso colombiano (COP)**.

**Consecuencias técnicas:**

| Aspecto | Valor |
|---|---|
| Escala del importe | **0 decimales** — el COP no tiene subdivisión en el uso comercial |
| Tipo en PostgreSQL | `NUMERIC(12,0)` |
| Tipo en Java | `BigDecimal` con `setScale(0, RoundingMode.UNNECESSARY)` |
| Redondeo | **No se redondea.** Si una operación produce fracciones, es un error de programación y se rechaza |
| Columna `currency` | Se mantiene `CHAR(3)` con el valor `COP`, por si algún día hay más de una moneda |
| Formato de presentación | Miles con punto y sin decimales: `12.500 COP` |

**Por qué `UNNECESSARY` y no `HALF_UP`.** En una moneda sin decimales no existe una regla de negocio que
decida cómo redondear: el resultado correcto es siempre un entero. Redondear escondería un error
(por ejemplo, una división que no debería ocurrir). Convertir el redondeo en una excepción convierte un
fallo silencioso en un fallo visible, que es justo lo que `RN-IM-06` exige.

**Efecto sobre el resto de la planificación:**

- `RN-IM-05` y `RN-IM-06` actualizados en [`requirements-v1.0.md`](../requirements/requirements-v1.0.md).
- `AGENTS.md` §4.3 actualizado.
- `docs/development/conventions.md` §4 actualizado.
- `docs/development/spring-conventions.md` §4.4 actualizado a `NUMERIC(12,0)`.
- Los tests de redondeo de US-030 cambian: en lugar de comprobar un redondeo, comprueban que un
  importe fraccionario **se rechaza**.

**Nota para el responsable:** si en algún momento el negocio necesita cobrar con centavos, esta decisión
se revisa y se escribe una `requirements-v1.1.md`. Es un cambio de esquema, por eso conviene decidirlo
antes de escribir la migración.

---

### D2 · Reglas de cancelación del administrador

**Pregunta:** ¿Puede el administrador cancelar un pedido `CONFIRMED`? ¿Hace falta motivo?

**Contexto:** el diseño original decía «el administrador puede cancelar pedidos PENDING o CONFIRMED,
de acuerdo con las reglas que se definan». Esas reglas nunca se definieron.

**Valor por defecto:** ⚙️ Admin cancela `PENDING` y `CONFIRMED`; en `CONFIRMED` el motivo es
obligatorio; nunca `DELIVERED` ni `CANCELLED`.

---

### D3 · Cancelación repetida

**Pregunta:** Si el cliente pulsa «cancelar» dos veces, ¿qué devolvemos?

**Valor por defecto:** ⚙️ `409 CONFLICT`. Es la respuesta honesta: el estado solicitado no es alcanzable.

**Alternativa considered:** devolver `204` y tratar la segunda llamada como éxito (idempotencia).

---

### D4 · Acceso a un pedido ajeno

**Pregunta:** Si Lucía pide el pedido de Marcos, ¿`403` o `404`?

**Valor por defecto:** ⚙️ `404`. No revela que ese pedido existe, lo que evita filtrar información de otros usuarios.

**Alternativa considerada:** `403` es más explícito para el cliente legítimo, pero confirma la existencia del recurso.

---

### D5 · Límites de cantidad y de líneas

**Pregunta:** ¿Cuántas unidades por línea y cuántas líneas por pedido?

**Valor por defecto:** ⚙️ 1–20 unidades por línea, 10 líneas distintas por pedido. Valores a ajustar.

---

### D6 · Variante desactivada con un pedido ya creado

**Pregunta:** Si la pizzería desactiva la Diavola LARGE, ¿puede el admin confirmar o entregar un pedido pendiente que la contiene?

**Valor por defecto:** ⚙️ Sí. La foto histórica manda; desactivar afecta a **nuevos** pedidos, no a los ya creados.

---

### D7 · Cambio de precio con pedido abierto

**Pregunta:** Si sube el precio de la Margherita con pedidos `PENDING`, ¿se cobra el nuevo?

**Valor por defecto:** ⚙️ Se cobra el precio del snapshot. El pedido guarda lo que el cliente aceptó.

---

### D8 · Caducidad de pedidos `PENDING`

**Pregunta:** ¿Un pedido no confirmado se cancela solo pasado un tiempo?

**Valor por defecto:** ⚙️ No en el MVP. Requiere un módulo de temporizadores que no está previsto.
Queda registrado como deuda en el backlog.

---

### D9 · Importe recibido en la recogida

**Pregunta:** ¿Puede el importe recibido diferir del total del pedido?

**Valor por defecto:** ⚙️ No. Debe coincidir; si no, `422` con el detalle de la diferencia.

---

### D10 · Ficha de cliente

**Pregunta:** ¿Qué campos se piden al registrarse? ¿Hace falta teléfono o dirección?

**Valor por defecto:** ⚙️ Email (único, insensible a mayúsculas), nombre y contraseña de 10 caracteres o más.
El rol se **siempre** fuerza a `CUSTOMER` en el servidor.

---

### D11 · ¿El registro devuelve token?

**Pregunta:** Tras registrarse, ¿el cliente recibe JWT o tiene que hacer login?

**Valor por defecto:** ⚙️ 201 con el recurso del cliente, **sin** token. El cliente hace login después.

**Alternativa considerada:** devolver el token en el mismo 201, ahorrando un viaje.

---

### D12 · Emisión y caducidad del JWT

**Pregunta:** ¿RS256 o HMAC? ¿Caducidad? ¿Refresh token?

**Valor por defecto:** ⚙️ RS256 (asimétrico: la API sólo verifica), caducidad de 15 minutos, **sin**
refresh token en el MVP. La rotación de claves se aborda en S9.

---

### D13 · Datos visibles en el historial de estados

**Pregunta:** ¿El cliente ve todo el historial del pedido o sólo hitos públicos?

**Valor por defecto:** ⚙️ Historial completo. Si algún motivo contiene información interna, se registra
un ADR para ocultarlo al cliente.

---

### D14 · Autorización y presupuesto para AWS

**Pregunta:** ¿Existe una cuenta preparada y un límite de gasto? ¿Se autoriza crear recursos?

**Estado:** ⬜ Abierta. **US-072, US-081 y US-082 permanecen bloqueadas** hasta que exista una
autorización escrita con presupuesto y alarma de coste.

**Valor por defecto:** ⚙️ No se crea ningún recurso de AWS. Los sprints 8 y 9 entregan Dockerfile,
pipeline y ADR sin despliegue real.

**Requisito previo a cualquier despliegue:**

- [ ] Autorización escrita del responsable
- [ ] Presupuesto máximo acordado
- [ ] Alarma de coste configurada **antes** de crear el primer recurso
- [ ] Rol de IAM de mínimo privilegio, sin claves estáticas en el repositorio
- [ ] Checklist de destrucción documentado

---

### D15 · Dónde vive el estado del pago

**Pregunta:** ¿el estado del pago (`UNPAID` / `PAID`) vive en una tabla `payments` o como columnas de
`orders`?

**Estado:** ⬜ Abierta. Detectada el **2026-10-11** al implementar US-003.

**La contradicción.** Dos documentos del repositorio no coinciden:

| Fuente | Dice | Prioridad |
|---|---|---|
| `requirements-v1.0.md` §7 — modelo de datos | **8 tablas**, y `payments` **no está entre ellas**. `cash_payments` sí, descrita como «cobro en efectivo» | **1 — fuente de verdad** |
| `backlog.md` US-037, criterio 4 | *«`payments.status = PAID` y `orders.status = DELIVERED` en la misma transacción»* | 6 — planificación |
| `sprint-04.md` US-037 | *«no existe ninguna fila en `cash_payments` para ese pedido»* | — — registro efímero |

En ninguna parte de `requirements-v1.0.md` se describe una tabla `payments`. La referencia del backlog apunta
a un identificador que el modelo de datos aprobado no contiene.

**Valor por defecto aplicado:** ⚙️ el estado del pago se modela como **columnas de `orders`**
(`payment_status`, `payment_method`), y `cash_payments` queda como el **registro del cobro efectivo**
(`order_id`, `amount`, `received_by`, `paid_at`).

**Por qué esta lectura:**

1. `requirements-v1.0.md` es la fuente de verdad de mayor precedencia y fija **8 tablas**. Añadir una novena
   para resolver una abreviatura del backlog sería alargar el modelo en contra de la fuente que manda.
2. El sistema tiene **un solo método de pago** (RF-PA-01: `CASH`) y **un pago por pedido** (RF-PA-07). Un
   estado de pago no tiene cardinalidad propia: es un atributo del pedido mientras no exista otro.
3. `UNIQUE (order_id)` en `cash_payments` sigue teniendo todo el sentido: impide el doble cobro (RN-PG-07),
   que es la garantía que de verdad importa y la que US-042 concurrentemente pone a prueba.

**Consecuencia si más adelante se decide una tabla `payments`:** sería una migración `V2`, nunca una
edición de `V1`, y habría que revisar US-037, US-038 y el contrato de API.

---

## Registro de resolución

| ID | Fecha | Decisión | Resuelta por |
|---|---|---|---|
| **D1** | 2026-10-11 | Moneda: **COP**, sin decimales. `NUMERIC(12,0)`, `RoundingMode.UNNECESSARY` | Responsable |