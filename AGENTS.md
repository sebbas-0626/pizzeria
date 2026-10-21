# AGENTS.md — Reglas de desarrollo del proyecto Pizzeria

> Este archivo es la **fuente operativa** del repositorio. Cualquier persona o agente de IA que trabaje aquí debe leerlo antes de tocar código.

- **Proyecto:** backend de una pizzería (una sede, pedidos para recogida, pago en efectivo)
- **Arquitectura:** monolito con arquitectura hexagonal por capas ([ADR-0004](docs/adr/0004-estructura-de-paquetes-por-capas.md))
- **Estado actual:** fase S1 (fundamentos). La base y el contrato HTTP están; **todavía no hay código de negocio.**

---

## 1. Fuentes de verdad y orden de precedencia

Cuando dos documentos se contradigan, gana el primero de esta lista:

| Prioridad | Documento | Qué gobierna |
|---|---|---|
| 1 | `docs/requirements/requirements-v1.0.md` (aprobado y congelado) **+ [`requirements-v1.1.md`](docs/requirements/requirements-v1.1.md)** (delta: el rol `EMPLOYEE`) | **Reglas de negocio.** Aprobado explícitamente por el responsable |
| 2 | `docs/adr/*` | Decisiones técnicas de arquitectura y stack, con sus alternativas |
| 3 | `docs/architecture/{architecture,data-model,api-contract,security}.md` | Diseño de capas, esquema, contrato HTTP y modelo de amenazas |
| 4 | `docs/architecture/openapi.json` | Contrato observable, verificado por pruebas |
| 5 | `docs/development/{conventions,spring-conventions}.md` | Estas reglas de código |
| 6 | `docs/planning/{backlog,roadmap}.md` | Qué falta y en qué orden |
| 7 | El código | La realidad ejecutable |
| — | `docs/planning/sprint-N.md` | Registro histórico **efímero**. Nunca es fuente de verdad |
| — | `docs/diagrams/*.drawio` | Representación. Se corrigen cuando cambian, no mandan |

**Regla anti-contradicción:** si un cambio altera requisito, contrato, esquema o decisión, la misma historia actualiza el documento correspondiente. No se cierra una historia con documentación desfasada.

---

## 2. Stack fijo (no cambiar sin ADR nuevo)

| Componente | Versión | Decisión |
|---|---|---|
| Java | **21 LTS** (Temurin) | [ADR-0010](docs/adr/0010-versiones-de-java-y-spring-boot.md) |
| Spring Boot | **4.1.x** | [ADR-0010](docs/adr/0010-versiones-de-java-y-spring-boot.md) |
| Maven | **3.10.0** (wrapper) | [ADR-0010](docs/adr/0010-versiones-de-java-y-spring-boot.md) |
| PostgreSQL | 17.x | [ADR-0010](docs/adr/0010-versiones-de-java-y-spring-boot.md) |
| Testcontainers | 2.0.x | Atención: los módulos cambiaron de nombre en 2.0 |
| Emulador AWS local | Floci | [ADR-0006](docs/adr/0006-floci-como-emulador-aws.md) |

Reglas dura:

- **Java 21 es el objetivo, no el mínimo.** Se compila con `--release 21`.
- **Prohibido usar características *preview* de Java.** Sin `--enable-preview`. Si alguna vez hace falta una, se pide aprobación explícita y se documenta en un ADR.
- **Prohibido cambiar la versión de Java o Spring Boot sin escribir un ADR nuevo.** Una ADR aceptada no se edita: se sustituye.

---

## 3. Reglas de arquitectura

### 3.1 Estructura

Por **capas globales**, con la dirección de dependencias visible en el nombre del paquete ([ADR-0004](docs/adr/0004-estructura-de-paquetes-por-capas.md)):

```
infrastructure ──► application ──► domain
  (conoce a todos)   (orquesta)     (no conoce a nadie)
shared  (capa transversal: traduce, registra, correlaciona)
```

```
com.pizzeria.{domain, application, infrastructure, shared}
```

Árbol completo y normativo en [ADR-0004](docs/adr/0004-estructura-de-paquetes-por-capas.md) §3. **Sólo esos cuatro paquetes de primer nivel**: un quinto es un fallo de build (`ArchitectureRulesTest.noHayPaquetesHuerfanos`).

| Paquete | Contenido |
|---|---|
| `domain.model` · `domain.model.vo` | `Pedido`, `LineaPedido`, `Cliente`, `Producto` y sus objetos de valor (`Email`, `Telefono`, `Precio`) |
| `domain.exception` | `DomainException`, `ErrorCode` y las excepciones concretas del negocio |
| `domain.port.in` · `domain.port.out` | Puertos ([ADR-0005](docs/adr/0005-los-puertos-se-declaran-en-el-dominio.md)) |
| `application.usecase` · `application.service` · `application.dto` | Interfaces de casos de uso, sus implementaciones y los DTOs |
| `infrastructure.adapter.in.web` | Controladores, filtros y DTOs de transporte |
| `infrastructure.adapter.out.persistence` · `.storage` · `.messaging` | Adaptadores JPA con `entity/`, `mapper/`, `repository/`; adaptadores de S3 y SQS |
| `infrastructure.config` | `SecurityConfig`, `JwtConfig`, `CorsConfig`, `OpenApiConfig`, `ClockConfig`, `BeanConfig` |
| `shared.security` · `shared.exception` · `shared.util` | `JwtService`, `JwtAuthenticationFilter`, `UserDetailsServiceImpl`, `GlobalExceptionHandler`, `Mapper` |

### 3.2 Reglas de dependencia (obligatorias, se verifican con ArchUnit)

| Capa | Puede importar | **Prohibido** importar |
|---|---|---|
| `domain` | `java.*` y nada más | Spring, Jakarta, JPA, Jackson, SDK AWS, lombok |
| `application` | `domain`, `application` | `infrastructure`, `shared`, Spring Web, Spring Data, Jackson, SDK AWS |
| `infrastructure.adapter.in.web` | `application`, Spring Web/Security/Jackson | `infrastructure.adapter.out.*`, entidades JPA |
| `infrastructure.adapter.out.persistence` | `domain.port.out`, `domain.model`, Spring Data | `infrastructure.adapter.in.*`, `application` |
| `infrastructure.adapter.out.storage` / `messaging` | `domain.port.out`, SDK AWS v2 | `infrastructure.adapter.in.*`, `domain.model`, `domain.exception`, JPA |
| `shared` | `domain.exception`, `shared`, `infrastructure` | `application`, `domain.model`, `domain.port`, `infrastructure.adapter.out` |

- El **SDK de AWS no aparece nunca** en `domain` ni en `application`.
- Una entidad JPA **nunca** es el modelo de dominio ([ADR-0003](docs/adr/0003-dominio-separado-de-jpa.md)).
- Los repositorios son **puertos de salida** definidos en `domain.port.out` ([ADR-0005](docs/adr/0005-los-puertos-se-declaran-en-el-dominio.md)).

### 3.3 Abstracciones

**Antes de crear una interfaz, una clase base o un patrón, hay que poder responder sí a las tres preguntas:**

1. ¿Existe al menos una segunda implementación **real** o un test que lo exija?
2. ¿Secruza una frontera del hexágono (HTTP, base de datos, almacenamiento, mensajería, reloj)?
3. ¿Aísla el dominio de una decisión que cambia (persistencia, infraestructura)?

Si las tres son "no", no se abstrae. En este proyecto **no** existen: CQRS, strategy de pagos, Specification, `AbstractAggregateRoot`, outbox (sólo a partir de S7).

**Excepción declarada:** la interfaz de caso de uso (`application.usecase`) separada de su implementación (`application.service`). Responde "sí" a la segunda y a la tercera — cruza la frontera HTTP y aísla los casos de uso de Spring — pero tiene una sola implementación. Se mantiene por [ADR-0004](docs/adr/0004-estructura-de-paquetes-por-capas.md), no por la regla anterior, y **no se extiende a ningún otro tipo**.

---

## 4. Reglas de negocio innegociables

1. **El servidor calcula todos los importes.** El cliente nunca envía ni decide precio unitario ni total. Si aparece un campo `price`/`total` en la entrada, se ignora y hay un test que lo demuestra.
2. **El pedido guarda una foto histórica**: nombre de pizza, tamaño, precio unitario, cantidad e importe por línea.
3. **`BigDecimal` para dinero**, nunca `double` ni `float`. La moneda es **COP (peso colombiano)**: escala **2**, `RoundingMode.UNNECESSARY` explícito y `DECIMAL(10,2)` en PostgreSQL.
4. **La máquina de estados vive en el dominio**, no en los controladores (ver diagrama `02-order-state-machine`).
5. **La propiedad del pedido se verifica en el servidor**, filtrando por `customerId` en la consulta, no comparando después.
6. **No existe forma pública de registrarse como `ADMIN` ni como `COOK`.** El endpoint de registro ignora cualquier rol recibido y fuerza `CLIENTE`; el personal se da de alta por dentro.
7. **Nada de secretos en el repositorio.** `.env` está en `.gitignore`; sólo se versiona `.env.example`. El CI ejecuta escaneo de secretos.
8. **Nada de contraseñas, tokens ni pilas de excepciones en los logs.**
9. **Ningún recurso de AWS con coste sin autorización escrita del responsable.**

---

## 5. Reglas de código

Detalle completo en [`docs/development/conventions.md`](docs/development/conventions.md). Resumen no negociable:

- Identificadores en **inglés**; comentarios, Javadoc y documentación en **español**.
- Sin Lombok. Código explícito.
- Sin `System.out`; sólo SLF4J con parametrización (`log.info("Order {} created", id)`), nunca concatenación.
- Sin números mágicos en el dominio: constantes con nombre (`OrderStatus.MAX_ITEMS_PER_ORDER`).
- Sin `null` como valor de retorno en puertos; se usa `Optional` o excepciones.
- Sin `catch (Exception e) {}` vacío. Si se captura, se registra o se propaga.

---

## 6. Reglas de Spring, persistencia y tests

Detalle completo en [`docs/development/spring-conventions.md`](docs/development/spring-conventions.md). Resumen no negociable:

- **Transacciones en los casos de uso** (`application`), no en controladores ni en el dominio ([ADR-0002](docs/adr/0002-transacciones-en-la-capa-de-aplicacion.md)).
- **Inyección por constructor**, nunca por campo.
- **`ddl-auto=validate`**. El esquema es sólo de Flyway.
- **Las migraciones son inmutables.** Una migración aplicada no se edita; se añade una nueva.
- Los tests de dominio **no** usan Spring. Los de casos de uso usan dobles. Los de persistencia y API usan Testcontainers.
- En Spring Boot 4 se usa **`@MockitoBean`**, nunca `@MockBean` (deprecado).

---

## 7. Protocolo de trabajo

### Antes de implementar una historia

1. Leer `AGENTS.md`, el requisito afectado y el ADR relacionado.
2. Comprobar el estado real del repositorio y las dependencias declaradas.
3. Enunciar **qué** se implementa, **qué archivos** se tocan y **cómo se verifica**.
4. Presentar un plan corto y verificable.
5. **Esperar aprobación** si toca arquitectura, seguridad, negocio, dinero, esquema o AWS.

### Durante

- Una historia (o subtarea acotada) por sesión.
- Código + pruebas + migración + documentación, en el mismo cambio.
- **Endpoint nuevo o endpoint cambiado → su petición en `api/bruno/pizzeria.postman_collection.json`, en el mismo cambio.** Es la colección de Postman para probar la API a mano y crece con la API: carpeta por recurso (`Sistema`, `Catálogo de pizzas`, …), una petición por caso con nombre corto en español, descripción de una línea y la respuesta real guardada como ejemplo. Sin ella, un endpoint no está terminado: `ColeccionPostmanTest.todoEndpointImplementadoTieneSuPeticion` falla en cuanto el contrato lo marca como `Hecho`.
- Ejecutar `./mvnw verify` y **reportar la salida real**.
- Prohibido simular: `TODO`, `@Disabled`, `return null`, mock fijado o feature flag que finja comportamiento se declara como pendiente, nunca como terminado.
- No sobrescribir ni borrar trabajo previo sin revisarlo y pedir autorización.

### Al terminar

1. Resumen de cambios (archivos y efecto).
2. Decisiones técnicas y por qué.
3. Pruebas ejecutadas **con resultado literal** (`Tests run: 42, Failures: 0` o el fallo).
4. Riesgos y trabajo pendiente.
5. Documentación y `backlog.md` actualizados.
6. Recomendación del siguiente paso, y **detenerse**.

---

## 8. Comandos

| Comando | Para qué |
|---|---|
| `./mvnw verify` | Build completo: compila, tests unitarios, integración y empaquetado |
| `./mvnw -Dtest=Clase test` | Test concreto |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` | Arranque local |
| `docker compose -f infra/local/docker-compose.yml up -d --build` | Laboratorio local (Postgres + backend + emulador) |
| `pwsh scripts/smoke-test.ps1` | Humo contra la app levantada |
| `pwsh scripts/dev-up.ps1 -Detach` | Levanta el laboratorio y comprueba `health` |
| `pwsh scripts/db.ps1 -Accion shell` | psql dentro del contenedor (el puerto de la base no se publica) |
| `gitleaks detect` | Ausencia de secretos |

---

## 9. Definition of Done (resumen)

Una historia **no** está terminada porque compile. Ver [`docs/planning/definition-of-done.md`](docs/planning/definition-of-done.md). En resumen:

1. Criterios de aceptación cumplidos.
2. Arquitectura y reglas de dependencia respetadas.
3. Pruebas automatizadas del comportamiento nuevo **y de sus fallos**.
4. `./mvnw verify` en verde, ejecutado y con salida reportada.
5. Sin `TODO`, `@Disabled`, `System.out` ni stubs que finjan comportamiento.
6. Migraciones versionadas e inmutables; aplicadas desde cero.
7. Sin secretos ni datos sensibles en código, logs ni repositorio.
8. Documentación actualizada si cambió contrato, esquema o decisión.
9. OpenAPI regenerado si cambió la API.
10. Si la API cambió, su carpeta en la colección de Postman (`api/bruno/`) al día.
11. Commit atómico y mensaje en formato Conventional Commits.

---

## 10. Commits

Formato: `<tipo>(<ámbito>): <descripción en imperativo>`

Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`.

Ámbitos: `catalog`, `orders`, `identity`, `payments`, `common`, `infra`, `docs`.

Ejemplos:

```
feat(orders): add PlaceOrderUseCase with server-side pricing
test(orders): cover illegal transition CONFIRMED -> PENDING
docs(adr): add ADR-0010 java and spring boot versions
```

Reglas: un commit = una historia o un paso coherente. Nunca se commitea código roto del `main`. Nunca se commitean secretos.