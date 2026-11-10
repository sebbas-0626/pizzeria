# Revisión del CD: qué falla y por qué

> **Alcance:** `.github/workflows/cd.yml` (`Build, Push y Deploy a Floci`), su imagen
> (`backend/Dockerfile`) y la infraestructura que referencia (`infra/floci/`).
>
> **Fecha:** 2026-11-08 · **Base:** `develop` (pendiente de commit) · **Rama del CD:** `master`
>
> **Cómo se comprobó:** lectura del workflow y **ejecución real** del mismo flujo contra
> `floci/floci:2.4.0` en Windows. La evidencia de cada prueba está en
> [`floci-laboratorio.md`](floci-laboratorio.md).

---

## 1. Resumen

El workflow está bien pensado en lo que respecta a **cuándo** corre (sólo tras un CI verde, con
`head_sha`, permisos mínimos y concurrencia). El problema es **qué** despliega y **cómo** habla con
Floci.

Hoy el CD **no puede terminar en verde** y, si terminara, **no desplegaría nada útil**. Hay cuatro
bloqueos duros, todos reproducidos:

| # | Bloqueo | Efecto |
|---|---|---|
| **B1** | `backend/Dockerfile` es una plantilla (`ubuntu` + `top -b`) | Publica una imagen que no ejecuta la aplicación |
| **B2** | `infra/floci/task-definition.json` no existe | El paso 5 falla siempre |
| **B3** | El healthcheck del servicio usa `curl`, que la imagen no trae | El job se queda esperando y falla antes de cualquier paso |
| **B4** | El registro ECR no está en la red de Floci y el tag no es el que Floci espera | `docker login`/`push` devuelven 503 |

Y un problema de fondo: **el proyecto no tiene nada que desplegar todavía**. El `pom.xml` no incluye
el SDK de AWS, no hay endpoint de subida de imágenes y la historia del Dockerfile de producción
(US-070) no está hecha. El CD se adelantó al código.

---

## 2. Bloqueos, con evidencia

### B1 · La imagen es un marcador de posición · ✅ corregido el 2026-11-08 (ver §9)

`backend/Dockerfile` completo:

```dockerfile
FROM ubuntu:latest
LABEL authors="sebba"

ENTRYPOINT ["top", "-b"]
```

Consecuencias:

- No arranca Spring Boot, no escucha en `8080`, no responde `/actuator/health`.
- `ubuntu:latest` incumple la regla de no usar tags flotantes.
- El paso «Verificar deploy» sólo hace `docker ps` y `ecs list-tasks`: **un contenedor `top` en
  marcha pasaría la verificación**. Es un falso verde por diseño.
- Además, `backend/.dockerignore` excluye `target/`. Cuando US-070 escriba el Dockerfile real,
  `COPY target/*.jar` **no funcionará**: el contexto no incluye el JAR. O se hace *multi-stage*
  (compila dentro) o se saca `target/` del `.dockerignore`.

**Arreglo:** el Dockerfile de producción es US-070: *multi-stage* (`maven:3.10.0-eclipse-temurin-21`
para construir, `eclipse-temurin:21-jre` para ejecutar), usuario no root, `HEALTHCHECK` real y
`ENV` del puerto. Hasta entonces el CD no tiene sentido.

### B2 · Falta la definición de tarea que el workflow inyecta

```yaml
sed -i 's|__FLOCI_ENDPOINT__|http://floci:4566|g' infra/floci/task-definition.json
```

`infra/floci/` no existe en el repositorio (verificado con `Test-Path infra/floci` → `False`), y el
directorio `infra/` sólo contiene `local/`. `sed` falla y `register-task-definition` también.

**Arreglo:** crear `infra/floci/task-definition.json` versionado, con la familia, el contenedor, el
puerto y el marcador `__FLOCI_ENDPOINT__`. Pero antes hay que decidir **ECS o EC2** (§4).

### B3 · El healthcheck no puede funcionar

```yaml
options: >-
  --health-cmd "curl -f http://localhost:4566/ || exit 1"
```

La imagen nativa de Floci no trae `curl` ni `wget`:

```text
/bin/sh: line 1: curl: command not found   → ExitCode 1
```

Resultado real de arrancar el servicio con ese healthcheck: `unhealthy`, y GitHub Actions no ejecuta
ningún paso hasta que el servicio esté sano.

**Arreglo verificado** (la imagen sí trae `bash`):

```yaml
options: >-
  --health-cmd "bash -c 'exec 3<>/dev/tcp/127.0.0.1/4566'"
  --health-interval 10s
  --health-timeout 5s
  --health-retries 12
  --health-start-period 10s
```

### B4 · El push a ECR no es el que Floci espera

Tres errores encadenados:

1. **Sin el socket de Docker**, ECR no puede arrancar su registro:

   ```text
   The ECR backing registry is unavailable because no Docker daemon is reachable from Floci.
   ```

2. **Aun con socket**, si el registro (`floci-aws-ecr-registry`) queda en `bridge` y Floci en otra
   red, `/v2/` devuelve `503`. Un `services:` de GitHub Actions no crea esa red compartida ni fija
   `FLOCI_SERVICES_ECR_DOCKER_NETWORK`.

3. **El tag no coincide con el URI que Floci publica.** El workflow usa
   `localhost:4566/pizzeria-backend`, pero Floci devuelve:

   - estilo `hostname` (por defecto): `000000000000.dkr.ecr.us-east-1.localhost:4566/pizzeria-backend`
   - estilo `path`: `localhost:4566/000000000000/us-east-1/pizzeria-backend`

   Con el tag del workflow, las capas acaban enrutadas al manejador de S3:

   ```text
   error parsing HTTP 400 response body: ...
   <Error><Code>InvalidArgument</Code><Message>POST requires either ?uploads, ?uploadId,
   ?restore or ?select parameter.</Message>...
   ```

   Y en Windows el estilo `hostname` tampoco resuelve:

   ```text
   lookup 000000000000.dkr.ecr.us-east-1.localhost: no such host
   ```

**Arreglo:** capturar el `repositoryUri` que devuelve `create-repository` (o
`describe-repositories`) y etiquetar con **ese** URI. Y fijar `FLOCI_SERVICES_ECR_URI_STYLE=path`
para que funcione igual en Windows y en el runner Linux.

---

## 3. Hallazgos menores

| # | Hallazgo | Por qué importa | Arreglo |
|---|---|---|---|
| M1 | `image: floci/floci:latest` | Viola la regla de no usar `latest` (`AGENTS.md` §2, ADR-0006 §5) | `floci/floci:2.4.0` |
| M2 | `concurrency.cancel-in-progress: true` | Cancelar un deploy a mitad deja el registro o el servicio a medias | `false` en CD |
| M3 | `ecr create-repository ... 2>/dev/null \|\| echo` | Un fallo real (permisos, red) se confunde con «ya existe» | Comprobar el código de error de AWS (`RepositoryAlreadyExistsException`) |
| M4 | `create-service ... \|\| update-service ...` | Un fallo de definición de tarea se convierte en un `update-service` que falla después, con el error equivocado | `describe-services` y decidir explícitamente |
| M5 | Sin `timeout-minutes` | Un `docker login` colgado consume 6 h de runner | Añadirlo por job |
| M6 | Verificación = `list-tasks` + `docker ps` | No prueba que la aplicación responda | `curl -fsS http://localhost:8080/actuator/health` con reintentos |
| M7 | `branches: [master]` en `workflow_run` | Filtra por la rama del *workflow ejecutado*; un PR desde una rama llamada `master` también entra | Añadir `github.event.workflow_run.head_branch == 'master'` |
| M8 | `configure-aws-credentials@v4` | Es innecesario con Floci y deja credenciales en el entorno; cualquier comando sin `--endpoint-url` apuntaría a AWS real | Quitar el paso y fijar `AWS_ENDPOINT_URL`; mantener `--endpoint-url` explícito |
| M9 | `sed -i` sobre el JSON del repo | Modifica el checkout; funciona, pero impide reutilizar el fichero tal cual | Generar el JSON con `envsubst` o `jq` a un archivo temporal |
| M10 | El endpoint inyectado es `http://floci:4566` | Ese nombre sólo resuelve en la red de Compose, no desde la tarea ECS ni desde un contenedor de EC2, que viven en otras redes | Definir red explícita (`FLOCI_SERVICES_DOCKER_NETWORK`) o usar `host.docker.internal` |

---

## 4. La contradicción de fondo: ECS no es lo que el proyecto decidió

`aws-local.md` §2 dice, literalmente, que **no se usa ECS**:

> | **ECS / EKS** | El despliegue es un contenedor en EC2, orquestado por systemd |

Y `ADR-0006` (§5 y §6) marca el despliegue en EC2 como **US-072, bloqueada** hasta que haya
autorización escrita de AWS real. `AGENTS.md` §4.9 lo exige.

El CD, en cambio:

- despliega en **ECS** en cada push a `master`,
- crea clúster, definición de tarea y servicio,
- y lo llama «Deploy a Floci».

No es sólo una incoherencia documental: es una **decisión de arquitectura de despliegue tomada en un
YAML**, sin ADR. Hay que elegir una de las dos y dejarla escrita:

- **Opción A (recomendada):** el CD sigue el plan. Se queda en verde hasta US-070/071 y, cuando
  exista, publica la imagen en ECR y la ejecuta como **contenedor** (el mecanismo que decida US-072,
  hoy EC2 + systemd). ECS desaparece del workflow.
- **Opción B:** se cambia el plan a ECS. Entonces hace falta un ADR nuevo que sustituya la parte
  correspondiente de ADR-0006 y actualizar `aws-local.md` §2, la tabla de servicios y US-072.

Mientras no se decida, el pipeline no debería desplegar automáticamente. La forma barata de cumplirlo:
`on: workflow_dispatch` (manual) y quitar `workflow_run`, hasta que US-070 y US-071 estén hechas.

---

## 5. Qué haría falta, en orden

1. **Decidir ECS vs EC2** (§4) y escribirlo donde corresponda (ADR o corrección de ADR-0006).
2. ~~**US-070:** Dockerfile *multi-stage* real + `.dockerignore` coherente + `HEALTHCHECK`.~~
   ✅ Hecho el 2026-11-08 (ver §9).
3. **US-071:** publicar la imagen en ECR con el URI devuelto por Floci y `uri-style=path`.
4. **Rehacer el arranque de Floci en el job** sin `services:`:

   ```yaml
   - name: Floci
     run: |
       docker network create floci-net
       docker run -d --name floci --network floci-net \
         -p 4566:4566 \
         -e FLOCI_SERVICES_ECR_URI_STYLE=path \
         -e FLOCI_SERVICES_ECR_DOCKER_NETWORK=floci-net \
         -v /var/run/docker.sock:/var/run/docker.sock \
         floci/floci:2.4.0
   ```

5. **Verificar de verdad:** `/actuator/health` del contenedor desplegado, no `docker ps`.
6. **Quitar los `||` que esconden errores** y añadir `timeout-minutes`.
7. **Fijar `2.4.0`** y actualizar `aws-local.md` §3 y §9 (ya hecho en la parte de hechos verificados).

> El paso 1 no lo puede decidir un agente: toca arquitectura y AWS. Los pasos 2–7 son mecánicos una
> vez decidido.

---

## 6. Cómo se reprodujo (sin GitHub)

El flujo del CD se ejecutó a mano contra Floci:

```powershell
docker network create floci-net
docker run -d --name floci --network floci-net -p 4566:4566 `
  -e FLOCI_SERVICES_ECR_URI_STYLE=path `
  -e FLOCI_SERVICES_ECR_DOCKER_NETWORK=floci-net `
  -v //var/run/docker.sock:/var/run/docker.sock `
  floci/floci:2.4.0

$env:AWS_ACCESS_KEY_ID='test'; $env:AWS_SECRET_ACCESS_KEY='test'; $env:AWS_DEFAULT_REGION='us-east-1'
$e = '--endpoint-url=http://localhost:4566'

aws $e ecr create-repository --repository-name pizzeria-backend --query repository.repositoryUri --output text
# → localhost:4566/000000000000/us-east-1/pizzeria-backend

aws $e ecr get-login-password | docker login --username AWS --password-stdin localhost:4566
docker tag nginx:alpine localhost:4566/000000000000/us-east-1/pizzeria-backend:prueba
docker push localhost:4566/000000000000/us-east-1/pizzeria-backend:prueba
```

Resultado: `Login Succeeded`, push aceptado y `list-images` mostrando el tag. El mismo flujo con los
valores que usa hoy `cd.yml` (sin red, sin socket o con `localhost:4566/pizzeria-backend`) falla con
los errores de §2.

---

## 7. Veredicto

| Aspecto | Estado |
|---|---|
| Disparadores y permisos | ✅ Correctos |
| Reproducibilidad de la imagen | ❌ Placeholder (`top -b`) |
| Infraestructura referenciada | ❌ Falta `infra/floci/task-definition.json` |
| Comunicación con Floci | ❌ Healthcheck imposible, red y URI incorrectos |
| Verificación del despliegue | ❌ No comprueba que la app responda |
| Coherencia con ADR-0006 y el backlog | ❌ Despliega en ECS algo bloqueado y no previsto |

**El CD no está listo para `master`.** Dejarlo como está garantiza un rojo en el próximo push (o, peor,
un verde que no despliega nada). Ver el plan de §5.

---

## 8. Estado aplicado (2026-11-08)

Se aplicó la **opción A**: el pipeline deja de desplegar hasta que existan US-070 y US-071. El archivo
`.github/workflows/cd.yml` quedó así:

| Antes | Ahora |
|---|---|
| Se disparaba solo con `workflow_run` de CI sobre `master` | **Manual**: `workflow_dispatch` |
| Desplegaba en ECS (decisión sin ADR, contra `aws-local.md` §2) | **No despliega**: valida Floci + ECR |
| `floci/floci:latest` | `floci/floci:2.4.0` |
| `services:` con healthcheck `curl` | `docker run` en `floci-net`, espera con el CLI de AWS |
| Tag `localhost:4566/pizzeria-backend` | URI devuelto por `describe-repositories` |
| `configure-aws-credentials` con claves falsas | `env:` + salvaguarda que exige endpoint local |
| Sin límite de tiempo | `timeout-minutes: 20` |
| Limpieza inexistente | Limpieza `always()` de Floci, su registro y la red |

El flujo exacto de los pasos se ejecutó a mano (mismos flags, puerto de laboratorio) antes de
escribirlo: Floci respondió al primer intento, el URI fue
`localhost:4572/000000000000/us-east-1/pizzeria-backend`, `Login Succeeded`, y el push de
`public.ecr.aws/docker/library/alpine:3.20` quedó consultable por tag
(`sha256:6c2a9711…`, 3.630.933 bytes).

Lo que el job **sí** demuestra en cada ejecución manual:

1. Floci arranca con el socket y la red correctos.
2. ECR crea el repositorio y devuelve un URI utilizable.
3. El login funciona (la ruta `/v2/` no devuelve 503).
4. Un `docker push` real llega al registro y se puede consultar por tag.

Lo que **no** hace, a propósito: construir la imagen de la aplicación (sigue publicando `alpine` como
prueba de la ruta de datos), registrar una definición de tarea, crear un servicio ni tocar AWS real.

### Cuando se complete US-071

El sitio para completarlo es el mismo archivo:

1. Sustituir el push de `alpine` por `docker build -t "$URI:$GITHUB_SHA" backend/` (la imagen ya
   existe: US-070, ver §9).
2. Añadir `docker push` con `latest` y con `$GITHUB_SHA`.
3. Cuando US-072 decida el mecanismo (hoy: contenedor en EC2 + systemd), añadir el despliegue **con
   aprobación** (`environment:` protegido), no antes.

> `docs/architecture/estructuras.md` §2.5 dice que «el CD no existe todavía: es S8». Sigue siendo
> cierto: lo que hay ahora es un laboratorio manual, no un despliegue.

---

## 9. Dockerfile corregido (US-070)

`backend/Dockerfile` ya no es una plantilla. Es *multi-stage*:

| Etapa | Imagen | Qué hace |
|---|---|---|
| build | `maven:3.10.0-eclipse-temurin-21` | Copia el `pom.xml`, resuelve dependencias y compila (`-DskipTests`: las pruebas las corre CI con Testcontainers, que no caben dentro de un `docker build`) |
| runtime | `eclipse-temurin:21-jre` | Sólo JRE + `app.jar`; usuario del sistema `app`; `EXPOSE 8080` |

Decisiones:

- **Sin perfil de Spring activo.** `local` enciende Swagger y el detalle del health, y US-005 prohíbe
  publicarlos fuera de local. La configuración entra por variables (`SPRING_DATASOURCE_*`, `JWT_SECRET`).
- **`HEALTHCHECK` sin `curl`.** La imagen de Temurin no lo trae: se usa `bash` + `/dev/tcp` y se
  comprueba `"status":"UP"` del actuator, no sólo que el puerto escuche.
- **Sin `latest`.** Maven 3.10.0 y Java 21, las versiones de ADR-0010.
- **`target/` sigue ignorado.** Al compilar dentro de la imagen, no hace falta sacarlo del
  `.dockerignore` ni atar la imagen al `target/` del host.

### Evidencia de la prueba (2026-11-08)

```text
docker build -t pizzeria-backend:local backend/        → BUILD SUCCESS · imagen 386 MB
docker run ... -p 8099:8080 pizzeria-backend:local     → Started PizzeriaApplication in 9.8 s

curl localhost:8099/actuator/health
  → 200 {"groups":["liveness","readiness"],"status":"UP"}

docker inspect pizzeria-img --format '{{.State.Health.Status}}'
  → healthy

docker exec pizzeria-img id
  → uid=999(app) gid=999(app) groups=999(app)

curl localhost:8099/v3/api-docs      → 404   (springdoc apagado fuera de local)
curl localhost:8099/actuator/env     → 404   (no expuesto)
```

Queda pendiente que el workflow de laboratorio construya **esta** imagen en vez de `alpine`; es un
cambio de una línea en `.github/workflows/cd.yml` y pertenece a US-071.
