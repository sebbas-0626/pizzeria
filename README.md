<div align="center">

# 🍕 Pizzeria

**Backend de una pizzería de una sola sede: catálogo, pedidos para recogida presencial y pago en efectivo.**

[![CI](https://github.com/sebbas-0626/pizzeria/actions/workflows/ci.yml/badge.svg)](https://github.com/sebbas-0626/pizzeria/actions/workflows/ci.yml)
[![Integración](https://github.com/sebbas-0626/pizzeria/actions/workflows/ci-integration.yml/badge.svg)](https://github.com/sebbas-0626/pizzeria/actions/workflows/ci-integration.yml)
[![Java 21](https://img.shields.io/badge/java-21_LTS-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/temurin/releases/?version=21)
[![Spring Boot 4.1](https://img.shields.io/badge/spring--boot-4.1.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

Java 21 LTS · Spring Boot 4.1 · PostgreSQL 17 · Maven 3.10 · Flyway 12 · JUnit 6 · Testcontainers 2.0

</div>

---

> **Estado: en desarrollo — Sprint 1 (S1).** La API todavía **no expone ningún endpoint**: este sprint
> construye los cimientos (stack, entorno local, esquema de datos y errores). El primer endpoint llega
> con US-010. Lo que ya funciona se puede comprobar en [Qué hay hecho](#qué-hay-hecho).

---

## Qué es

Una API REST para tomar pedidos de pizzas **para recoger en el local** y pagarlos **en efectivo**.

**No existe, y es una decisión de alcance, no una carencia:**

| No hay | Por qué |
|---|---|
| Envíos a domicilio | Fuera del alcance acordado |
| Pasarelas de pago | Sólo efectivo en el MVP |
| Pantalla de cocina | Fuera del alcance |
| Varias sedes | Una sola sede |
| Frontend | Fuera del MVP. Ver [`frontend/README.md`](frontend/README.md) |

---

## Arrancar en local

Con **Docker Desktop** abierto y **JDK 21** instalado:

```powershell
./scripts/dev-up.ps1          # levanta PostgreSQL y la aplicación
```

La primera vez descarga dependencias y compila dentro del contenedor: puede tardar unos minutos.
Después:

| Qué | Dónde |
|---|---|
| API | <http://localhost:8080> |
| Estado | <http://localhost:8080/actuator/health> |
| Documentación interactiva | <http://localhost:8080/swagger-ui/index.html> |

Para parar:

```powershell
./scripts/dev-down.ps1            # conserva los datos
./scripts/dev-down.ps1 -Volumen   # destruye también los datos
```

### Docker sin los scripts

Los scripts sólo empaquetan esto, con la ventaja de crear el `.env` si falta:

```powershell
Copy-Item infra/local/.env.example infra/local/.env   # sólo si no existe
docker compose -f infra/local/docker-compose.yml up --build -d --wait
docker compose -f infra/local/docker-compose.yml ps
docker compose -f infra/local/docker-compose.yml down            # conserva los datos
docker compose -f infra/local/docker-compose.yml down -v         # destruye también los datos
```

Para usar la imagen de Maven suelta, **siempre con comando**:

```powershell
docker run --rm -v "${PWD}/backend:/workspace" -w /workspace maven:3.10.0-eclipse-temurin-21 mvn -B -ntp verify
```

> **No pulses «Run» sobre `maven:3.10.0-eclipse-temurin-21` en Docker Desktop.** Su `CMD` por defecto es
> `mvn` sin ningún objetivo, y eso falla con `NoGoalSpecifiedException`. No es un fallo del proyecto: es
> `mvn` al que nadie le dijo qué construir. Los nombres `post-site` y `site-deploy` que salen en el
> mensaje no son goals que se estén invocando, son la lista de fases que Maven imprime como ayuda.
> El contenedor que sobra se quita con `docker container prune`.

### Sin Docker

```powershell
cd backend
$env:POSTGRES_HOST='localhost'; $env:POSTGRES_PORT='15432'
$env:POSTGRES_DB='pizzeria'; $env:POSTGRES_USER='pizzeria_dev'; $env:POSTGRES_PASSWORD='...'
./mvnw spring-boot:run '-Dspring-boot.run.profiles=local'
```

---

## Comandos

| Comando | Qué hace |
|---|---|
| `./mvnw verify` | Compila, pruebas unitarias, integración y empaqueta. **Todo en uno.** |
| `./mvnw -Dtest=Clase test` | Un test concreto |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` | Arranca en local |
| `./scripts/dev-up.ps1` | Levanta el entorno con Docker Compose |
| `./scripts/dev-down.ps1` | Lo para |
| `./scripts/db.ps1 -Accion seed` | Carga el catálogo de ejemplo (6 pizzas con los casos que interesan) |
| `./scripts/db.ps1 -Accion shell` | psql dentro del contenedor: el puerto de la base **no** se publica |
| `./scripts/db.ps1 -Accion backup` | Vuelca la base a `infra/local/backups/` (no versionada) |
| `./scripts/db.ps1 -Accion reset -Force` | Destruye el volumen y levanta de cero |
| [`api/bruno/`](api/bruno) | Colección de Postman: casos de éxito, de 400 y de 404 de cada endpoint |
| `docker compose -f infra/local/docker-compose.yml up --build -d --wait` | Lo mismo que `dev-up.ps1`, sin el script |
| `docker run --rm -v "${PWD}/backend:/workspace" -w /workspace maven:3.10.0-eclipse-temurin-21 mvn -B -ntp verify` | `verify` dentro de la imagen de Maven, sin Compose |

> **⚠️ Ojo con PostgreSQL.** Si tienes uno instalado en Windows, `localhost:5432` responde, pero **es otro
> servidor**: el de la aplicación va en un contenedor cuyo puerto no se publica. Para mirar los datos con
> DBeaver hay un stack de inspección en el `5433`; los datos, el ciclo de vida del volumen y los respaldos
> están en [`docs/development/entorno-local.md`](docs/development/entorno-local.md).

---

## Stack

| | |
|---|---|
| Java | **21 LTS** (Temurin), compilado con `--release 21` |
| Framework | **Spring Boot 4.1.x** · Spring Framework 7.0.x |
| Build | **Maven 3.10.0** vía Maven Wrapper con SHA-256 fijado |
| Base de datos | **PostgreSQL 17** · JDBC 42.7 · HikariCP |
| Persistencia | Hibernate ORM 7.4 · Spring Data JPA · **Flyway 12.4** |
| Pruebas | JUnit 6 · Mockito 5.23 · AssertJ · **Testcontainers 2.0** · ArchUnit · JaCoCo |
| Documentación | springdoc-openapi 3.1 (Swagger UI, **sólo en local**) |
| AWS | **Ninguno.** Sin S3 ni SQS: el MVP no depende de la nube |

**Sin Lombok.** Código explícito. **Sin `double`** para dinero: `BigDecimal` escala 0, porque el peso
colombiano no tiene decimales.

Tabla completa con la justificación de cada versión:
[`docs/architecture/architecture.md`](docs/architecture/architecture.md)

---

## Arquitectura

Monolito con arquitectura hexagonal por capas. Las dependencias apuntan en un solo sentido:

```text
infrastructure  ──►  application  ──►  domain
   (conoce a todos)    (orquesta)      (no conoce a nadie)

shared   capa transversal: traduce, registra, correlaciona
```

Bajo `com.pizzeria` sólo existen cuatro paquetes de primer nivel: `domain`, `application`,
`infrastructure` y `shared` ([ADR-0004](docs/adr/0004-estructura-de-paquetes-por-capas.md)).
Los puertos se declaran en `domain.port` y los implementa `infrastructure.adapter.out`
([ADR-0005](docs/adr/0005-los-puertos-se-declaran-en-el-dominio.md)).

**Reglas que el build verifica con ArchUnit**, no sólo en el documento:

| Capa | Prohibido |
|---|---|
| `domain` | Spring, JPA, Jackson, SDK de AWS, Lombok |
| `application` | `infrastructure`, `shared`, Spring Web, Spring Data, SDK de AWS |
| `infrastructure.adapter.in.web` | `infrastructure.adapter.out`, entidades JPA |
| `shared` | `application`, `domain.model`, `domain.port`, adaptadores de salida |

Detalle: [`docs/development/spring-conventions.md`](docs/development/spring-conventions.md)

---

## Documentación

| Documento | Para qué |
|---|---|
| [`docs/requirements/requirements-v1.0.md`](docs/requirements/requirements-v1.0.md) | Las reglas de negocio. **Fuente de verdad nº1** |
| [`docs/adr/`](docs/adr) | Por qué se eligió cada tecnología |
| [`docs/architecture/architecture.md`](docs/architecture/architecture.md) | Stack con versiones y diseño de capas |
| [`docs/architecture/estructuras.md`](docs/architecture/estructuras.md) | Las dos estructuras hexagonales posibles y la arquitectura global del proyecto |
| [`docs/architecture/api-contract.md`](docs/architecture/api-contract.md) | Contrato de la API: errores, autenticación, códigos |
| [`docs/development/conventions.md`](docs/development/conventions.md) | Cómo se escribe el código |
| [`docs/planning/`](docs/planning) | Qué falta y en qué orden |
| Swagger UI | Contrato vivo y ejecutable. Sólo en `local` |
| [`api/bruno/`](api/bruno) | Colección de Postman para probar los endpoints a mano, con sus tests |

---

## Qué hay hecho

| Sprint 1 | Estado |
|---|---|
| US-001 · Stack verificado sobre Java 21 | ✅ |
| US-002 · Entorno local con Docker Compose | ✅ |
| US-003 · Esquema de datos con Flyway (8 tablas) | ✅ |
| US-004 · Errores en `problem+json` con códigos estables | ✅ |
| US-005 · Documentación OpenAPI con esquema `bearer` | ✅ |
| US-006 · CI en GitHub Actions | ✅ |
| US-010…US-013 · Catálogo de pizzas | Pendiente (S2) |
| US-020…US-023 · Registro, login y autorización | Pendiente (S4) |
| US-030…US-038 · Pedidos y cobro | Pendiente (S3, S4) |

Seguimiento completo: [`docs/planning/backlog.md`](docs/planning/backlog.md)

---

## Reglas que no se saltan

1. **El servidor calcula todos los importes.** Si una petición manda `price` o `total`, se ignoran.
2. **Dinero en COP, sin decimales**, con `RoundingMode.UNNECESSARY`: una fracción es un error, no algo
   que redondear.
3. **Una clave ajena responde 404, no 403**, para no revelar que el recurso existe.
4. **No hay forma pública de obtener el rol `ADMIN`.** El registro siempre fuerza `CUSTOMER`.
5. **Nada de secretos en el repositorio.** `.env` está en `.gitignore` y CI escanea el historial.
6. **Un pedido no puede cobrarse dos veces.** Lo garantiza `UNIQUE (order_id)`, no el código.

---

## Licencia

Uso interno. Backend de un proyecto de portafolio.