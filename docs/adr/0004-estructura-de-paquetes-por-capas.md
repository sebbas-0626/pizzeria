    # ADR-0004 — Estructura de paquetes por capas, no por módulo

- **Estado:** Aceptada
- **Fecha:** 2026-10-15
- **Decidido por:** responsable del proyecto
- **Sprint:** S1 — ejecuta la sustitución de una decisión de S0 aún no redactada
- **ADRs relacionados:** ADR-0002 (transacciones en la capa de aplicación), ADR-0003 (dominio separado de JPA), ADR-0005 (dónde viven los puertos), ADR-0008 (monolito modular)
- **Decide:** la forma de los paquetes del código fuente bajo `com.pizzeria`
- **Sustituye a:** la decisión de paquete por módulo registrada en `sprint-00.md` §4 y en `AGENTS.md` §3.1 hasta el 2026-10-15

---

## 1. Contexto

Hasta esta fecha la estructura era **módulo primero**: `com.pizzeria.<módulo>.{domain, application, adapters.in.web, adapters.out.persistence, config}`, con los módulos `catalog`, `identity`, `orders`, `payments` y `common`.

El responsable revisó el árbol de destino y lo considera más completo y más fácil de leer. Pide **capas globales**: un único `domain`, un único `application`, una única `infrastructure` y un `shared` transversal.

El cambio se aplica ahora, antes de que exista una sola clase de negocio, porque después costaría un orden de magnitud más: las 44 historias del backlog aún no han escrito nada de `catalog`, `identity`, `orders` ni `payments`.

## 2. Opciones evaluadas

| Opción | Forma | Veredicto |
|---|---|---|
| **A** | **Capas globales** (`domain` · `application` · `infrastructure` · `shared`) | **Elegida** |
| B | Módulo primero (`orders.domain`, `catalog.application`, …) | Descartada:Preferred por el responsable, pero se pierde el aislamiento entre módulos |
| C | Híbrido: capas globales con subpaquetes por módulo debajo (`domain.model.orders`) | Descartada: es B con más niveles de indirección; no aporta nada que B no dé |

## 3. Decisión

**El código se organiza por capas globales.** Cuatro paquetes de primer nivel bajo `com.pizzeria`, y nada más:

```
infrastructure ──► application ──► domain
  (conoce a todos)   (orquesta)     (no conoce a nadie)

shared  (capa transversal: traduce, registra, correlaciona)
```

Árbol resultante:

```text
com/pizzeria/
├── PizzeriaApplication.java
├── domain/                      ◄── NÚCLEO. Sólo java.*
│   ├── model/                   Cliente, Producto, Pedido, LineaPedido
│   ├── exception/               DomainException, ErrorCode, excepciones concretas
│   └── port/
│       ├── in/                  Puertos de entrada (los implementa application)
│       └── out/                 ClienteRepositoryPort, ProductoRepositoryPort,
│                               PedidoRepositoryPort, ImagenStoragePort
├── application/                 ◄── ORQUESTACIÓN
│   ├── usecase/                 Interfaces de casos de uso
│   ├── service/                 Implementaciones (@Transactional)
│   └── dto/                     DTOs de casos de uso
├── infrastructure/              ◄── ADAPTADORES
│   ├── adapter/in/web/          Controllers y filtros
│   ├── adapter/out/
│   │   ├── persistence/         *JpaAdapter, entity/, mapper/, repository/
│   │   ├── storage/             S3ImagenAdapter
│   │   │   └── config/          S3Config, SqsConfig  (clientes del SDK de AWS)
│   │   └── messaging/           Adaptadores de SQS (a partir de S7)
│   └── config/                  SecurityConfig, JwtConfig, CorsConfig,
│                               OpenApiConfig, ClockConfig, BeanConfig
└── shared/                      ◄── TRANSVERSAL
    ├── security/                JwtService, JwtAuthenticationFilter, UserDetailsServiceImpl
    ├── exception/               GlobalExceptionHandler
    └── util/                    Mapper
```

La lista de paquetes de primer nivel permitidos **es normativa** y la verifica `ArchitectureRulesTest.noHayPaquetesHuerfanos`. Un quinto paquete de primer nivel es un fallo de build.

## 4. Consecuencias

### Aceptadas conscientemente

| # | Consecuencia | Por qué se acepta |
|---|---|---|
| 1 | **Se pierde el aislamiento entre módulos.** `Pedido`, `Cliente` y `Producto` conviven en `domain.model` y el dominio ya no puede expresar «orders no depende de catalog» como regla de compilación | El monolito sigue siendo un monolito (ADR-0008). La frontera entre módulos pasa a ser una convención del Javadoc y del nombre del paquete de los tests, no una barrera del compilador |
| 2 | **`shared` es una capa transversal que conoce a `infrastructure`.** `shared.exception.GlobalExceptionHandler` lee `TraceIdFilter` | Se acepta porque `shared` sólo traduce: su regla (`ArchitectureRulesTest.sharedNoConoceElNucleo`) le prohíbe conocer `application`, `domain.model`, `domain.port` y `infrastructure.adapter.out` |
| 3 | **Más clases y más indirección**: un caso de uso son dos tipos (interfaz `…UseCase` + implementación `…Service`) | El coste se paga una vez por historia y compra un punto de extensión único para las pruebas y para `BeanConfig` |
| 4 | Los paquetes crecen sin límite: `domain.model` acabará con todas las entidades | Se acepta. Si alguno llega a veinte clases, la subdivisión es una decisión posterior con su propia ADR |

### Ventajas

| # | Ventaja |
|---|---|
| 1 | La dirección de las dependencias es **visible en el nombre del paquete**. `adapters` son adaptadores, no módulos |
| 2 | Las reglas de ArchUnit se leen como la arquitectura: una regla por capa, sin combinatoria de módulos |
| 3 | Quien llega al repositorio encuentra `domain` en el primer nivel y no tiene que saber de antemano en qué módulo vive un concepto |
| 4 | Los transversal (`shared`) tienen un sitio explícito, en lugar de repartirse por todos los módulos |

### Neutras

- La entidad JPA sigue sin ser el modelo de dominio y los mappers siguen existiendo: eso lo decide [ADR-0003](0003-dominio-separado-de-jpa.md), no esta ADR.
- La frontera transaccional sigue en la capa de aplicación: [ADR-0002](0002-transacciones-en-la-capa-de-aplicacion.md).
- El **nombre del módulo desaparece de los paquetes, pero no de los códigos de error**: `ErrorCode` sigue siendo `CATALOG_…`, `ORDERS_…`, `IDENTITY_…`, `PAYMENTS_…`. Es parte del contrato público y no se toca (ver `api-contract.md`).

## 5. Aplicación en el repositorio

| Paquete anterior | Paquete actual |
|---|---|
| `com.pizzeria.common.domain` | `com.pizzeria.domain.exception` |
| `com.pizzeria.common.config` | `com.pizzeria.infrastructure.config` |
| `com.pizzeria.common.web.TraceIdFilter` | `com.pizzeria.infrastructure.adapter.in.web` |
| `com.pizzeria.common.web.GlobalExceptionHandler` | `com.pizzeria.shared.exception` |
| `com.pizzeria.common.web` (resto) | `com.pizzeria.infrastructure.adapter.in.web` |

Documentos actualizados en el mismo cambio: `AGENTS.md` §3, `architecture.md` §1, `spring-conventions.md` §1–§3.

## 6. Verificación

```powershell
# Las seis reglas de capa, más las dos guardas anti-vacuidad
./mvnw -Dtest=ArchitectureRulesTest test

# El dominio no arrastra framework y los adaptadores no se cruzan
./mvnw verify
```

`noHayPaquetesHuerfanos` es la prueba que hace útil esta ADR: si alguien crea `orders.domain` «sólo para esta historia», el build falla en vez de dejar la arquitectura erosionada en silencio.

## 7. Registro

| Fecha | Cambio |
|---|---|
| 2026-10-15 | Decidida y aplicada. Sustituye a la estructura por módulo. |
