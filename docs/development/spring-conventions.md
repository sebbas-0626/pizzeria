# Convenciones de Spring, persistencia y pruebas

> Documento normativo. Complementa a [`AGENTS.md`](../../AGENTS.md) y a [`conventions.md`](conventions.md).
> Ante cualquier conflicto: `AGENTS.md` manda.

Stack: **Java 21 LTS · Spring Boot 4.1.x · Spring Framework 7.0.x · Maven 3.10.0 · PostgreSQL 17 · Flyway · JUnit 6 · Testcontainers 2.0.x** ([ADR-0010](../adr/0010-versiones-de-java-y-spring-boot.md)).

---

## 1. Reglas de dependencia entre capas (verificables)

Estas reglas se comprueban automáticamente con **ArchUnit**. Un test de arquitectura es criterio de aceptación de US-001.

```java
// Referencia de la regla que se implementará en el Sprint 1.
// NO se escribe hasta US-001; aquí queda la especificación.
```

| Capa | Puede importar | Prohibido importar |
|---|---|---|
| `..domain..` | `java..`, nada más | `org.springframework..`, `jakarta..`, `com.fasterxml..`, `software.amazon.awssdk..`, `lombok..` |
| `..application..` | `..domain..` | `org.springframework.web..`, `org.springframework.data..`, `com.fasterxml..`, `software.amazon.awssdk..` |
| `..adapters.in.web..` | `..application..`, Spring Web/Security/Jackson | `..adapters.out..`, `jakarta.persistence..` |
| `..adapters.out.persistence..` | `..application.port.out..`, `..domain..`, Spring Data | `..adapters.in..` |
| `..adapters.out.storage..` / `..messaging..` | `..application.port.out..`, SDK AWS v2 | `..adapters.in..`, `..domain..`, `jakarta.persistence..` |

**Excepciones permitidas y documentadas:**

| Excepción | Motivo |
|---|---|
| `application` puede usar `@Transactional` | La frontera transaccional es una decisión de aplicación. Se registra en [ADR-0002](../adr/0002-transacciones-en-la-capa-de-aplicacion.md) junto con la alternativa más pura rechazada |
| `application` puede usar `Clock` (JDK, no Spring) | Es una abstracción estándar, no un framework |

---

## 2. Componentes Spring

### 2.1 Inyección

```java
// Correcto
public final class PlaceOrderUseCase {

    private final OrderRepository orderRepository;
    private final PricingCatalog pricingCatalog;
    private final Clock clock;

    public PlaceOrderUseCase(OrderRepository orderRepository,
                             PricingCatalog pricingCatalog,
                             Clock clock) {
        this.orderRepository = orderRepository;
        this.pricingCatalog = pricingCatalog;
        this.clock = clock;
    }
}
```

| Regla | Detalle |
|---|---|
| **Prohibido** | `@Autowired` en campos, en cualquier forma |
| **Prohibido** | `Field injection`, setters o construcción manual desde fuera |
| Clases | `final` cuando no se herit'an. Spring soporta beans `final` sin problema |
| Beans | Registrados por constructor o por `@Configuration` explícita; **sin** component scanning amplio sobre los adaptadores |

### 2.2 Configuración

- Un `@Configuration` por módulo, en `com.pizzeria.<modulo>.config`.
- Propiedades externas con `@ConfigurationProperties` validadas: nunca cadenas sueltas en `@Value`.
- Perfiles: `application.yml` (base, sólo lo imprescindible), `application-local.yml`, `application-test.yml`. Nada de secretos en ninguna de ellas.
- `ddl-auto=validate`. **Nunca** `create`, `update` ni `none`.

---

## 3. Transacciones ([ADR-0002](../adr/0002-transacciones-en-la-capa-de-aplicacion.md))

**Una historia de negocio = una transacción.**

```java
@Transactional
public OrderResponse placeOrder(PlaceOrderCommand command) { ... }

@Transactional(readOnly = true)
public List<OrderSummary> listMyOrders(UUID customerId, PageRequest page) { ... }
```

| Regla | Detalle |
|---|---|
| Dónde | En los **casos de uso** (`application`) |
| **Prohibido** | `@Transactional` en controladores, entidades de dominio o repositorios |
| Escrituras | `@Transactional` con rollback por defecto ante cualquier `RuntimeException` |
| Lecturas | `@Transactional(readOnly = true)` |
| `rollbackFor` | Sólo si una excepción comprobada debe deshacer; por defecto no hace falta |
| Aislamiento | `READ_COMMITTED` (por defecto). **No** subir el nivel para resolver concurrencia |
| `self-invocation` | No invocar métodos transaccionales desde dentro de la misma clase; rompe el proxy |

**La concurrencia se resuelve con el esquema**, no con el nivel de aislamiento:

- `@Version` (bloqueo optimista) en `orders`.
- `UNIQUE (order_id)` en `cash_payments`: es lo que impide realmente el doble cobro.
- La comprobación del estado se hace dentro de la misma transacción que la escritura.

---

## 4. Persistencia con JPA

### 4.1 Separación de modelos ([ADR-0003](../adr/0003-dominio-separado-de-jpa.md))

Las entidades JPA viven **exclusivamente** en `adapters.out.persistence` y **nunca** se exponen fuera de ese paquete.

```text
domain.Order                    ← agregado de negocio, sin anotaciones
        ↑
        │  mapper explícito (OrderPersistenceMapper)
        ↓
persistence.OrderJpaEntity      ← @Entity, tabla orders
```

| Regla | Detalle |
|---|---|
| Nombres | `OrderJpaEntity`, `PizzaJpaEntity`… para que la frontera sea evidente al leer |
| Mapper | **Manual**, sin MapStruct en el MVP. Escribirlos a mano obliga a pensar en qué campos se persisten |
| Constructor | La entidad JPA puede tener constructor vacío; la de dominio, no |
| Relación | El adaptador traduce identificadores; los agregados se referencian por `UUID`, no por `@ManyToOne` |
| Devolver | El adaptador devuelve objetos de **dominio**, nunca entidades JPA |

### 4.2 Consultas

| Regla | Detalle |
|---|---|
| N+1 | Prohibido. Si hace falta `@EntityGraph` o `join fetch`, se usa de forma explícita |
| Filtro de propietario | **Siempre en la consulta**: `findByIdAndCustomerId(id, customerId)`. Nunca `findById` y comparar después |
| Consultas derivadas | Preferidas para filtros simples |
| `@Query` | Sólo cuando la derivada se vuelva ilegible. Se documenta el motivo |
| Proyecciones | Para listados se usan DTOs de lectura, no entidades, cuando el volumen lo justifica |
| Paginación | `Pageable` de Spring Data **sólo en el adaptador**; hacia fuera se traduce a `PageResponse<T>` propio |

### 4.3 Migraciones (Flyway)

| Regla | Detalle |
|---|---|
| Ubicación | `src/main/resources/db/migration/` |
| Nomenclatura | `V<fecha>__<descripcion_en_ingles>.sql`, siempre ascendente |
| **Inmutabilidad** | Una migración aplicada **no se edita jamás**. Se añade una nueva |
| Recursos de Spring Boot 4 | Flyway **no** se autoconfigura sólo con `flyway-core`. Desde Spring Boot 4 la autoconfiguración vive en un módulo propio, **`spring-boot-flyway`**, que hay que declarar |
| Dependencias de Flyway | `flyway-core` + `flyway-database-postgresql` + `spring-boot-flyway` |
| Por qué | Con las dos primeras y sin la tercera, el build compila, la app arranca y **no se ejecuta ninguna migración**. `ddl-auto=validate` tampoco avisa, porque aún no hay entidades que validar. Fallo silencioso, descubierto el 2026-10-11 en US-003 |
| Validación | `spring.flyway.clean-disabled=true` siempre |
| Datos de prueba | **Nunca** en migraciones `V`. Van en el perfil de test o en scripts de `scripts/` |

### 4.4 Integridad en la base de datos

Las reglas de negocio viven en el dominio. La base de datos garantiza que nada se rompa **por accidente**:

```sql
price      NUMERIC(12,0) NOT NULL CHECK (price > 0)
quantity   INTEGER       NOT NULL CHECK (quantity > 0 AND quantity <= 20)
line_total NUMERIC(12,0) NOT NULL CHECK (line_total = unit_price * quantity)
UNIQUE (pizza_id, size)
UNIQUE (order_id)          -- en cash_payments: impide el doble cobro
```

- Importes en **COP, sin decimales**: `NUMERIC(12,0)`. El peso colombiano no tiene subdivisión, así que
  el `CHECK line_total = unit_price * quantity` es una igualdad exacta y además el motor la usa como
  verificación de integridad real. Con `NUMERIC(12,2)` esa comprobación habría que relajarla para tolerar
  fracciones, que es justo lo que no queremos.

- Tipos enumerados: `CHECK` con valores literales, **no** tipos `ENUM` de PostgreSQL (cambiar un valor no requiere `ALTER TYPE` ni bloquea la tabla).
- Claves foráneas con `RESTRICT` donde haya historial. Nunca `CASCADE` sobre datos de negocio.
- Índices para las consultas reales: `orders(customer_id, placed_at DESC)`, `orders(status, placed_at)`, `order_items(order_id)`, `order_status_history(order_id)`.
- `TIMESTAMPTZ` siempre, almacenado en UTC.

---

## 5. Capa web

### 5.1 Controladores

| Regla | Detalle |
|---|---|
| Responsabilidad | Traducir HTTP ↔ aplicación. Cero lógica de negocio |
| `@Valid` | En el `@RequestBody`. Valida formato, **no** reglas de negocio |
| Respuestas | Códigos correctos: `201` + `Location` al crear, `204` al cambiar estado sin cuerpo, `200` con cuerpo al leer |
| Versión | Prefijo `/api/v1` |
| DTOs | `record` para entrada y salida. Nunca se expone una entidad de dominio |
| Mapeo | Conversión explícita en un mapper; nada de reflexión mágica |

### 5.2 Validación (tres capas, sin solapamiento)

| Capa | Qué valida | Herramienta |
|---|---|---|
| Formato | Tipos, obligatoriedad, longitudes, rangos | Bean Validation (`@NotNull`, `@Min`, `@Size`) en el DTO |
| Negocio | Variante no disponible, transición ilegal, pedido vacío | Dominio y caso de uso |
| Integridad | Precio positivo, cantidad válida, unicidad | PostgreSQL |

> La misma regla **no** se implementa en dos capas. Si el dominio dice "precio positivo", el DTO de entrada no repite esa validación.

### 5.3 Errores HTTP

Formato único **RFC 9457** (`application/problem+json`) mediante `ProblemDetail`, ampliado con:

- `code`: identificador estable en mayúsculas (`ORDERS_NOT_FOUND`).
- `timestamp` y `traceId`.

Mapeo por defecto en `common.web.GlobalExceptionHandler`:

| Excepción | HTTP |
|---|---|
| `MethodArgumentNotValidException`, `ConstraintViolationException` | 400 |
| Excepción de autenticación | 401 |
| `AccessDeniedException` | 403 |
| Recurso inexistente **o de otro cliente** | 404 |
| `EmailAlreadyRegisteredException`, `InvalidOrderTransitionException`, `PaymentAlreadyRegisteredException` | 409 |
| Regla de negocio violada (variante no disponible, pedido vacío, importe distinto) | 422 |
| Cualquier otra | 500, mensaje genérico, sin detalles internos |

**Regla dura:** el mensaje de error nunca contiene datos de otro usuario, contraseñas, tokens ni SQL.

### 5.4 Seguridad

| Aspecto | Configuración |
|---|---|
| Autenticación | JWT **RS256** verificado por Spring Security Resource Server |
| Emisión | Puerto `JwtTokenIssuer` + adaptador con Nimbus JOSE JWT |
| Claims | `sub` (UUID del cliente), `roles`, `iss`, `iat`, `exp` |
| Caducidad | 15 minutos. Sin refresh token en el MVP |
| Contraseñas | `DelegatingPasswordEncoder` con **BCrypt**. Nunca hashing propio, nunca texto plano |
| Autorización | Reglas por ruta en `SecurityFilterChain`: `/api/v1/admin/**` → `hasRole("ADMIN")` |
| `@PreAuthorize` | Sólo para reglas de método que no se puedan expresar por ruta. Un solo criterio, documentado |
| Método | `authorizeHttpRequests` con `requestMatchers`, en orden explícito. Las reglas no implícitas |
| CORS | Sin configurar en el MVP |
| Registro de admin | Sólo por script de bootstrap con variables de entorno. **Ninguna** ruta pública |
| Filtros | Un `OncePerRequestFilter` propio + `SecurityContextHolder`. Sin anotaciones de seguridad en el dominio |

**Sobre 403 vs 404:** un cliente que pide un pedido ajeno recibe **404**, no 403, para no revelar que ese pedido existe. La regla está en `requirements-v1.0.md` y hay test que lo demuestra.

---

## 6. Pruebas

### 6.1 Estrategia por capas

| Capa | Tecnología | Arranque | Qué comprueba |
|---|---|---|---|
| Dominio | JUnit 6 puro | Ninguno | Invariantes, transiciones, redondeos |
| Casos de uso | JUnit 6 + dobles | Ninguno | Orquestación, puertos usados, excepciones |
| Adaptador web | `@WebMvcTest` | Contexto web recortado | Contrato HTTP, validación, códigos, mapeo de errores |
| Persistencia | `@DataJpaTest` | Contexto de datos | Mapeo JPA, constraints, queries |
| Integración | Spring Boot + **Testcontainers** | Contexto completo | El sistema entero contra PostgreSQL real |
| Arquitectura | **ArchUnit** | Ninguno | Reglas de dependencia entre capas |
| AWS (S3/SQS) | Testcontainers contra **Floci** | — | Adaptadores de infraestructura |

### 6.2 Reglas

| Regla | Detalle |
|---|---|
| Nombre | `ClaseTest` para clases de producción, `MetodoTest` para casos de uso. TestNG con la misma clase `XxxTest` |
| Enunciado | DesCRIBE **el comportamiento**, no el método. `shouldRejectOrderWhenVariantIsNotAvailable`, no `testCancel2` |
| **Cada historia incluye su caso negativo** | Un test que sólo verifica el camino feliz no cuenta |
| Sin `@Disabled` | Nunca. Un test pendiente se borra y se anota en el backlog |
| Sin `Thread.sleep` | Para esperas: `Awaitility` con timeout acotado |
| Sin `assertTrue(true)` | Aserciones sobre comportamiento observable |
| Sin mocks como fuente de verdad | Un doble mal configurado hace pasar un test roto |
| Reloj | `Clock.fixed(...)` en los tests de dominio y aplicación |
| Base de datos | **Testcontainers**, nunca H2 ni una BD en memoria. H2 miente sobre tipos y constraints |
| Sin estado compartido | Un test no depende del orden ni de otro test |

### 6.3 Testcontainers

```text
⚠ Testcontainers 2.0 cambió la nomenclatura:
  org.testcontainers:mysql                  → org.testcontainers:testcontainers-mysql
  org.testcontainers.containers.PostgreSQLContainer
                                            → org.testcontainers.postgresql.PostgreSQLContainer
  Se verifica la nomenclatura exacta en US-001 antes de escribir el primer test.
```

| Regla | Detalle |
|---|---|
| Reutilización | `@Testcontainers` + `withReuse(true)` + `~/.testcontainers.properties` para no levantar Postgres por clase |
| Aislamiento | Limpieza por test (`@Sql` con rollback, o `DELETE` en orden) |
| Anatomía | Si Docker no está disponible, el test **falla con un mensaje claro**, no se salta en silencio |
| Imagen | `postgres:17-alpine` con tag **fijado**, nunca `latest` |
| Emulador AWS | Floci en el puerto 4566; se verificará si `LocalStackContainer` es compatible o si hace falta `GenericContainer` (tarea de US-051) |

### 6.4 Reemplazos de Spring Boot 3.4+

| Obsoleto | Usar |
|---|---|
| `@MockBean` | **`@MockitoBean`** |
| `@SpyBean` | **`@MockitoSpyBean`** |
| `spring.factories` para auto-configuración | `AutoConfiguration.imports` |

---

## 7. Integraciones AWS

- El **SDK de AWS v2 no aparece** en `domain` ni en `application`.
- Toda integración se declara como un **puerto** en `application.port.out` y se implementa en `adapters.out.storage` o `adapters.out.messaging`.
- El perfil por defecto **no** depende de AWS: el arranque sin S3/SQS debe funcionar siempre (ver US-050).
- El emulador local es **Floci** en `http://localhost:4566` con credenciales de prueba; el endpoint se inyecta por variable de entorno ([ADR-0006](../adr/0006-floci-como-emulador-aws.md)).
- **Ningún recurso real de AWS se crea sin autorización escrita del responsable**, con presupuesto y alarma de coste previos.

---

## 8. Antes de dar por buena una historia

- [ ] ¿`./mvnw verify` pasa **y he reportado la salida real**?
- [ ] ¿Los tests de dominio y aplicación arrancan sin contexto de Spring?
- [ ] ¿Hay test del caso negativo, no sólo del feliz?
- [ ] ¿Las reglas de ArchUnit siguen verdes?
- [ ] ¿Las migraciones se aplican sobre una base **vacía**?
- [ ] ¿`ddl-auto=validate` y no `create`/`update`?
- [ ] ¿Las excepciones comprobadas tienen su código HTTP y su test?
- [ ] ¿Los logs no contienen contraseñas ni tokens?
- [ ] ¿Hay algún `@MockBean` en vez de `@MockitoBean`?
- [ ] ¿El `Clock` está inyectado y los tests no dependen del reloj real?