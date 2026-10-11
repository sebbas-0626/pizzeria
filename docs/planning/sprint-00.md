# Sprint 00 — Requisitos y diseño

> **Estado:** En curso
> **Objetivo:** congelar las reglas de negocio y las decisiones técnicas antes de escribir una línea de código.
> **Historias:** transversales (no son historias de código)
> **Puntos:** —
> **Documento efímero:** al terminar, lo relevante pasa a `requirements-v1.0.md`, a los ADR y al backlog.

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

- [x] Definir arquitectura hexagonal por módulo ([ADR-0001](docs/adr/0010-versiones-de-java-y-spring-boot.md) como referencia de stack)
- [x] Definir reglas de dependencia verificables ([`spring-conventions.md`](../development/spring-conventions.md) §1)
- [x] Decidir la frontera transaccional
- [x] Decidir la separación entre dominio y JPA
- [x] Decidir el estilo de paquetes (módulo primero)
- [x] Decidir repositorios como puertos de salida
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

| ADR | Título | Decisión |
|---|---|---|
| ADR-0001 | Índices de plantilla del proyecto | Nomenclatura de ADR, revisiones de plantilla |
| ADR-0002 | Transacciones en la capa de aplicación | Frontera transaccional y alternativa más pura rechazada |
| ADR-0003 | Dominio separado de JPA | Dos modelos y mappers, frente a entidad JPA rica |
| ADR-0004 | Paquetes por módulo | Módulo primero frente a capas globales |
| ADR-0005 | Repositorios como puertos de salida | Dónde se declaran las interfaces |
| ADR-0006 | Floci como emulador AWS | Elección del emulador local y sus límites |
| ADR-0007 | JWT con RS256 y claves por entorno | Algoritmo y gestión de claves |
| ADR-0008 | Monolito modular antes que microservicios | Justificación del monolito |
| ADR-0009 | Sin compilación nativa GraalVM en el MVP | Exclusión de AOT |

> Estos ADR están planificados y referenciados desde `AGENTS.md` y los documentos de convenciones.
> Su redacción es trabajo de S0; hasta entonces, las decisiones seleo directamente en los documentos.

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