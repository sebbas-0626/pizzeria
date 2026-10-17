# ADR-0005 — Los puertos se declaran en el dominio

- **Estado:** Aceptada
- **Fecha:** 2026-10-15
- **Decidido por:** responsable del proyecto
- **Sprint:** S1
- **ADRs relacionados:** ADR-0002 (transacciones en la capa de aplicación), ADR-0003 (dominio separado de JPA), ADR-0004 (estructura de paquetes por capas)
- **Decide:** en qué paquete se declaran las interfaces que son puertos de entrada y de salida
- **Sustituye a:** la decisión de declarar los repositorios en `application.port.out`, registrada en `sprint-00.md` §4 y en `AGENTS.md` §3.2 hasta el 2026-10-15

---

## 1. Contexto

La arquitectura hexagonal separa dos cosas que en un proyecto pequeño acaban en el mismo sitio:

- el **dominio**, que dice qué es un pedido válido;
- la **aplicación**, que dice qué operaciones se pueden pedir y en qué orden se orquestan.

Ambas necesitan nombres para lo que el mundo exterior les ofrece. Un repositorio de pedidos no es un detalle de infraestructura: es una promesa del dominio («puede haber pedidos que no están en memoria»). Dónde se escribe esa promesa cambia lo que el compilador sabe y lo que las reglas de dependencia pueden verificar.

## 2. Opciones evaluadas

| Opción | Interfaz en | Veredicto |
|---|---|---|
| **A** | **`domain.port.out` / `domain.port.in`** | **Elegida.** Es el heart de la aplicación: el dominio declara qué necesita, los adaptadores cumplen |
| B | `application.port.out` | Descartada al sustituirse por [ADR-0004](0004-estructura-de-paquetes-por-capas.md). Era coherente con la estructura anterior por módulo, pero en una estructura por capas deja el `application` con una subcarpeta que sólo contiene interfaces |
| C | Interfaz dentro de la clase que la consume | Descartada: cada consumidor declara su propia forma del repositorio y las implementaciones dejan de ser sustituibles |
| D | `domain.repository` sin subpaquete `port` | Descartada: no distingue entrada de salida y se mezcla con clases de negocio |

## 3. Decisión

**Las interfaces de los puertos viven en el dominio**, y su implementación, en infraestructura.

```text
domain/port/
├── in/    Interfaces de casos de uso.  Las implementa application/service.
└── out/   Interfaces de repositorio y de servicios externos.
           Las implementa infrastructure/adapter/out/*.
```

| Puerto | Declarado en | Implementado por |
|---|---|---|
| `ClienteRepositoryPort` | `domain.port.out` | `infrastructure.adapter.out.persistence.ClienteJpaAdapter` |
| `ProductoRepositoryPort` | `domain.port.out` | `infrastructure.adapter.out.persistence.ProductoJpaAdapter` |
| `PedidoRepositoryPort` | `domain.port.out` | `infrastructure.adapter.out.persistence.PedidoJpaAdapter` |
| `ImagenStoragePort` | `domain.port.out` | `infrastructure.adapter.out.storage.S3ImagenAdapter` |
| `RegistrarClienteUseCase` | `application.usecase` | `application.service.ClienteService` |

**Los puertos de salida son interfaces, siempre.** Un caso de uso que depende de `PedidoRepositoryPort` se puede probar con un doble; uno que depende de una clase concreta de Spring Data, no.

**Un puerto de salida devuelve `Optional` y lanza excepciones. Nunca `null`** ([`conventions.md` §5](../development/conventions.md)).

## 4. Consecuencias

| # | Consecuencia |
|---|---|
| 1 | Un puerto de salida **no** puede devolver una entidad JPA ni un `Page` de Spring Data: son tipos de infraestructura. Si el puerto necesita paginar, pagina con su propia Expresión (`PageRequest`, `PageResult`), que es un tipo del dominio |
| 2 | La regla de dominio (`..domain..` sólo depende de `java..` y de `..domain..`) sigue valiendo: una interfaz en `domain.port.out` no puede importar nada |
| 3 | `infrastructure.adapter.out.persistence` depende de `domain.port.out` y de `domain.model`, y **nunca** de `application`: el adaptador implementa el contrato, no el caso de uso |
| 4 | `application.service` depende de `domain.port.out` y de `application.usecase`, y **nunca** de `infrastructure` |
| 5 | Un puerto de salida que sólo usa un caso de uso sigue siendo un puerto: no se declara dentro del caso de uso, porque en el momento en que lo es deja de ser sustituible |

## 5. Verificación

Las reglas que hacen cumplir esta ADR son las de [ADR-0004](0004-estructura-de-paquetes-por-capas.md) §6, ejecutadas por `ArchitectureRulesTest`:

| Regla | Qué garantiza |
|---|---|
| `elDominioNoDependeDeFrameworks` | Un puerto en `domain.port.out` no puede importar Spring Data |
| `losAdaptadoresDeSalidaNoConocenLaEntrada` | El adaptador de persistencia no ve los casos de uso |
| `laAplicacionNoDependeDeInfrastructure` | El caso de uso no puede saltarse el puerto e inyectarse el adaptador |
| `losAdaptadoresDeInfraestructuraEstanAislados` | S3 y SQS no tocan `domain.model` ni `domain.exception` |

```powershell
./mvnw -Dtest=ArchitectureRulesTest test
```

## 6. Registro

| Fecha | Cambio |
|---|---|
| 2026-10-15 | Decidida y aplicada: los puertos pasan de `application.port.out` a `domain.port` |