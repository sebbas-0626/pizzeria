# Sprint 09 — Observabilidad y mejoras de infraestructura

> **Estado:** Planificado · **⚠️ US-081 y US-082 requieren autorización expresa del responsable**
> **Objetivo:** saber si el servicio está sano, poder diagnosticarlo y controlar el coste.
> **Historias:** US-080, US-081, US-082, US-083 *(fuera del MVP)*, US-084
> **Puntos:** 21
> **Documento efímero:** al terminar, lo relevante pasa al backlog y a `docs/architecture/security.md`.

---

## 1. Objetivo del sprint

Un sistema desplegado sin observabilidad es un sistema que se descubre cuando se cae. Este sprint cierra
el proyecto con tres preguntas respondidas:

1. **¿Está sano?** → salud y métricas.
2. **¿Qué ha pasado?** → registros correlacionados y centralizados.
3. **¿Cuánto cuesta y cómo escalaría?** → evaluación de la base de datos gestionada.

---

## 2. Historias del sprint

| ID | Título | Puntos | Bloqueante |
|---|---|---|---|
| US-080 | Actuator, salud y métricas (cierre) | — | — |
| US-081 | Observabilidad en la nube | 5 | ⚠️ Autorización |
| US-082 | Evolución a RDS | 8 | ⚠️ Autorización |
| US-083 | Frontend estático en S3 + CloudFront | 5 | 🚫 Fuera del MVP |
| US-084 | Estado final del portafolio | 5 | — |

---

## 3. US-080 — Actuator, salud y métricas (cierre)

### Tareas

- [ ] `/actuator/health` con detalle de la base de datos
- [ ] Endpoint de métricas estándar
- [ ] Endpoint de métricas en formato de monitorización (si procede)
- [ ] Restricción de endpoints sensibles por perfil o por rol
- [ ] Comprobación de que los endpoints administrativos no están expuestos en producción

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `/actuator/health` responde `UP` con el detalle de la base de datos | `curl` |
| 2 | Si la base de datos cae, la salud pasa a `DOWN` | Prueba: parar la base y consultar |
| 3 | Las métricas del proceso y de la JVM están disponibles | `curl` |
| 4 | **`/actuator/env` y `/actuator/configprops` no están expuestos en producción** | Prueba con perfil `prod` |
| 5 | El health check del orquestador utiliza el endpoint correcto y no filtra datos sensibles | Revisión |

> El criterio 2 es el que da valor al health check: un `UP` que no detecta la caída de la base de datos
> es peor que no tener health check, porque da una confianza falsa.

---

## 4. US-081 — Observabilidad en la nube *(bloqueada por autorización)*

### Tareas

- [ ] ADR con el diseño: agregación de registros, métricas y alarmas
- [ ] Política de retención y su coste
- [ ] Alarma de disponibilidad (caída de salud)
- [ ] Alarma de coste
- [ ] Dashboard mínimo con: peticiones por minuto, latencia y errores
- [ ] Documentación de qué se puede ver y qué no

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | ADR redactado con costes estimados |
| 2 | Alarma de disponibilidad activa y **probada** (se provoca un fallo y se comprueba que suena) |
| 3 | Alarma de coste activa antes de cualquier recurso |
| 4 | Política de retención documentada |
| 5 | Los registros no contienen datos sensibles (verificación con el patrón de redacción de US-043) |
| 6 | **Sin crear recursos sin autorización** |

### Qué se aprende

| Concepto | En la práctica |
|---|---|
| Logs centralizados | Ya se generan estructurados (US-043). Ahora se recogen |
| Métricas | De "el servicio está vivo" a "está lento" y "está fallando" |
| Alarmas | Sólo las que alguien puede actuar. Una alarma que nadie atiende es ruido |
| Retención | Un compromiso entre coste y capacidad de diagnóstico |

---

## 5. US-082 — Evolución a RDS *(bloqueada por autorización)*

### Tareas

- [ ] ADR de diferencias entre PostgreSQL en contenedor y PostgreSQL gestionado
- [ ] Diseño de la red: la base de datos **no** es accesible desde Internet
- [ ] Grupos de seguridad mínimos
- [ ] Plan de copia de seguridad y restauración **probado**
- [ ] Gestión de credenciales sin almacenarlas en el repositorio ni en la imagen
- [ ] Si se autoriza: cambio de **sólo** variables de entorno

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | ADR con diferencias, costes y plan de migración |
| 2 | La base de datos no es accesible desde Internet (verificado) |
| 3 | La restauración desde copia de seguridad está **probada**, no sólo configurada |
| 4 | La aplicación funciona contra la base gestionada **sin cambios de código** |
| 5 | Las migraciones de Flyway siguen siendo la fuente del esquema |
| 6 | Las credenciales no están en el repositorio ni en la imagen |

### La prueba que demuestra el criterio 4

```text
1. Base de datos en contenedor          → aplicación en verde
2. Base de datos gestionada             → aplicación en verde
3. Comparar: el único cambio es la URL y las credenciales, por variable de entorno
```

Si alguien puede cambiar de base de datos sin tocar código, la arquitectura está bien. Es la prueba más
barata y más reveladora de este sprint.

---

## 6. US-083 — Frontend estático *(fuera del MVP)*

### Tareas

- [ ] Documentar la distinción: **S3 para imágenes · S3 para estáticos del frontend · CloudFront como CDN**
- [ ] ADR de publicación estática con control de origen
- [ ] Plan: Inhabilitación de acceso directo al bucket, CDN como único punto de entrada
- [ ] **No se implementa** hasta que exista frontend

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | ADR redactado y aceptado |
| 2 | Historia marcada como `Descartada (MVP)` en el backlog |
| 3 | **Ningún recurso creado** |

> Esta historia existe para dejar clara la distinción conceptual, que es donde suelen confundirse las
> tres cosas. El frontend sigue vacío en [`frontend/`](../../frontend/README.md).

---

## 7. US-084 — Estado final del portafolio

### Tareas

- [ ] `README.md` final con métricas **reales**: cobertura, número de pruebas, número de endpoints, líneas
- [ ] Tabla de sprints completados con lo entregado en cada uno
- [ ] Índice de decisiones técnicas (los ADR) con su motivo en una frase
- [ ] Lista de lo que **no** se hizo y por qué (deuda consciente)
- [ ] Revisión de que ningún secreto está en el historial del repositorio
- [ ] Correspondencia de ADR-0010 §6: ¿revisar Java 25 o 27?

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | Todas las cifras del README están medidas, no estimadas |
| 2 | Un tercero puede clonar, arrancar y probar el proyecto siguiendo el README |
| 3 | La lista de decisiones permite defender cada una en una entrevista |
| 4 | La deuda técnica está declarada, no escondida |
| 5 | Revisión de versiones de Java documentada |

---

## 8. Criterio de salida del sprint

- [ ] Health check que detecta realmente una caída de la base de datos
- [ ] Endpoints sensibles de Spring Boot no expuestos en producción
- [ ] ADR de observabilidad redactado (con o sin despliegue)
- [ ] ADR de base de datos gestionada redactado
- [ ] Si hubo despliegue: alarmas probadas y coste vigilado
- [ ] `README.md` con métricas reales
- [ ] Revisión de versiones de Java completada
- [ ] `./mvnw verify` en verde
- [ ] US-080, US-084 en `Hecho`; US-081 y US-082 en `Hecho` o `Bloqueada` con motivo

---

## 9. Revisión de versiones de Java ([ADR-0010](../adr/0010-versiones-de-java-y-spring-boot.md) §6)

Cierre del proyecto = momento de revisar si la decisión de Java 21 sigue siendo la correcta.

| Condición | Estado actual (2026-10-11) |
|---|---|
| Spring Boot estable con Java 25 o 27 documentado | ❌ Spring Boot 4.1.1 declara hasta Java 26; 4.2 sigue en milestone |
| Byte Buddy con soporte nativo sin banderas experimentales | ❌ Soporte oficial hasta Java 25 |
| Mockito y Testcontainers probados contra 25/27 | ❌ Testcontainers 2.0.5 es anterior al lanzamiento de JDK 27 |

**Conclusión de la revisión: se mantiene Java 21 LTS.** La decisión queda justificada con datos, no con
preferencias.

---

## 10. Dependencias y riesgos

| | |
|---|---|
| **Depende de** | Sprint 08 |
| **Riesgo** | Crear recursos de observabilidad sin autorización → bloqueado por D14 |
| **Riesgo** | Alarmas que nadie atiende → documentar quién responde a cada una |
| **Riesgo** | RDS sin probar la restauración → una copia que no se restaura no es una copia |
| **Riesgo** | Métricas de udara sin valor → medir únicamente lo que se va a mirar |

---

## 11. Cierre del proyecto

Al terminar este sprint, el proyecto debe poder responder estas preguntas sin consultar nada más:

| Pregunta | Dónde está la respuesta |
|---|---|
| ¿Qué hace el sistema? | `README.md` + `requirements-v1.0.md` |
| ¿Cómo está construido y por qué? | `architecture.md` + los ADR |
| ¿Cómo se arranca? | `README.md` + `infra/local/` |
| ¿Cómo se despliega? | Sprint 08 + `infra/aws/` |
| ¿Qué decisiones se tomaron y por qué? | `docs/adr/` |
| ¿Qué está pendiente y por qué? | `backlog.md` + `decisions-pending.md` |

Si alguna de estas preguntas no tiene respuesta, el proyecto todavía no está terminado.

---

## 12. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| — | — | — | Sprint no iniciado |
| — | Autorización AWS | Pendiente | Requiere D14 resuelta |