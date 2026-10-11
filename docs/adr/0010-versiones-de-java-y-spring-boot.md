# ADR-0010 · Versiones de Java y Spring Boot

- **Estado:** Aceptada
- **Fecha:** 2026-10-11
- **Decidido por:** responsable del proyecto
- **Sprint:** S0 (Requisitos y diseño) → aplica a partir de S1
- **ADRs relacionados:** ADR-0009 (sin compilación nativa GraalVM en el MVP)
- **Decide:** versión de Java objetivo de compilación, pruebas y runtime, y la línea de Spring Boot
- **Enmendada:** 2026-10-11 — la recomendación inicial (Java 25 LTS) fue revisada por el responsable y se adoptó **Java 21 LTS** (ver §7)

---

## 1. Contexto

El proyecto necesita fijar una versión de Java **antes** de crear el `pom.xml`, porque esa decisión se propaga al compilador, a las imágenes de Docker, al pipeline de CI/CD y al entorno de despliegue.

Requisito explícito del responsable: **evaluar Java 25 LTS como versión objetivo inicial, evaluar Java 27 como alternativa experimental verificando primero su compatibilidad oficial con la versión estable de Spring Boot, Maven, Testcontainers, los plugins y las librerías elegidas**, y no confundir la versión *mínima* exigida por Spring Boot con la versión que usaremos.

## 2. Hechos verificados (2026-10-11)

| # | Hecho | Fuente |
|---|---|---|
| H1 | JDK 21 sigue siendo una **LTS** (GA 2023-09), con soporte hasta septiembre de 2026 bajo NFTC y luego bajo OTN | oracle.com/java/technologies/downloads |
| H2 | JDK 25 es la **última LTS** (GA 2025-09-16) | oracle.com/java/technologies/downloads, openjdk.org/projects/jdk/25 |
| H3 | JDK 27 salió en GA el **2026-09-15** y **no es LTS**; Oracle indica que se actualiza hasta marzo de 2027, cuando es sustituido por JDK 28 | openjdk.org/projects/jdk/27, nota de Oracle JDK 27 |
| H4 | **Spring Boot 4.1.1** (estable actual) *«requires at least Java 17 and is compatible with versions up to and including Java 26»* | docs.spring.io/spring-boot/system-requirements.html |
| H5 | El issue oficial **«Document support for Java 27» sigue abierto** desde 2026-08-24 | spring-projects/spring-boot#51424 |
| H6 | **Spring Boot 4.2** es la primera línea orientada a Java 27, pero está en *milestones* (4.2.0-M1 el 2026-08-20, 4.2.0-M2 el 2026-09-17, con Spring Framework 7.1.0-M2). GA esperada en noviembre de 2026 | spring.io/blog, wiki *Spring Boot 4.2 Release Notes* |
| H7 | Spring Framework 7.0 *«retains a JDK 17 baseline while at the same time **recommending JDK 25 as the latest LTS release**»* | notas de la release de Spring Framework 7.0 |
| H8 | Byte Buddy, motor de generación de código de Mockito, declara soporte nativo hasta **Java 25**; con versiones anteriores hay que recurrir a `-Dnet.bytebuddy.experimental=true` | raphw/byte-buddy README, issues #1852 y #1760 |
| H9 | Para Java 21 no hace falta ningún flag experimental: Byte Buddy 1.14.3+ da soporte nativo a la versión de clase 65 | raphw/byte-buddy README (tabla de versiones) |
| H10 | **Testcontainers 2.0.5** se publicó el **2026-04-20**, antes del GA de JDK 27 | testcontainers/testcontainers-java releases |
| H11 | **Temurin 21** dispone de imágenes oficiales `eclipse-temurin:21-jdk`, `21-jre`, `-alpine`, `-noble`, `-ubi9-minimal` y `-windowsservercore` | adoptium.net/installation/containers |
| H12 | Maven 3.10.0 es la versión estable recomendada; **Maven 4 está en release candidate** y Apache lo marca como no apto para producción | maven.apache.org/download.cgi, maven.apache.org/docs/history.html |
| H13 | Java 21 **no** publica imágenes UBI10 (esa exclusividad empieza en JDK 25), por lo que mantiene compatibilidad `x86-64-v2` en UBI9 | adoptium.net/news/2025/09/eclipse-temurin-25-available |

## 3. Opciones evaluadas

| Opción | Java | Spring Boot | Veredicto |
|---|---|---|---|
| **A** | **21 LTS** | 4.1.x | **Elegida** |
| B | 25 LTS | 4.1.x | Válida y más reciente; se descartó por decisión del responsable (ver §7) |
| C | 27 (no LTS) | 4.2 en milestone | Descartada: sin Spring Boot estable compatible, sin soporte nativo de Byte Buddy, Testcontainers no probado contra ella y soporte hasta marzo de 2027 |
| D | 17 | cualquiera | Descartada: es el *mínimo* de Spring Boot, no una elección de proyecto; EOL antes que el resto del stack |

### 3.1 La distinción clave: mínimo ≠ elegido

Spring Boot 4.1.1 **exige como mínimo Java 17**. Eso describe el límite inferior que Spring acepta, **no** la versión que vamos a usar. Elegimos la intersección de tres requisitos:

```
1. Ser una LTS con soporte largo                      → { 21, 25 }
2. Estar dentro del rango certificado de Spring Boot  → { 17 … 26 }
3. Tener soporte nativo en las librerías del proyecto → Java 21 ✔ (Java 27 ✘)
──────────────────────────────────────────────────────────────
Intersección estable y elegida                        → Java 21 LTS
```

## 4. Decisión

**Compilamos, probamos y ejecutamos con Java 21 LTS (Eclipse Temurin 21). Spring Boot 4.1.x. Maven 3.10.0 vía Maven Wrapper. Sin características *preview* de Java.**

| Ámbito | Ajuste | Nota |
|---|---|---|
| `pom.xml` | `<java.version>21</java.version>` | El parent de Spring Boot lo traduce a `maven.compiler.release=21` |
| Bytecode | `--release 21` | Permite compilar sobre un JDK superior y emitir bytecode 21 |
| Maven Wrapper | 3.10.0 | Maven 4 no apto para producción (H12) |
| Enforcer | `requireJavaVersion [21,)` | Fija el **mínimo**. Con 21, Spring Boot admite 17–26, así que cualquier JDK ≥21 es válido y no se restringe a una ventana |
| Preview | Sin `--enable-preview` ni `<enablePreview>` | Requisito explícito del responsable |
| Preview en JDK 21 | Descartadas: *String Templates*, *Sequenced Collections* y *Pattern Matching for switch* en modo preview | Ninguna se usa |
| Byte Buddy / Mockito | Los gestionados por el BOM de Spring Boot (soporte nativo de clase 65) | No hace falta `net.bytebuddy.experimental` (H9) |
| Docker (build) | `FROM eclipse-temurin:21-jdk` | |
| Docker (runtime) | `FROM eclipse-temurin:21-jre` | Alternativa: `21-jre-alpine`, `21-jdk-ubi9-minimal` o runtime con `jlink` |
| Actualización de parches | Dependabot configurado para Maven y Docker | Se fija la versión de *feature*, no el parche, para no bloquear el desarrollo |
| CI (`ci.yml`, `integration.yml`) | `actions/setup-java@v5`, `distribution: temurin`, `java-version: '21'`, `cache: maven` | Matriz de una sola versión en el MVP |
| Despliegue | Imagen con runtime 21; no depender del JDK de la AMI | |

### 4.1 Características de Java 21 que sí se pueden usar

Records, `sealed`, *pattern matching for switch* (final en 21), text blocks, `Stream.toList()`, `java.net.http.HttpClient`, **hilos virtuales (JEP 444, final en 21)** y `SequencedCollection` en su forma final cuando exista.

El uso de hilos virtuales queda **acotado al adaptador de entrada HTTP y a tareas de E/S**; no se usan en el dominio ni para paralelizar la lógica de negocio.

## 5. Consecuencias

**Positivas**

- Stack sobre versiones estables y certificadas, sin banderas experimentales en ninguna parte del build.
- Java 21 es la LTS con mayor adopción en el ecosistema Java empresarial: máxima compatibilidad con librerías, agentes de IA y documentación existente.
- No bloquea una futura actualización: al compilar con `--release 21`, cambiar a 25 consistirá en ajustar cuatro valores (`java.version`, imagen Docker, `java-version` en CI y el Enforcer).
- Se conservan las imágenes UBI9 con soporte `x86-64-v2` (H13), lo que simplifica el despliegue en EC2.

**Negativas / costes asumidos**

- Se renuncia a las novedades de Java 25 (muchas aún en *preview*), entre ellas las primitivas vectoriales y la mejora de `ScopedValue`/`StructuredTaskScope`.
- En el Sprint 8 habrá que revisar si la AMI de EC2 trae un JDK distinto al 21; la solución sigue siendo ejecutar siempre en contenedor.

**Riesgos**

- springdoc-openapi 3.x declara compatibilidad con Spring Boot 4.x, pero su matriz oficial sólo enumera Boot 4.0.x. Verificación en el spike de US-001 con plan B documentado (desactivar OpenAPI y documentar el contrato a mano).
- Testcontainers 2.0 cambió los nombres de módulo (`org.testcontainers:mysql` → `org.testcontainers:testcontainers-mysql`; `org.testcontainers.containers.PostgreSQLContainer` → `org.testcontainers.postgresql.PostgreSQLContainer`). Verificación en el mismo spike.

## 6. Criterios de reevaluación (Java 25 / 27)

Revisión en el Sprint 9 y en cuanto se cumplan las condiciones:

- [ ] Spring Boot con Java 25 o 27 en su rango documentado y en versión estable.
- [ ] Byte Buddy con soporte nativo de la versión de clase correspondiente **sin** `net.bytebuddy.experimental`.
- [ ] Mockito y Testcontainers con versión probada y publicada contra esa versión.

Si se cumplen, **esta ADR se sustituye por una nueva** (no se edita una ADR aceptada) y se actualizan los puntos del §4.

## 7. Enmienda: Java 25 → Java 21 (2026-10-11)

La recomendación inicial de esta ADR fue **Java 25 LTS**. El responsable revisó los datos y decidió usar **Java 21 LTS**, por estos motivos:

1. **Compatibilidad máxima del ecosistema**: Java 21 es la LTS con mayor despliegue en Spring Boot empresarial; reduce la fricción con documentación, librerías de terceros y herramientas.
2. **Prudencia de calendario**: JDK 21 está en soporte comercial extendido; JDK 25 es más reciente y su ecosistema (imágenes, agentes, librerías) aún se está consolidando.
3. **Suficiencia técnica**: el proyecto no necesita las primitivas vectoriales ni las mejoras de concurrencia de JDK 25. Los hilos virtuales, landed en 21, cubren el caso de uso relevante (I/O en el adaptador HTTP).
4. **Reversibilidad**: `--release 21` mantiene abierta la puerta a 25 o 27 con un cambio mínimo (ver §6).

Esta enmienda **no altera ninguna otra conclusión** de la ADR: Java 27 sigue descartado, y el resto de versiones (Spring Boot 4.1.x, Maven 3.10.0, PostgreSQL, Flyway, Testcontainers 2.x, Floci) se mantienen.

## 8. Consecuencias para el resto de la planificación

- En `docs/planning/backlog.md` la US-001 incluye el spike de verificación de la matriz (Spring Boot 4.1.x + JDK 21 + Testcontainers 2.x + Mockito + springdoc).
- `docs/architecture/architecture.md` publica una tabla de stack con estas versiones y su justificación.
- Si el spike obligara a caer a Spring Boot 3.5.x, **Java 21 seguiría siendo válido** (Boot 3.5.x declara 17–25): sólo cambiaría la línea de Spring Boot, no el JDK.

## 9. Fuentes

- `https://docs.spring.io/spring-boot/system-requirements.html` (rango de Java de Spring Boot 4.1.1)
- `https://spring.io/projects/spring-boot` y `https://spring.io/blog/2026/06/10/spring-boot-4`
- `https://spring.io/blog/2026/08/20/spring-boot-4-2-0-m1-available-now` y wiki *Spring Boot 4.2 Release Notes*
- `https://github.com/spring-projects/spring-boot/issues/51424` (*Document support for Java 27*)
- Notas de la release de Spring Framework 7.0 (wiki `spring-projects/spring-framework`)
- `https://openjdk.org/projects/jdk/25/`, `https://openjdk.org/projects/jdk/27/`, `https://openjdk.org/projects/jdk/21/`
- `https://www.oracle.com/java/technologies/downloads/` y nota de Oracle JDK 27 (2026-09-15)
- `https://adoptium.net/installation/containers`, `https://adoptium.net/news/2026/10/eclipse-temurin-27-available`, `https://adoptium.net/news/2025/09/eclipse-temurin-25-available`
- `https://github.com/raphw/byte-buddy` (tabla de versiones soportadas) e issues #1852 y #1760
- `https://github.com/testcontainers/testcontainers-java/releases` (2.0.0 y 2.0.5)
- `https://maven.apache.org/download.cgi` y `https://maven.apache.org/docs/history.html`

## 10. Registro de aprobación

| | |
|---|---|
| Recomendación técnica inicial | Java 25 LTS + Spring Boot 4.1.x |
| Decisión final | **Java 21 LTS** + Spring Boot 4.1.x + Maven 3.10.0 |
| Estado | **Aceptada** (con enmienda §7) |
| Fecha | 2026-10-11 |