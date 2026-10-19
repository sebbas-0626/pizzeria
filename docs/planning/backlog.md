# Backlog — Proyecto Pizzeria

> **Fuente de verdad del estado de las historias.** Documento vivo: se actualiza al cerrar cada historia.
> Formato de estados: `Pendiente` · `En curso` · `En revisión` · `Hecho` · `Bloqueada` · `Descartada`
> Última actualización: **2026-10-17**

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
| [EP-00](#31-épica-ep-00--fundación-y-plataforma) | Fundación y plataforma | 6 | 24 | **6** |
| [EP-01](#32-épica-ep-01--catálogo) | Catálogo | 4 | 16 | **1** |
| [EP-02](#33-épica-ep-02--identidad-y-seguridad) | Identidad y seguridad | 4 | 20 | 0 |
| [EP-03](#34-épica-ep-03--pedidos-y-pagos) | Pedidos y pagos | 9 | 60 | 0 |
| [EP-04](#35-épica-ep-04--calidad-documentación-y-endurecimiento) | Calidad, documentación y endurecimiento | 6 | 34 | 0 |
| [EP-05](#36-épica-ep-05--integración-con-s3-imágenes) | Integración con S3 (imágenes) | 3 | 21 | 0 |
| [EP-06](#37-épica-ep-06--integración-con-sqs-mensajería) | Integración con SQS (mensajería) | 3 | 26 | 0 |
| [EP-07](#38-épica-ep-07--cicd-y-despliegue) | CI/CD y despliegue | 4 | 26 | 0 |
| [EP-08](#39-épica-ep-08--observabilidad-e-infraestructura) | Observabilidad e infraestructura | 5 | 26 | 0 |
| | **Total** | **44** | **≈253** | **7** |

---

## 3. Épicas

### 3.1 Épica EP-00 — Fundación y plataforma

Objetivo: un proyecto que compila, arranca, se migra y se valida en un solo comando.

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| [US-001](#us-001--esqueleto-del-proyecto-y-verificación-del-stack) | Esqueleto del proyecto y verificación del stack | S1 | Crítica | — | 3 | Hecho |
| [US-002](#us-002--composición-local-con-docker-compose) | Composición local con Docker Compose | S1 | Alta | US-001 | 5 | Hecho |
| [US-003](#us-003--esquema-inicial-y-flyway) | Esquema inicial y Flyway | S1 | Crítica | US-001, US-002 | 5 | Hecho |
| [US-004](#us-004--estrategia-global-de-errores-y-validación) | Estrategia global de errores y validación | S1 | Alta | US-001 | 3 | Hecho |
| [US-005](#us-005--documentación-openapi) | Documentación OpenAPI | S1 | Media | US-004 | 3 | Hecho |
| [US-006](#us-006--pipeline-base-en-github-actions) | Pipeline base en GitHub Actions | S1 | Alta | US-001, US-003 | 5 | Hecho |

#### US-001 — Esqueleto del proyecto y verificación del stack

**Como** desarrollador, **quiero** un proyecto Maven que arranque y verifique su build con un único comando, **para** tener una base reproducible antes de escribir funcionalidad.

- **Prioridad:** Crítica · **Dependencias:** — · **Estimación:** 3
- **Estado:** **Hecho** el 2026-10-11. Los 8 criterios verificados con salida real.
- **Criterios de aceptación**
  1. `./mvnw verify` compila, pasa los tests y empaqueta un JAR ejecutable.
  2. El bytecode generado es de versión 65 (Java 21) y el build usa `--release 21`.
  3. `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` arranca y `/actuator/health` responde `UP`.
  4. No hay ninguna dependencia de AWS en el perfil por defecto.
  5. Existe un test de carga de contexto de Spring vacío.
  6. Un test **ArchUnit** verifica las reglas de dependencia entre capas sobre el esqueleto vacío.
  7. Maven Enforcer rechaza el build si la versión de Java es inferior a 21. Es una **prueba negativa**: el proyecto **nunca** se compila ni se ejecuta con un JDK 17; sólo se comprueba que el guardián cierra.
  8. **Spike verificado**: la matriz Spring Boot 4.1.x + JDK 21 + Testcontainers 2.x + Mockito + springdoc 3.x compila y ejecuta un test unitario y uno de integración.
- **Tareas técnicas**
  - `backend/pom.xml` con parent de Spring Boot, `<java.version>21</java.version>`, Maven Wrapper 3.10.0 y `maven-enforcer-plugin` (`requireJavaVersion [21,)`).
  - `PizzeriaApplication`, `.editorconfig`, `.gitignore`.
  - `application.yml` base, `application-local.yml`, `application-test.yml`.
  - Test de arquitectura ArchUnit.
  - `docs/architecture/architecture.md` con la tabla de stack.
- **Pruebas requeridas**
  - `PizzeriaApplicationIT`: carga de contexto contra PostgreSQL real. Es un `*IT` y no un `*Test` porque la carga completa necesita la base de datos; Failsafe lo ejecuta en `verify`.
  - `ArchitectureRulesTest` (ArchUnit): las 5 reglas de la tabla de `spring-conventions.md`.
  - `EnforcerTest`: no automatizable; en local y en CI se verifica que con un JDK antiguo (17) el build **falla**. Java 17 no se usa nunca para trabajar: el objetivo, las pruebas y el runtime son 21.
- **Definición de terminado:** [DoD completa](definition-of-done.md).
- **Riesgos / decisiones pendientes**
  - springdoc 3.x declara compatibilidad con Boot 4.x pero su matriz oficial sólo lista Boot 4.0.x → **plan B**: si falla, fijar Boot 4.0.x o documentar el contrato a mano (US-005).
    - **Resuelto 2026-10-11**: `springdoc-openapi:3.1.1` se construye sobre `spring-boot-starter-parent` 4.1.0. Instalado y verificado: `/v3/api-docs` responde 200 en `local` y **404 sin perfil**, y Swagger UI igual. US-005 añade el esquema bearer y `api-contract.md`.
  - Testcontainers 2.0 renombró módulos y paquetes → **resuelto**: `org.testcontainers:testcontainers-postgresql` con la clase `org.testcontainers.postgresql.PostgreSQLContainer`.
  - Si el spike fuerza a bajar a Spring Boot 3.5.x, **Java 21 sigue siendo válido** (Boot 3.5.x declara 17–25).

#### US-002 — Composición local con Docker Compose

**Como** desarrollador, **quiero** `docker compose up` que levante la aplicación y PostgreSQL con espera healthy, **para** desarrollar sin instalar nada y con datos persistentes.

- **Prioridad:** Alta · **Dependencias:** US-001 · **Estimación:** 5
- **Estado:** **Hecho** el 2026-10-11. Los 5 criterios verificados con ejecución real.
- **Criterios de aceptación**
  1. `docker compose up --build` deja aplicación y base de datos en estado `healthy` en menos de 90 s.
  2. Un volumen nombrado conserva los datos entre reinicios.
  3. El puerto de PostgreSQL **no** se publica en el host por defecto.
  4. Ninguna credencial real está en el repositorio: todo procede de `.env` (copiado de `.env.example`).
  5. `docker compose down -v` deja el entorno limpio.
- **Tareas técnicas:** `infra/local/docker-compose.yml`, `infra/local/docker-compose.test.yml`, `.env.example`, `scripts/dev-up.ps1`, `scripts/dev-down.ps1`, healthchecks (`pg_isready` y actuator).
- **Pruebas requeridas:** job de CI que levanta, consulta `/actuator/health` y destruye; verificación manual documentada.
- **Riesgos:** tags de imagen desalineados → se fijan versiones explícitas, nunca `latest`.
- **Verificación ejecutada (2026-10-11)**
  1. ✅ `up --build` → ambos `healthy` en **60,8 s** la primera vez (descargando dependencias) y **50,4 s** en caliente.
  2. ✅ Volumen `postgres-data`: una fila escrita antes de `restart db` sigue ahí después.
  3. ✅ `docker compose ps` muestra `5432/tcp` **sin** mapeo al host, y `localhost:5432` desde el host no responde. Sólo se publica la API en 8080.
  4. ✅ `.env` ignorado por `.gitignore:14`; `.env.example` versionado con valores de mentira; ninguna credencial real en el repositorio.
  5. ✅ `down -v` deja 0 contenedores y 0 volúmenes de este proyecto.
- **Decisiones tomadas aquí**
  - `.env` vive en `infra/local/`, no en la raíz: Compose busca el archivo en el directorio del compose, y en la raíz el comando del criterio 1 fallaría sin dar una razón evidente.
  - La app usa la imagen `maven:3.10.0-eclipse-temurin-21` y su `mvn`, no `./mvnw`: el wrapper exige `unzip` para validar el SHA-256 y la imagen **no lo trae** (comprobado). El `mvn` de la imagen es 3.10.0, la versión que fija ADR-0010.
  - Cachés de Maven y de compilación en volúmenes con nombre, para que el `target/` del host (Windows) no se mezcle con clases compiladas en Linux.
  - El Dockerfile de producción es **US-070**; aquí no se adelanta.

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
- **Riesgos:** el `CHECK` de coherencia de `orders.total_amount` requiere un *trigger* porque PostgreSQL no admite subconsultas en `CHECK` → **resuelto** con un `CONSTRAINT TRIGGER ... DEFERRABLE INITIALLY DEFERRED` que se ancla al commit.
- **Estado:** **Hecho** el 2026-10-11. Verificación con salida real:
  1. ✅ Las 8 tablas creadas, con sus FK, índices y restricciones `CHECK`. 9 claves foráneas, todas `RESTRICT`. 0 tipos `ENUM`.
  2. ✅ Arranca sobre base vacía y `flyway:validate` limpio: `Successfully validated 1 migration` / `Schema "public" is up to date`.
  3. ✅ `ddl-auto=validate` en `application.yml` y `application-test.yml`.
  4. ✅ `V1` no se ha editado después de aplicarse. Las correcciones se hicieron **antes** del merge.
  5. ✅ `SchemaMigrationIT`: 14 pruebas contra PostgreSQL real (Testcontainers), con aserciones sobre `information_schema` y sobre el comportamiento real de cada restricción.
- **Tres hallazgos de esta historia** (detalle en `db/migration/README.md` y en `spring-conventions.md` §4.3)
  - **`spring-boot-flyway` es obligatorio en Spring Boot 4.** Con `flyway-core` + `flyway-database-postgresql` a solas, el build compila, la app arranca y **no se ejecuta ninguna migración**. La regla de `spring-conventions.md` §4.3 era incorrecta y se ha corregido.
  - **PostgreSQL rechaza una expresión dentro de `UNIQUE` de tabla.** El email único sin mayúsculas tiene que ser un `CREATE UNIQUE INDEX ... ON t (lower(email))`.
  - **RN-IM-06 no lo puede cumplir la base de datos.** Al convertir a `NUMERIC(12,0)`, PostgreSQL redondea en silencio (`12500.50 → 12501`). La garantía vive en `Money` con `RoundingMode.UNNECESSARY`, en el dominio. Documentado con una prueba dedicada.

#### US-004 — Estrategia global de errores y validación

**Como** consumidor de la API, **quiero** errores consistentes y sin datos sensibles, **para** integrarme sin adivinar formatos.

- **Prioridad:** Alta · **Dependencias:** US-001 · **Estimación:** 3
- **Criterios de aceptación**
  1. Todo error sale como `application/problem+json` con `code` estable.
  2. `MethodArgumentNotValidException` → 400 con lista campo/mensaje.
  3. Ningún mensaje incluye traza de pila en producción.
  4. Existe un test por cada tipo de excepción mapeada.
- **Tareas técnicas:** `domain/exception/DomainException`, `shared/exception/GlobalExceptionHandler`, catálogo de códigos. *(Rutas vigentes tras [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md); en el momento de la historia eran `common/domain/` y `common/web/`.)*
- **Pruebas requeridas:** `@WebMvcTest` con controlador de pruebas y 6–8 escenarios.
- **Estado:** **Hecho** el 2026-10-11. Verificación con salida real:
  1. ✅ Todo error sale como `application/problem+json` con `code` estable, más `timestamp` y `traceId`.
  2. ✅ `MethodArgumentNotValidException` → 400 con lista `errors` de campo y mensaje. El test exige los nombres de campo concretos, no sólo el código.
  3. ✅ Ningún mensaje incluye traza. El test del 500 comprueba que el cuerpo **no** contiene el mensaje original, ni el nombre de la excepción, ni `java.lang`.
  4. ✅ 9 escenarios en `GlobalExceptionHandlerTest`, uno por tipo de excepción mapeada y otro por cada código del catálogo.
- **Decisiones tomadas aquí**
  - **El estado HTTP lo decide una tabla, no la excepción.** El dominio no sabe qué es un 404, así que `DomainException` transporta un `ErrorCode` y `GlobalExceptionHandler` lo traduce. Añadir un caso es añadir una fila.
  - **401 y 403 NO están mapeados.** Mapearlos exigiría importar Spring Security, y esa dependencia no se añade hasta US-021 a propósito. Documentado en `spring-conventions.md` §5.3 para que no parezca un olvido.
  - **Las excepciones concretas las crea cada módulo en su historia.** `OrderNotFoundException` viene con US-032, no aquí. En `src/test` hay una `ExcepcionDePrueba` que demuestra que el manejador traduce *cualquier* `DomainException`.
  - **El controlador de pruebas está en `src/test`**, no en `src/main`: no se despliega. No había forma de probar el `@RestControllerAdvice` sin una ruta que lance cada excepción, y aún no existe ningún endpoint real (US-010 va después).
- **Hallazgo**: `@WebMvcTest` y `ObjectMapper` han cambiado de sitio en Spring Boot 4. `@WebMvcTest` pasó a `org.springframework.boot.webmvc.test.autoconfigure` y necesita su propio starter, `spring-boot-starter-webmvc-test`; y el mapper es `tools.jackson.databind.ObjectMapper` (Jackson 3), no `com.fasterxml.jackson.databind`.

#### US-005 — Documentación OpenAPI

**Como** desarrollador o revisor, **quiero** documentación viva de la API, **para** no depender de la memoria.

- **Prioridad:** Media · **Dependencias:** US-004 · **Estimación:** 3
- **Criterios de aceptación**
  1. `/api-docs` y `/swagger-ui` disponibles en `local` y **deshabilitados** en `prod`.
  2. Autenticación por `bearer` documentada.
  3. `api-contract.md` alineado con la definición.
- **Riesgos:** ver US-001 (plan B).
- **Estado:** **Hecho** el 2026-10-11. El riesgo de US-001 queda cerrado: springdoc 3.1.1 compila sobre Spring Boot 4.1.1 y sirve el contrato.
- **Verificación con salida real**
  1. ✅ `OpenApiDeshabilitadoIT` — **sin** perfil activo (el que se parece a producción): `/v3/api-docs` → **404**, `/swagger-ui/index.html` → **404** y sin HTML. `/actuator/health` → **200**, para que un 404 en la doc no pueda deberse a que la app no arrancó. Peticiones HTTP reales sobre un puerto real, no MockMvc.
  2. ✅ `OpenApiHabilitadoIT` — con perfil `local`: `/v3/api-docs` → **200** con OpenAPI 3.1 y Swagger UI → **200**. El esquema `bearerAuth` se verifica **leyendo el JSON generado**: `type: http`, `scheme: bearer`, `bearerFormat: JWT`.
  3. ✅ `docs/architecture/api-contract.md` creado, con convenciones, autenticación, el catálogo completo de 14 códigos y la tabla de endpoints marcados como pendientes.
- **Decisiones tomadas aquí**
  - **Se apaga en `application.yml`, no en un perfil `prod`.** Springdoc se activa en cuanto entra en el classpath: un `application-prod.yml` que se olvidara no evitaría nada. Se documenta por qué en `spring-conventions.md` §5.5.
  - **El esquema `bearer` se declara pero NO se aplica globalmente.** Hay endpoints públicos (`GET /api/v1/pizzas`) y protegidos. Un requisito global marcaría como privados los públicos. Hay un test que lo comprueba.
  - **Los tests usan el `HttpClient` del JDK**, no `TestRestTemplate`: en Spring Boot 4 latter pasó a `org.springframework.boot.resttestclient` y **no hay starter**, sólo el módulo suelto. Comprobar un 404 real por HTTP no merece una dependencia nueva.
- **Trabajo pendiente que esta historia deja**
  - `docs/architecture/openapi.json` versionado y test de contraste contra el OpenAPI generado → **US-045**.
  - El `paths` del contrato sigue vacío porque aún no hay endpoints. Es lo esperado; el primero llega con US-010.

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
- **Estado:** **Hecho** el 2026-10-11.
- **Verificación con salida real**
  1. ✅ `WorkflowFilesTest` (7 pruebas) lee los tres ficheros con SnakeYAML y comprueba que declaran `name`, `jobs` y `on:`, que fijan `java-version: '21'` con `distribution: temurin` y `cache: maven`, y que ejecutan `./mvnw -B -ntp verify`.
  2. ✅ Se ejecuta `verify`, no `test`: las pruebas `*IT` viven en `verify` (Failsafe) y `test` no las lanza.
  3. ✅ `ci-integration.yml` ejecuta `verify` sobre los runners de GitHub, donde Docker viene de serie para Testcontainers.
  4. ✅ `gitleaks-action` con `fetch-depth: 0`, para recorrer el historial completo y no sólo el commit actual.
  5. ✅ Distintivos de CI, Integración, Java 21 y Spring Boot en el `README`, que además documenta cómo arrancar, el stack, la arquitectura y el estado real del proyecto.
- **Decisiones que conviene no deshacer**
  - **`pull_request` además de `push`.** Los forks no disparan el `push` de su rama ni tienen acceso a secretos: es la única forma de comprobar un PR externo.
  - **El pipeline no usa secretos propios.** Hay un test que lo comprueba: el único permitido es `GITHUB_TOKEN`, que GitHub emite por ejecución. Cualquier otro sería legible desde un fork.
  - **`permissions: contents: read`.** Por defecto un workflow puede escribir en el repositorio; dejarlo en sólo lectura reduce el daño si algo se ejecuta por error.
  - **`concurrency` cancela la ejecución anterior.** Sin eso, cinco pushes seguidos consumen cinco runners para comprobar el mismo commit cinco veces.
  - **`ci.yml` y `ci-integration.yml` están separados.** Las pruebas de integración levantan PostgreSQL y tardan: mezcladas con las unitarias, cada push tardaría minutos y la señal tardía es la menos útil.
  - **Dependabot agrupa por ecosistema.** Spring Boot, Flyway, Testcontainers y Jackson suben juntos: si el motor de Flyway y su módulo de Spring Boot se separan, las migraciones dejan de ejecutarse **y en silencio** (lo que pasó en US-003).
- **Pendiente que deja esta historia**
  - La ejecución real en los runners de GitHub **no se puede comprobar en local**: sólo se valida la estructura y el contenido. El primer `push` confirma el resto.
  - Métricas, alarmas y CloudWatch → US-081.

---

### 3.2 Épica EP-01 — Catálogo

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-010 | Consulta pública del catálogo | S2 | Alta | US-003, US-004 | 5 | Hecho |
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
- **Estado:** **Hecho** el 2026-10-17. Verificación con salida real (`./mvnw clean verify`: **108 unitarias + 40 de integración, 0 fallos**).
  1. ✅ `PizzaCatalogAdapterIT`: el catálogo público devuelve `Diavola, Margarita` y excluye `Bufala` (activa pero con su única variante agotada, RN-DS-02) y `Retirada` (dada de baja). `availableOnly=false` devuelve las cuatro. Variantes agotadas nunca se exponen.
  2. ✅ `PizzaControllerTest`: 200 con el envoltorio de página; 404 con `CATALOG_PIZZA_NOT_FOUND`; un UUID mal formado da 400 `MALFORMED_REQUEST`, no 404.
  3. ✅ Sin anotación de autorización y sin Spring Security en el proyecto: no hay token que exigir. La protección de `/admin/**` llega con US-021.
  4. ✅ `availableOnly` por defecto `true`, verificado por `verify(listarPizzas).listar(new PageRequest(0, 20), true)`.
  5. ✅ `price` es número JSON con escala 0 (`12500`) y `size` es `MEDIUM`/`SMALL`/`LARGE`.
  6. ✅ Paginación con `content`, `page`, `size`, `totalElements`, `totalPages`. Catálogo vacío → `200` con `content: []`; página fuera de rango → `200` vacía, no `404`.
- **El N+1 no ocurrió, y está probado que no**
  - `PizzaCatalogAdapterIT` cuenta las sentencias SQL con las estadísticas de Hibernate y exige un número **exacto**: **2** (las pizzas de la ventana y sus variantes) para páginas de 1, 3 y 4 pizzas. No se usa `join fetch` ni `@EntityGraph`, y la colección de variantes **no está mapeada** en `PizzaJpaEntity` a propósito: sin el mapeo, la carga perezosa que causaría el N+1 es imposible por construcción. El orden de tamaños se aplica en memoria porque `order by` sobre el enumerado ordenaría alfabéticamente.
  - `@DataJpaTest` **no se usó**: no encaja con un adaptador que devuelve objetos de dominio con dos consultas coordinadas. La alternativa es un `*IT` contra PostgreSQL real, que es lo que dice la convención del proyecto.
- **No hizo falta migración `V2`.** Comprobado con `EXPLAIN` sobre 50 000 pizzas: el plan usa `Index Scan using uq_pizzas_name_active` **y no hay nodo `Sort`**, es decir, el índice parcial existente sirve a la vez el filtro `active` y el orden `lower(name)`. El `exists` del filtro RN-DS-02 usa `uq_pizza_variants_pizza_size`. El índice compuesto que la historia anticipaba **no se creó** porque la consulta no lo necesita.
- **Decisiones tomadas aquí**
  - **`Money` se adelantó desde US-030.** US-010 ya devuelve precios, y crear el catálogo sin el objeto de valor obligaría a retrabajar los DTOs más adelante. `Money` es COP con escala 0 y `RoundingMode.UNNECESSARY` (RN-IM-06): un importe fraccionario se **rechaza** al construirlo, y hay 6 pruebas que lo demuestran.
  - **El puerto habla de límite y desplazamiento, no de páginas.** `PizzaCatalogPort` vive en `domain`, y la regla `elDominioNoDependeDeFrameworks` le prohíbe depender de nada ajeno; los tipos de paginación de la respuesta HTTP son de `application`. Alternativa descartada: un `Page` compartido en `domain/model/vo`, que metería en el núcleo un concepto que no es de negocio. Coste aceptado: contenido y total son dos llamadas, dentro de la misma transacción de sólo lectura.
  - **`availableOnly` es una vista, no un permiso.** El mismo endpoint sirve el catálogo público y la vista previa del administrador. Autorizarlo es trabajo de US-021…US-022, no de esta historia.
  - **`RN-IM-07` (precio mayor que cero) NO se comprueba en `PizzaVariant`.** La ruta que puede producir un precio inválido es la API de administración, y su código de error llega con US-012. Aquí sólo se lee, y la base ya lo garantiza con `chk_pizza_variants_price`. Anotado en el propio dominio para que no parezca un olvido.
  - **Los literales de `VariantSize` deben coincidir con `chk_pizza_variants_size`.** Por eso el enum se serializa como `EnumType.STRING` y hay una prueba que documenta que el orden natural **no** es el alfabético.
- **Contrato documentado en Swagger, verificado contra la app corriendo**
  - `@Tag`, `@Operation` y `@ApiResponses` en `PizzaController`. **Sin ellas springdoc publicaba las rutas pero dejaba el 404 y el 400 sin documentar, y llamaba a la operación `listar`** —un identificador interno en el campo que se usa para generar clientes—. El tag era `pizza-controller`, no `Catálogo`.
  - Verificado con peticiones HTTP reales contra la app del laboratorio local, no leyendo el código: `GET /api/v1/pizzas` → **200** con el catálogo real, y `/v3/api-docs` → `paths` con `/api/v1/pizzas` y `/api/v1/pizzas/{pizzaId}`.
  - `operationId` `listPizzas` y `getPizza`; respuestas `200, 400` y `200, 400, 404`; esquemas `PageResponsePizzaResponse`, `PizzaResponse`, `VariantResponse`, `ProblemDetail`.
  - El `200` **no** declara `implementation` a propósito: al declararlo a mano se pierde el parámetro genérico y el esquema sale como `PageResponse` sin saber qué contiene. Se deduce del tipo de retorno.
  - **4 pruebas nuevas en `OpenApiHabilitadoIT`** que fijan que las rutas están, que cada operación documenta sus errores y que ninguna exige token. Motivo: un `paths` vacío **no falla**: `/v3/api-docs` responde 200 con un contrato aparentemente válido, y así es como se habría colado.
- **Trabajo pendiente que esta historia deja**
  - Sin `openapi.json` versionado: sigue siendo US-045.
  - `imageUrl` en la respuesta: US-052.
  - Los endpoints de administración: US-011, US-012, US-013.

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
- **Tareas técnicas:** agregado `Order` + `OrderItem` + `OrderStatus` (máquina de estados), `Money` en `domain/model/vo/`, `PlaceOrderUseCase` (`application/usecase`) + `PlaceOrderService` (`application/service`, `@Transactional`), puertos `OrderRepositoryPort` y `PricingCatalogPort` en `domain/port/out/`, `OrderController` en `infrastructure/adapter/in/web/`, `CreateOrderRequest`, mappers y entidades JPA en `infrastructure/adapter/out/persistence/`.
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
  - ✅ **Criterio de aprobación cumplido el 2026-10-17**: `requirements-v1.0.md` aprobado y congelado por el responsable del proyecto, con las 15 decisiones ratificadas. Este criterio ya no es trabajo pendiente de US-044; queda registrarlo como cumplido. **Pendiente:** los 6 ADR que faltan (0001, 0002, 0003, 0007, 0008, 0009) y los diagramas.

#### US-045 — Contrato de API congelado

- **Criterios de aceptación:** `api-contract.md` con todos los endpoints, cuerpos, códigos y errores; snapshot de OpenAPI versionado en `docs/architecture/openapi.json`; test de contrato que falla si el OpenAPI generado cambia sin actualizar el documento.

---

### 3.6 Épica EP-05 — Integración con S3 (imágenes)

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-050 | Puerto de almacenamiento y adaptador local por defecto | S6 | Media | US-030 | 5 | Pendiente |
| US-051 | Adaptador S3 verificado contra el emulador | S6 | Media | US-050 | 8 | Pendiente |
| US-052 | Imágenes de pizza gestionadas por el administrador | S6 | Media | US-051, US-011 | 8 | Pendiente |

- **US-050:** puerto `ImageStoragePort` (`store` / `presign` / `delete`) en `domain/port/out/` con adaptador por defecto local o "no-op" activo **sin** AWS; test ArchUnit que prohíbe el SDK de AWS en `domain` y `application`; el backend debe arrancar sin AWS.
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
  - **El ensayo completo es posible en local** (documentado el 2026-10-11): Floci emula ECR con un **registro OCI real** detrás, así que `docker push` funciona contra él sin configurar el demonio. Y emula **EC2** lanzando un contenedor Docker real por instancia, con límites de CPU y memoria. Eso permite probar el ciclo `build → push → lanzar → hablar con S3/SQS` **antes** de tocar AWS real. Ver [`docs/development/aws-local.md`](../development/aws-local.md) §5. Sigue exigiendo autorización para cualquier recurso real.
- **US-072:** presupuesto y alarma de coste **antes** de crear nada; rol de IAM de mínimo privilegio y sin claves estáticas en el repositorio; script de destrucción documentado y ejecutado al terminar. **No se ejecuta sin autorización expresa del responsable.**
- **US-073:** ADR con coste aproximado, carga operativa, escalado y criterio de migración. **Sin crear recursos.**

---

### 3.9 Épica EP-08 — Observabilidad e infraestructura

| ID | Título | Sprint | Prioridad | Depende de | Puntos | Estado |
|---|---|---|---|---|---|---|
| US-080 | Actuator, salud y métricas | S1 + S5 | Media | US-001, US-002 | 3 | En curso |
| US-081 | Observabilidad en la nube | S9 | Baja | US-043, US-072 | 5 | Bloqueada |
| US-082 | Evolución a RDS (evaluación y migración) | S9 | Baja | US-072 | 8 | Bloqueada |
| US-083 | Frontend estático en S3 + CloudFront | Fuera del MVP | Fuera de alcance | Existe frontend | 5 | Descartada (MVP) |
| US-084 | Estado final del portafolio | S9 | Media | Todas | 5 | Pendiente |

- **US-080:** `/actuator/health` con detalle de base de datos; métricas; endpoints sensibles restringidos por perfil o rol; test de que `/actuator/env` no está expuesto en `prod`.
  - **S1 cerrado 2026-10-11**: `/actuator/health` responde 200 con el detalle de PostgreSQL. `OpenApiDeshabilitadoIT` comprueba que, **sin** perfil (el que se parece a producción), `/actuator/env` → 404 y que los enlaces de `/actuator` sólo incluyen `health`, `info` y `metrics`, sin `env`, `configprops`, `beans`, `loggers`, `heapdump`, `threaddump` ni `mappings`. La parte de S5 (métricas publicadas, restricciones por rol) sigue abierta.
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
| Microservicios | Monolito ([ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md)) |
| Rate limiting | Se resuelve en el borde; anotado como deuda |
| Clave de idempotencia en creación de pedido | Anotado como deuda |
| Caducidad automática de pedidos `PENDING` | Sin módulo de temporizadores en el MVP |

---

## 5. Registro de cambios

| Fecha | Cambio |
|---|---|
| 2026-10-18 | **El catálogo pasa de `UUID` a `BIGINT`: `GET /api/v1/pizzas/1` en vez de un UUID de 36 caracteres.** Decisión del responsable. Sólo el catálogo: `pizzas.id`, `pizza_variants.id`, `pizza_variants.pizza_id` y `order_items.variant_id`. Clientes, pedidos y pagos **siguen con `UUID`**, porque son datos que nacen en el servidor y nadie teclea. **Migración `V2__catalogo_con_identificadores_bigint`** (V1 no se toca: una migración aplicada no se edita) que **conserva las filas**: tabla de equivalencia con `row_number()`, columna nueva al lado, `DROP` de la vieja y renombrado. Un `DROP COLUMN` a pelo habría sido más corto y habría borrado el catálogo del laboratorio. Se eligieron **secuencias y no `IDENTITY`** porque el id lo genera la aplicación y existe antes de insertar. Dominio, aplicación, persistencia y web con `long`; invariante nuevo: **el id tiene que ser positivo**, imposible de comprobar con UUID, y con `PizzaVariantTest` como fichero nuevo. **176 pruebas en verde: 132 unitarias + 44 de integración.** Verificado sobre el laboratorio **con datos**: Flyway aplicó V2 sobre las 6 pizzas ya sembradas, las renumeró sin perder ninguna y la API responde con `"id": 2`. `SchemaMigrationIT.elCatalogoUsaBigintYLosPedidosUuid` fija que el cambio se aplique a todo el catálogo y a ninguno del resto. Actualizados: `api-contract.md`, `db/migration/README.md`, `datos-dev.sql` y el README de la colección. Riesgo asumido: los identificadores del catálogo dejan de ser globalmente únicos, así que **US-030 no puede acepta** un `order_items.variant_id` que apunte a otra pizza sólo por su id. |
| 2026-10-18 | **Colección de Postman para probar la API a mano, y las reglas para que crezca con ella.** `api/bruno/pizzeria.postman_collection.json` (Postman v2.1) organiza por recurso —`Sistema`, `Catálogo de pizzas`, `Correlación`—, con una petición por caso, nombre corto en español, descripción de una línea y la **respuesta real guardada como ejemplo**, que es lo que se ve en Postman sin ejecutar. 18 peticiones sobre los 2 endpoints de US-010. **4 pruebas en `ColeccionPostmanTest`** la atan al desarrollo: JSON importable, ninguna URL con el host escrito a mano, toda variable usada declarada, toda petición con descripción y **todo endpoint `Hecho` en `api-contract.md` con su petición** —esta última es una versión barata de lo que hará US-045—. Regla añadida a `AGENTS.md` §7 y §9: endpoint nuevo o cambiado entra en la colección en el mismo commit. Prueba negativa hecha: cambiada la URL de la petición del detalle, el test falla nombrando la ruta que falta. Nota: el archivo se quedó a 0 bytes dos veces por un guardado externo (editor abierto sobre él), sin relación con estos cambios. |
| 2026-10-17 | **El laboratorio local dejó de poder levantarse, por dos motivos, ambos corregidos.** ① Una línea ajena se coló en `infra/local/.env` —parecía la salida de un comando de shell— y `docker compose` respondía `key cannot contain a space`, un mensaje que no dice qué línea es. El archivo está en `.gitignore` y ningún script del repositorio escribe en él, así que no se puede evitar el origen; lo que sí se hace es **avisar antes y con el número de línea**: validación en `dev-up.ps1` y **5 pruebas en `LocalEnvFilesTest`**, que comprueban la plantilla siempre y el `.env` local cuando existe. El mensaje **no imprime la línea**, porque un `.env` puede contener una contraseña: sólo el número y la longitud. ② `.env.example` **no terminaba en salto de línea**: lo que se añadiera al final se pegaba a `APP_PORT=8080` y Docker Compose leía `APP_PORT=8080<basura>` como una variable **válida, sin ningún error**, levantando el laboratorio con el puerto equivocado. Añadido el salto y una prueba que lo fija. ③ **Bug preexistente en `dev-up.ps1`**: con `$ErrorActionPreference = 'Stop'`, PowerShell 5.1 convierte en error terminante cualquier salida por stderr de un comando nativo, y `docker info` escribe avisos (`daemon is not using the default seccomp profile`). **El script abortaba antes de llamar a Docker.** Ahora la preferencia se restaura alrededor de las llamadas nativas y se juzga por `$LASTEXITCODE`. Verificado: `dev-up.ps1 -Detach` levanta desde cero, `app` healthy en 16 s, `GET /api/v1/pizzas` → 200 y `/v3/api-docs` con las 2 rutas. Prueba negativa hecha: corrupta la plantilla a propósito, el test falla con `linea 27 (29 caracteres)` **sin filtrar el token**. |
| 2026-10-17 | **US-010 → `Hecho`.** Primer código de negocio del proyecto y primero bajo capas globales. `GET /api/v1/pizzas` y `GET /api/v1/pizzas/{pizzaId}`, ambos públicos. **108 unitarias + 40 de integración, 0 fallos.** Se adelantó `Money` desde US-030 y el puerto quedó hablando en límite/desplazamiento para no meter paginación en el dominio. Sin migración: el índice parcial existente ya sirve el filtro y el orden, comprobado con `EXPLAIN`. El N+1 queda descartado y probado con un número **exacto** de sentencias. Contrato nuevo documentado: orden `name` asc, `size` 20 por defecto y máximo 100, `availableOnly=true` por defecto. Contadores de la tabla de épicas corregidos: **7 historias `Hecho` de 44**. |
| 2026-10-17 | **`requirements-v1.0.md` aprobado y congelado** por el responsable del proyecto. Se eliminaron las 12 marcas ⚙️ de las reglas de negocio y se ratificaron con sus valores por defecto las 15 decisiones (D1–D15), que en `decisions-pending.md` pasan a *Resuelta*. §11 pasó de «Decisiones pendientes que bloquean la aprobación» a «Decisiones ratificadas en la aprobación», con la decisión definitiva de cada una; §12 registra responsable y fecha. **Ninguna historia queda bloqueada por ambigüedad.** D14 sigue siendo una restricción permanente: US-072, US-081 y US-082 no se ejecutan sin autorización escrita para AWS. S0 pasa de *En curso* a *Aprobado* en `roadmap.md`, con pendientes que no bloquean (6 ADR y los diagramas, ambos de US-044). El criterio «`requirements-v1.0.md` aprobado» de US-044 queda cumplido. |
| 2026-10-17 | **Documentos de planificación adaptados a las capas globales.** El cambio de estructura del 2026-10-15 había actualizado `AGENTS.md`, `architecture.md` y las convenciones, pero **no** los documentos de planificación, que seguían indicando `catalog/domain/…`, `identity/domain/…`, `orders/application/port/out/…` y `adapters/out/storage`. Corregidos `sprint-02` (completo, es el sprint activo), `sprint-03`, `sprint-04`, `sprint-06` y `sprint-07`; `sprint-00` y `sprint-01` anotados como registro histórico sin reescribir su cuerpo. Cambios de contenido, no sólo de rutas: **los puertos pasan a nombre `…Port`** (`PizzaCatalogPort`, `PricingCatalogPort`, `OrderRepositoryPort`, `PaymentRepositoryPort`, `CustomerRepositoryPort`, `PasswordEncoderPort`, `JwtTokenIssuerPort`, `ImageStoragePort`), las implementaciones de caso de uso pasan a `application/service/*Service`, y `PageRequest`/`PageResponse` a `application/dto` porque `application` **no puede importar `shared`** (`laAplicacionNoDependeDeInfrastructure`). Corregidos además enlaces rotos a clases movidas en `api-contract.md` (2) y `spring-conventions.md` (1), en `requirements-v1.0.md` (2), en `db/migration/README.md` (3), un enlace roto a ADR en `sprint-00.md`, un carácter corrupto (`U+3002`) en `adr/0005-…md` y los estados de S0 y S1 en `roadmap.md`. Sin cambios en el código. |
| 2026-10-15 | **Cambio de estructura de paquetes.** Sustituye al «paquete por módulo» de `AGENTS.md` §3.1. Se aplica **capas globales**: `com.pizzeria.{domain, application, infrastructure, shared}`. Registrado en [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md) y [ADR-0005](../adr/0005-los-puertos-se-declaran-en-el-dominio.md) (los puertos pasan de `application.port.out` a `domain.port`). Consecuencia aceptada: **se pierde el aislamiento entre módulos** (`catalog`, `identity`, `orders`, `payments`), que era una barrera del compilador. Las 6 clases existentes se han movido, `ArchitectureRulesTest` se ha reescrito con 8 pruebas (6 reglas de capa + 2 guardas, una de ellas nueva: `noHayPaquetesHuerfanos`) y se han actualizado `AGENTS.md`, `architecture.md`, `spring-conventions.md`, `conventions.md`, `aws-local.md`, `README.md` y `pom.xml`. Sin código de negocio afectado: a esta fecha no existe ninguna clase de `catalog`, `identity`, `orders` ni `payments`. |
| 2026-10-11 | US-006 → **Hecho**. `ci.yml` (verificar, secretos, compose), `ci-integration.yml`, `dependabot.yml` y `README` con distintivos. `WorkflowFilesTest` valida los tres ficheros con SnakeYAML. US-080 parcial de S1: `/actuator/env` no expuesto y sólo `health`/`info`/`metrics` públicos. |
| 2026-10-11 | US-005 → **Hecho**. OpenAPI publicado sólo en `local` y verificado con peticiones HTTP reales: 404 sin perfil, 200 con `local`, y el esquema `bearerAuth` leído del JSON generado. `api-contract.md` creado con el catálogo de 14 códigos de error. |
| 2026-10-11 | US-004 → **Hecho**. Errores en `problem+json` con `code` estable, `timestamp` y `traceId`, y 9 escenarios de prueba. El estado HTTP lo decide una tabla, no la excepción. `@WebMvcTest` y `ObjectMapper` han cambiado de paquete en Spring Boot 4. |
| 2026-10-11 | US-003 → **Hecho**. Las 8 tablas migradas por Flyway y 14 pruebas de integración sobre PostgreSQL real. Tres hallazgos: `spring-boot-flyway` es obligatorio en Boot 4 (sin él ninguna migración se ejecuta, en silencio); PostgreSQL rechaza expresiones en `UNIQUE` de tabla; y RN-IM-06 no lo puede cumplir la base, porque `NUMERIC(12,0)` redondea en silencio. Registrada la ambigüedad **D15** sobre dónde vive el estado del pago. |
| 2026-10-11 | US-002 → **Hecho**. `docker compose up --build` deja ambos servicios `healthy` en 60,8 s (frío) y 50,4 s (caliente). PostgreSQL sin puerto publicado en el host. `.env` generado desde `.env.example` y protegido por `.gitignore`. Scripts `dev-up.ps1` y `dev-down.ps1`. |
| 2026-10-11 | US-001 → **Hecho**. Spike de springdoc cerrado: 3.1.1 sobre Boot 4.1.1 compila y sirve `/v3/api-docs` en `local` y **404 sin perfil**. springdoc se apagaba por defecto en todos los entornos (incluido producción): corregido en `application.yml` + `application-local.yml`. |
| 2026-10-11 | US-001 pasa a **En curso**: criterios 1–7 verificados con salida real. Corregido el mensaje del Enforcer, que mostraba `Versión detectada: 21` al fallar con JDK 17 (`${java.version}` resolvía a la propiedad del `pom.xml`, no al JDK). |
| 2026-10-11 | Backlog creado. 44 historias, ≈253 puntos. Java 21 LTS fijado en [ADR-0010](../adr/0010-versiones-de-java-y-spring-boot.md) |