# Backlog — Proyecto Pizzeria

> **Fuente de verdad del estado de las historias.** Documento vivo: se actualiza al cerrar cada historia.
> Formato de estados: `Pendiente` · `En curso` · `En revisión` · `Hecho` · `Bloqueada` · `Descartada`
> Última actualización: **2026-10-11**

---

## 1. Reglas de uso

1. Un identificador de historia (`US-xxx`) **nunca se reutiliza ni se renumera**.
2. Las historias se estiman en puntos Fibonacci. No se estiman horas.
3. Una historia pasa a `Hecho` sólo si cumple la [Definition of Done](definition-of-done.md) completa.
4. El desglose técnico de una historia se escribe en `sprint-N.md` cuando entra en un sprint.
5. Una historia que revela un requisito nuevo se añade aquí **y** se propaga a `requirements-v1.0.md`.
6. Toda ambigüedad de negocio detectada se registra en [`decisions-pending.md`](decisions-pending.md) antes de implementar.

### Escala de estimación

| Puntos | Significado |
|---|---|
| 1 | Trivial, menos de medio día |
| 2 | Una tarde con el contexto ya conocido |
| 3 | Un día de trabajo |
| 5 | Un día y medio |
| 8 | Dos o tres días |
| 13 | Más de tres días, o con riesgo técnico relevante |

**Supuesto de velocidad:** ~1 punto por jornada efectiva de 4 h, sin bloqueos y con Docker funcionando.

---

## 2. Resumen por épica

| Épica | Nombre | Historias | Puntos | Historias Hechas |
|---|---|---|---|---|
| [EP-00](#31-épica-ep-00--fundación-y-plataforma) | Fundación y plataforma | 6 | 24 | 0 |
| [EP-01](#32-épica-ep-01--catálogo) | Catálogo | 4 | 16 | 0 |
| [EP-02](#33-épica-ep-02--identidad-y-seguridad) | Identidad y seguridad | 4 | 20 | 0 |
| [EP-03](#34-épica-ep-03--pedidos-y-pagos) | Pedidos y pagos | 9 | 60 | 0 |
| [EP-04](#35-épica-ep-04--calidad-documentación-y-endurecimiento) | Calidad, documentación y endurecimiento | 6 | 34 | 0 |
| [EP-05](#36-épica-ep-05--integración-con-s3-imágenes) | Integración con S3 (imágenes) | 3 | 21 | 0 |
| [EP-06](#37-épica-ep-06--integración-con-sqs-mensajería) | Integración con SQS (mensajería) | 3 | 26 | 0 |
| [EP-07](#38-épica-ep-07--cicd-y-despliegue) | CI/CD y despliegue | 4 | 26 | 0 |
| [EP-08](#39-épica-ep-08--observabilidad-e-infraestructura) | Observabilidad e infraestructura | 5 | 26 | 0 |
| | **Total** | **44** | **≈253** | **0** |

---

## 3. Épicas

### 3.1 Épica EP-00 — Fundación y plataforma

Objetivo: un proyecto que compila, arranca, se migra y se valida en un solo comando.

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| [US-001](#us-001--esqueleto-del-proyecto-y-verificación-del-stack) | Esqueleto del proyecto y verificación del stack | S1 | Crítica | — | 3 | Pendiente |
| [US-002](#us-002--composición-local-con-docker-compose) | Composición local con Docker Compose | S1 | Alta | US-001 | 5 | Pendiente |
| [US-003](#us-003--esquema-inicial-y-flyway) | Esquema inicial y Flyway | S1 | Crítica | US-001, US-002 | 5 | Pendiente |
| [US-004](#us-004--estrategia-global-de-errores-y-validación) | Estrategia global de errores y validación | S1 | Alta | US-001 | 3 | Pendiente |
| [US-005](#us-005--documentación-openapi) | Documentación OpenAPI | S1 | Media | US-004 | 3 | Pendiente |
| [US-006](#us-006--pipeline-base-en-github-actions) | Pipeline base en GitHub Actions | S1 | Alta | US-001, US-003 | 5 | Pendiente |

#### US-001 — Esqueleto del proyecto y verificación del stack

**Como** desarrollador, **quiero** un proyecto Maven que arranque y verifique su build con un único comando, **para** tener una base reproducible antes de escribir funcionalidad.

- **Prioridad:** Crítica · **Dependencias:** — · **Estimación:** 3
- **Criterios de aceptación**
  1. `./mvnw verify` compila, pasa los tests y empaqueta un JAR ejecutable.
  2. El bytecode generado es de versión 65 (Java 21) y el build usa `--release 21`.
  3. `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` arranca y `/actuator/health` responde `UP`.
  4. No hay ninguna dependencia de AWS en el perfil por defecto.
  5. Existe un test de carga de contexto de Spring vacío.
  6. Un test **ArchUnit** verifica las reglas de dependencia entre capas sobre el esqueleto vacío.
  7. Maven Enforcer rechaza el build si la versión de Java es inferior a 21.
  8. **Spike verificado**: la matriz Spring Boot 4.1.x + JDK 21 + Testcontainers 2.x + Mockito + springdoc 3.x compila y ejecuta un test unitario y uno de integración.
- **Tareas técnicas**
  - `backend/pom.xml` con parent de Spring Boot, `<java.version>21</java.version>`, Maven Wrapper 3.10.0 y `maven-enforcer-plugin` (`requireJavaVersion [21,)`).
  - `PizzeriaApplication`, `.editorconfig`, `.gitignore`.
  - `application.yml` base, `application-local.yml`, `application-test.yml`.
  - Test de arquitectura ArchUnit.
  - `docs/architecture/architecture.md` con la tabla de stack.
- **Pruebas requeridas**
  - `PizzeriaApplicationTests`: carga de contexto.
  - `ArchitectureRulesTest` (ArchUnit): las 5 reglas de la tabla de `spring-conventions.md`.
  - `EnforcerTest`: no automatizable; se verifica en CI que un JDK antiguo falla.
- **Definición de terminado:** [DoD completa](definition-of-done.md).
- **Riesgos / decisiones pendientes**
  - springdoc 3.x declara compatibilidad con Boot 4.x pero su matriz oficial sólo lista Boot 4.0.x → **plan B**: si falla, fijar Boot 4.0.x o documentar el contrato a mano (US-005).
  - Testcontainers 2.0 renombró módulos y paquetes → verificar la nomenclatura exacta en este spike.
  - Si el spike fuerza a bajar a Spring Boot 3.5.x, **Java 21 sigue siendo válido** (Boot 3.5.x declara 17–25).

#### US-002 — Composición local con Docker Compose

**Como** desarrollador, **quiero** `docker compose up` que levante la aplicación y PostgreSQL con espera healthy, **para** desarrollar sin instalar nada y con datos persistentes.

- **Prioridad:** Alta · **Dependencias:** US-001 · **Estimación:** 5
- **Criterios de aceptación**
  1. `docker compose up --build` deja aplicación y base de datos en estado `healthy` en menos de 90 s.
  2. Un volumen nombrado conserva los datos entre reinicios.
  3. El puerto de PostgreSQL **no** se publica en el host por defecto.
  4. Ninguna credencial real está en el repositorio: todo procede de `.env` (copiado de `.env.example`).
  5. `docker compose down -v` deja el entorno limpio.
- **Tareas técnicas:** `infra/local/docker-compose.yml`, `infra/local/docker-compose.test.yml`, `.env.example`, `scripts/dev-up.ps1`, `scripts/dev-down.ps1`, healthchecks (`pg_isready` y actuator).
- **Pruebas requeridas:** job de CI que levanta, consulta `/actuator/health` y destruye; verificación manual documentada.
- **Riesgos:** tags de imagen desalineados → se fijan versiones explícitas, nunca `latest`.

#### US-003 — Esquema inicial y Flyway

**Como** desarrollador, **quiero** el esquema versionado con Flyway, **para** que la base de datos sea reproducible y evolutiva.

- **Prioridad:** Crítica · **Dependencias:** US-001, US-002 · **Estimación:** 5
- **Criterios de aceptación**
  1. `flyway:migrate` crea las 8 tablas con sus FK, índices y restricciones `CHECK`.
  2. Arranca correctamente sobre base vacía y `flyway:validate` queda limpio.
  3. `spring.jpa.hibernate.ddl-auto=validate`.
  4. Ninguna migración `V` modificada después de su merge.
  5. Un test de integración con Testcontainers aplica las migraciones y verifica el esquema.
- **Tareas técnicas:** `V1__baseline_schema.sql`, política de migraciones en `db/migration/README.md`, `flyway-database-postgresql`.
- **Pruebas requeridas:** integración con Testcontainers; aserciones sobre `information_schema`.
- **Riesgos:** el `CHECK` de coherencia de `orders.total_amount` requiere un *trigger* porque PostgreSQL no admite subconsultas en `CHECK` → resolver en esta historia.

#### US-004 — Estrategia global de errores y validación

**Como** consumidor de la API, **quiero** errores consistentes y sin datos sensibles, **para** integrarme sin adivinar formatos.

- **Prioridad:** Alta · **Dependencias:** US-001 · **Estimación:** 3
- **Criterios de aceptación**
  1. Todo error sale como `application/problem+json` con `code` estable.
  2. `MethodArgumentNotValidException` → 400 con lista campo/mensaje.
  3. Ningún mensaje incluye traza de pila en producción.
  4. Existe un test por cada tipo de excepción mapeada.
- **Tareas técnicas:** `common/domain/DomainException`, `common/web/GlobalExceptionHandler`, catálogo de códigos.
- **Pruebas requeridas:** `@WebMvcTest` con controlador de pruebas y 6–8 escenarios.

#### US-005 — Documentación OpenAPI

**Como** desarrollador o revisor, **quiero** documentación viva de la API, **para** no depender de la memoria.

- **Prioridad:** Media · **Dependencias:** US-004 · **Estimación:** 3
- **Criterios de aceptación**
  1. `/api-docs` y `/swagger-ui` disponibles en `local` y **deshabilitados** en `prod`.
  2. Autenticación por `bearer` documentada.
  3. `api-contract.md` alineado con la definición.
- **Riesgos:** ver US-001 (plan B).

#### US-006 — Pipeline base en GitHub Actions

**Como** desarrollador, **quiero** que cada *push* ejecute build y pruebas, **para** no romper la rama principal.

- **Prioridad:** Alta · **Dependencias:** US-001, US-003 · **Estimación:** 5
- **Criterios de aceptación**
  1. Workflow en `push` y `pull_request` con JDK 21 y caché de Maven.
  2. Ejecuta `./mvnw verify`.
  3. Job de integración con Testcontainers sobre los runners de GitHub.
  4. Falla si se detecta un secreto.
  5. Distintivo (badge) en el `README`.
- **Tareas técnicas:** `.github/workflows/ci.yml`, `ci-integration.yml`, escaneo de secretos, `dependabot.yml` (Maven y Docker).

---

### 3.2 Épica EP-01 — Catálogo

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-010 | Consulta pública del catálogo | S2 | Alta | US-003, US-004 | 5 | Pendiente |
| US-011 | Alta y edición de pizzas (admin) | S2 | Alta | US-010 | 5 | Pendiente |
| US-012 | Alta y edición de variantes (admin) | S2 | Alta | US-011 | 5 | Pendiente |
| US-013 | Baja lógica del catálogo | S2 | Media | US-012 | 3 | Pendiente |

#### US-010 — Consulta pública del catálogo

**Como** cliente, **quiero** ver las pizzas disponibles con sus variantes, tamaños y precios, **para** decidir qué pedir.

- **Criterios de aceptación**
  1. `GET /api/v1/pizzas` → 200 con página de pizzas activas y sus variantes disponibles.
  2. `GET /api/v1/pizzas/{id}` → 200 o 404.
  3. Sin autenticación requerida.
  4. Filtro `?availableOnly=true` (por defecto `true` en el catálogo público).
  5. La respuesta expone `price` decimal y `size` ∈ {`SMALL`, `MEDIUM`, `LARGE`}.
- **Pruebas requeridas:** unitarias del caso de uso con doble del puerto; `@DataJpaTest` del filtro y ausencia de N+1; `@WebMvcTest` de ambos endpoints; integración con catálogo sembrado.
- **Riesgos:** N+1 al cargar variantes → `@EntityGraph` o `join fetch`; se comprueba en el test de integración.

#### US-011 — Alta y edición de pizzas (admin)

**Como** administrador, **quiero** crear y editar pizzas, **para** mantener el catálogo al día.

- **Criterios de aceptación**
  1. `POST /api/v1/admin/pizzas` → 201 con cabecera `Location`.
  2. `PATCH /api/v1/admin/pizzas/{id}` actualiza únicamente los campos presentes.
  3. Sin rol ADMIN → 403; identificador inexistente → 404.
  4. Nombre obligatorio, entre 3 y 80 caracteres, único entre pizzas activas → 409 si se repite.
- **Pruebas requeridas:** unitarias (nombre duplicado), `@WebMvcTest` con ADMIN y con CUSTOMER (403), integración.

#### US-012 — Alta y edición de variantes (admin)

**Como** administrador, **quiero** crear variantes con precio y disponibilidad, **para** controlar qué se vende.

- **Criterios de aceptación**
  1. `POST /api/v1/admin/pizzas/{pizzaId}/variants` → 201.
  2. `PATCH /api/v1/admin/variants/{variantId}` cambia precio y disponibilidad.
  3. Tamaño duplicado en la misma pizza → 409 (`UNIQUE (pizza_id, size)`).
  4. Precio ≤ 0 → 422.
  5. **Cambiar el precio no altera pedidos previos**: un `order_item` conserva su `unit_price` histórico.

#### US-013 — Baja lógica del catálogo

**Como** administrador, **quiero** desactivar pizzas y variantes sin borrarlas, **para** no romper el historial de pedidos.

- **Criterios de aceptación**
  1. `PATCH` con `active=false` y `available=false`.
  2. **No existe ningún endpoint `DELETE` en el catálogo** (verificado con un test que enumera las rutas).
  3. Un pedido que ya contiene la variante sigue siendo legible.
  4. La variante desactivada desaparece del catálogo público.

---

### 3.3 Épica EP-02 — Identidad y seguridad

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-020 | Registro de cliente | S4 | Crítica | US-003, US-004 | 5 | Pendiente |
| US-021 | Autenticación y emisión de JWT | S4 | Crítica | US-020 | 8 | Pendiente |
| US-022 | Autorización por roles y propiedad del recurso | S4 | Crítica | US-021 | 5 | Pendiente |
| US-023 | Perfil del cliente | S4 | Baja | US-021 | 2 | Pendiente |

#### US-020 — Registro de cliente

**Como** visitante, **quiero** registrarme con email, nombre y contraseña, **para** poder pedir.

- **Criterios de aceptación**
  1. `POST /api/v1/auth/register` → 201 con el recurso del cliente, **sin** contraseña.
  2. Email único, insensible a mayúsculas yúsculas (`CITEXT` o índice único funcional sobre `LOWER()`).
  3. Contraseña mínima de 10 caracteres.
  4. Hash con BCrypt mediante `DelegatingPasswordEncoder`. Nunca texto plano.
  5. **Enviar `role: "ADMIN"` → 400.** El rol se fuerza a `CUSTOMER` en el servidor.
  6. La contraseña no aparece en los logs ni en la respuesta de error.
- **Pruebas requeridas:** unitarias del codificador, integración (email duplicado con distinta capitalización), web (intento de ADMIN rechazado), test que captura el log y afirma que no contiene la contraseña.
- **Riesgos:** enumeración de usuarios mediante 409 → aceptado en el MVP, anotado como deuda.

#### US-021 — Autenticación y emisión de JWT

**Como** cliente registrado, **quiero** iniciar sesión y recibir un token, **para** acceder a mis pedidos.

- **Criterios de aceptación**
  1. `POST /api/v1/auth/login` → 200 con `accessToken`, `tokenType: Bearer` y `expiresIn`.
  2. Credenciales incorrectas → 401 con mensaje genérico (no revela si el email existe).
  3. Token firmado con **RS256**, claims `sub` (UUID), `roles`, `iss`, `iat`, `exp`; caducidad de 15 minutos.
  4. La clave privada **no** está en el repositorio: se genera en local y se inyecta por entorno.
  5. Token alterado o caducado → 401.
  6. Sin refresh token (fuera de alcance, documentado).
- **Pruebas requeridas:** unitarias del emisor con reloj fijo (firma y expiración), web con token válido/inválido/caducado, integración con la clave real de pruebas.
- **Riesgos:** rotación de claves → tarea de S9.

#### US-022 — Autorización por roles y propiedad del recurso

**Como** administrador, **quiero** que las rutas administrativas sólo las alcancen administradores y que cada cliente sólo vea lo suyo, **para** evitar fugas de datos.

- **Criterios de aceptación**
  1. `/api/v1/admin/**` exige ADMIN: sin token → 401, con rol insuficiente → 403.
  2. `GET /api/v1/orders/{idDeOtro}` → **404**, no 403.
  3. El filtrado por `customerId` ocurre **en la consulta**.
  4. Test ArchUnit que impide que `domain` importe Spring.
  5. Ninguna ruta pública devuelve datos de otro usuario.
- **Pruebas requeridas:** matriz de seguridad parametrizada sobre todas las rutas; dos clientes reales en Testcontainers.

#### US-023 — Perfil del cliente

**Como** cliente, **quiero** consultar mis datos con mi token, **para** comprobar que mi cuenta es correcta.

- **Criterios de aceptación**
  1. `GET /api/v1/customers/me` → 200 con id, email, nombre, roles y fecha de alta.
  2. Nunca devuelve el hash de contraseña.
  3. Sin token → 401.

---

### 3.4 Épica EP-03 — Pedidos y pagos

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-030 | Crear pedido (precio en servidor) | S3 (dominio) + S4 (web) | Crítica | US-010, US-012, US-021, US-022 | 13 | Pendiente |
| US-031 | Listar mis pedidos | S4 | Alta | US-030, US-022 | 3 | Pendiente |
| US-032 | Ver detalle de un pedido propio | S4 | Alta | US-030 | 3 | Pendiente |
| US-033 | Cancelar mi pedido pendiente | S4 | Alta | US-030, US-022 | 5 | Pendiente |
| US-034 | Listar pedidos (admin) | S4 | Alta | US-030 | 3 | Pendiente |
| US-035 | Confirmar pedido | S4 | Alta | US-034 | 3 | Pendiente |
| US-036 | Cancelar pedido (admin) | S4 | Alta | US-035 | 3 | Pendiente |
| US-037 | Registro de recogida y pago en efectivo | S4 | Crítica | US-035 | 13 | Pendiente |
| US-038 | Historial de cambios de estado | S4 | Media | US-033, US-035, US-036, US-037 | 3 | Pendiente |

#### US-030 — Crear pedido (recogida, precio en servidor)

**Como** cliente, **quiero** crear un pedido de recogida con sus variantes y cantidades, **para** reservar mi compra.

> **Ejemplo de negocio:** Lucía pide 2 × Margherita MEDIUM (12,50) y 1 × Diavola LARGE (15,00). Total = **40,00**, calculado en el servidor.

- **Criterios de aceptación**
  1. `POST /api/v1/orders` → 201 con cabecera `Location`, `orderId` y `orderNumber`.
  2. El cuerpo acepta **sólo** `variantId` y `quantity`. Cualquier `unitPrice`, `total` o `price` enviado se ignora — **y hay un test que lo demuestra**.
  3. Pedido vacío → 422 `ORDERS_EMPTY`.
  4. Variante inexistente, inactiva o no disponible → 422 `CATALOG_VARIANT_NOT_AVAILABLE`.
  5. Cantidad fuera de rango → 400.
  6. Total = Σ(precio × cantidad) con `BigDecimal` escala 0 y `RoundingMode.UNNECESSARY`. Un importe fraccionario se rechaza, no se redondea.
  7. Estado inicial `PENDING`, pago `CASH`/`UNPAID`, `fulfillmentType = PICKUP`.
  8. Cada `order_item` guarda la foto histórica: `pizza_name`, `size`, `unit_price`.
  9. Se inserta una fila en `order_status_history` (null → PENDING) con el actor.
  10. Dos variantes iguales en el mismo pedido se fusionan en una única línea.
- **Tareas técnicas:** agregado `Order` + `OrderItem` + `OrderStatus` (máquina de estados), `Money`, `PlaceOrderUseCase` (`@Transactional`), puerto `PricingCatalog`, `OrderController`, `CreateOrderRequest`, mappers, repositorios JPA.
- **Pruebas requeridas:** unitarias del agregado (todos los invariantes, incluidos los negativos); unitarias del caso de uso con dobles; web (401/403/400/422/201 y el caso del precio inyectado); integración con Testcontainers (persistencia, tipos monetarios, fusión de líneas).
- **Riesgos:** la historia más grande del backlog (13 puntos). Se divide internamente en subtareas y **no se toca más de un concepto por sesión**.

#### US-031 — Listar mis pedidos

- **Criterios de aceptación:** `GET /api/v1/orders?page=0&size=20` → 200 con `content`, `totalElements`, `totalPages`, orden `placedAt DESC`; `size` máximo 100; devuelve **sólo** los del cliente autenticado (test con dos clientes); lista vacía → 200 con `content: []`.

#### US-032 — Ver detalle de un pedido propio

- **Criterios de aceptación:** 200 con cabecera (número, estado, total, fechas), líneas con su foto histórica y estado del pago; pedido ajeno → 404; incluye `order_status_history`.

#### US-033 — Cancelar mi pedido pendiente

**Como** cliente, **quiero** cancelar mi pedido mientras esté pendiente, **para** rectificar si me he equivocado.

- **Criterios de aceptación**
  1. `POST /api/v1/orders/{id}/cancel` → 204 si está en `PENDING`.
  2. Pedido de otro cliente → 404.
  3. Estados `CONFIRMED`, `DELIVERED` o `CANCELLED` → 409 `ORDERS_INVALID_TRANSITION`.
  4. Se registra actor, fecha y motivo opcional; se añade la entrada al historial.
  5. Un pedido `CANCELLED` nunca vuelve a `PENDING` (test explícito).

#### US-034 — Listar pedidos (admin)

- **Criterios de aceptación:** `GET /api/v1/admin/orders?status=PENDING&page=0` → 200 paginado; filtro por estado; sólo ADMIN; orden `placedAt ASC` por defecto (lo más antiguo primero, que es la operativa real de una pizzería).

#### US-035 — Confirmar pedido

- **Criterios de aceptación**
  1. `POST /api/v1/admin/orders/{id}/confirm` → 204.
  2. Sólo desde `PENDING`; cualquier otro estado → 409.
  3. Añade entrada en el historial.
  4. **No toca el pago** (sigue `UNPAID`) — test explícito de que confirmar no paga.

#### US-036 — Cancelar pedido (admin)

**Como** administrador, **quiero** cancelar pedidos que no puedo cumplir, **para** gestionar incidencias.

- **Criterios de aceptación**
  1. `POST /api/v1/admin/orders/{id}/cancel` — *endpoint que faltaba en el diseño inicial de la API*.
  2. Permite `PENDING` y `CONFIRMED`.
  3. En `CONFIRMED`, el `reason` es obligatorio → 400 si falta.
  4. `DELIVERED` y `CANCELLED` → 409.
  5. Motivo persistido y visible en el historial.

#### US-037 — Registro de recogida y pago en efectivo

**Como** administrador, **quiero** registrar la recogida y el cobro al entregar, **para** cerrar el pedido con su pago coherente.

- **Criterios de aceptación**
  1. `POST /api/v1/admin/orders/{id}/complete-pickup` → 204.
  2. Sólo desde `CONFIRMED`.
  3. Inserta `cash_payments(order_id, amount, received_by = admin, paid_at = now)` con `amount = orders.total_amount`.
  4. `payments.status = PAID` y `orders.status = DELIVERED` **en la misma transacción**.
  5. Si algo falla, **no** queda `DELIVERED` sin pago (test que fuerza el fallo tras insertar el pago y comprueba el rollback).
  6. Un segundo intento, concurrente o posterior → 409 `PAYMENTS_ALREADY_REGISTERED`, garantizado por `UNIQUE (order_id)`.
  7. `received_by` es el administrador autenticado, nunca un dato del cuerpo.
  8. Si el importe recibido ≠ total → 422.
  9. Se registra la entrada de historial con su actor.
- **Pruebas requeridas:** integración con Testcontainers y dos hilos concurrentes (uno gana, otro recibe 409); test de rollback con excepción inyectada; verificación de que no existe ningún `DELIVERED` sin fila en `cash_payments`.
- **Riesgos:** la historia técnicamente más delicada. Si el tiempo aprieta, se priorizan las pruebas de rollback y doble cobro por encima de la elegancia del diseño.

#### US-038 — Historial de cambios de estado

- **Criterios de aceptación:** cada transición escribe `from`, `to`, `changedBy`, `reason` y `changedAt`; visible en el detalle; las filas son inmutables (no hay endpoint de escritura); test de la secuencia completa `PENDING → CONFIRMED → DELIVERED` con tres filas.

---

### 3.5 Épica EP-04 — Calidad, documentación y endurecimiento

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-040 | Base de pruebas de integración con Testcontainers | S5 | Alta | US-003 | 5 | Pendiente |
| US-041 | Matriz de pruebas de autorización | S5 | Alta | US-022, US-031…US-037 | 8 | Pendiente |
| US-042 | Pruebas de atomicidad y concurrencia | S5 | Alta | US-037, US-040 | 5 | Pendiente |
| US-043 | Logs estructurados y correlación | S5 | Media | US-001, US-004 | 3 | Pendiente |
| US-044 | Documentación y diagramas del sistema | S5 | Alta | Todas | 8 | Pendiente |
| US-045 | Contrato de API congelado | S5 | Media | US-005, US-030…US-037 | 5 | Pendiente |

#### US-040 — Base de pruebas de integración con Testcontainers

- **Criterios de aceptación:** contenedor reutilizado entre clases; cada test parte del esquema migrado por Flyway; aislamiento guaranteed; si Docker no está disponible el test falla con mensaje claro (no se salta en silencio); perfil `test` con `ddl-auto=validate`.

#### US-041 — Matriz de pruebas de autorización

- **Criterios de aceptación:** test parametrizado que recorre la lista de endpoints y espera el código correcto; cubre pedido ajeno (404), cancelación ajena (404), ruta admin sin token (401), ruta admin con CUSTOMER (403) y token caducado (401).

#### US-042 — Pruebas de atomicidad y concurrencia

- **Criterios de aceptación:** fallo entre la escritura del pago y el cambio de estado → rollback completo; dos hilos llamando a `complete-pickup` simultáneamente → exactamente un 204 y un 409; ninguna fila `DELIVERED` sin su pago.

#### US-043 — Logs estructurados y correlación

- **Criterios de aceptación:** salida JSON en `local` y `prod`; identificador de correlación propagado y devuelto; el patrón de log **redacta** `password`, `token` y `authorization`; test que captura el log de `/auth/login` y afirma que no contiene la contraseña ni el JWT.

#### US-044 — Documentación y diagramas del sistema

- **Criterios de aceptación:** las 8 páginas `.drawio` más sus SVG exportados; ADR-0001…ADR-0010 redactados; `README.md` con qué es el proyecto, cómo arrancarlo, el stack con versiones justificadas, un diagrama embebido y los endpoints principales; `requirements-v1.0.md` aprobado por el responsable.

#### US-045 — Contrato de API congelado

- **Criterios de aceptación:** `api-contract.md` con todos los endpoints, cuerpos, códigos y errores; snapshot de OpenAPI versionado en `docs/architecture/openapi.json`; test de contrato que falla si el OpenAPI generado cambia sin actualizar el documento.

---

### 3.6 Épica EP-05 — Integración con S3 (imágenes)

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-050 | Puerto de almacenamiento y adaptador local por defecto | S6 | Media | US-030 | 5 | Pendiente |
| US-051 | Adaptador S3 verificado contra el emulador | S6 | Media | US-050 | 8 | Pendiente |
| US-052 | Imágenes de pizza gestionadas por el administrador | S6 | Media | US-051, US-011 | 8 | Pendiente |

- **US-050:** puerto `ImageStorage` (`store` / `presign` / `delete`) con adaptador por defecto local o "no-op" activo **sin** AWS; test ArchUnit que prohíbe el SDK de AWS en `domain` y `application`; el backend debe arrancar sin AWS.
- **US-051:** bucket creado idempotentemente al arrancar en el perfil local; `PUT`, `GET` y URL prefirmada funcionando contra **Floci**; test de integración con Testcontainers; `endpointOverride` por variable de entorno; caducidad de las URLs prefirmadas configurable.
- **US-052:** `POST /api/v1/admin/pizzas/{id}/image` (multipart) → clave del objeto; validación de tipo MIME y tamaño máximo (5 MB) rechazando ejecutables; reemplazo de la imagen anterior; el catálogo público devuelve `imageUrl` prefirmado; sin ADMIN → 403; **el cliente no puede indicar una URL arbitraria** (evita SSRF).

---

### 3.7 Épica EP-06 — Integración con SQS (mensajería)

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-060 | Eventos de dominio y puerto de publicación | S7 | Media | US-030, US-035 | 8 | Pendiente |
| US-061 | Outbox transaccional y publicador SQS | S7 | Media | US-060 | 13 | Pendiente |
| US-062 | Consumidor de ejemplo y prueba contra emulador | S7 | Baja | US-061 | 5 | Pendiente |

- **US-060:** eventos `OrderPlaced`, `OrderConfirmed`, `OrderCancelled`, `OrderPickedUp`; puerto `DomainEventPublisher`; test ArchUnit que prohíbe el SDK de AWS en el dominio.
- **US-061:** tabla `outbox` escrita **en la misma transacción** que el cambio de estado (patrón *transactional outbox*); publicador a SQS con reintentos y marca de fallo; **test**: si SQS está caído, el pedido se confirma igual y el mensaje sale al recuperar el servicio.
- **US-062:** consumidor de `OrderConfirmed` con log estructurado y fila de auditoría; test de integración contra el emulador (producir → consumir → verificar); idempotencia documentada por identificador de evento.
- **Riesgo US-061:** es la historia más ambiciosa (consistencia sin 2PC). Si el tiempo no da, se entrega publicación directa y el ADR registra la deuda.

---

### 3.8 Épica EP-07 — CI/CD y despliegue

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-070 | Dockerfile multi-stage | S8 | Alta | US-001 | 5 | Pendiente |
| US-071 | Pipeline de entrega continua | S8 | Alta | US-006, US-070 | 8 | Pendiente |
| US-072 | Despliegue en EC2 | S8 | Media — **requiere autorización** | US-070, US-071 | 8 | Bloqueada |
| US-073 | Evaluación de ECS frente a EC2 | S8 | Baja | US-072 | 5 | Pendiente |

- **US-070:** imagen multi-stage con tag fijo; ejecución como usuario no root; sin fuentes ni credenciales; `.dockerignore` correcto; test de humo arrancando contra PostgreSQL de pruebas. **Fuera de alcance: compilación nativa GraalVM** ([ADR-0009](../adr/0009-sin-nativo-graalvm-en-el-mvp.md)).
- **US-071:** jobs `verify` → `docker build` → escaneo de imagen → publicación en ECR **sólo con autorización**; artefactos JAR, imagen y OpenAPI.
- **US-072:** presupuesto y alarma de coste **antes** de crear nada; rol de IAM de mínimo privilegio y sin claves estáticas en el repositorio; script de destrucción documentado y ejecutado al terminar. **No se ejecuta sin autorización expresa del responsable.**
- **US-073:** ADR con coste aproximado, carga operativa, escalado y criterio de migración. **Sin crear recursos.**

---

### 3.9 Épica EP-08 — Observabilidad e infraestructura

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-080 | Actuator, salud y métricas | S1 + S5 | Media | US-001, US-002 | 3 | Pendiente |
| US-081 | Observabilidad en la nube | S9 | Baja | US-043, US-072 | 5 | Bloqueada |
| US-082 | Evolución a RDS (evaluación y migración) | S9 | Baja | US-072 | 8 | Bloqueada |
| US-083 | Frontend estático en S3 + CloudFront | Fuera del MVP | Fuera de alcance | Existe frontend | 5 | Descartada (MVP) |
| US-084 | Estado final del portafolio | S9 | Media | Todas | 5 | Pendiente |

- **US-080:** `/actuator/health` con detalle de base de datos; métricas; endpoints sensibles restringidos por perfil o rol; test de que `/actuator/env` no está expuesto en `prod`.
- **US-081:** ADR de CloudWatch Logs con alarmas básicas, política de retención y coste. Sólo tras autorización.
- **US-082:** ADR de diferencias operativas, coste y configuración segura (grupos de seguridad, sin exposición pública). Si se autoriza, la aplicación funciona cambiando **sólo** variables de entorno.
- **US-083:** fuera del MVP. Se planifica cuando exista frontend; documenta la distinción entre S3 para imágenes, S3 para estáticos y CloudFront.
- **US-084:** `README` con métricas reales (cobertura, número de pruebas, endpoints), tabla de sprints completados y lista de decisiones con su porqué.

---

## 4. Historias fuera del MVP

Documentadas para que no se pierdan, **explícitamente no planificadas**:

| Tema | Motivo |
|---|---|
| Entregas a domicilio | Fuera del alcance acordado |
| Pasarelas de pago | Sólo efectivo en el MVP |
| Módulo de cocina (pantalla de producción) | Fuera del alcance |
| Múltiples sedes | Una sola sede |
| Microservicios | Monolito modular ([ADR-0008](../adr/0008-monolito-modular-antes-que-microservicios.md)) |
| Rate limiting | Se resuelve en el borde; anotado como deuda |
| Clave de idempotencia en creación de pedido | Anotado como deuda |
| Caducidad automática de pedidos `PENDING` | Sin módulo de temporizadores en el MVP |

---

## 5. Registro de cambios

| Fecha | Cambio |
|---|---|
| 2026-10-11 | Backlog creado. 44 historias, ≈253 puntos. Java 21 LTS fijado en [ADR-0010](../adr/0010-versiones-de-java-y-spring-boot.md) |