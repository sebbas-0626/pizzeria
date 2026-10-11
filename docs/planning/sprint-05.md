# Sprint 05 — Calidad, documentación y endurecimiento — **fin del MVP**

> **Estado:** Planificado
> **Objetivo:** endurecer, documentar y medir. Al terminar, el proyecto es **demostrable**.
> **Historias:** US-040, US-041, US-042, US-043, US-044, US-045, US-080 (cierre)
> **Puntos:** 34
> **Documento efímero:** al terminar, lo relevante pasa al backlog y a los documentos de arquitectura.

---

## 1. Por qué este sprint existe

Un sistema con la funcionalidad terminada pero sin pruebas de autorización sistemáticas, sin garantía de
atomicidad probada y sin documentación no es presentable. Este sprint es el que convierte «funciona» en
«se puede explicar y demostrar».

---

## 2. Historias del sprint

| ID | Título | Puntos | Resultado |
|---|---|---|---|
| US-040 | Base de pruebas de integración con Testcontainers | 5 | Pruebas de integración rápidas y fiables |
| US-041 | Matriz de pruebas de autorización | 8 | Cada endpoint, con cada rol, verificado |
| US-042 | Pruebas de atomicidad y concurrencia | 5 | RN-PG-05 y RN-PG-06 demostradas |
| US-043 | Logs estructurados y correlación | 3 | Diagnóstico sin tocar el código |
| US-044 | Documentación y diagramas | 8 | El proyecto se entiende sin Asking |
| US-045 | Contrato de API congelado | 5 | El contrato no cambia por accidente |
| US-080 | Actuator, salud y métricas | 0 | Cierre de la parte begun en S1 |

---

## 3. US-040 — Base de pruebas de integración

### Tareas

- [ ] Clase base con el contenedor PostgreSQL reutilizado entre clases
- [ ] `~/.testcontainers.properties` documentado en `README`
- [ ] Migraciones de Flyway aplicadas antes de cada prueba
- [ ] Estrategia de aislamiento: rollback por prueba o limpieza explícita
- [ ] `application-test.yml` con `ddl-auto=validate`
- [ ] Fichero de datos de prueba en `src/test/resources`
- [ ] Mensaje claro cuando Docker no está disponible (fallar, **no** saltar)

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | El contenedor se levanta una vez para todas las clases |
| 2 | Cada prueba parte del esquema migrado |
| 3 | Ninguna prueba depende del orden ni de otra prueba |
| 4 | Sin Docker, la prueba **falla** con un mensaje explicativo |
| 5 | Tiempo de ejecución del conjunto de integración por debajo de lo acordado |

---

## 4. US-041 — Matriz de pruebas de autorización

### Tareas

- [ ] Inventario de rutas: método + ruta + rol requerido
- [ ] Prueba parametrizada sobre ese inventario
- [ ] Escenarios: anónimo, `CUSTOMER` propietario, `CUSTOMER` ajeno, `ADMIN`, token caducado

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | La matriz cubre los 17 endpoints de la API |
| 2 | Pedido ajeno en lectura → 404 |
| 3 | Pedido ajeno en cancelación → 404 |
| 4 | Ruta admin sin token → 401 |
| 5 | Ruta admin con `CUSTOMER` → 403 |
| 6 | Token caducado → 401 |
| 7 | **La matriz falla si se añade un endpoint nuevo sin actualizar** |

> El criterio 7 es el que da valor a la prueba: convierte la matriz en una lista de verificación
> obligatoria del contrato de seguridad, en lugar de un documento que se queda obsoleto.

---

## 5. US-042 — Pruebas de atomicidad y concurrencia

### Tareas

- [ ] Prueba de fallo entre la escritura del pago y el cambio de estado
- [ ] Prueba de dos llamadas concurrentes a `complete-pickup`
- [ ] Consulta de verificación: ningún pedido `DELIVERED` sin fila en `cash_payments`
- [ ] Prueba de concurrencia también para confirmación y cancelación simultáneas

### Criterios de aceptación

| # | Criterio | Expresa |
|---|---|---|
| 1 | Fallo intermedio → ni cobro ni cambio de estado persistidos | RN-PG-05, RN-PG-06 |
| 2 | Dos llamadas simultáneas → un 204 y un 409, una sola fila de cobro | RN-PG-07, RN-PG-08 |
| 3 | Ningún `DELIVERED` sin cobro | RN-PG-05 |
| 4 | Confirmación y cancelación simultáneas → una sola gana, la otra recibe 409 | RN-ES-04 |

---

## 6. US-043 — Logs estructurados y correlación

### Tareas

- [ ] Salida JSON en `local` y `prod`
- [ ] `X-Trace-Id` de entrada o generación, propagado y devuelto
- [ ] Patrón de log que **redacta** `password`, `token`, `authorization`
- [ ] Prueba que captura el log de `/auth/login` y comprueba que no contiene la contraseña ni el JWT
- [ ] Hitos de negocio con SLF4J parametrizado

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | Log JSON con identificador de correlación |
| 2 | El identificador viaja en la respuesta |
| 3 | El patrón redacta los campos sensibles |
| 4 | Prueba que lo demuestra sobre un caso real |

---

## 7. US-044 — Documentación y diagramas

### Tareas

- [ ] `01-use-cases.drawio` — casos de uso por rol y módulo
- [ ] `02-order-state-machine.drawio` — estados, transiciones y actores
- [ ] `03-hexagonal-architecture.drawio` — capas, módulos y puertos
- [ ] `04-data-model.drawio` — entidad-relación
- [ ] `05-local-infrastructure.drawio` — laboratorio local
- [ ] `06-aws-target.drawio` — arquitectura objetivo
- [ ] `07-sequence-place-order.drawio` — desde S3
- [ ] `08-sequence-complete-pickup.drawio` — desde S4
- [ ] Exportar el SVG de cada uno
- [ ] ADR-0001 a ADR-0009 redactados
- [ ] `architecture.md`, `data-model.md`, `security.md` actualizados
- [ ] `README.md` con: qué es, cómo arrancar, stack con versiones justificadas, diagrama embebido, endpoints principales y estado del proyecto
- [ ] Actualizar el diagrama si el código cambió

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | Los 8 diagramas existen en `.drawio` y en SVG |
| 2 | Cada diagrama lleva fecha, historia y ADR de referencia |
| 3 | Los ADR están redactados y aceptados |
| [ ] | El README permite a un tercero arrancar el proyecto sin Asking |
| 5 | Cobertura medida y publicada (número real, no estimado) |

---

## 8. US-045 — Contrato de API congelado

### Tareas

- [ ] `api-contract.md` con los 17 endpoints: cuerpos, respuestas y errores
- [ ] Snapshot `openapi.json` versionado
- [ ] Prueba de contrato que compara el OpenAPI generado con el snapshot
- [ ] Semántica de `PATCH`, paginación, `Location` y códigos documentados

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | `api-contract.md` refleja toda la API |
| 2 | El snapshot está versionado |
| 3 | **La prueba falla si el OpenAPI generado cambia sin actualizar el documento** |

---

## 9. US-080 — Actuator, salud y métricas (cierre)

### Tareas

- [ ] `/actuator/health` con detalle de la base de datos
- [ ] Métricas estándar disponibles
- [ ] Endpoints sensibles restringidos por perfil o por rol
- [ ] Prueba que verifica que `/actuator/env` no está expuesto en `prod`

---

## 10. Criterio de salida del sprint — **el MVP está terminado**

- [ ] Cobertura medida: dominio ≥ 80 %, casos de uso ≥ 70 %, **revisando qué está probado, no sólo cuánto**
- [ ] Matriz de autorización verde y completa
- [ ] Pruebas de atomicidad y concurrencia verdes
- [ ] Los 8 diagramas y sus SVG están en el repositorio y coinciden con el código
- [ ] ADR-0001 … ADR-0010 redactados
- [ ] `README.md` permite a un tercero clonar, arrancar y probar el proyecto
- [ ] `api-contract.md` y `openapi.json` congelados y verificados por prueba
- [ ] `gitleaks detect` sin hallazgos
- [ ] `./mvnw verify` en verde con la salida reportada
- [ ] `backlog.md` con US-040 … US-045 y US-080 en `Hecho`
- [ ] `requirements-v1.0.md` aprobado

### Hito

> **H5 — MVP demostrable.** A partir de aquí el proyecto puede presentarse, defenderse y entenderse
> sin ayuda externa. Los sprints siguientes amplían el alcance, pero el proyecto ya está completo.

---

## 11. Dependencias y riesgos

| | |
|---|---|
| **Depende de** | Sprint 04 |
| **Bloquea a** | Sprint 06 |
| **Riesgo** | La matriz de autorización se escribe al final y no cubre endpoints nuevos → se exige el criterio 7 |
| **Riesgo** | Perseguir el porcentaje de cobertura en lugar de revisar qué se prueba |
| **Riesgo** | Documentar por encima del código real → cada documento se escribe leyendo el código, no de memoria |
| **Riesgo** | Gastar el sprint en pulir documentación y no en endurecer pruebas | La prioridad es US-041 y US-042 antes que US-044 |

---

## 12. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| — | — | — | Sprint no iniciado |