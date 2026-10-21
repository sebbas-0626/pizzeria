# Sprint 02 — Catálogo, variantes e identidad

> **Estado:** En curso · US-010 y US-020 `Hecho` · US-021, US-022 (mitad) y US-011…US-013 pendientes
> **Objetivo:** el catálogo se lee públicamente y se gestiona en privado por el administrador.
> **Historias:** US-010, US-020, US-021, US-022 (regla de `/admin/**`), US-011, US-012, US-013
> **Puntos:** 34
> **Documento efímero:** al terminar, lo relevante pasa al backlog y a la documentación de arquitectura.
>
> **Estructura de paquetes: capas globales**, según [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md).
> Este es el **primer sprint que escribe código de negocio** y por eso es el primero que aplica la estructura
> por capas. Las tareas de `sprint-03.md`…`sprint-07.md` se corrigieron el mismo día por el mismo motivo.
>
> **⚠️ Orden cambiado el 2026-10-18** (ver el registro de cambios de [`backlog.md`](backlog.md)). Se
> adelantaron **US-020 y US-021**, y con ellas la regla de rol de **US-022**, desde S4. No es un cambio de
> gusto: el criterio 3 de US-011 pide un 403 sin ADMIN, y sin Spring Security **`/api/v1/admin/**`
> quedaría abierto a cualquiera** con sólo conocer la URL. Un endpoint sin proteger es peor que uno que
> aún no existe.
>
> US-022 se **parte**: aquí entra la regla por rol (401 sin token, 403 con rol insuficiente) y en S4 queda
> la de propiedad del recurso (pedido ajeno → 404), que necesita los pedidos.

---

## 1. Historias del sprint

| ID | Título | Puntos | Riesgo principal |
|---|---|---|---|
| US-010 | Consulta pública del catálogo | 5 | ✅ Hecho · Consultas N+1 al cargar variantes |
| US-020 | Registro de cliente | 5 | ✅ Hecho · Que el registro público permita crear ADMIN |
| US-021 | Autenticación y emisión de JWT | 8 | Caducidad y filtrado del token; la clave no puede versionarse |
| US-022 | Autorización por roles (mitad de la historia) | ~2 | Que una ruta quede fuera de la regla y quede abierta |
| US-011 | Alta y edición de pizzas (admin) | 5 | Semántica de `PATCH` con campos ausentes |
| US-012 | Alta y edición de variantes (admin) | 5 | Restricción de unicidad por tamaño |
| US-013 | Baja lógica del catálogo | 3 | Que alguien añada un `DELETE` sin darse cuenta |

**Nada de identidad entra aquí sin decisión previa:** D11 y D12 ya estaban ratificadas el 2026-10-17, así
que se implementa lo aprobado —RS256, 15 minutos, sin refresh token, y registro con `201` del recurso sin
token— sin volver a abrir la discusión.

---

## 2. US-010 — Consulta pública del catálogo

### Tareas

Paquetes según [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md) y puertos según
[ADR-0005](../adr/0005-los-puertos-se-declaran-en-el-dominio.md). Ningún archivo de este sprint se llama
`catalog/…`: el nombre del módulo desaparece de los paquetes, **no** de los códigos de error (`CATALOG_…`).

- [x] `domain/model/Pizza.java`, `PizzaVariant.java`, `VariantSize.java` — `US-010`
- [x] `domain/exception/CatalogException.java` y excepciones derivadas (`PizzaNotFoundException`) — `US-010`
  · **Desviación:** no hay `CatalogException`; la raíz ya existe y es `DomainException`, así que una clase
  intermedia que sólo existiera para colgar de ella no respondería a ninguna de las tres preguntas de
  `AGENTS.md` §3.3.
- [x] `domain/port/out/PizzaCatalogPort.java` (puerto de salida) — `US-010`
- [x] `application/usecase/ListPizzasUseCase.java`, `GetPizzaUseCase.java` (interfaces) — `US-010`
- [x] `application/service/ListPizzasService.java`, `GetPizzaService.java` (`@Transactional(readOnly = true)`) — `US-010`
- [x] `application/dto/PageRequest.java` y `PageResponse.java` — `US-010` · **Desviación:** `PageResponse` es
  de transporte y vive en `infrastructure/adapter/in/web/dto/`, no en `application`. `application/dto` sólo
  tiene `PageRequest` y `PizzaPage`.
- [x] `infrastructure/adapter/out/persistence/` — entidad JPA, repositorio Spring Data, **adaptador** que implementa el puerto y mappers (`entity/`, `repository/`, `mapper/`) — `US-010`
- [x] `infrastructure/adapter/in/web/PizzaController.java` + DTOs `record` + mapper — `US-010`
- [x] Índice compuesto en `pizzas (active, name)` **si la consulta lo necesita** — `US-010`
  · **No hizo falta:** comprobado con `EXPLAIN` sobre 50 000 pizzas, el plan usa
  `Index Scan using uq_pizzas_name_active` y **no hay nodo `Sort`**. El índice parcial que ya existía
  sirve el filtro y el orden. No se creó ninguna migración `V2`.

> **Dos decisiones de esta lista que conviene no deshacer**
>
> 1. **`PageRequest` y `PageResponse` no están en `shared/util`.** `ArchitectureRulesTest` prohíbe que
>    `application` importe `shared`; si la paginación viviera ahí, el servicio no podría usarla y la regla
>    fallaría en el build. `PageRequest` es de `application/dto` y `PageResponse` de la capa web, que es
>    quien decide el JSON.
> 2. **Los identificadores van en inglés** (`Pizza`, `PizzaVariant`, `PizzaCatalogPort`), según
>    [`conventions.md`](../development/conventions.md) §2. El árbol de ADR-0004 §3 es **ilustrativo** y usa
>    nombres en español (`Pedido`, `Precio`): no es una licencia para crear clases en español.

### Criterios de aceptación

| # | Criterio | Verificación | Estado |
|---|---|---|---|
| 1 | `GET /api/v1/pizzas` devuelve página de pizzas activas con sus variantes disponibles | Prueba web e integración | ✅ |
| 2 | `GET /api/v1/pizzas/{id}` → 200 o 404 | Prueba web | ✅ |
| 3 | Sin autenticación | Prueba web sin token | ✅ |
| 4 | Filtro `?availableOnly=true` por defecto en el catálogo público | Prueba de integración | ✅ |
| 5 | `price` decimal y `size` ∈ {`SMALL`, `MEDIUM`, `LARGE`} | Revisión del JSON | ✅ |
| 6 | Paginación con `totalElements` y `totalPages` | Prueba web | ✅ |

### Pruebas requeridas

| Prueba | Tipo | Nota | Estado |
|---|---|---|---|
| `ListPizzasServiceTest` | Unitario, sin Spring | Doble de `PizzaCatalogPort` | ✅ 6 |
| `GetPizzaServiceTest` | Unitario | Caso «no existe» incluido | ✅ 3 |
| `PizzaControllerTest` | `@WebMvcTest` | 200, 404, paginación y validación | ✅ 13 |
| `PizzaCatalogAdapterIT` | Integración con Testcontainers | **Verifica que no hay N+1** | ✅ 16 |
| `ArchitectureRulesTest` | ArchUnit | Sigue verde tras añadir las capas | ✅ 8 |
| `MoneyTest` | Unitario puro | RN-IM-06: importe fraccionario se rechaza | ✅ 17 |
| `VariantSizeTest` | Unitario puro | Literales del esquema y orden natural | ✅ 14 |
| `PizzaTest` | Unitario puro | `esPedible()` y lista defensiva | ✅ 8 |
| `PageRequestTest` | Unitario puro | Límites del contrato | ✅ 23 |

> Las pruebas de caso de uso apuntan a la **implementación** (`application/service`), no a la interfaz: la
> interfaz de `application.usecase` no tiene comportamiento que probar y además `@InjectMocks` no puede
> instanciar una interfaz.

### Riesgo: N+1 — cerrado y probado

La carga de pizzas con sus variantes era el caso clásico. Está resuelto por diseño y verificado por
`PizzaCatalogAdapterIT`, que cuenta las sentencias SQL con las estadísticas de Hibernate y exige un número
**exacto**: **2** (las pizzas de la ventana y sus variantes), para páginas de 1, 3 y 4 pizzas. No se usa
`join fetch` ni `@EntityGraph`, y la colección de variantes **no está mapeada** en `PizzaJpaEntity`: sin el
mapeo, la carga perezosa que causaría el N+1 es imposible por construcción.

Se descartó `datasource-proxy` para contar consultas: Hibernate ya las cuenta, y añadir una dependencia
para eso no respondería a ninguna de las tres preguntas de `AGENTS.md` §3.3.

---

## 3. US-011 — Alta y edición de pizzas (admin)

### Tareas

- [ ] `application/usecase/CreatePizzaUseCase.java`, `UpdatePizzaUseCase.java` + `application/service/CreatePizzaService.java`, `UpdatePizzaService.java`
- [ ] Comandos de aplicación con validación en `application/dto`
- [ ] `infrastructure/adapter/in/web/AdminPizzaController.java` con `@Valid`
- [ ] `CreatePizzaRequest`, `UpdatePizzaRequest` (campos opcionales) como DTOs de transporte
- [ ] Regla de unicidad de nombre entre pizzas activas
- [ ] Pruebas de autorización con `@MockitoBean` (nunca `@MockBean`)

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `POST /api/v1/admin/pizzas` → 201 con cabecera `Location` | Prueba web |
| 2 | `PATCH /api/v1/admin/pizzas/{id}` actualiza sólo los campos presentes | Prueba de integración: el resto queda intacto |
| 3 | Sin ADMIN → 403 · Identificador inexistente → 404 | Prueba web parametrizada |
| 4 | Nombre obligatorio, 3–80 caracteres | Prueba web con `@Valid` |
| 5 | Nombre duplicado entre pizzas activas → 409 | Prueba de integración |

### Nota sobre `PATCH`

Campo **ausente** = no se modifica. Campo presente con valor = se modifica. No se admite `null` explícito
como instrucción de borrado; para vaciar un campo hay que definir qué significa y documentarlo. Esta
semántica se escribe en `api-contract.md` y se prueba.

---

## 4. US-012 — Alta y edición de variantes (admin)

### Tareas

- [ ] `application/usecase/CreateVariantUseCase.java`, `UpdateVariantUseCase.java` + implementaciones en `application/service`
- [ ] `infrastructure/adapter/in/web/AdminVariantController.java`
- [ ] Validación de precio positivo (dominio) y rango (DTO)
- [ ] Test que demuestra que cambiar el precio no altera pedidos previos

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `POST /admin/pizzas/{pizzaId}/variants` → 201 | Prueba web |
| 2 | `PATCH /admin/variants/{variantId}` cambia precio y disponibilidad | Prueba de integración |
| 3 | Tamaño duplicado en la misma pizza → 409 | Prueba de integración (`UNIQUE (pizza_id, size)`) |
| 4 | Precio ≤ 0 → 422 | Prueba unitaria del dominio y prueba web |
| 5 | ⚙️ **Cambiar el precio no altera pedidos previos** | Prueba de integración que compara el `unit_price` antes y después |

> El criterio 5 implementa RN-FH-02. En este sprint se puede comprobar con una fila de pedido insertada
> por el script de pruebas, aunque el módulo de pedidos todavía no exista.

---

## 5. US-013 — Baja lógica del catálogo

### Tareas

- [ ] Desactivación de pizza y variante mediante `PATCH`
- [ ] **Test que enumera las rutas registradas y falla si aparece cualquier `DELETE` en el catálogo**
- [ ] Actualizar el catálogo público para excluir lo inactivo

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `PATCH` con `active=false` y `available=false` | Prueba de integración |
| 2 | **No existe ningún endpoint `DELETE` en el catálogo** | Test de rutas |
| 3 | Un pedido que contiene la variante sigue siendo legible | Prueba de integración |
| 4 | La variante desactivada desaparece del catálogo público | Prueba de integración |

---

## 6. Archivos creados o modificados

```text
backend/src/main/java/com/pizzeria/
├── domain/
│   ├── model/            Pizza, PizzaVariant, VariantSize
│   ├── model/vo/         Money                                        (US-010)
│   ├── exception/        PizzaNotFoundException                        (US-010)
│   └── port/out/         PizzaCatalogPort                              (US-010)
├── application/
│   ├── usecase/          ListPizzasUseCase, GetPizzaUseCase            (US-010)
│   ├── service/          ListPizzasService, GetPizzaService            (US-010)
│   └── dto/              PageRequest, PizzaPage                        (US-010)
└── infrastructure/
    ├── adapter/in/web/       PizzaController, dto/{PizzaResponse,PageResponse}, mapper/
    └── adapter/out/persistence/  entity/, repository/, PizzaCatalogAdapter, mapper/

backend/src/test/
├── java/com/pizzeria/domain/model/       PizzaTest, VariantSizeTest, vo/MoneyTest
├── java/com/pizzeria/application/        dto/PageRequestTest, service/{ListPizzas,GetPizza}ServiceTest
├── java/com/pizzeria/infrastructure/     adapter/in/web/PizzaControllerTest,
│                                         adapter/out/persistence/PizzaCatalogAdapterIT
└── resources/catalogo-datos.sql          (las 4 pizzas del IT, determinista)

docs/architecture/api-contract.md     (§4.1 El catálogo público)
```

> No hay `config/` de módulo: si hiciera falta algún bean de configuración del catálogo, va en
> `infrastructure/config/BeanConfig.java`, que es donde vive el resto. No hizo falta ninguno: los
> adaptadores se descubren por `@Repository`.
>
> **No se creó `shared/util/Mapper`.** El árbol de ADR-0004 lo menciona, pero una interfaz genérica de
> conversión sólo tiene sentido si existe una razón de negocio para la cambiar, y aquí no la hay: hay dos
> mappers concretos y ninguno se sustituye. `AGENTS.md` §3.3 manda sobre el árbol ilustrativo del ADR.

---

## 7. Criterio de salida del sprint

- [ ] Un ADMIN crea una pizza con dos variantes y las ve en el catálogo público
- [ ] Un CUSTOMER no puede crear ni modificar pizzas (403)
- [ ] El precio de una variante nunca es menor o igual que cero, ni por API ni por base de datos
- [x] La prueba de N+1 está en verde
- [ ] El test que prohíbe `DELETE` en el catálogo está en verde
- [x] `api-contract.md` refleja los 6 endpoints de catálogo — **2 de 6**: los públicos. Quedan los 4 de administración
- [ ] `./mvnw verify` en verde con la salida reportada — verde tras US-010: 108 + 40, 0 fallos
- [ ] US-010…US-013 marcadas como `Hecho` en el backlog — **US-010 sí**

---

## 8. Dependencias y riesgos

| | |
|---|---|
| **Depende de** | Sprint 01 (build, base de datos, errores, pruebas de integración) |
| **Bloquea a** | Sprint 03 (los pedidos necesitan precio del catálogo) |
| **Riesgo** | Reutilizar la entidad JPA como modelo de dominio por comodidad → rompe RN y el test ArchUnit |
| **Riesgo** | Olvidar el filtro de disponibilidad en el listado público → variantes no disponibles visibles |
| **Riesgo** | Crear `catalog/` «porque es más cómodo agrupar» → contradice ADR-0004 y falla `noHayPaquetesHuerfanos` |

---

## 9. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| 2026-10-18 | **US-020** | **Hecho** | ./mvnw clean verify: **160 unitarias + 49 de integración, 0 fallos**. POST /api/v1/auth/register → 201 sin token (D11). El rol no existe en el DTO, lo fija Customer.registrar y mandarlo devuelve 400. spring-security-crypto (cifrado sin autoconfigurar seguridad) y ail-on-unknown-properties global. Verificado con 6 peticiones HTTP reales contra el laboratorio |
| 2026-10-18 | — (planificación) | — | **Orden cambiado por decisión del responsable**: US-020 y US-021 se adelantan de S4, y con ellas la regla por rol de US-022, para que `/api/v1/admin/**` **no exista nunca sin protección**. US-022 se parte: la propiedad del recurso (404) sigue en S4 con los pedidos. Puntos 16 → 34. Ninguna decisión de negocio modificada: D11 y D12 ya estaban ratificadas. Sin código |
| 2026-10-17 | **US-010** | **Hecho** | `./mvnw clean verify`: **108 unitarias + 40 de integración, 0 fallos**. 2 endpoints públicos, `Money` adelantado desde US-030, puerto en `domain/port/out` hablando en límite/desplazamiento. N+1 descartado con un número exacto de 2 sentencias. Sin migración: `EXPLAIN` sobre 50 000 pizzas confirma que `uq_pizzas_name_active` sirve filtro y orden. Desviaciones de la lista de tareas anotadas en §2 y §6 |
| 2026-10-17 | — | — | Corregidas las rutas de este documento y de `sprint-03`…`sprint-07`: el sprint se planificó con la estructura por **módulo** (`catalog/domain/…`, `catalog/application/port/out/…`), sustituida por **capas globales** el 2026-10-15 ([ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md), [ADR-0005](../adr/0005-los-puertos-se-declaran-en-el-dominio.md)). Sin historia empezada. |
| — | — | — | Sprint no iniciado |