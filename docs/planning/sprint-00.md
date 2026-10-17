# Sprint 00 — Requisitos y diseño

> **Estado:** ✅ **Aprobado** el 2026-10-17 · requisitos y decisiones cerrados
> **Objetivo:** congelar las reglas de negocio y las decisiones técnicas antes de escribir una línea de código.
> **Historias:** transversales (no son historias de código)
> **Puntos:** —
> **Documento efímero:** al terminar, lo relevante pasa a `requirements-v1.0.md`, a los ADR y al backlog.
>
> **Pendiente sin bloquear a nadie:** 6 ADR (0001, 0002, 0003, 0007, 0008, 0009) y las 8 páginas `.drawio`.
> Los recoge como entregables US-044 en el Sprint 05.

---

## 0. Por qué existe este sprint

Escribir código antes de decidir el modelo de estados del pedido, quién puede hacer qué y qué significa
«entregar un pedido» es la forma más rápida de construir algo que después hay que rehacer. Este sprint
produce las decisiones; los siguientes las ejecutan.

---

## 1. Tareas

### 1.1 Requisitos

- [x] Analizar los requisitos funcionales y no funcionales del negocio
- [x] Identificar contradicciones, ambigüedades y decisiones pendientes
- [x] Redactar `docs/requirements/requirements-v1.0.md` con identificadores `RF-`, `RN-` y `RNF-`
- [x] Documentar la trazabilidad requisito → historia
- [ ] **Obtener la aprobación del responsable** y congelar el documento

### 1.2 Decisiones pendientes

- [x] Recoger las 14 decisiones abiertas en `docs/planning/decisions-pending.md`
- [ ] Obtener respuesta a **D1 (moneda)** — bloquea el esquema de la base de datos
- [ ] Obtener respuesta al resto (D2–D14)
- [ ] Aplicar los valores confirmados y eliminar las marcas ⚙️ de los requisitos

### 1.3 Arquitectura

- [x] Definir arquitectura hexagonal ( monolito modular, ADR-0008 — *pendiente de redactar*; stack en [ADR-0010](../adr/0010-versiones-de-java-y-spring-boot.md))
- [x] Definir reglas de dependencia verificables ([`spring-conventions.md`](../development/spring-conventions.md) §1)
- [x] Decidir la frontera transaccional
- [x] Decidir la separación entre dominio y JPA
- [x] Decidir el estilo de paquetes (módulo primero) — ⚠️ **decisión sustituida** el 2026-10-15 por [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md) (capas globales); ver nota de sustitución más abajo
- [x] Decidir repositorios como puertos de salida — ⚠️ **sustituido** el 2026-10-15 por [ADR-0005](../adr/0005-los-puertos-se-declaran-en-el-dominio.md) (`domain.port.out`)
- [x] Fijar el stack y sus versiones ([ADR-0010](../adr/0010-versiones-de-java-y-spring-boot.md) — **Java 21 LTS**)
- [ ] Redactar los ADR de arquitectura pendientes (§4)

### 1.4 Diagramas

- [ ] `01-use-cases.drawio` — casos de uso por rol y módulo
- [ ] `02-order-state-machine.drawio` — estados, transiciones y actores permitidos
- [ ] `03-hexagonal-architecture.drawio` — capas, módulos y puertos
- [ ] `04-data-model.drawio` — modelo entidad-relación
- [ ] `05-local-infrastructure.drawio` — laboratorio local
- [ ] `06-aws-target.drawio` — arquitectura objetivo (diseño, no desplegado)
- [ ] `07-sequence-place-order.drawio` — secuencia del caso de uso crítico
- [ ] `08-sequence-complete-pickup.drawio` — secuencia con atomicidad

### 1.5 Planificación

- [x] Definir épicas e historias ([`backlog.md`](backlog.md)) — 44 historias, ≈253 puntos
- [x] Estimar en puntos Fibonacci con supuestos explícitos
- [x] Ordenar los sprints ([`roadmap.md`](roadmap.md))
- [ ] Detallar cada sprint en `sprint-N.md`
- [ ] Definir la Definition of Done global ✅
- [ ] Definir el protocolo de trabajo ✅ (`AGENTS.md` §7)

### 1.6 Reglas de desarrollo

- [x] `AGENTS.md` — reglas operativas y protocolo
- [x] `docs/development/conventions.md` — convenciones de código
- [x] `docs/development/spring-conventions.md` — reglas Spring, persistencia y pruebas
- [ ] Crear el repositorio Git con `.gitignore` y `README.md`

---

## 2. Entregables verificables

| Entregable | Estado |
|---|---|
| `docs/requirements/requirements-v1.0.md` con requisitos trazados a historias | ✅ Creado (pendiente de aprobación) |
| `docs/planning/decisions-pending.md` con las decisiones abiertas | ✅ Creado |
| `docs/adr/0010-versiones-de-java-y-spring-boot.md` aceptada | ✅ Hecho (Java 21 LTS) |
| `docs/planning/backlog.md`, `roadmap.md`, `definition-of-done.md` | ✅ Creados |
| `AGENTS.md` y los dos documentos de convenciones | ✅ Creados |
| `frontend/` reservado | ✅ Hecho |
| ADR de arquitectura (0001–0009) | ⬜ Pendiente |
| 8 diagramas `.drawio` | ⬜ Pendiente |
| Repositorio Git inicializado | ⬜ Pendiente |
| `README.md` | ⬜ Pendiente |

---

## 3. Criterios de salida

- [ ] `requirements-v1.0.md` **aprobado por el responsable**
- [ ] Ninguna ambigüedad bloqueante sin resolver (las 14 decisiones tienen respuesta)
- [ ] Los ADR de arquitectura redactados y aceptados
- [ ] Los 8 diagramas creados en `.drawio` (borrador admisible, sin marcar «final»)
- [ ] Repositorio Git inicializado con el primer commit
- [ ] `backlog.md` con todas las historias en `Pendiente`
- [ ] `roadmap.md` con S0 marcado como `Completado`

---

## 4. ADR pendientes de redactar en este sprint

| ADR | Título | Decisión | Estado |
|---|---|---|---|
| ADR-0001 | Índices de plantilla del proyecto | Nomenclatura de ADR, revisiones de plantilla | Pendiente |
| ADR-0002 | Transacciones en la capa de aplicación | Frontera transaccional y alternativa más pura rechazada | Pendiente |
| ADR-0003 | Dominio separado de JPA | Dos modelos y mappers, frente a entidad JPA rica | Pendiente |
| ADR-0004 | **Paquetes por capas, no por módulo** | Capas globales frente a módulo primero | ✅ [Redactada y aceptada](../adr/0004-estructura-de-paquetes-por-capas.md) — el título previsto era «Paquetes por módulo» |
| ADR-0005 | **Los puertos se declaran en el dominio** | `domain.port.out` frente a `application.port.out` | ✅ [Redactada y aceptada](../adr/0005-los-puertos-se-declaran-en-el-dominio.md) |
| ADR-0006 | Floci como emulador AWS | Elección del emulador local y sus límites | ✅ [Redactada](../adr/0006-floci-como-emulador-aws.md) |
| ADR-0007 | JWT con RS256 y claves por entorno | Algoritmo y gestión de claves | Pendiente |
| ADR-0008 | Monolito modular antes que microservicios | Justificación del monolito | Pendiente |
| ADR-0009 | Sin compilación nativa GraalVM en el MVP | Exclusión de AOT | Pendiente |

> Estos ADR están planificados y referenciados desde `AGENTS.md` y los documentos de convenciones.
> Su redacción es trabajo de S0; hasta entonces, las decisiones se leen directamente en los documentos.
>
> **Dos de ellos se desviaron de lo previsto.** ADR-0004 y ADR-0005 no eligieron el módulo primero ni
> `application.port.out`, sino **capas globales** y `domain.port.out` ([mapa de sustitución](../adr/0004-estructura-de-paquetes-por-capas.md) §5).
> Una ADR aceptada no se edita: se sustituye, y eso es exactamente lo que ocurrió.

---

## 5. Dependencias

| Depende de | — |
|---|---|
| Bloquea a | S1 (todos los sprints posteriores) |

---

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| Aprobar los requisitos sin resolver las 14 decisiones | Los valores por defecto están escritos y marcados; aprobar sin resolver es una decisión consciente y documentable |
| Los diagramas se hacen al final y no coinciden con el código | Cada diagrama lleva el identificador de la historia que lo originó; si el código cambia, el diagrama cambia en el mismo commit |
| Los ADR quedan como tareas sin dueño | Se redactan antes de S1: son la entrada de las reglas de Spring |

---

## 7. Registro de avance

| Fecha | Tarea | Estado | Notas |
|---|---|---|---|
| 2026-10-11 | Análisis de alcance y arquitectura | Hecho | — |
| 2026-10-11 | `requirements-v1.0.md` redactado | Hecho | Pendiente de aprobación |
| 2026-10-11 | Decisiones pendientes recogidas (D1–D14) | Hecho | Pendiente de respuesta |
| 2026-10-11 | ADR-0010 aceptada (Java 21 LTS) | Hecho | — |
| 2026-10-11 | Convenciones de desarrollo escritas | Hecho | — |
| 2026-10-11 | Backlog, roadmap y DoD creados | Hecho | — |
| 2026-10-11 | `frontend/` reservado | Hecho | — |
| 2026-10-11 | ADR de arquitectura 0001–0009 | Pendiente | — |
| 2026-10-11 | Diagramas `.drawio` | Pendiente | — |
| 2026-10-11 | Repositorio Git inicializado | Pendiente | — |
| 2026-10-15 | ADR de arquitectura 0001–0009 | En curso | 0004, 0005 y 0006 redactadas y aceptadas |
| 2026-10-15 | Decisiones de estructura de paquetes **sustituidas** | Hecho | El «módulo primero» de §1.3 y el ADR-0004 previsto se sustituyen por [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md) (**capas globales**) y [ADR-0005](../adr/0005-los-puertos-se-declaran-en-el-dominio.md) (puertos en `domain.port.out`). Sin código de negocio afectado |
| 2026-10-17 | `requirements-v1.0.md` **aprobado y congelado** | Hecho | El responsable ratifica D1–D15 con sus valores por defecto; se eliminan las marcas ⚙️. Cierra el criterio de salida de S0 |
| 2026-10-17 | `sprint-02`…`sprint-07` corregidos a capas globales | Hecho | Documentos de planificación adaptados a la estructura vigente |