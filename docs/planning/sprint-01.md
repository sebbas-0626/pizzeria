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
| **JDK 21 (Temurin) instalado y `JAVA_HOME` definido** | ✅ Hecho — `Temurin-21.0.12.1+1`, `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\` |
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

### Verificación del stack ejecutada de nuevo el 2026-10-11 (local)

Con Temurin 21.0.12.1 ya instalado y `JAVA_HOME` definido, `./mvnw -B verify` completa en verde:

```text
Rule 0: RequireJavaVersion passed
Rule 1: RequireMavenVersion passed
[INFO] Compiling 2 source files with javac [debug parameters release 21] to target\classes
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.pizzeria.architecture.ArchitectureRulesTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.pizzeria.PizzeriaApplicationIT
Database version: 17.11
[INFO] BUILD SUCCESS
[INFO] Total time:  22.757 s
```

El fallo anterior (`Unable to find main class`) ya no aparece. El resultado del spike está publicado en
[`architecture.md` §4](../architecture/architecture.md), junto con dos hallazgos abiertos: el requisito de
`unzip` en la imagen de build y el autoenganche de agente de Mockito, que un JDK futuro rechazará.

### Entorno detectado (2026-10-11, revisado tras instalar Temurin 21)

| Comprobación | Resultado | Consecuencia |
|---|---|---|
| `java -version` | **Temurin 21.0.12.1+1 (LTS)** ✅ | Compila, prueba y ejecuta en el JDK del ADR |
| `JAVA_HOME` | **Definido** → `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\` ✅ | Maven usa el JDK correcto y no el del sistema |
| `mvn` | **No instalado** | Se usa el Maven Wrapper 3.10.0, que se auto-instala en la primera ejecución |
| `docker` | 24.0.7 ✅ | Correcto para Testcontainers |

**Divergencia resuelta.** Antes de esta corrección el entorno tenía Oracle JDK 22.0.2 sin `JAVA_HOME`: el
Enforcer `[21,)` lo aceptaba, de modo que el build no fallaba pero las pruebas se ejecutaban en 22 mientras
CI usa 21. Esa divergencia es exactamente la que produce «funciona en mi máquina».

**Sobre el rango `[21,)`.** Es intencionadamente una ventana abierta y no una versión exacta: Spring Boot
admite de 17 a 26 y no queremos bloquear a nadie con un JDK superior. Pero la convención «local y CI con la
misma versión» se mantiene como norma del proyecto, y por eso hace falta el 21 instalado.

---

## 1. Historia a historia

### US-001 · Esqueleto del proyecto y verificación del stack — 3 puntos

**Objetivo:** comprobar que toda la pila técnica encaja **antes** de escribir una línea de negocio.

#### Tareas

- [x] Inicializar el repositorio Git en la raíz del monorepo
- [x] Crear `backend/pom.xml` con el parent de Spring Boot 4.1.x
- [x] Declara `<java.version>21</java.version>` (el parent lo traduce a `--release 21`)
- [x] Añadir `maven-enforcer-plugin` con `requireJavaVersion [21,)` y `requireMavenVersion`
- [x] Añadir Maven Wrapper **3.10.0** (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`) con checksum SHA-256 fijado
- [x] Generar **`.gitignore`** (el build ya genera `target/`, que no debe versionarse)
- [x] Crear `com.pizzeria.PizzeriaApplication`
- [x] Crear `common/domain/DomainException.java`
- [x] Crear `application.yml`, `application-local.yml`, `application-test.yml`
- [x] Activar `spring-boot-starter-actuator` y exponer `health` (US-080 parcial)
- [x] Escribir el test **ArchUnit** con las 5 reglas de dependencia
- [x] Escribir el test de carga de contexto → es `PizzeriaApplicationIT`, **no** `PizzeriaApplicationTests`: la carga de contexto completa necesita un PostgreSQL real, así que es un `*IT` que ejecuta Failsafe y no corre en cada `mvn test`. Cubre el criterio 5 igual, pero es más lenta
- [x] Crear `.editorconfig` y `.gitignore` (Java + `.env`)
- [x] Crear `docs/architecture/architecture.md` con la tabla de stack
- [x] **Spike de verificación** (ver §2) — 5 de 5 pruebas hechas, incluidas springdoc 3.1.1

#### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `./mvnw verify` compila, pasa los tests y empaqueta | Ejecutar y reportar la salida literal |
| 2 | El bytecode es versión 65 (Java 21) | `javap -verbose` sobre una clase, o el log del compilador |
| 3 | Arranca en perfil `local` y `/actuator/health` = `UP` | `curl` o el test de contexto |
| 4 | Sin dependencias de AWS | Revisión del árbol de dependencias |
| 5 | Test de carga de contexto verde | JUnit |
| 6 | Test ArchUnit verde | JUnit |
| 7 | El build falla con un JDK < 21 | Ejecutar `mvnw` con `JAVA_HOME` apuntando a un JDK 17: debe fallar. Es una prueba negativa; el trabajo real es siempre con 21 |

#### Pruebas requeridas

| Prueba | Tipo | Qué verifica |
|---|---|---|
| `PizzeriaApplicationIT` | Contexto Spring + Testcontainers | La aplicación arranca contra un PostgreSQL 17 real |
| `ArchitectureRulesTest` | ArchUnit (puro) | Las 5 reglas de dependencia entre capas |
| Verificación manual | Enforcer | Con un JDK antiguo el build **falla**. Prueba negativa: Java 17 no se usa nunca para trabajar |

#### Verificación de los criterios de aceptación (2026-10-11)

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | `./mvnw verify` compila, pasa los tests y empaqueta | ✅ | `BUILD SUCCESS` en 20,832 s · 6 unitarias + 1 de integración · `Building jar: pizzeria-backend.jar` |
| 2 | Bytecode versión 65 | ✅ | `javap -verbose` → `major version: 65`; log del compilador `javac [debug parameters release 21]` |
| 3 | Arranca en `local` y `/actuator/health` = `UP` | ✅ | `Started PizzeriaApplication in 2.854 seconds` → `HTTP 200` con `{"status":"UP","components":{"db":{"details":{"database":"PostgreSQL"},"status":"UP"},…}}` |
| 4 | Sin dependencias de AWS | ✅ | `dependency:list` sin un solo `software.amazon.awssdk` |
| 5 | Test de carga de contexto verde | ✅ | `Tests run: 1, Failures: 0, Errors: 0` en `PizzeriaApplicationIT` |
| 6 | Test ArchUnit verde | ✅ | `Tests run: 6, Failures: 0, Errors: 0` en `ArchitectureRulesTest` |
| 7 | El build falla con un JDK < 21 | ✅ | Con `JAVA_HOME=C:\Program Files\Java\jdk-17`: `BUILD FAILURE` + `RequireJavaVersion failed` |
| 8 | Spike de la matriz | ✅ | `springdoc-openapi:3.1.1` compila sobre Boot 4.1.1; `/v3/api-docs` → 200 en `local`, **404 sin perfil**; Swagger UI igual |

**Detalle del spike de springdoc.** Era la parte más delicada del sprint, así que se probó de verdad:
dependencia añadida, `./mvnw verify` en verde, arranque real y petición HTTP a los dos endpoints.

| Escenario | `/v3/api-docs` | `/swagger-ui/index.html` |
|---|---|---|
| Con perfil `local` | **HTTP 200** (OpenAPI 3.1.0) | **HTTP 200** |
| Sin perfil (simula producción) | **HTTP 404** | **HTTP 404** |

**Segundo fallo encontrado por el spike.** Al arrancar, springdoc avisó:

```text
WARN SpringDoc /v3/api-docs endpoint is enabled by default. To disable it in production…
```

Es decir: instalar la dependencia **publicaba el mapa completo de la API en todos los entornos**, producción
incluida, sin pedirlo. No lo detecta ningún test de arranque; sólo aparece si alguien lee el log.

**Corrección:** `application.yml` apaga `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled`;
`application-local.yml` los enciende. Verificado con los 404 de la tabla.

*Trampa evitada en el intento:* la primera corrección se escribió bajo `spring:` en vez de `springdoc:`.
Spring no habría dado ningún error — simplemente habría ignorado una propiedad que no existe y el endpoint
habría seguido publicado. Un fallo silencioso es el peor de los dos tipos de fallo, porque aparenta estar
arreglado: el build pasa, los tests pasan y el agujero sigue abierto.

**Cómo se ejecutó el criterio 3.** El perfil `local` exige un PostgreSQL real y lee las credenciales de
variables de entorno (`application-local.yml`); no hay H2 ni base en memoria. Para la comprobación se levantó
un contenedor **temporal** `postgres:17-alpine` en el puerto **15432** —el 5432 ya estaba ocupado por otro
servicio de la máquina y el 55432 cae en un rango de puertos reservado por Windows— con credenciales de
usar y tirar escritas **sólo** en el entorno de la consola, nunca en un archivo. Al terminar se detuvieron
el proceso y el contenedor. La versión permanente de este entorno es **US-002** (Docker Compose + `.env.example`).

#### Fallo encontrado y corregido por el criterio 7

La prueba negativa **desmintió a sí misma** en la primera ejecución. El build fallaba correctamente, pero
el mensaje mostraba información falsa:

```text
[ERROR] El proyecto exige Java 21 LTS (ADR-0010).
[ERROR] Versión detectada: 21.              ← FALSO: el JDK real era 17.0.12
```

**Causa:** dentro del mensaje, `${java.version}` resuelve a la propiedad `<java.version>21</java.version>` del
propio `pom.xml`, que **tapa** la propiedad de sistema del JVM con el mismo nombre. El mensaje anunciaba la
versión declarada, no la detectada, justo cuando más falta hacía saber cuál era.

**Corrección:** `backend/pom.xml` usa `${java.home}`, que nadie más define en el proyecto y por tanto sí
resuelve al JDK real. Verificado:

```text
[ERROR] El proyecto exige Java 21 LTS (ADR-0010).
[ERROR] JAVA_HOME detectado: C:\Program Files\Java\jdk-17.
[ERROR] Instala Eclipse Temurin 21 y define JAVA_HOME.
```

**Lección:** un criterio de aceptación que sólo dice «falla» no es suficiente. Hubo que **leer el mensaje**
para saber si el guardián estaba mintiendo o no. Cuando el mensaje de un error importa, se prueba el mensaje.

#### Definition of Done

Además de la [DoD global](definition-of-done.md): el spike de §2 documenta el resultado, aunque sea negativo.

---

### US-002 · Composición local con Docker Compose — 5 puntos

#### Tareas

- [x] `infra/local/docker-compose.yml` con servicio `db` y servicio `app`
- [x] `infra/local/docker-compose.test.yml` para el perfil de integración
- [x] `.env.example` (plantilla); `.env` en `.gitignore` → en `infra/local/`, ver nota
- [x] `scripts/dev-up.ps1` y `scripts/dev-down.ps1`
- [x] Healthcheck de PostgreSQL con `pg_isready`
- [x] Healthcheck de la aplicación contra `/actuator/health`
- [x] Volumen nombrado para los datos
- [x] PostgreSQL **sin** puerto publicado en el host

#### Criterios de aceptación

| # | Criterio | Verificación | Resultado (2026-10-11) |
|---|---|---|---|
| 1 | `docker compose up --build` deja ambos servicios `healthy` en < 90 s | `docker compose ps` | ✅ **60,8 s** en frío, **50,4 s** en caliente |
| 2 | Los datos sobreviven a un reinicio | Parar y arrancar el servicio | ✅ 1 fila antes de `restart db`, 1 fila después |
| 3 | El puerto de la base de datos no está publicado | `docker compose ps` | ✅ `5432/tcp` sin mapeo; `localhost:5432` no responde |
| 4 | Ninguna credencial real en el repositorio | Revisión del diff + `.gitignore` | ✅ `git check-ignore` marca `.env`; `.env.example` versionado con valores de mentira |
| 5 | `docker compose down -v` deja el entorno limpio | Ejecutar y comprobar | ✅ 0 contenedores y 0 volúmenes del proyecto |

#### Pruebas requeridas

Job de CI que levanta el entorno, consulta `/actuator/health` y lo destruye. **Pendiente: es US-006.**

#### Dos decisiones que conviene no deshacer sin leer el porqué

1. **`.env` vive en `infra/local/`, no en la raíz del repositorio.** Compose busca el archivo `.env` en el
   directorio del propio compose. En la raíz, el comando del criterio 1 fallaría pidiendo `POSTGRES_USER`
   sin una razón evidente. `scripts/dev-up.ps1` lo crea solo si falta.

2. **La app usa `mvn`, no `./mvnw`.** Comprobado dentro de `maven:3.10.0-eclipse-temurin-21`:
   `curl` ✅ · `wget` ✅ · `bash` ✅ · `mvn` 3.10.0 ✅ · **`unzip` ❌**. El wrapper necesita `unzip` para validar
   el SHA-256 de la distribución, así que dentro del contenedor fallaría. El `mvn` de la imagen es 3.10.0,
   exactamente la versión de ADR-0010, y el Enforcer la acepta.

Además: los cachés de Maven y de compilación (`maven-cache`, `app-target`) son volúmenes con nombre. Sin
ello, el `target/` del host —Windows— se llenaría de clases compiladas en Linux y el `./mvnw verify` local
empezaría a fallar con errores de permisos.

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
- [x] `docs/architecture/architecture.md` con la tabla de stack y el resultado del spike
- [ ] `backlog.md` con US-001…US-006 en `Hecho`
- [ ] Ningún secreto en el repositorio

**Si el sprint termina pero alguno de estos puntos falla, el sprint no está cerrado.**

---

## 5. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| — | — | — | Sprint no iniciado |