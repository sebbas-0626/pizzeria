# Sprint 02 — Catálogo y variantes

> **Estado:** Planificado
> **Objetivo:** el catálogo se lee públicamente y se gestiona en privado por el administrador.
> **Historias:** US-010, US-011, US-012, US-013
> **Puntos:** 16
> **Documento efímero:** al terminar, lo relevante pasa al backlog y a la documentación de arquitectura.

---

## 1. Historias del sprint

| ID | Título | Puntos | Riesgo principal |
|---|---|---|---|
| US-010 | Consulta pública del catálogo | 5 | Consultas N+1 al cargar variantes |
| US-011 | Alta y edición de pizzas (admin) | 5 | Semántica de `PATCH` con campos ausentes |
| US-012 | Alta y edición de variantes (admin) | 5 | Restricción de unicidad por tamaño |
| US-013 | Baja lógica del catálogo | 3 | Que alguien añada un `DELETE` sin darse cuenta |

---

## 2. US-010 — Consulta pública del catálogo

### Tareas

- [ ] `catalog/domain/Pizza.java`, `PizzaVariant.java`, `VariantSize.java`
- [ ] `catalog/domain/CatalogException.java` y excepciones derivadas
- [ ] `catalog/application/port/out/PizzaRepository.java` (puerto)
- [ ] `catalog/application/ListPizzasUseCase.java`, `GetPizzaUseCase.java`
- [ ] `catalog/adapters/out/persistence/` — entidad JPA, repositorio Spring Data, **adaptador** que implementa el puerto y mappers
- [ ] `catalog/adapters/in/web/PizzaController.java` + DTOs `record` + mapper
- [ ] `common/domain/PageRequest.java` y `common/domain/PageResponse.java`
- [ ] Índice compuesto en `pizzas (active, name)` si la consulta lo necesita

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `GET /api/v1/pizzas` devuelve página de pizzas activas con sus variantes disponibles | Prueba web e integración |
| 2 | `GET /api/v1/pizzas/{id}` → 200 o 404 | Prueba web |
| 3 | Sin autenticación | Prueba web sin token |
| 4 | Filtro `?availableOnly=true` por defecto en el catálogo público | Prueba de integración |
| 5 | `price` decimal y `size` ∈ {`SMALL`, `MEDIUM`, `LARGE`} | Revisión del JSON |
| 6 | Paginación con `totalElements` y `totalPages` | Prueba web |

### Pruebas requeridas

| Prueba | Tipo | Nota |
|---|---|---|
| `ListPizzasUseCaseTest` | Unitario, sin Spring | Doble del puerto |
| `GetPizzaUseCaseTest` | Unitario | Caso «no existe» incluido |
| `PizzaControllerTest` | `@WebMvcTest` | 200, 404, paginación |
| `PizzaRepositoryAdapterIT` | Integración con Testcontainers | **Verifica que no hay N+1** |
| `ArchitectureRulesTest` | ArchUnit | Sigue verde tras añadir el módulo |

### Riesgo: N+1

La carga de pizzas con sus variantes es el caso clásico. La prueba de integración debe contar las
consultas ejecutadas (por ejemplo, con un datasource envolvente o con `datasource-proxy`) y fallar si
hay más de las esperadas. Sin esa comprobación, el defecto aparece cuando ya hay datos reales.

---

## 3. US-011 — Alta y edición de pizzas (admin)

### Tareas

- [ ] `CreatePizzaUseCase`, `UpdatePizzaUseCase`
- [ ] Comandos de aplicación con validación
- [ ] `AdminPizzaController` con `@Valid`
- [ ] `CreatePizzaRequest`, `UpdatePizzaRequest` (campos opcionales)
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

- [ ] `CreateVariantUseCase`, `UpdateVariantUseCase`
- [ ] `AdminVariantController`
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
catalog/
├── domain/          Pizza, PizzaVariant, VariantSize, excepciones
├── application/     casos de uso + port/out/PizzaRepository
├── adapters/
│   ├── in/web/      PizzaController, AdminPizzaController, dto/, mappers
│   └── out/persistence/  entidades JPA, repositorio Spring Data, adaptador, mappers
└── config/          CatalogBeanConfiguration

docs/architecture/api-contract.md     (sección de catálogo)
docs/architecture/data-model.md       (detalle de pizzas y variantes)
```

---

## 7. Criterio de salida del sprint

- [ ] Un ADMIN crea una pizza con dos variantes y las ve en el catálogo público
- [ ] Un CUSTOMER no puede crear ni modificar pizzas (403)
- [ ] El precio de una variante nunca es menor o igual que cero, ni por API ni por base de datos
- [ ] La prueba de N+1 está en verde
- [ ] El test que prohíbe `DELETE` en el catálogo está en verde
- [ ] `api-contract.md` refleja los 6 endpoints de catálogo
- [ ] `./mvnw verify` en verde con la salida reportada
- [ ] US-010…US-013 marcadas como `Hecho` en el backlog

---

## 8. Dependencias y riesgos

| | |
|---|---|
| **Depende de** | Sprint 01 (build, base de datos, errores, pruebas de integración) |
| **Bloquea a** | Sprint 03 (los pedidos necesitan precio del catálogo) |
| **Riesgo** | Reutilizar la entidad JPA como modelo de dominio por comodidad → rompe RN y el test ArchUnit |
| **Riesgo** | Olvidar el filtro de disponibilidad en el listado público → variantes no disponibles visibles |

---

## 9. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| — | — | — | Sprint no iniciado |