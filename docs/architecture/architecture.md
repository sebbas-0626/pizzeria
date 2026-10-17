# Arquitectura del sistema y tabla de stack

> **Qué gobierna:** el stack tecnológico elegido con sus versiones y el diseño de capas de alto nivel.
> **Precedencia:** `requirements-v1.0.md` → ADR → este documento → `openapi.json` → convenciones → código (ver `AGENTS.md` §1).
> **Alcance:** backend `pizzeria-backend`. El frontend no forma parte del MVP (ver `frontend/README.md`).
> **Reglas de código:** este documento **no** duplica las convenciones; las cita y las manda a su fuente.
> Última verificación de la pila: **2026-10-11** (Sprint 1, US-001).

---

## 0. Estado de la documentación de arquitectura

| Documento | Existe | Contiene |
|---|---|---|
| `architecture.md` (este) | ✅ | Stack con versiones, diseño de capas de alto nivel, resultado del spike |
| [`estructuras.md`](estructuras.md) | ✅ | Referencia: las dos estructuras hexagonales posibles (la adoptada y la alternativa) y la arquitectura global del proyecto. **No es fuente de verdad** |
| `data-model.dbml` | ✅ | Boceto del modelo de datos y diferencias frente al SQL desplegable |
| `aws-local.md` *(en `docs/development/`)* | ✅ | Emulador Floci: servicios usados, arranque y trampas del SDK |
| `data-model.md` | ❌ Sprint 5 (US-044) | Esquema de datos y su justificación |
| `api-contract.md` | ✅ Sprint 1 (US-005) | Contrato HTTP: convenciones, autenticación, catálogo de errores y endpoints |
| `security.md` | ❌ Sprint 5 (US-044) | Modelo de amenazas y controles |
| `openapi.json` | ❌ Sprint 5 (US-045) | Snapshot verificable del contrato |
| ADR | [`ADR-0004`](../adr/0004-estructura-de-paquetes-por-capas.md), [`ADR-0005`](../adr/0005-los-puertos-se-declaran-en-el-dominio.md), [`ADR-0006`](../adr/0006-floci-como-emulador-aws.md), [`ADR-0010`](../adr/0010-versiones-de-java-y-spring-boot.md) | ADR-0001, ADR-0002, ADR-0003 y ADR-0007…ADR-0009 están **referenciadas** en otros documentos pero aún no redactadas. No se enlazan aquí para no crear rutas rotas |

---

## 1. Contexto y forma del sistema

Una pizzería de **una sola sede**, con pedidos **para recogida presencial** y **pago en efectivo**. Sin envíos a domicilio, sin pasarelas de pago, sin múltiples ubicaciones.

| Decisión | Valor | Dónde está la fuente |
|---|---|---|
| Estilo arquitectónico | **Monolito con arquitectura hexagonal por capas** | [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md), `AGENTS.md` §3 |
| Unidad de despliegue | **Un único artefacto** (`pizzeria-backend.jar`) | `backend/pom.xml` |
| Capas | `domain` · `application` · `infrastructure` · `shared` | [ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md) §3 |
| Frontera de cada capa | `domain.port` · `application.{usecase,service,dto}` · `infrastructure.{adapter.in.web,adapter.out.*,config}` · `shared.{security,exception,util}` | `AGENTS.md` §3.1 |
| Dónde viven los puertos | `domain.port.out` (repositorios y servicios externos) | [ADR-0005](../adr/0005-los-puertos-se-declaran-en-el-dominio.md) |
| Dirección de dependencias | `infrastructure → application → domain`; el dominio no depende de nadie | [`spring-conventions.md` §1](../development/spring-conventions.md) |
| Moneda | **COP**, sin decimales: `BigDecimal` escala 0, `RoundingMode.UNNECESSARY`, `NUMERIC(12,0)` | `requirements-v1.0.md` RN-IM-05, [`conventions.md` §4](../development/conventions.md) |

### 1.1 Estructura del repositorio

```text
pizzeria/
├── backend/                 Aplicación Spring Boot (pom.xml, src/main, src/test)
├── frontend/                Placeholder declarado, fuera del MVP
├── docs/                    adr · architecture · development · planning · requirements
└── AGENTS.md                Reglas de desarrollo del repositorio
```

### 1.2 Capas y qué puede importar cada una

Normativa y completa en [`spring-conventions.md` §1](../development/spring-conventions.md); verificada en cada build por `ArchitectureRulesTest` (ArchUnit).

| Capa | Puede importar | Prohibido importar |
|---|---|---|
| `..domain..` | `java..` | Spring, Jakarta, JPA, Jackson, SDK AWS, Lombok |
| `..application..` | `..domain..`, `..application..` | `..infrastructure..`, `..shared..`, Spring Web, Spring Data, Jackson, SDK AWS |
| `..infrastructure.adapter.in.web..` | `..application..`, Spring Web/Security/Jackson | `..infrastructure.adapter.out..`, entidades JPA |
| `..infrastructure.adapter.out.persistence..` | `..domain.port.out..`, `..domain.model..`, Spring Data | `..infrastructure.adapter.in..`, `..application..` |
| `..infrastructure.adapter.out.storage..` / `..messaging..` | `..domain.port.out..`, SDK AWS v2 | `..infrastructure.adapter.in..`, `..domain.model..`, `..domain.exception..`, JPA |
| `..shared..` | `..domain.exception..`, `..shared..`, `..infrastructure..` | `..application..`, `..domain.model..`, `..domain.port..`, `..infrastructure.adapter.out..` |

Además, `ArchitectureRulesTest.noHayPaquetesHuerfanos` fija la lista de paquetes de primer nivel: sólo `domain`, `application`, `infrastructure` y `shared`.

**Excepciones documentadas:** `application` puede usar `@Transactional` (la frontera transaccional es una decisión de aplicación) y `java.time.Clock` (abstracción del JDK, no de Spring).

---

## 2. Tabla de stack

Todas las versiones de esta tabla son **verificadas**: se leyeron del árbol de dependencias resuelto (`./mvnw dependency:list`) y del artefacto compilado el **2026-10-11**. Donde el número cambia dentro de la misma línea menor se indica la línea (`4.1.x`), no el parche, para no bloquear el desarrollo.

### 2.1 Plataforma, lenguaje y build

| Componente | Tecnología | Versión | Dónde se fija | Motivo |
|---|---|---|---|---|
| Lenguaje objetivo | Java (Eclipse Temurin) | **21 LTS** (`21.0.12.1` en el entorno verificado) | `pom.xml` `<java.version>21</java>` | LTS con mayor adopción del ecosistema; decisión del responsable ([ADR-0010 §7](../adr/0010-versiones-de-java-y-spring-boot.md)) |
| Bytecode | `--release 21` (major version **65**) | 21 | `maven.compiler.release` | Compilar sobre un JDK superior sin emitir bytecode superior |
| Características *preview* | **Desactivadas** | — | `<maven.compiler.enablePreview>false</maven.compiler.enablePreview>` | Requisito explícito del responsable |
| Build y gestor de dependencias | Apache Maven | **3.10.0** | `.mvn/wrapper/maven-wrapper.properties` (`distributionUrl` + `distributionSha256Sum`) | Maven estable; Maven 4 no apto para producción ([ADR-0010 H12](../adr/0010-versiones-de-java-y-spring-boot.md)) |
| Wrapper | maven-wrapper | 3.3.4 (tipo `only-script`) | `maven-wrapper.properties` | Reproducibilidad: la versión de Maven la decide el repositorio, no la máquina |
| POM padre | `spring-boot-starter-parent` | **4.1.1** | `pom.xml` `<parent>` | Aporta el BOM que fija todo el resto del stack |
| Reglas de entorno | `maven-enforcer-plugin` | 3.5.0 | `pom.xml` | `requireJavaVersion [21,)`, `requireMavenVersion [3.9.9,)`, sin versiones duplicadas |
| Nombre del artefacto | `pizzeria-backend` | `0.1.0-SNAPSHOT` | `pom.xml` | — |
| Repositorio | **Monorepo**, sin submódulos | — | — | Estructura fija desde el principio |

### 2.2 Framework, web y validación

| Componente | Tecnología | Versión | Dónde se fija | Motivo |
|---|---|---|---|---|
| Framework | **Spring Boot** | **4.1.1** | BOM (POM padre) | Línea estable actual; 4.2 sigue en *milestones* ([ADR-0010 H6](../adr/0010-versiones-de-java-y-spring-boot.md)) |
| Base del framework | Spring Framework | 7.0.9 | BOM | Transitiva; compatible con Java 17–26 |
| Controlador web | Spring MVC (`spring-webmvc`) | 7.0.9 | BOM | Web MVC sobre servlet; es lo que exige `spring-boot-starter-web` |
| Servidor | Tomcat embebido | 11.0.24 | BOM | Sin servidor externo que desplegar |
| Serialización JSON | **Jackson 3** (`tools.jackson.*`) | 3.1.5 | BOM | Spring Boot 4 usa Jackson 3; `jackson-annotations` 2.21 sigue en `com.fasterxml.jackson.annotation` |
| Validación de formato | Jakarta Validation | 3.1.1 | BOM | Formato y obligatoriedad en los DTO |
| Implementación | Hibernate Validator | 9.1.3.Final | BOM | Implementación de referencia de Bean Validation |
| Documentación de API | springdoc-openapi (Swagger UI) | **3.1.1** | `pom.xml` (versión explícita) | Genera el OpenAPI 3.1 desde el código. Construido sobre Boot 4.1.0. **Apagado por defecto**, encendido sólo en `local` |
| Errores HTTP | `ProblemDetail` (RFC 9457) | Framework | US-004 | Formato único `application/problem+json` con `code` estable |
| Lombok | **No se usa** | — | — | Código explícito ([`conventions.md` §2](../development/conventions.md)) |

### 2.3 Persistencia

| Componente | Tecnología | Versión | Dónde se fija | Motivo |
|---|---|---|---|---|
| Base de datos | **PostgreSQL** | **17** (`17.11` en la prueba de integración; imagen `postgres:17-alpine`) | `application-*.yml`, Testcontainers | `NUMERIC`, `CHECK`, `TIMESTAMPTZ` y `UNIQUE` son lo que hace cumplir el negocio en la base |
| Driver JDBC | `org.postgresql:postgresql` | 42.7.13 | BOM | ÚNico driver oficial mantenido por la comunidad de PostgreSQL |
| Pool de conexiones | HikariCP | 7.0.2 | BOM | Pool por defecto de Spring Boot |
| ORM | Hibernate ORM | 7.4.5.Final | BOM |sólo en `infrastructure.adapter.out.persistence`, nunca en el dominio (ADR-0003) |
| Acceso a datos | Spring Data JPA | 4.1.1 | BOM | Repositorios como **puertos de salida** declarados en `domain.port.out` (ADR-0005) |
| API de persistencia | `jakarta.persistence-api` | 3.2.0 | BOM | Estandarización de JPA |
| Migraciones | Flyway Core | 12.4.0 | BOM | El esquema es **sólo** de Flyway |
| Soporte PostgreSQL | `flyway-database-postgresql` | 12.4.0 | BOM | Módulo separado desde Flyway 10 |
| Generación de esquema | `ddl-auto=validate` | — | `application.yml`, `application-test.yml` | Nunca `create` ni `update` |
| Dinero en base de datos | `NUMERIC(12,0)` | — | `V1__baseline_schema.sql` (US-003) | COP no tiene decimales: la igualdad `line_total = unit_price * quantity` es exacta |

### 2.4 Operación y observabilidad

| Componente | Tecnología | Versión | Dónde se fija | Motivo |
|---|---|---|---|---|
| Endpoints de operación | Spring Boot Actuator | 4.1.1 | BOM | Salud y métricas |
| Exposición | `health,info,metrics` | — | `application.yml` | `env` y `configprops` **no** se exponen: pueden filtrar secretos |
| Métricas | Micrometer | 1.17.1 | BOM | Se cierra en US-080 (S5) |
| Logging | SLF4J + Logback | 2.0.18 / 1.5.38 | BOM | `log4j-to-slf4j` 2.25.5 puentea cualquier log4j del entorno |
| Configuración YAML | SnakeYAML | 2.6 | BOM | — |
| Archivos de log | **JSON estructurado** en `local` y `prod` | — | US-043 | El patrón redacta `password`, `token` y `authorization` |

### 2.5 Pruebas

| Componente | Tecnología | Versión | Dónde se fija | Motivo |
|---|---|---|---|---|
| Motor de tests | **JUnit 6** (`junit-jupiter`, `junit-platform`) | 6.0.3 | BOM | Estándar de la línea Spring Boot 4 |
| Dobles | Mockito | 5.23.0 | BOM | Con `@MockitoBean`, nunca `@MockBean` (obsoleto en Boot 3.4+) |
| Motor de los dobles | Byte Buddy / Byte Buddy Agent | 1.18.11 | BOM | Soporte nativo de la clase 65; no hace falta `-Dnet.bytebuddy.experimental` ([ADR-0010 H9](../adr/0010-versiones-de-java-y-spring-boot.md)) |
| Creación de instancias sin constructor | Objenesis | 3.3 | BOM | — |
| Aserciones | AssertJ | 3.27.7 | BOM | — |
| Aserciones (alternativa) | Hamcrest | 3.0 | BOM | — |
| Esperas | Awaitility | 4.3.0 | BOM | Prohíbe `Thread.sleep` en tests |
| Reglas de arquitectura | ArchUnit (**módulo `core`**) | 1.5.1 | `pom.xml` (versión explícita) | Spring Boot no lo gestiona. Se usa `core` y no `archunit-junit5` para no atar el proyecto al motor de ArchUnit frente a JUnit 6 |
| Contenedores | **Testcontainers** | **2.0.5** | BOM | PostgreSQL real, nunca H2 |
| Módulos de Testcontainers | `testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`, `testcontainers-jdbc`, `testcontainers-database-commons` | 2.0.5 | `pom.xml` | En 2.0 los artefactos llevan prefijo `testcontainers-` y las clases se movieron a paquetes por motor |
| Integración con Spring | `spring-boot-testcontainers` | 4.1.1 | BOM | `@ServiceConnection` inyecta el datasource sin escribir URLs |
| Tests unitarios | maven-surefire (del POM padre) | — | POM padre | Ejecuta `*Test` |
| Tests de integración | maven-failsafe | 3.5.6 | `pom.xml` | Ejecuta `*IT` en la fase `verify`, después de empaquetar; necesita Docker |
| Cobertura | JaCoCo | 0.8.13 | `pom.xml` | Informe por paquete; umbrales por capa en US-044 |
| Docker (entorno local) | Docker Engine | 24.0.7 | Máquina de desarrollo | Requisito de Testcontainers |

### 2.6 AWS (previsto, **aún no está en el `pom.xml`**)

| Componente | Tecnología | Versión | Entra en | Nota |
|---|---|---|---|---|
| Emulador local | **Floci** | — | US-051 | Puerto 4566, credenciales de prueba, `endpointOverride` por variable de entorno (ADR-0006, pendiente de redactar) |
| SDK | AWS SDK for Java **v2** | — | US-050 / US-060 | **Prohibido** en `domain` y `application`; sólo en `infrastructure.adapter.out.storage` y `..messaging` |
| Almacenamiento | S3 (imágenes de pizza) | — | US-050…US-052 | Puerto `ImageStorage` (`store` / `presign` / `delete`) |
| Mensajería | SQS + *transactional outbox* | — | US-060…US-062 | A partir de S7; hoy **no existe** en el proyecto |

**Invariante comprobable:** con el perfil por defecto el backend arranca **sin ningún servicio de AWS**. No hay una sola dependencia `software.amazon.awssdk` en el `pom.xml`.

### 2.7 Infraestructura y herramientas (previsto)

| Componente | Tecnología | Versión | Entra en | Nota |
|---|---|---|---|---|
| Entorno local | Docker Compose | — | US-002 | PostgreSQL **sin** puerto publicado en el host |
| Imagen de build | `eclipse-temurin:21-jdk` | 21 | US-070 | Multi-stage, usuario no root, tag fijo |
| Imagen de runtime | `eclipse-temurin:21-jre` | 21 | US-070 | Alternativa: `21-jre-alpine`, `21-jdk-ubi9-minimal` o runtime con `jlink` |
| CI | GitHub Actions `actions/setup-java@v5` | — | US-006 | `distribution: temurin`, `java-version: '21'`, `cache: maven` |
| Actualizaciones | Dependabot (Maven y Docker) | — | US-006 | Actualiza el parche, no la línea de versión |
| Escaneo de secretos | Gitleaks | — | US-006 | Sin hallazgos obligatorios |
| Despliegue | EC2 (contenedor) | — | US-072 | **Bloqueado**: requiere autorización escrita del responsable |

### 2.8 Frontend

| Componente | Decisión |
|---|---|
| Stack | **Ninguno.** No hay decisión de framework ni versión |
| Motivo | Fuera del MVP; US-083 (S3 + CloudFront) está marcada **Descartada** en el backlog |

---

## 3. Política de versionado

| Regla | Detalle |
|---|---|
| **BOM primero** | Todo lo que gestione el BOM de Spring Boot se declara **sin `<version>`**: PostgreSQL, Flyway, Testcontainers, Mockito, JUnit, Jackson, Hibernate, Actuator |
| **Versión explícita sólo si hace falta** | ArchUnit 1.5.1 y los plugins (Enforcer 3.5.0, Failsafe 3.5.6, JaCoCo 0.8.13), con el motivo escrito al lado |
| **Línea, no parche** | Se fija la versión de *feature* (4.1.x, 17, 21) y no el parche, para no bloquear el desarrollo |
| **Java 21 es el objetivo, no el mínimo** | `[21,)` en el Enforcer es un **piso**, no una ventana cerrada: cualquier JDK ≥ 21 vale. El JDK 17 sólo aparece como **escenario de fallo** en el criterio 7 de US-001; nunca se compila ni se ejecuta el proyecto con él |
| **Maven sí se fija entera** | 3.10.0 exacta, con SHA-256, porque es la única forma de garantizar que dos máquinas construyen lo mismo |
| **Tags de imagen fijados** | `postgres:17-alpine`, `eclipse-temurin:21-jre`. **Nunca** `latest` |
| **Una ADR por cambio** | Cambiar Java o Spring Boot exige una ADR **nueva** que sustituya a la anterior; una ADR aceptada no se edita |
| **Dependabot** | Automatiza el parche; la subida de línea es una decisión humana con ADR |

---

## 4. Spike de verificación de la pila (US-001)

**Objetivo:** comprobar, antes de construir nada, que la combinación elegida funciona. Ejecutado el **2026-10-11** con `./mvnw -B verify` sobre **Temurin 21.0.12.1**, Docker 24.0.7, Maven 3.10.0.

| Prueba | Criterio de éxito | Resultado |
|---|---|---|
| Resolver dependencias con Spring Boot 4.1.1 | Sin conflictos | ✅ `BUILD SUCCESS` |
| Enforcement de Java 21 | `RequireJavaVersion` pasa | ✅ `Rule 0: …RequireJavaVersion passed` |
| Enforcement de Maven | `RequireMavenVersion` pasa | ✅ `Rule 1: …RequireMavenVersion passed` |
| Compilación con `--release 21` | Bytecode 65 | ✅ `javac [debug parameters release 21]`; `javap -verbose` → `major version: 65` |
| Test unitario (ArchUnit, JUnit 6) | Pasa sin `-Dnet.bytebuddy.experimental` | ✅ `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` |
| Test de integración con **Testcontainers 2.x** | Levanta PostgreSQL 17 | ✅ `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; `Database version: 17.11` |
| Empaquetado | JAR ejecutable | ✅ `Building jar: …\target\pizzeria-backend.jar` |
| Nomenclatura de Testcontainers 2.0 | Módulos y paquetes nuevos | ✅ `org.testcontainers:testcontainers-postgresql`, clase `org.testcontainers.postgresql.PostgreSQLContainer` |
| Maven 3.10.0 sobre JDK 21 | `./mvnw verify` completo | ✅ `BUILD SUCCESS` en 22,757 s |
| springdoc-openapi 3.x con Boot 4.1.1 | Arranca y expone `/api-docs` | ✅ **Ejecutado.** `springdoc-openapi:3.1.1` se construye sobre `spring-boot-starter-parent` **4.1.0** (`swagger-ui` 5.32.14, `swagger-core-jakarta` 2.2.55). `BUILD SUCCESS` con la dependencia y, en runtime, `/v3/api-docs` → **200 en `local`** y **404 sin perfil** |
| Sin dependencias de AWS en el perfil por defecto | Árbol sin `software.amazon.awssdk` | ✅ Confirmado en `dependency:list` |

### 4.1 Hallazgos que quedan abiertos

| # | Hallazgo | Efecto | Dónde se atiende |
|---|---|---|---|
| 1 | El `mvnw` de sólo-script **necesita el comando `unzip`** cuando `distributionSha256Sum` está fijado. Sin `unzip` cambia la URL a `.tar.gz` y la validación falla aunque el hash sea correcto | La imagen `maven:3.10.0-eclipse-temurin-21` no lo trae | Instalar `unzip` en la imagen de build de CI, o eliminar el checksum (se prefiere instalarlo) |
| 2 | Mockito 5.23 se autoengancha como agente para el *inline mock maker*: `Mockito is currently self-attaching… This will no longer work in future releases of the JDK` | Aviso, no error en Java 21. Pasará a error en un JDK futuro | Declarar Byte Buddy Agent como agente del build cuando se actualice el JDK. Anotado en `spring-conventions.md` §6 |
| 3 | springdoc 3.x «sólo declara compatibilidad con Boot 4.x y su matriz enumera 4.0.x» (riesgo de [ADR-0010 §5](../adr/0010-versiones-de-java-y-spring-boot.md)) | **Cerrado**: 3.1.1 se construye sobre Boot 4.1.0, compila y sirve el contrato | US-005: esquema `bearer` y `api-contract.md` |

---

## 5. Elementos del stack pendientes de añadir

| Dependencia | Entra en | Por qué **no** está todavía |
|---|---|---|
| `spring-boot-starter-security` + `oauth2-resource-server` | US-021 | Activaría la autoconfiguración de seguridad por defecto y bloquearía endpoints (incluido Actuator) sin que exista configuración intencionada |
| `springdoc-openapi-starter-webmvc-ui` 3.x | US-005 | **Ya instalado** (3.1.1, §2.2). Falta el esquema `bearer` en `OpenApiConfig` y `api-contract.md` |
| Nimbus JOSE JWT + `spring-security-oauth2-jose` | US-021 | Emisor de JWT RS256 como **puerto** + adaptador |
| `software.amazon.awssdk:*` (S3 y SQS) | US-050, US-060 | El perfil por defecto debe arrancar sin AWS |
| Testcontainers para S3/SQS | US-051, US-062 | Verificar si `LocalStackContainer` es compatible con 2.0.x o hace falta `GenericContainer` |

---

## 6. Riesgos y criterios de reevaluación

| Riesgo | Estado | Criterio de reevaluación |
|---|---|---|
| Java 25 / 27 | Java 21 mantenido | Spring Boot estable con esa versión, Byte Buddy con soporte nativo de la clase correspondiente **sin** flag experimental, y Mockito y Testcontainers publicados contra ella. Entonces **ADR-0010 se sustituye** por una nueva |
| Spring Boot 4.2 (orientado a Java 27) | En *milestones* | GA estable más Spring Framework 7.1 GA |
| springdoc con Boot 4.1.x | No probado | US-005 |
| Nomenclatura de Testcontainers 2.x | Verificada en el spike | Revisar en cada subida de línea |
| Amazon Bedrock / agentes de IA en el tooling | Fuera del stack de la aplicación | — |

---

## 7. Cómo se verifica esta tabla

```powershell
# Versiones realmente resueltas (incluye transitivas)
./mvnw dependency:list "-DoutputFile=target\deps.txt" "-DincludeScope=test"

# Build completo: compila, pruebas unitarias, integración y empaquetado
./mvnw -B verify

# Bytecode generado (debe ser 65)
javap -verbose -cp target\classes com.pizzeria.PizzeriaApplication

# Sin dependencias de AWS
./mvnw dependency:tree "-Dincludes=software.amazon.awssdk:*"
```

Si alguna versión de la §2 deja de coincidir con la resuelta, **esta tabla está mal**: se corrige aquí en el mismo cambio.

---

## 8. Registro de cambios

| Fecha | Cambio |
|---|---|
| 2026-10-11 | Documento creado en US-001. Tabla de stack publicada y verificada contra el árbol de dependencias resuelto y el build real (`BUILD SUCCESS`, 6 pruebas unitarias + 1 de integración) |