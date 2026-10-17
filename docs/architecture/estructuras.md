# Estructuras de arquitectura hexagonal y arquitectura global del proyecto

> **Qué es este documento:** una referencia para leer el repositorio de un vistazo. Muestra las **dos
> estructuras posibles** de la arquitectura hexagonal —la adoptada y la alternativa descartada— y
> después la **arquitectura de todo el proyecto**, desde el monorepo hasta los contenedores.
>
> **Qué NO es:** no es fuente de verdad. La estructura vigente es
> [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md); las reglas de dependencia son
> [`spring-conventions.md` §1](../development/spring-conventions.md) §1. Si este documento y esos
> documentos discrepan, **mandan ellos**.
>
> **Por qué existe:** la estructura por módulos y la estructura por capas se parecen pero no son lo
> mismo, y confundirlas cuesta días. Aquí están las dos, con lo que se gana y lo que se pierde en cada una.

---

# Parte 1 — Dos estructuras de arquitectura hexagonal

## 1.1 Qué es hexagonal, y qué no tiene nada que ver

La arquitectura hexagonal (Ports & Adapters) es **una sola cosa**: un contrato en el centro y
adaptadores que lo cumplen.

```text
            ┌──────────────────────┐
            │   MUNDO EXTERNO      │
            │  HTTP · BD · S3 · SQS│
            └───────────┬──────────┘
                        │  ← los adaptadores traducen el idioma del exterior
        ╔═══════════════╧═══════════════╗
        ║          EL HEXÁGONO          ║
        ║                               ║
        ║   ┌───────────────────────┐   ║
        ║   │   D O M I N I O       │   ║
        ║   │   modelo + reglas     │   ║
        ║   └───────────┬───────────┘   ║
        ║       ╔═══════╧═══════╗       ║
        ║       ║    PUERTOS   ║       ║  ← interfaces, no clases
        ║       ╚═══════╤═══════╝       ║
        ╚═══════════════╪═══════════════╝
                        │  ← las implementaciones cumplen el contrato
        ┌───────────────┴──────────────────────────┐
        │   A D A P T A D O R E S                  │
        │   entrada (HTTP) · salida (JPA, S3, SQS) │
        └──────────────────────────────────────────┘
```

Tres propiedades, y sólo tres:

| # | Propiedad | Cómo se comprueba en este repositorio |
|---|---|---|
| 1 | El dominio no depende de nadie | `ArchitectureRulesTest.elDominioNoDependeDeFrameworks` |
| 2 | El exterior entra y sale por puertos | `ArchitectureRulesTest.laAplicacionNoDependeDeInfrastructure` |
| 3 | Los adaptadores no se hablan entre sí | `ArchitectureRulesTest.losAdaptadoresDeEntradaNoConocenLaSalida` |

**No es hexagonal:** dividir el código en módulos de negocio (`catalog`, `orders`), usar DTOs,
tener una capa `service`, o ser un microservicio. Son cosas ortogonales que pueden convivir con
hexagonal o no existir.

Por eso hay **dos** estructuras posibles, y las dos son hexagonales: lo que cambia es **dónde
se apila cada capa**.

---

## 1.2 Estructura A — Por capas globales ✅ ADOPTADA

**Elegida** en [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md). Un único `domain`, un
único `application`, una única `infrastructure` y un `shared` transversal.

```text
com.pizzeria.{domain, application, infrastructure, shared}
```

```text
backend/src/main/java/com/pizzeria/
│
├── PizzeriaApplication.java
│
├── domain/                          ◄── NÚCLEO. SÓlo importa java.*
│   ├── model/                       Pedido, LineaPedido, Cliente, Producto
│   │   └── vo/                      Email, Telefono, Precio
│   ├── exception/                   DomainException, ErrorCode, concretas
│   └── port/                        ◄── LOS PUERTOS
│       ├── in/                      interfaces de casos de uso
│       └── out/                     ClienteRepositoryPort, ImagenStoragePort…
│
├── application/                     ◄── ORQUESTA
│   ├── usecase/                     interfaces de casos de uso
│   ├── service/                     implementaciones (@Transactional)
│   └── dto/                         DTOs de casos de uso
│
├── infrastructure/                  ◄── ADAPTA
│   ├── adapter/
│   │   ├── in/web/                  controllers, filtros, DTOs de transporte
│   │   └── out/
│   │       ├── persistence/         *JpaAdapter + entity/ mapper/ repository/
│   │       ├── storage/             S3ImagenAdapter + config/ (S3Config)
│   │       └── messaging/           adaptadores SQS (a partir de S7)
│   └── config/                      SecurityConfig, JwtConfig, CorsConfig,
│                                    OpenApiConfig, ClockConfig, BeanConfig
│
└── shared/                          ◄── TRANSVERSAL
    ├── security/                    JwtService, JwtAuthenticationFilter
    ├── exception/                   GlobalExceptionHandler
    └── util/                        Mapper
```

### Dirección de dependencias

```text
infrastructure  ──►  application  ──►  domain
  (conoce a todos)   (orquesta)      (no conoce a nadie)

shared ─────────────► infrastructure  (traduce, registra, correlaciona)
```

| Capa | Puede importar | **Prohibido** |
|---|---|---|
| `domain` | `java.*` y nada más | Spring, Jakarta, JPA, Jackson, SDK AWS, Lombok |
| `application` | `domain`, `application` | `infrastructure`, `shared`, Spring Web/Data, Jackson, SDK AWS |
| `infrastructure.adapter.in.web` | `application`, Spring Web/Security/Jackson | `infrastructure.adapter.out.*`, `jakarta.persistence` |
| `infrastructure.adapter.out.persistence` | `domain.port.out`, `domain.model`, Spring Data | `infrastructure.adapter.in.*`, `application` |
| `infrastructure.adapter.out.storage` / `messaging` | `domain.port.out`, SDK AWS v2 | `infrastructure.adapter.in.*`, `domain.model`, `domain.exception`, JPA |
| `shared` | `domain.exception`, `shared`, `infrastructure` | `application`, `domain.model`, `domain.port`, `infrastructure.adapter.out` |

Y una regla de estructura que no es de dependencias pero se verifica igual
(`noHayPaquetesHuerfanos`): **bajo `com.pizzeria` sólo hay cuatro paquetes de primer nivel**.

### Qué se gana y qué se pierde

| | |
|---|---|
| ✅ **Gana** | La dirección de las dependencias se lee en el nombre del paquete. Las reglas de ArchUnit son seis, sin combinatoria de módulos. Quien llega al repositorio encuentra `domain` en el primer nivel sin saber de antemano en qué módulo vive un concepto. Los transversal tienen un sitio explícito en lugar de repetirse por todos los módulos. |
| ❌ **Pierde** | `Pedido`, `Cliente` y `Pizza` conviven en `domain.model`: ya no se puede expresar «pedidos no depende de catálogo» como regla del compilador. Ese límite pasa a ser una convención del Javadoc. Y `domain.model` crecerá sin subdivisión automática. |

---

## 1.3 Estructura B — Por módulos (bounded contexts) ❌ DESCARTADA

La estructura **anterior** de este repositorio, y la alternativa estándar cuando el dominio tiene
varias áreas de negocio claramente separables. Sigue siendo hexagonal: los módulos sólo cambian
dónde se apila cada capa.

```text
com.pizzeria.<módulo>.{domain, application, adapters.in.web, adapters.out.persistence, config}
```

```text
backend/src/main/java/com/pizzeria/
│
├── orders/                          ◄── módulo
│   ├── domain/                      Pedido, LineaPedido, OrderStatus
│   │   ├── exception/               PedidoInvalidoException
│   │   └── port/
│   │       ├── in/                  CrearPedidoUseCase
│   │       └── out/                 PedidoRepositoryPort
│   ├── application/                 CrearPedidoService, dto/
│   ├── adapters/
│   │   ├── in/web/                  PedidoController + DTOs
│   │   └── out/persistence/         PedidoJpaAdapter + entity/ mapper/ repository/
│   └── config/
│
├── catalog/                         ◄── módulo: pizzas y variantes
│   └── {domain, application, adapters.in.web, adapters.out.persistence, config}
│
├── identity/                        ◄── módulo: clientes y JWT
├── payments/                        ◄── módulo: cobro en efectivo
└── common/                          ◄── transversal
```

### Dirección de dependencias

```text
adapters.in.web  ──►  application  ──►  domain
adapters.out.*    ─────────────────────↗

orders.*  ✗  no puede importar  catalog.*     ← ESTA es la diferencia
```

| Capa | Puede importar | **Prohibido** |
|---|---|---|
| `…domain` | `java.*` | Spring, Jakarta, JPA, Jackson, SDK AWS |
| `…application` | `…domain` | `…adapters`, Spring Web/Data, Jackson, SDK AWS |
| `…adapters.in.web` | `…application` | `…adapters.out.*`, JPA |
| `…adapters.out.persistence` | `…port.out`, `…domain`, Spring Data | `…adapters.in.*` |
| `<otro módulo>` | — | **cualquier otro módulo**, salvo el declarado explícitamente |

### Qué se gana y qué se pierde

| | |
|---|---|
| ✅ **Gana** | El límite entre áreas de negocio es una **barrera del compilador**: si `orders` importa `catalog`, no compila. Cada módulo se puede leer entero sin salir de su carpeta. Si el dominio crece hacia servicios independientes, la ruta a extraer un módulo es directa. |
| ❌ **Pierde** | Para leer un caso de uso hay que saltar entre al menos tres carpetas (`orders.application.service` → `orders.domain.port.out` → `catalog.adapters.out.persistence`). Con seis módulos y 44 historias, las carpetas se multiplican y el mismo concepto (`Precio`, `Cliente`) vive en sitios distintos según quién lo use. Y obliga a aprender **dos** cosas —hexagonal y DDD modular— con el mismo nombre de carpeta. |

---

## 1.4 Comparación

| Pregunta | **A · Por capas** (adoptada) | **B · Por módulos** (descartada) |
|---|---|---|
| ¿Es hexagonal? | Sí | Sí |
| ¿Dónde está un caso de uso? | `application/service` | `orders/application/service` |
| ¿Un compilador impide que pedidos toque catálogo? | No | **Sí** |
| ¿Cuántas reglas de ArchUnit hay que mantener? | 6 + 1 de estructura | 5 + combinatoria de módulos |
| Coste de añadir una historia | Un fichero | Un fichero, pero hay que acertar el módulo |
| Coste de leer el proyecto entero | Crece en un solo eje | Crece en dos ejes |
| Mejor cuando… | el equipo es pequeño y quiere **aprender el patrón** | el dominio ya tiene contextos que se tocan y duelen |
| Peor cuando… | el dominio crece y `domain.model` pasa de ~15 clases | el dominio todavía no está estabilizado |

**Por qué A en este proyecto:** el objetivo declarado es aprender y aplicar arquitectura hexagonal, y
la estructura A hace que cada fichero pertenezca a una de las cinco categorías del hexágono sin una
capa intermedia. La estructura B es mejor más adelante, no antes; y recuperarla es un cambio local
(subpaquetes `domain.model.orders`) con una ADR nueva, no una reescritura.

---

# Parte 2 — Arquitectura de todo el proyecto

## 2.1 Qué es el sistema

Una pizzería de **una sola sede**, con pedidos **para recogida presencial** y **pago en efectivo**.
Sin envíos a domicilio, sin pasarelas de pago, sin múltiples ubicaciones.

| Decisión | Valor |
|---|---|
| Estilo | Monolito con arquitectura hexagonal por capas ([ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md)) |
| Unidad de despliegue | **Un único artefacto**: `pizzeria-backend.jar` |
| Fronteras de negocio | Contexto único por ahora; `ErrorCode` los nombra (`CATALOG_…`, `ORDERS_…`, `IDENTITY_…`, `PAYMENTS_…`) como convención |
| Moneda | **COP**, sin decimales: `BigDecimal` escala 0, `RoundingMode.UNNECESSARY`, `NUMERIC(12,0)` |
| Fuera del MVP | Frontend (US-003/US-083 descartada), AWS (S6–S9), despliegue en EC2 (bloqueado: requiere autorización escrita) |

## 2.2 El repositorio

```text
pizzeria/                       monorepo, sin submódulos
├── backend/                    la aplicación Spring Boot — único artefacto
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   └── src/
│       ├── main/java/com/pizzeria/     ◄── LA ESTRUCTURA HEXAGONAL (Parte 1, A)
│       │   ├── domain/ application/ infrastructure/ shared/
│       ├── main/resources/
│       │   ├── application.yml             base: sólo lo imprescindible
│       │   ├── application-local.yml       + Swagger y OpenAPI
│       │   ├── application-test.yml        + Testcontainers
│       │   └── db/migration/               Flyway, inmutable, sólo él crea el esquema
│       │       ├── V1__baseline_schema.sql
│       │       └── V2…__*.sql
│       └── test/java/com/pizzeria/
│           ├── architecture/          ArchitectureRulesTest — las reglas, como código
│           ├── domain/                tests puros, sin Spring
│           ├── application/           tests con dobles
│           └── infrastructure/        tests de integración, Testcontainers
├── frontend/                   placeholder declarado, fuera del MVP
├── infra/local/                Docker Compose, .env.example (nunca .env versionado)
├── scripts/                    dev-up.ps1 · dev-down.ps1
├── .github/
│   ├── workflows/              ci.yml · ci-integration.yml   ← §2.5
│   └── dependabot.yml          maven · docker · github-actions
├── docs/                       adr · architecture · development · planning · requirements
└── AGENTS.md                   reglas de desarrollo del repositorio
```

## 2.3 Topología en ejecución (entorno local)

```text
┌──────────────────────── MACHINE ─────────────────────────┐
│                                                           │
│   navegador                                               │
│      │  http://localhost:8080                             │
│      │  /api/v1/*      /swagger-ui   /actuator/health     │
│      ▼                                                    │
│  ┌──────────────── RED DE COMPOSE ─────────────────────┐  │
│  │                                                     │  │
│  │  ┌────────────┐              ┌───────────────────┐  │  │
│  │  │    app     │─────────────▶│       db          │  │  │
│  │  │  :8080     │   5432 int.  │  postgres:17      │  │  │
│  │  │            │              │  SIN puerto en    │  │  │
│  │  │ 8080:8080  │              │  el host          │  │  │
│  │  └────────────┘              └───────────────────┘  │  │
│  │       │ volumen                                      │  │
│  │  maven:3.10.0-                 postgres-data        │  │
│  │  temurin-21                   maven-cache          │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                           │
│   ◄── S3 (Floci) y SQS: previstos, NO están en el        │
│        compose. El perfil por defecto arranca sin AWS.   │
└───────────────────────────────────────────────────────────┘
```

| Decisión | Detalle |
|---|---|
| `db` **no publica puerto** | Sólo la app habla con él, por la red interna. Reduce superficie: nada del host alcanza la base |
| La app no arranca hasta que `db` está sana | `depends_on: condition: service_healthy`, con `pg_isready` |
| La app corre con imagen de Maven, no propia | Recarga código al instante. El Dockerfile multi-stage de producción es US-070 (S8) |
| Volúmenes con nombre | Los datos sobreviven a `docker compose down`; sólo `down -v` los destruye |

Arranque: `scripts/dev-up.ps1` · parada: `scripts/dev-down.ps1`.

## 2.4 De la capa hexagonal a la base de datos

Cada tabla tiene un dueño en el dominio. El mapeo no es decorativo: es la lista de trabajo de las
historias de S2–S4.

| Tabla | Modelo de dominio | Puerto de salida | Adaptador |
|---|---|---|---|
| `customers` · `customer_roles` | `domain.model.Cliente` | `ClienteRepositoryPort` | `ClienteJpaAdapter` |
| `pizzas` · `pizza_variants` | `domain.model.Producto` | `ProductoRepositoryPort` | `ProductoJpaAdapter` |
| `orders` · `order_items` · `order_status_history` | `domain.model.Pedido` · `LineaPedido` | `PedidoRepositoryPort` | `PedidoJpaAdapter` |
| `cash_payments` | `domain.model.PagoEfectivo` | `PedidoRepositoryPort` | `PedidoJpaAdapter` |
| *(futuro)* | `domain.model.Imagen` | `ImagenStoragePort` | `S3ImagenAdapter` |

Reglas que cruzan toda la fila:

- **El servidor calcula todos los importes.** El cliente nunca envía ni decide precio unitario ni total.
- **El pedido guarda una foto histórica**: nombre, tamaño, precio unitario, cantidad e importe por línea.
- `NUMERIC(12,0)`: la igualdad `line_total = unit_price * quantity` es exacta.
- La propiedad del pedido se verifica **en la consulta** (`findByIdAndCustomerId`), no comparando después.

## 2.5 El pipeline de CI/CD

El CI **ya está construido y en verde** (cerrado en US-006). El CD **no existe todavía**: es S8, y su
pieza de despliegue está bloqueada. Conviene separarlos porque son dos decisiones distintas.

```text
   push a master  ┐
   pull_request   ┴─►  ¿qué se dispara?

                          ┌─────────────────────────── ci.yml ───────────────────────────┐
                          │                                                              │
   respuesta rápida ──────►│  verify    ──►  ./mvnw -B -ntp verify  ──►  JAR + cobertura   │
   (~2 min)                │  secrets   ──►  Gitleaks sobre el historial completo          │
                          │  compose   ──►  docker compose up --wait + curl /health       │
                          │                + /v3/api-docs 200 + /actuator/env 404        │
                          └──────────────────────────────────────────────────────────────┘
                          ┌─────────────────────── ci-integration.yml ─────────────────────┐
                          │                                                              │
   respuesta definitiva ──►│  integration ─►  ./mvnw verify contra PostgreSQL real (Failsafe) │
   (~1 min, Docker)        │  cobertura    ─►  publica el informe de JaCoCo                │
                          └──────────────────────────────────────────────────────────────┘

                                      ╳  CD: imagen + despliegue
                                          Sprint 8 · US-070…US-073
                                          US-072 requiere autorización escrita
```

### Por qué dos workflows y no uno

| Decisión | Motivo |
|---|---|
| `ci.yml` responde en ~2 min, `ci-integration.yml` da la señal definitiva | Las pruebas de integración levantan PostgreSQL con Testcontainers: tardan y consumen recursos. Si compartieran job, cada push tardaría minutos y **la señal tardía es la menos útil** |
| **Sin** trigger `workflow_run` entre ellos | Ya fue un error: los dos se disparan con `push` y `pull_request`, así que `workflow_run` sólo duplicaba. Y como el grupo de `concurrency` era el mismo, las ejecuciones duplicadas **se cancelaban entre sí** (seis «cancelled» en un push real). El resultado era correcto porque una terminaba bien, pero se quemaban arranques de runner y el panel quedaba lleno de ruido que parece un fallo |

### Los tres jobs de `ci.yml`

| Job | Qué hace | Por qué ese detalle |
|---|---|---|
| **`verify`** | `setup-java` Temurin **21** con `cache: maven`, instala `unzip`, y corre `./mvnw -B -ntp verify` en `backend/` | El Enforcer ya falla con otro JDK, pero se fija `java-version` igualmente: compilar con 25 y probar con 21 es exactamente el «funciona en mi máquina» que evita ADR-0010. El `unzip` hace falta porque el wrapper valida el SHA-256 y en imágenes mínimas no está |
| **`secrets`** | Gitleaks con `fetch-depth: 0` | Sin historial completo, sólo ve el commit actual: un secreto mergeado la semana pasada pasaría el escaneo sin que nadie se entere |
| **`compose`** | Levanta el entorno local documentado con `--wait` y lo comprueba con `curl` | Un pipeline que no se ejecuta parece un pipeline que pasa. Además comprueba los criterios de US-005 en CI y no sólo en local: `/v3/api-docs` responde 200 y `/actuator/env` responde 404 |

### Las cuatro decisiones que hacen que esto sea seguro

| Decisión | Qué previene |
|---|---|
| `pull_request` además de `push` | Los forks no tienen acceso a los secretos ni disparan el push de su rama. Es la **única** forma de comprobar un PR que viene de fuera |
| `permissions: contents: read` | Por defecto un workflow puede escribir en el repositorio. Sólo lectura reduce el daño si algo se ejecuta por error |
| `concurrency` con `cancel-in-progress` | Cinco pushes seguidos no consumen cinco runners para comprobar el mismo commit cinco veces |
| El job `compose` usa `.env.example`, **nunca** un secreto | Si el workflow usara un secreto, un fork podría leerlo al abrir un `pull_request`. `WorkflowFilesTest` lo verifica: el único secreto permitido es `GITHUB_TOKEN`, que emite GitHub por ejecución |

### Actualizaciones automáticas

`dependabot.yml` cubre los tres ecosistemas: **`maven`**, **`docker`** y **`github-actions`**.
Actualiza el parche, nunca la línea de versión: subir de línea es una decisión humana y exige ADR.

### Lo que aún no hay (Sprint 8)

| Historia | Qué falta | Estado |
|---|---|---|
| **US-070** | Imagen multi-stage `eclipse-temurin:21-jre`, usuario no root, tag fijo | Pendiente. Hoy la app corre con la imagen de Maven |
| **US-071** | Etapa de despliegue reproducible, etiqueta de versión y artefacto trazable | Pendiente |
| **US-072** | Despliegue en **EC2** (contenedor) | 🔴 **Bloqueado**: requiere autorización escrita del responsable ([`architecture.md` §2.7](architecture.md), [`backlog.md`](../planning/backlog.md)) |
| **US-073** | Tests de humo y de rollback contra el entorno desplegado | Pendiente |

Mientras US-072 no esté autorizado, el pipeline **se detiene en `verify`**: produce un JAR y una
cobertura, y no publica nada. Esa es una decisión consciente, no un olvido.

## 2.6 De compilar a desplegar

| Etapa | Herramienta | Qué ocurre |
|---|---|---|
| Build | Maven 3.10.0 (wrapper), Java 21 LTS, `--release 21` | Compila, unitarios, integración, empaqueta. `./mvnw verify` |
| Unitarios | JUnit 6 · Mockito 5 (`@MockitoBean`) · ArchUnit | Dominio puro, casos de uso con dobles, **reglas de capa** (`ArchitectureRulesTest`) |
| Integración | Testcontainers 2.0 · PostgreSQL 17 real | Nunca H2. `SchemaMigrationIT` aplica Flyway desde cero y verifica las 8 tablas |
| Esquema | **Sólo Flyway**. `ddl-auto=validate` | Una migración aplicada no se edita jamás: se añade otra |
| Entregable | `target/pizzeria-backend.jar` | Artefacto único. Subido 7 días en cada ejecución |
| Cobertura | JaCoCo 0.8.15 | Informe por paquete. **Umbrales por capa: US-044 (S5)**, no antes |

## 2.7 Cómo leer el repositorio en 10 segundos

```text
1.  docs/requirements/requirements-v1.0.md   ← qué tiene que hacer (la verdad del negocio)
2.  docs/planning/backlog.md                ← qué falta y en qué orden
3.  backend/src/test/java/…/architecture/    ← cómo se ROMPE si alguien rompe las reglas
4.  backend/src/main/java/com/pizzeria/
      domain/          ¿qué es un pedido válido?
      application/     ¿qué operaciones hay y cómo se orquestan?
      infrastructure/  ¿cómo entra HTTP y cómo sale hacia PostgreSQL?
      shared/          ¿cómo se traduce un fallo a un 404?
5.  .github/workflows/                       ← qué se comprueba antes de que nada llegue a master
```

Si una historia toca las cuatro carpetas, la historia es demasiado grande.

---

## Registro de cambios

| Fecha | Cambio |
|---|---|
| 2026-10-15 | Documento creado al aplicar [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md). Estructura A vigente; la B queda registrada como alternativa evaluada con su coste y su beneficio. |
| 2026-10-15 | Añadido el §2.5 **Pipeline de CI/CD** a petición del responsable, y §2.7 (leer el repositorio). El CI de `ci.yml` e `ci-integration.yml` se documenta como construido; el CD se documenta como pendiente en S8, con US-072 marcado como bloqueado. |