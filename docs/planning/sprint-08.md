# Sprint 08 — CI/CD y despliegue

> **Estado:** Planificado · **⚠️ US-072 requiere autorización expresa del responsable**
> **Objetivo:** imagen reproducible, pipeline completo y decisión documentada sobre dónde desplegar.
> **Historias:** US-070, US-071, US-072 *(bloqueada)*, US-073
> **Puntos:** 26
> **Documento efímero:** al terminar, lo relevante pasa al backlog y a `infra/aws/`.

---

## 1. Frontera de autorización

> **Este sprint no crea ningún recurso de AWS sin autorización escrita del responsable**
> (ver [`decisions-pending.md`](decisions-pending.md) D14).

Si la autorización no llega, el sprint **se cierra igualmente** entregando US-070, US-071 y US-073. El
proyecto no depende de que se autorice nada.

### Requisitos previos a cualquier recurso con coste

- [ ] Autorización escrita del responsable
- [ ] Presupuesto máximo acordado
- [ ] **Alarma de coste configurada antes de crear el primer recurso**
- [ ] Rol de IAM de mínimo privilegio, sin claves estáticas en el repositorio
- [ ] Checklist de destrucción documentado

---

## 2. Historias del sprint

| ID | Título | Puntos | Bloqueante |
|---|---|---|---|
| US-070 | Dockerfile multi-stage | 5 | — |
| US-071 | Pipeline de entrega continua | 8 | — |
| US-072 | Despliegue en EC2 | 8 | ⚠️ Autorización |
| US-073 | Evaluación de ECS frente a EC2 | 5 | — |

---

## 3. US-070 — Dockerfile multi-stage

### Tareas

- [ ] Etapa de compilación con `eclipse-temurin:21-jdk`
- [ ] Etapa de ejecución con `eclipse-temurin:21-jre` ([ADR-0010](../adr/0010-versiones-de-java-y-spring-boot.md))
- [ ] Copiar **sólo** el JAR, no el código fuente
- [ ] Usuario no root
- [ ] `.dockerignore` que excluya `target`, `.git`, `.env`, `docs` y `frontend`
- [ ] Etiqueta con versión explícita, nunca `latest` en despliegue
- [ ] Prueba de humo: la imagen arranca contra PostgreSQL de pruebas y responde `UP`
- [ ] Dependabot configurado para actualizar la imagen base

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | La imagen compila y arranca | `docker build` + `docker run` |
| 2 | El usuario de ejecución no es root | `docker inspect` |
| 3 | No contiene fuentes, `.git` ni `.env` | Inspección de la imagen |
| 4 | No contiene credenciales | `gitleaks` + revisión |
| 5 | La imagen es reproducible con tag fijo | Revisión de la etiqueta |
| 6 | El tamaño es razonable para un servicio Java | Medición y registro |
| 7 | Arranca con Java 21 (coherente con el `pom.xml`) | Registro de arranque |

### Criterio 6 en la práctica

Una imagen JRE estándar para un servicio Spring Boot ronda los **250–300 MB**. Se registra el número real
en el ADR. Si se quiere reducir, las opciones son (a) imagen con `jlink` generada a medida, o (b)
compilación nativa, excluido del MVP por ADR-0009. Se documenta como posible mejora, no como pendiente
oculto.

---

## 4. US-071 — Pipeline de entrega continua

### Tareas

- [ ] Job `verify`: `./mvnw verify` con caché de Maven
- [ ] Job `docker`: construcción de la imagen
- [ ] Job `scan`: análisis de vulnerabilidades de la imagen y escaneo de secretos
- [ ] Job `package`: publicación en ECR **sólo con autorización**; si no, artefacto local
- [ ] Artefactos: JAR, imagen y `openapi.json`
- [ ] Condiciones de ejecución por rama y por etiqueta

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | El pipeline falla si `./mvnw verify` falla | Ejecución real |
| 2 | La imagen se construye con el mismo JDK que el `pom.xml` | Inspección |
| 3 | El escaneo de secretos bloquea la integración | Prueba con un secreto en una rama |
| 4 | Los artefactos quedan disponibles | Descarga desde la interfaz |
| 5 | Publicar en ECR sólo ocurre con autorización activada | Revisión de la condición |

---

## 5. US-072 — Despliegue en EC2 *(bloqueada por autorización)*

### Tareas

- [ ] Configurar la alarma de coste **antes** de crear la instancia
- [ ] Crear la instancia con la imagen construida
- [ ] Abrir **sólo** los puertos necesarios; la base de datos no se expone
- [ ] IAM: rol de mínimo privilegio, sin claves estáticas en el repositorio
- [ ] HTTPS mediante proxy con terminación de TLS y renovación de certificado
- [ ] Copia de seguridad de PostgreSQL
- [ ] Script de destrucción documentado y ejecutado al terminar
- [ ] Costes estimados antes de crear y comparados después

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | La alarma de coste está activa antes del recurso | Captura de la consola |
| 2 | El backend responde por HTTPS | `curl` |
| 3 | La base de datos **no** es accesible desde Internet | Prueba desde fuera |
| 4 | No hay claves de AWS en el repositorio | `gitleaks detect` |
| 5 | El coste real se compara con el estimado | Registro |
| 6 | El script de destrucción está probado | Ejecución documentada |
| 7 | El teardown se ejecuta al terminar el aprendizaje | Evidencia de que no queda ningún recurso cobrando |

### Sobre HTTPS y secretos

| Concepto | Dónde vive |
|---|---|
| Claves de la base de datos | **Nunca** en el repositorio ni en variables de la imagen. En el almacén de secretos del proveedor o en el gestor de secretos de la máquina |
| Certificado TLS | Servicio de certificados del proveedor o proxy con renovación automática |
| Credenciales de AWS del despliegue | Rol con permisos temporales, no claves estáticas |

---

## 6. US-073 — Evaluación de ECS frente a EC2

### Tareas

- [ ] Documento de comparación con criterios objetivos
- [ ] Costes aproximados de ambos enfoques
- [ ] Carga operativa de cada uno
- [ ] Camino de migración
- [ ] Conclusión razonada

### Criterios de aceptación

| # | Criterio |
|---|---|
| 1 | Comparación con coste, operatividad, escalado y complejidad |
| 2 | Conclusión razonada, no una preferencia |
| 3 | **Sin crear ningún recurso** |
| 4 | ADR con la decisión y sus condiciones de reevaluación |

### Cuadro de comparación esperable

| Criterio | EC2 | ECS sobre Fargate |
|---|---|---|
| Modelo mental | Una máquina que hay que mantener | Contenedores gestionados |
| Escalado | Manual o con grupo de autoescalado | Por definición de tareas |
| Coste fijo | Sí, mientras la instancia exista | Sólo mientras haya tareas |
| Actualizaciones | Manual, sustituida por completo | Despliegue gradual con reversión |
| Complejidad | Baja al principio, alta con el tiempo | Media desde el primer día |
| Encaja del proyecto | Primera práctica de cómputo | Cuando haya más de una instancia |

> Conclusión esperable y defendible: **EC2 para aprender los conceptos; ECS cuando el servicio necesite
> más de una instancia o despliegues frecuentes.** No es mejor ni peor: es distinto.

---

## 7. Criterio de salida del sprint

- [ ] Imagen reproducible y sin secretos
- [ ] Pipeline en verde en un `push` real
- [ ] Prueba de humo de la imagen verde
- [ ] ADR de EC2 frente a ECS redactado
- [ ] Si hubo despliegue: HTTPS funcionando, base de datos privada, alarma de coste activa
- [ ] Si hubo despliegue: **recursos destruidos al terminar** o alarma y responsable asignados
- [ ] `./mvnw verify` en verde
- [ ] US-070, US-071 y US-073 en `Hecho`; US-072 en `Hecho` o `Bloqueada` con motivo

---

## 8. Dependencias y riesgos

| | |
|---|---|
| **Depende de** | Sprint 07 |
| **Bloquea a** | Sprint 09 |
| **Riesgo de coste** | Una instancia olvidada sigue facturando → alarma antes del recurso y teardown documentado |
| **Riesgo de seguridad** | Base de datos expuesta a Internet → el criterio 3 lo comprueba |
| **Riesgo** | Publicar en ECR sin autorización → la publicación está condicionada en el pipeline |
| **Riesgo** | Que el despliegue bloquee el sprint → US-070, US-071 y US-073 se entregan igualmente |

---

## 9. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| — | — | — | Sprint no iniciado |
| — | Autorización AWS | Pendiente | Requiere D14 resuelta |