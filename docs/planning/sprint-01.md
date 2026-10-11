# Sprint 01 — Fundamentos del proyecto y PostgreSQL

> **Estado:** Planificado
> **Objetivo:** un proyecto que compila, arranca en Docker, migra la base de datos y se valida en CI.
> **Historias:** US-001, US-002, US-003, US-004, US-005, US-006, US-080 (parcial)
> **Puntos:** 27
> **Documento efímero:** al terminar, lo relevante se copia a `backlog.md`, `architecture.md` o un ADR. Este archivo es un registro, no una fuente de verdad.

---

## 0. Antes de empezar

| Requisito | Estado |
|---|---|
| S0 cerrado (`requirements-v1.0.md` aprobado) | ⬜ Pendiente |
| ADR-0010 (Java 21 LTS + Spring Boot 4.1.x) aceptado | ✅ Hecho |
| `AGENTS.md`, `conventions.md`, `spring-conventions.md` creados | ✅ Hecho |
| Decisión D1 (moneda) resuelta | ✅ Hecho — **COP, sin decimales** |
| `backend/pom.xml` creado | ✅ Hecho |
| Repositorio inicializado en Git | ⬜ Pendiente |
| **JDK 21 (Temurin) instalado y `JAVA_HOME` definido** | ⬜ **Pendiente — bloqueante** |
| Maven Wrapper 3.10.0 generado (`./mvnw`) | ✅ Hecho — checksum SHA-256 fijado y validado |
| Docker disponible en la máquina | ✅ Hecho (Docker 24.0.7) |

### Verificación del stack ejecutada el 2026-10-11

Se ejecutó `./mvnw -B verify` dentro de `maven:3.10.0-eclipse-temurin-21`. Resultado real:

```text
Rule 0: RequireJavaVersion passed
Rule 1: RequireMavenVersion passed
[INFO] argLine set to -javaagent:.../jacoco-0.8.13-runtime.jar
[INFO] Building jar: /w/target/pizzeria-backend.jar
[ERROR] BUILD FAILURE
[ERROR] spring-boot-maven-plugin:4.1.1:repackage failed: Unable to find main class
```

**Interpretación:** todo el stack está verificado — resolución de dependencias con Spring Boot 4.1.1,
enforcement de Java 21 y de Maven 3.10.0, compilación, JaCoCo y empaquetado. El único fallo es que
todavía **no existe la clase principal**, que es precisamente la siguiente tarea de US-001.

**Dos hallazgos que quedaron documentados:**

1. `mvnw wrapper:wrapper` dejó `distributionUrl` apuntando al alias versionless `apache-maven-3-bin.zip`
   (resuelve a la última Maven 3 publicada). Se corrigió a mano a `apache-maven-3.10.0-bin.zip`, que es lo
   que exige el ADR-0010.
2. Con `distributionSha256Sum` fijado, `mvnw` necesita el comando `unzip`: si no existe, cambia la URL a
   `.tar.gz` y la validación falla aunque el hash sea correcto. La imagen `maven:3.10.0-eclipse-temurin-21`
   no lo trae. Está anotado en `maven-wrapper.properties` y habrá que tenerlo presente en el Dockerfile
   de CI.

### ⚠️ Entorno detectado (2026-10-11)

| Comprobación | Resultado | Consecuencia |
|---|---|---|
| `java -version` | **Oracle JDK 22.0.2** — no es LTS y no es el 21 del ADR | El Enforcer `[21,)` **lo acepta**, así que el build no fallaría, pero las pruebas se ejecutarían en 22 mientras CI usa 21. Esa divergencia es exactamente la que produce «funciona en mi máquina» |
| `JAVA_HOME` | **No definido** | Maven elegiría el JDK por defecto del sistema |
| `mvn` | **No instalado** | Se usará el Maven Wrapper, que se auto-instala en la primera ejecución |
| `docker` | 24.0.7 ✅ | Correcto |

**Acción requerida antes de dar por bueno US-001:** instalar **Eclipse Temurin 21** y definir `JAVA_HOME`
para que compilación, pruebas y CI usen exactamente la misma versión.

**Sobre el rango `[21,)`.** Es intencionadamente una ventana abierta y no una versión exacta: Spring Boot
admite de 17 a 26 y no queremos bloquear a nadie con un JDK superior. Pero la convención «local y CI con la
misma versión» se mantiene como norma del proyecto, y por eso hace falta instalar el 21.

---

## 1. Historia a historia

### US-001 · Esqueleto del proyecto y verificación del stack — 3 puntos

**Objetivo:** comprobar que toda la pila técnica encaja **antes** de escribir una línea de negocio.

#### Tareas

- [ ] Inicializar el repositorio Git en la raíz del monorepo
- [ ] Crear `backend/pom.xml` con el parent de Spring Boot 4.1.x
- [ ] Declara `<java.version>21</java.version>` (el parent lo traduce a `--release 21`)
- [x] Añadir `maven-enforcer-plugin` con `requireJavaVersion [21,)` y `requireMavenVersion`
- [x] Añadir Maven Wrapper **3.10.0** (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`) con checksum SHA-256 fijado
- [ ] Generar **`.gitignore`** (el build ya genera `target/`, que no debe versionarse)
- [ ] Crear `com.pizzeria.PizzeriaApplication`
- [ ] Crear `common/domain/DomainException.java`
- [ ] Crear `application.yml`, `application-local.yml`, `application-test.yml`
- [ ] Activar `spring-boot-starter-actuator` y exponer `health` (US-080 parcial)
- [ ] Escribir el test **ArchUnit** con las 5 reglas de dependencia
- [ ] Escribir `PizzeriaApplicationTests` (carga de contexto)
- [ ] Crear `.editorconfig` y `.gitignore` (Java + `.env`)
- [ ] Crear `docs/architecture/architecture.md` con la tabla de stack
- [ ] **Spike de verificación** (ver §2)

#### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `./mvnw verify` compila, pasa los tests y empaqueta | Ejecutar y reportar la salida literal |
| 2 | El bytecode es versión 65 (Java 21) | `javap -verbose` sobre una clase, o el log del compilador |
| 3 | Arranca en perfil `local` y `/actuator/health` = `UP` | `curl` o el test de contexto |
| 4 | Sin dependencias de AWS | Revisión del árbol de dependencias |
| 5 | Test de carga de contexto verde | JUnit |
| 6 | Test ArchUnit verde | JUnit |
| 7 | El build falla con un JDK < 21 | Ejecutar `mvnw` con `JAVA_HOME` apuntando a un JDK 17: debe fallar |

#### Pruebas requeridas

| Prueba | Tipo | Qué verifica |
|---|---|---|
| `PizzeriaApplicationTests` | Contexto Spring | La aplicación arranca |
| `ArchitectureRulesTest` | ArchUnit (puro) | Las 5 reglas de dependencia entre capas |
| Verificación manual | Enforcer | Falla el build con JDK 17 |

#### Definition of Done

Además de la [DoD global](definition-of-done.md): el spike de §2 documenta el resultado, aunque sea negativo.

---

### US-002 · Composición local con Docker Compose — 5 puntos

#### Tareas

- [ ] `infra/local/docker-compose.yml` con servicio `db` y servicio `app`
- [ ] `infra/local/docker-compose.test.yml` para el perfil de integración
- [ ] `.env.example` (plantilla); `.env` en `.gitignore`
- [ ] `scripts/dev-up.ps1` y `scripts/dev-down.ps1`
- [ ] Healthcheck de PostgreSQL con `pg_isready`
- [ ] Healthcheck de la aplicación contra `/actuator/health`
- [ ] Volumen nombrado para los datos
- [ ] PostgreSQL **sin** puerto publicado en el host

#### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `docker compose up --build` deja ambos servicios `healthy` en < 90 s | `docker compose ps` |
| 2 | Los datos sobreviven a un reinicio | Parar y arrancar el servicio |
| 3 | El puerto de la base de datos no está publicado | `docker compose ps` |
| 4 | Ninguna credencial real en el repositorio | Revisión del diff + `.gitignore` |
| 5 | `docker compose down -v` deja el entorno limpio | Ejecutar y comprobar |

#### Pruebas requeridas

Job de CI que levanta el entorno, consulta `/actuator/health` y lo destruye.

---

### US-003 — Esquema inicial y Flyway — 5 puntos

#### Tareas

- [ ] Añadir `flyway-database-postgresql` (el soporte de PostgreSQL es un módulo aparte)
- [ ] `spring.flyway.clean-disabled=true`
- [ ] Escribir `V1__baseline_schema.sql` con las 8 tablas
- [ ] Restricciones `CHECK` de importes y cantidades
- [ ] `UNIQUE (pizza_id, size)` en `pizza_variants`
- [ ] `UNIQUE (order_id)` en `cash_payments` (impide el doble cobro)
- [ ] Claves foráneas con `RESTRICT` donde hay historial
- [ ] Índices para las consultas reales
- [ ] Coherencia de `orders.total_amount`: resolver con *trigger* (PostgreSQL no admite subconsultas en `CHECK`)
- [ ] `db/migration/README.md` con la política de migraciones inmutables
- [ ] `ddl-auto=validate` en todos los perfiles
- [ ] Test de integración con Testcontainers

#### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | Las 8 tablas se crean con sus restricciones | Test sobre `information_schema` |
| 2 | Arranca sobre base vacía y `flyway:validate` queda limpio | Test |
| 3 | `ddl-auto=validate` en todos los perfiles | Revisión de configuración |
| 4 | Ninguna migración `V` editada tras su merge | Revisión de commits |
| 5 | El esquema se aplica desde cero sin errores | Test de integración |

#### Notas de diseño

- `NUMERIC(12,0)` para el dinero (COP no tiene decimales), `TIMESTAMPTZ` para las fechas, `UUID` generado por la aplicación.
- Decisión **D1 resuelta**: la moneda es **COP** sin decimales. Ver [`decisions-pending.md`](decisions-pending.md).
- Enumerados como `CHECK` con literales, **no** como tipos `ENUM` de PostgreSQL.

---

### US-004 — Estrategia global de errores y validación — 3 puntos

#### Tareas

- [ ] `common/domain/DomainException` (abstracta, sin dependencias)
- [ ] `common/web/GlobalExceptionHandler` con `ProblemDetail`
- [ ] Ampliar el problema con `code`, `timestamp` y `traceId`
- [ ] Mapeo de: validación (400), autenticación (401), acceso denegado (403), no encontrado (404), conflicto (409), regla de negocio (422), fallo inesperado (500)
- [ ] Catálogo de códigos de error documentado
- [ ] Controlador de pruebas para poder testear el.handler

#### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | Todo error sale como `application/problem+json` con `code` estable | Test por tipo de excepción |
| 2 | `MethodArgumentNotValidException` → 400 con campo y mensaje | Test |
| 3 | Ningún mensaje incluye traza de pila en producción | Test |
| 4 | El mensaje 500 no revela detalles internos | Test |

---

### US-005 · Documentación OpenAPI — 3 puntos

#### Tareas

- [ ] `springdoc-openapi-starter-webmvc-ui` **3.x**
- [ ] `OpenApiConfig` en `config/` con el esquema de seguridad `bearer`
- [ ] Habilitar Swagger UI sólo en `local` y `dev`
- [ ] Crear `docs/architecture/api-contract.md` inicial

#### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `/api-docs` disponible en `local`, deshabilitado en `prod` | Test de perfil |
| 2 | Autenticación por `bearer` documentada | Revisión del JSON |
| 3 | `api-contract.md` alineado | Revisión |

**Plan B:** si springdoc 3.x falla con Spring Boot 4.1.x, fijar Spring Boot 4.0.x o documentar el contrato a mano. **Decisión en US-001, no aquí.**

---

### US-006 — Pipeline base en GitHub Actions — 5 puntos

#### Tareas

- [ ] `.github/workflows/ci.yml`: `push` y `pull_request`
- [ ] `actions/setup-java@v5` con `distribution: temurin`, `java-version: '21'`, `cache: maven`
- [ ] Ejecución de `./mvnw verify` en modo batch
- [ ] `.github/workflows/ci-integration.yml`: job con Docker para Testcontainers
- [ ] Paso de escaneo de secretos
- [ ] `dependabot.yml` para Maven y Docker
- [ ] Distintivo en el `README`

#### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | El workflow usa JDK 21 y caché de Maven | Inspección del workflow |
| 2 | Ejecuta `./mvnw verify` | Ejecución real |
| 3 | El job de integración levanta Testcontainers | Ejecución real |
| 4 | Falla si hay secretos | Prueba con un secreto de prueba en una rama |

---

### US-080 (parcial) · Actuator, salud y métricas — parte de S1

#### Tareas

- [ ] `spring-boot-starter-actuator` activado
- [ ] `management.endpoints.web.exposure.include=health,info,metrics`
- [ ] Health check de PostgreSQL incluido

#### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `/actuator/health` responde `UP` con el detalle de la base de datos | `curl` |
| 2 | `/actuator/env` **no** está expuesto | Test |

> El resto de US-080 (métricas, restricciones por perfil) se cierra en S5.

---

## 2. Spike de verificación de la pila (parte de US-001)

**Objetivo:** confirmar antes de construir nada sobre ella que la combinación elegida funciona.

| Prueba | Criterio de éxito | Plan B si falla |
|---|---|---|
| Compilar con Spring Boot 4.1.x + JDK 21 + `--release 21` | Compila sin errores | Fijar Spring Boot 4.0.x (Java 21 sigue válido) |
| Test unitario con JUnit 6 + Mockito | Pasa sin `-Dnet.bytebuddy.experimental` | Revisar la versión de Byte Buddy gestionada por el BOM |
| Test de integración con Testcontainers 2.x | Levanta PostgreSQL 17 correctamente | Verificar la nomenclatura de módulos (`testcontainers-postgresql`, paquete `org.testcontainers.postgresql`) |
| springdoc 3.x con Boot 4.1.x | Arranca y expone `/api-docs` | US-005 pasa a documentación manual |
| Maven 3.10.0 sobre JDK 21 | `./mvnw verify` completo | Revisar la configuración del wrapper |

**El resultado del spike se documenta en `docs/architecture/architecture.md`**, aunque el resultado sea "todo funciona": es la evidencia que sostiene la decisión.

---

## 3. Archivos que se crean o modifican en el sprint

```text
backend/
├── pom.xml                                      (nuevo)
├── mvnw, mvnw.cmd, .mvn/wrapper/                (nuevo)
├── .gitignore                                   (nuevo)
├── .editorconfig                                (nuevo)
├── Dockerfile                                    (US-070, S8)
└── src/main/java/com/pizzeria/
    ├── PizzeriaApplication.java                  (nuevo)
    ├── common/domain/DomainException.java        (nuevo)
    └── common/web/GlobalExceptionHandler.java    (US-004)
└── src/main/resources/
    ├── application.yml                           (nuevo)
    ├── application-local.yml                     (nuevo)
    ├── application-test.yml                      (nuevo)
    └── db/migration/V1__baseline_schema.sql      (US-003)
└── src/test/java/com/pizzeria/
    ├── PizzeriaApplicationTests.java             (nuevo)
    └── architecture/ArchitectureRulesTest.java   (nuevo)

infra/local/
├── docker-compose.yml                            (nuevo)
└── docker-compose.test.yml                       (nuevo)

.github/workflows/
├── ci.yml                                       (nuevo)
└── ci-integration.yml                           (nuevo)

.github/dependabot.yml                           (nuevo)
scripts/
├── dev-up.ps1, dev-down.ps1                      (nuevo)

docs/architecture/
├── architecture.md                               (nuevo)
├── api-contract.md                               (nuevo)
└── data-model.md                                 (nuevo)

docs/development/spring-conventions.md            (actualizado)
docs/planning/backlog.md                          (estados actualizados)
docs/planning/roadmap.md                          (S1 → Hecho)
```

---

## 4. Criterio de salida del sprint

- [ ] `./mvnw verify` en verde, **con la salida reportada literalmente**
- [ ] `docker compose -f infra/local/docker-compose.yml up -d --build` deja ambos servicios `healthy`
- [ ] `flyway:migrate` crea las 8 tablas desde cero
- [ ] Test ArchUnit verde sobre el esqueleto
- [ ] Workflow de CI en verde en un `push` real
- [ ] `gitleaks detect` sin hallazgos
- [ ] `docs/architecture/architecture.md` con la tabla de stack y el resultado del spike
- [ ] `backlog.md` con US-001…US-006 en `Hecho`
- [ ] Ningún secreto en el repositorio

**Si el sprint termina pero alguno de estos puntos falla, el sprint no está cerrado.**

---

## 5. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| — | — | — | Sprint no iniciado |