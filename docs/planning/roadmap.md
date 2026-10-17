# Roadmap — Proyecto Pizzeria

> Fuente de verdad del **orden** del trabajo y de los **criterios de salida** de cada sprint.
> El detalle de historias vive en [`backlog.md`](backlog.md). El detalle de cada sprint, en `sprint-N.md`.
> Última actualización: **2026-10-17**

---

## 1. Principios de la ruta

| Principio | Consecuencia práctica |
|---|---|
| El núcleo de negocio va primero | Catálogo y pedidos se construyen y se prueban antes que cualquier integración de AWS |
| Una historia terminada es una historia **verificada** | Ningún sprint sale con `TODO`, `@Disabled` ni stubs |
| La seguridad no es un añadido | La identidad precede a la exposición de los endpoints de pedidos |
| Nada con coste sin autorización | US-072, US-081 y US-082 están **bloqueadas** hasta que el responsable lo autorice por escrito |
| Documentación en el mismo cambio | Si una historia cambia contrato, esquema o decisión, actualiza su documento en el mismo commit |

---

## 2. Vista general

| Sprint | Nombre | Historias | Puntos | Estado | Detalle |
|---|---|---|---|---|---|
| S0 | Requisitos y diseño | Transversal | — | **Aprobado** (ADR y diagramas pendientes) | [sprint-00.md](sprint-00.md) |
| S1 | Fundamentos del proyecto y PostgreSQL | US-001…US-006, US-080 (parcial) | 27 | **Hecho** | [sprint-01.md](sprint-01.md) |
| S2 | Catálogo y variantes | US-010…US-013 | 16 | Pendiente — **siguiente** | [sprint-02.md](sprint-02.md) |
| S3 | Pedidos: dominio y persistencia | US-030 (dominio), US-038 (parcial) | 8 | Pendiente | [sprint-03.md](sprint-03.md) |
| S4 | Identidad, autorización y recogida | US-020…US-023, US-030 (web), US-031…US-038 | 45 | Pendiente | [sprint-04.md](sprint-04.md) |
| S5 | Calidad, documentación y endurecimiento | US-040…US-045 | 34 | Pendiente | [sprint-05.md](sprint-05.md) |
| S6 | Integración con S3 (imágenes) | US-050…US-052 | 21 | Pendiente | [sprint-06.md](sprint-06.md) |
| S7 | Integración con SQS (mensajería) | US-060…US-062 | 26 | Pendiente | [sprint-07.md](sprint-07.md) |
| S8 | CI/CD y despliegue | US-070, US-071, US-072, US-073 | 26 | Pendiente | [sprint-08.md](sprint-08.md) |
| S9 | Observabilidad y mejoras de infraestructura | US-080, US-081, US-082, US-084 | 21 | Pendiente | [sprint-09.md](sprint-09.md) |

**El MVP termina en el Sprint 5.** Los sprints 6–9 pueden pausarse indefinidamente sin afectar al producto.

### Estado de S0

La **puerta de proceso está cerrada**: el responsable aprobó `requirements-v1.0.md` el **2026-10-17** y
ratificó con sus valores por defecto las 15 decisiones de [`decisions-pending.md`](decisions-pending.md).
**No queda ninguna ambigüedad bloqueante y ninguna decisión pendiente.**

Lo que S0 todavía tiene sin entregar, y que no bloquea a nadie:

| Entregable | Estado |
|---|---|
`requirements-v1.0.md` aprobado | ✅ 2026-10-17, **congelado** |
Decisiones D1–D15 | ✅ 15 de 15 resueltas |
`roadmap.md` · `backlog.md` · `definition-of-done.md` · `AGENTS.md` · convenciones | ✅ Hecho |
ADR-0001…ADR-0010 | 🟡 **4 de 10**: 0004, 0005, 0006 y 0010. Faltan 0001, 0002, 0003, 0007, 0008, 0009 |
8 páginas `.drawio` | ⬜ Sin empezar (US-044, S5) |

**D14 no es una ambigüedad sino una prohibición ratificada:** ningún recurso de AWS se crea sin autorización
escrita, así que US-072, US-081 y US-082 permanecen bloqueadas por diseño.

### Estructura de paquetes de todo el código

Desde el 2026-10-15 el código se organiza por **capas globales**, no por módulo
([ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md)): `com.pizzeria.{domain, application, infrastructure, shared}`.
Esta regla no es del roadmap, pero afecta a **todos** los sprints que faltan ejecutar: en S2 en adelante no se
crea ningún directorio `catalog/`, `identity/`, `orders/` ni `payments/`. Los puertos se declaran en
`domain.port.out` ([ADR-0005](../adr/0005-los-puertos-se-declaran-en-el-dominio.md)).

---

## 3. Detalle por sprint

### S0 — Requisitos y diseño *(aprobado el 2026-10-17)*

| | |
|---|---|
| **Objetivo** | Congelar las reglas de negocio y las decisiones técnicas antes de escribir código |
| **Historias** | Transversal: decisiones de arquitectura, diagramas, ADR iniciales |
| **Entregables verificables** | `requirements-v1.0.md` aprobado · ADR-0001…ADR-0010 · 8 páginas `.drawio` en borrador · `roadmap.md` · `backlog.md` · `definition-of-done.md` · `AGENTS.md` y documentos de convenciones |
| **Depende de** | — |
| **Criterio de salida** | No queda ninguna ambigüedad bloqueante. El responsable aprueba la lista de decisiones de [`decisions-pending.md`](decisions-pending.md) |
| **Estado actual** | ✅ **Cumplido el 2026-10-17**: requisitos aprobados y congelados, 15 de 15 decisiones resueltas. **Pendiente sin bloquear a nadie:** 6 ADR (0001, 0002, 0003, 0007, 0008, 0009) y las 8 páginas `.drawio` |

### S1 — Fundamentos del proyecto y PostgreSQL

| | |
|---|---|
| **Objetivo** | Un proyecto que compila, arranca en Docker, migra la base de datos y valida en CI |
| **Historias** | US-001, US-002, US-003, US-004, US-005, US-006, US-080 (parcial) |
| **Entregables verificables** | `./mvnw verify` en verde · `docker compose up` sano · 8 tablas migradas · test ArchUnit verde · workflow de CI en verde |
| **Depende de** | S0 |
| **Criterio de salida** | Alguien que clone el repositorio y ejecute los comandos documentados tiene la base de datos migrada y la aplicación respondiendo |
| **Riesgo principal** | Incompatibilidad de la matriz Spring Boot 4.1.x + Testcontainers 2.x + springdoc 3.x → plan B documentado en US-001 |
| **Estado actual** | ✅ **Cerrado el 2026-10-11.** US-001…US-006 en `Hecho`, US-080 cerrado en su parte de S1. Ninguna de las seis historias dejó `TODO` ni `@Disabled`. El 2026-10-15 se aplicó además [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md) sin abrir historias |

### S2 — Catálogo y variantes

| | |
|---|---|
| **Objetivo** | El catálogo se lee públicamente y se gestiona en privado por el administrador |
| **Historias** | US-010, US-011, US-012, US-013 |
| **Entregables verificables** | 6 endpoints de catálogo en Swagger · tests de rol · prueba de que un cambio de precio no altera pedidos previos |
| **Depende de** | S1 |
| **Criterio de salida** | Un ADMIN crea una pizza con dos variantes y un CLIENTE las ve con su precio; **no existe ningún endpoint `DELETE`** en el catálogo |
| **Primera historia** | **US-010** (consulta pública del catálogo, 5 puntos). Es el primer código de negocio del proyecto y el primero bajo capas globales |

### S3 — Pedidos: dominio y persistencia

| | |
|---|---|
| **Objetivo** | El corazón del negocio, verificado sin necesidad de HTTP |
| **Historias** | US-030 (dominio + persistencia + casos de uso), US-038 (parcial) |
| **Entregables verificables** | Agregado `Order` con invariantes probados · creación de pedido desde un test de integración · precio calculado por el servidor y foto histórica persistida |
| **Depende de** | S2 |
| **Criterio de salida** | Todos los invariantes del pedido probados en unitarios; creación de pedido probada contra PostgreSQL real |
| **Nota de diseño** | No se expone ningún endpoint de pedidos aquí. Sin identidad no hay propietario, y una API de pedidos sin autenticación crearía una falsa sensación de seguridad. La capa web llega en S4 |

### S4 — Identidad, autorización y recogida

| | |
|---|---|
| **Objetivo** | API completa y segura de extremo a extremo |
| **Historias** | US-020, US-021, US-022, US-023, US-030 (capa web), US-031…US-038 |
| **Entregables verificables** | API v1 completa · matriz de seguridad verde · transacción de recogida probada con rollback y doble cobro |
| **Depende de** | S3 |
| **Criterio de salida** | Recorrido completo: registro → login → pedido → confirmación → recogida con pago, sin que ningún endpoint filtre datos ajenos y sin doble cobro posible |

### S5 — Calidad, documentación y endurecimiento — **fin del MVP**

| | |
|---|---|
| **Objetivo** | Endurecer, documentar y medir |
| **Historias** | US-040, US-041, US-042, US-043, US-044, US-045, US-080 (cierre) |
| **Entregables verificables** | Cobertura medida (dominio ≥ 80 %, casos de uso ≥ 70 %) · ADR y diagramas al día · `README` presentable · contrato congelado |
| **Depende de** | S4 |
| **Criterio de salida** | El proyecto se puede enseñar y explicar de principio a fin. **Este es el punto en que el proyecto es demostrable** |

### S6 — Integración con S3 (imágenes de pizza)

| | |
|---|---|
| **Objetivo** | Primera integración con AWS, verificada localmente y sin coste |
| **Historias** | US-050, US-051, US-052 |
| **Entregables verificables** | Imagen subida por el administrador y servida · integración con Floci verde |
| **Depende de** | S5 |
| **Criterio de salida** | El backend **sigue arrancando sin AWS** con el perfil por defecto (requisito clave de US-050) |
| **Nota** | El SDK de AWS permanece fuera del dominio y de la aplicación |

### S7 — Integración con SQS (mensajería asíncrona)

| | |
|---|---|
| **Objetivo** | Eventos de dominio publicados de forma fiable |
| **Historias** | US-060, US-061, US-062 |
| **Entregables verificables** | Mensaje `OrderConfirmed` consumido · outbox probado con SQS caído |
| **Depende de** | S6 |
| **Criterio de salida** | Un pedido confirmado con SQS inaccesible **igual se confirma**, y el mensaje sale al recuperar el servicio |

### S8 — CI/CD y despliegue

| | |
|---|---|
| **Objetivo** | Imagen publicada y backend desplegado, o decisión documentada de no desplegar |
| **Historias** | US-070, US-071, US-072 *(requiere autorización)*, US-073 |
| **Entregables verificables** | Pipeline completo · imagen multi-stage · ADR de comparación EC2 frente a ECS |
| **Depende de** | S7 |
| **Criterio de salida** | Despliegue real **sólo** con autorización escrita; sin autorización, el sprint termina en US-071 + ADR, sin recursos creados |

### S9 — Observabilidad y mejoras de infraestructura

| | |
|---|---|
| **Objetivo** | Salud, métricas y coste bajo control |
| **Historias** | US-080, US-081, US-082, US-084 |
| **Entregables verificables** | Alarmas configuradas · política de coste · `README` final con métricas reales |
| **Depende de** | S8 |
| **Criterio de salida** | Costes vigilados y alertas activas; checklist de destrucción documentado. **Revisión de Java 25/27** ([ADR-0010 §6](../adr/0010-versiones-de-java-y-spring-boot.md)) |

---

## 4. Hitos verificables

| Hito | Sprint | Cómo se demuestra |
|---|---|---|
| **H1** · El proyecto arranca en local | S1 | `docker compose up` + `/actuator/health` = `UP` |
| **H2** · El catálogo se gestiona | S2 | Swagger muestra los 6 endpoints; tests de rol verdes |
| **H3** · El negocio del pedido es correcto | S3 | Tests de invariantes y de precios verdes |
| **H4** · La API completa y es segura | S4 | Recorrido extremo a extremo + matriz de autorización |
| **H5** · **MVP demostrable** | S5 | `README`, diagramas, ADR y métricas reales |
| **H6** · Integración con AWS sin coste | S7 | S3 y SQS funcionando contra el emulador local |
| **H7** · Desplegable | S8 | Dockerfile + pipeline + (opcionalmente) despliegue autorizado |
| **H8** · Operable | S9 | Métricas, alarmas y documentación final |

---

## 5. Dependencias entre sprints

```text
S0 ──► S1 ──► S2 ──► S3 ──► S4 ──► S5 ──► S6 ──► S7 ──► S8 ──► S9
 │      │      │             │      │      └──────────────┘
 │      │      │             │      └─► MVP demostrable
 │      │      └─► precios   └─► todo el negocio, seguro
 │      └─► build, BD, CI
 └─► decisiones congeladas
```

**Atajos no permitidos:** ningún sprint salta al siguiente sin cumplir su criterio de salida, aunque el calendario apriete. Es preferible entregar menos historias bien terminadas que más historias a medias.

---

## 6. Riesgos de la ruta

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La matriz Spring Boot 4.1.x + JDK 21 + Testcontainers 2.x falla | Bloquea S1 | Spike en US-001 con plan B: Spring Boot 3.5.x (Java 21 sigue válido) |
| springdoc 3.x no es certificable con Boot 4.1.x | Afecta a US-005 | Documentar el contrato a mano; no bloquea el MVP |
| **S0 cerrado:** requisitos aprobados, decisiones resueltas | Ninguno | Ya está: 15 de 15 decisiones ratificadas el 2026-10-17 |
| Faltan 6 ADR y los 8 diagramas | Afecta a US-044 y a la presentación final | Sprint-05 (US-044) los recoge como entregables con criterio de salida propio |
| **Reaparecer la estructura por módulo** (`catalog/`, `identity/`, `orders/`, `payments/`) | Erosiona la arquitectura en silencio | `ArchitectureRulesTest.noHayPaquetesHuerfanos` falla el build; ADR-0004 es la norma |
| US-037 (recogida) consume más de lo previsto | Afecta a S4 | Dividir en subtareas; priorizar rollback y doble cobro |
| US-061 (outbox) demasiado ambiciosa | Afecta a S7 | Entregar publicación directa y documentar la deuda |
| Autorización de AWS denegada | Afecta a S8, S9 | US-072/081/082 marcadas como bloqueadas; el proyecto sigue completo sin ellas |
| El alcance crece por sugerencias | Afecta a todo | Lo que no está en el backlog no se implementa (ver §4 del backlog) |