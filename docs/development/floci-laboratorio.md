# Laboratorio de Floci: lo verificado, con evidencia

> **Propósito:** dejar por escrito qué hace y qué no hace el emulador **con la versión que este
> proyecto va a fijar**, y cómo levantarlo en Windows sin tropezar con las mismas piedras.
>
> **Decisión:** [`ADR-0006`](../adr/0006-floci-como-emulador-aws.md)
> **Guía conceptual:** [`aws-local.md`](aws-local.md)
> **Revisión del pipeline que lo usa:** [`cd-revision.md`](cd-revision.md)
>
> **Fecha de la sesión:** 2026-11-08 · **Resultado:** S3, SQS, ECR, ECS, EC2, IAM, STS y CloudWatch Logs
> funcionan. Casi todo lo que `aws-local.md` daba por «pendiente de verificar» queda respondido.

---

## 1. Entorno de la prueba

| Elemento | Valor real observado |
|---|---|
| SO | Windows 11 + Docker Desktop |
| Motor Docker | Server `24.0.7`, `OSType=linux` |
| Imagen | `floci/floci:2.4.0` |
| Digest de la imagen | `sha256:5ee85db604c4aa74ac76e07ea5cc83086f37bb472bf3dbb97e34019e48efae9c` |
| AWS CLI | `aws-cli/2.27.55` (Python 3.13.4) |
| Puerto usado en el laboratorio | `4566` (el de la convención) |

### La versión que hay que fijar

Docker Hub publica `floci/floci:2.4.0` y, en el momento de la prueba, `latest` apuntaba **al mismo
digest** que `2.4.0`. Eso no es una garantía: `latest` cambiará. Se fija **`2.4.0`** y se acabó el
problema. Cumple la regla del proyecto de no usar `latest` (`AGENTS.md` §2, ADR-0006 §5).

```text
floci/floci:2.4.0  →  sha256:5ee85db604c4aa74ac76e07ea5cc83086f37bb472bf3dbb97e34019e48efae9c
```

---

## 2. Cómo se levanta (comando verificado)

El punto delicado es que **ECR, ECS y EC2 no son emulación de API en memoria: Floci lanza
contenedores Docker de verdad**. Para eso necesita el socket, una red propia y, en Windows, un
estilo de URI distinto del que trae por defecto.

```powershell
# 1. Red dedicada: el registro ECR que Floci arranca por detrás tiene que estar
#    en la MISMA red que Floci, o `docker login` devuelve 503.
docker network create floci-net

# 2. Floci con socket, red y estilo de URI `path` (obligatorio en Windows, ver §5).
docker run -d --name floci `
  --network floci-net `
  -p 4566:4566 `
  -e FLOCI_SERVICES_ECR_URI_STYLE=path `
  -e FLOCI_SERVICES_ECR_DOCKER_NETWORK=floci-net `
  -e FLOCI_STORAGE_MODE=hybrid `
  -v //var/run/docker.sock:/var/run/docker.sock `
  -v floci-data:/app/data `
  floci/floci:2.4.0

# 3. Sonda de salud. NO se puede usar curl: la imagen nativa no lo trae (ver §5.1).
docker run --rm --entrypoint bash floci/floci:2.4.0 -c 'exec 3<>/dev/tcp/127.0.0.1/4566'
```

Credenciales: las que sean. `test` / `test` funciona.

```powershell
$env:AWS_ACCESS_KEY_ID = 'test'
$env:AWS_SECRET_ACCESS_KEY = 'test'
$env:AWS_DEFAULT_REGION = 'us-east-1'
$env:AWS_ENDPOINT_URL = 'http://localhost:4566'
```

> **Sobre `FLOCI_STORAGE_MODE`.** La imagen publicada arranca en `memory`: los datos se pierden al
> reiniciar y **montar un volumen no cambia nada por sí solo**. Si se quiere que sobrevivan, hay que
> poner `FLOCI_STORAGE_MODE=hybrid` (o `persistent`) además del volumen. Ver §5.4.

---

## 3. Pruebas ejecutadas y resultado

Todas con `--endpoint-url=http://localhost:4566` y credenciales `test`/`test`.

### 3.1 S3 — funciona

```text
aws s3 mb s3://prueba-cd-2026                      → make_bucket: prueba-cd-2026
aws s3 cp probe.txt s3://prueba-cd-2026/probe.txt   → upload: ... (12 bytes)
aws s3 ls s3://prueba-cd-2026/                      → 2026-11-08 22:10:32  12  probe.txt
aws s3 presign s3://prueba-cd-2026/probe.txt --expires-in 300
  → http://localhost:4566/prueba-cd-2026/probe.txt?X-Amz-Algorithm=AWS4-HMAC-SHA256&...&X-Amz-Expires=300&...
GET a esa URL                                      → "hola floci"
```

- La URL prefirmada se sirve y **caduca** según `--expires-in` (300 s).
- El valor por defecto de caducidad es **3600 s** (`FLOCI_SERVICES_S3_DEFAULT_PRESIGN_EXPIRY_SECONDS`),
  configurable por variable de entorno, no por código.

### 3.2 SQS — funciona

```text
aws sqs create-queue --queue-name pedidos-cocina
  → http://localhost:4566/000000000000/pedidos-cocina
aws sqs send-message ... --message-body file://msg.json
  → {"MessageId":"4d199088-..."}
aws sqs receive-message ... --query 'Messages[0].Body' --output text
  → {"pedidoId":1,"estado":"PENDIENTE"}
```

El cuerpo JSON llega **intacto**. Ojo: si se pasa el JSON en línea desde PowerShell, PowerShell se come
las comillas (`{pedidoId:1}`). No es Floci; es el shell. Usar `file://` o escapar.

### 3.3 ECR — funciona, pero sólo con socket y red

Sin socket, el error es explícito:

```text
An error occurred (ServerException) when calling the ListImages operation:
The ECR backing registry is unavailable because no Docker daemon is reachable from Floci.
Repository metadata operations are supported; image push, pull and image queries require Docker.
```

Con socket pero con el registro en `bridge` (sin red compartida), el login falla:

```text
docker login --username AWS --password-stdin localhost:4566
  → login attempt to http://localhost:4566/v2/ failed with status: 503 Service Unavailable
```

Con socket **y** red compartida **y** `uri-style=path`:

```text
aws ecr create-repository --repository-name pizzeria-backend
  → repositoryUri: localhost:4566/000000000000/us-east-1/pizzeria-backend

aws ecr get-login-password | docker login --username AWS --password-stdin localhost:4566
  → Login Succeeded

docker tag nginx:alpine localhost:4566/000000000000/us-east-1/pizzeria-backend:web-test
docker push ...:web-test
  → web-test: digest: sha256:6bf533d0cb9aa03f79be3446b7db337c4c5cc9ba1412a38a8878ef9ead1a48f5 size: 1989

aws ecr list-images --repository-name pizzeria-backend
  → {"imageIds":[{"imageDigest":"sha256:6bf5...","imageTag":"web-test"}]}
```

Con el estilo por defecto (`hostname`), el URI es
`000000000000.dkr.ecr.us-east-1.localhost:4566/pizzeria-backend`, que **en Windows no resuelve**
(`lookup 000000000000.dkr.ecr.us-east-1.localhost: no such host`). Ver §5.2.

### 3.4 ECS — funciona (aunque el proyecto no lo use)

El `cd.yml` anterior desplegaba en ECS (hoy está desactivado, ver [`cd-revision.md`](cd-revision.md)
§8). Floci 2.4.0 lo emula de verdad: registra la definición, crea el servicio y **lanza el
contenedor**.

```text
aws ecs create-cluster --cluster-name pizzeria-cluster         → status ACTIVE
aws ecs register-task-definition --cli-input-json file://td.json → revision 1
aws ecs create-service --cluster pizzeria-cluster \
  --service-name pizzeria-backend-service \
  --task-definition pizzeria-backend --desired-count 1 --launch-type EC2
aws ecs list-tasks --cluster pizzeria-cluster
  → arn:aws:ecs:us-east-1:000000000000:task/pizzeria-cluster/b3269b9b...

docker ps
  → floci-aws-ecs-b3269b9b...-app | nginx | Up | 0.0.0.0:8080->80/tcp

curl localhost:8080  → 200
```

Matiz importante para el despliegue: la tarea corre en **su propia red Docker**, no en la red donde
esté la aplicación. Si el contenedor desplegado tiene que hablar con Floci (S3/SQS), hay que darle una
red común (`FLOCI_SERVICES_DOCKER_NETWORK`) o publicar Floci en un nombre alcanzable. No basta con que
el runner lo vea en `localhost`.

### 3.5 EC2 — funciona y lanza un contenedor real

```text
aws ec2 run-instances --image-id ami-00000000 --instance-type t3.micro --count 1
  → i-60aff554305d64d56, state pending, private IP 172.31.32.10

# Log de Floci:
WARN  AmiImageResolver: Unknown AMI ID ami-00000000; falling back to
      public.ecr.aws/amazonlinux/amazonlinux:2023
INFO  Ec2ContainerManager: EC2 instance i-60aff5... running in container e8575beb...
      (SSH host port 2200)
INFO  ContainerNetworkReachability: Docker backend is docker desktop: container IPs
      are not routable from the host, so EC2 public addresses stay on 127.0.0.1
      with published ports.
```

Dos consecuencias prácticas en Windows:

- El AMI desconocido cae a `public.ecr.aws/amazonlinux/amazonlinux:2023` (150 MB la primera vez).
- La IP privada de la instancia **no es alcanzable desde el host**: hay que usar el puerto publicado
  en `127.0.0.1` (por ejemplo SSH en `2200`). Esto no se parece a AWS, pero es el comportamiento
  documentado de Floci en Docker Desktop.

### 3.6 IAM, STS y CloudWatch Logs — funcionan

```text
aws sts get-caller-identity
  → {"UserId":"000000000000","Account":"000000000000","Arn":"arn:aws:iam::000000000000:root"}

aws iam create-role --role-name pizzeria-deploy --assume-role-policy-document file://pol.json
  → arn:aws:iam::000000000000:role/pizzeria-deploy

aws logs create-log-group --log-group-name /pizzeria/backend
aws logs describe-log-groups
  → ["/aws/ecr/registry", "/pizzeria/backend"]
```

IAM **no evalúa políticas** salvo que se active `FLOCI_SERVICES_IAM_ENFORCEMENT_ENABLED=true`. Que un
`create-role` funcione no demuestra que los permisos del rol sean correctos.

---

## 4. Lo que queda por verificar

| Qué | Estado |
|---|---|
| Módulo de Testcontainers 2.x para Floci | **Pendiente.** No se probó en esta sesión |
| `endpointOverride` desde el SDK de AWS v2 (`S3Client`, `SqsClient`) | **Pendiente.** Se probó con la CLI, no con el SDK |
| Bucket creado idempotentemente al arrancar | **Pendiente.** Floci tiene *init hooks* (`FLOCI_INIT_HOOKS_DIR`), pero este proyecto no los tiene configurados |
| Worker de SQS / eventos de pedido contra el emulador | **Pendiente.** No hay código de mensajería todavía |
| Digest fijo en el compose/local y en CI | **Pendiente de aplicar.** La versión verificada es `2.4.0` |

---

## 5. Trampas verificadas (y cómo evitarlas)

### 5.1 La imagen no trae `curl` ni `wget`

```text
docker run --rm --entrypoint sh floci/floci:2.4.0 -c 'command -v curl || echo sin-curl'
  → sin-curl
```

Un `--health-cmd "curl -f http://localhost:4566/"` deja el contenedor **unhealthy para siempre**:

```text
ExitCode: 1
Output: /bin/sh: line 1: curl: command not found
```

La imagen **sí trae `bash`**. Una sonda que funciona:

```yaml
healthcheck:
  test: ["CMD-SHELL", "bash -c 'exec 3<>/dev/tcp/127.0.0.1/4566'"]
```

Verificado: `healthy` en 15 s.

### 5.2 En Windows, `*.localhost` no resuelve

El estilo por defecto de ECR devuelve `<cuenta>.dkr.ecr.<región>.localhost:4566`, y Docker Desktop en
Windows no lo resuelve:

```text
docker login 000000000000.dkr.ecr.us-east-1.localhost:4566
  → lookup 000000000000.dkr.ecr.us-east-1.localhost: no such host
```

Solución verificada: `FLOCI_SERVICES_ECR_URI_STYLE=path`. El URI pasa a
`localhost:4566/000000000000/us-east-1/<repo>`, que sí resuelve y además se puede usar en Linux.

### 5.3 El registro ECR necesita red compartida

Floci arranca un contenedor `registry:2` aparte. Con el socket montado pero sin red común, Floci no
puede resolverlo por nombre y **toda** la ruta `/v2/` devuelve 503, aunque el API de ECR (crear
repositorio, listar metadatos) responda bien. Es el fallo más engañoso porque el `create-repository`
«funciona».

```powershell
docker network create floci-net
# Floci en esa red + FLOCI_SERVICES_ECR_DOCKER_NETWORK=floci-net
```

Si el contenedor `floci-aws-ecr-registry` ya existía en `bridge`, hay que borrarlo: Floci lo reutiliza
por nombre y no lo mueve de red.

```powershell
docker rm -f floci-aws-ecr-registry
docker restart floci
```

### 5.4 Montar un volumen no persiste nada por sí solo

La imagen publicada arranca con `FLOCI_STORAGE_MODE=memory` (lo dice su `application.yml`, no el
código). Reiniciar Floci borra buckets, colas, repositorios y metadatos. Para que sobrevivan:

```yaml
environment:
  FLOCI_STORAGE_MODE: hybrid
  FLOCI_STORAGE_PERSISTENT_PATH: /app/data
volumes:
  - floci-data:/app/data
```

### 5.5 PowerShell se come las comillas del JSON

`--message-body '{"pedidoId":1}'` llega como `{pedidoId:1}`. Para probar cuerpos JSON desde Windows,
escribir el cuerpo a un archivo y pasar `file://...` (o usar `--cli-input-json file://...`). El archivo
debe ir **sin BOM**: `Set-Content -Encoding utf8` en PowerShell 5 lo añade y la CLI responde
`Invalid JSON received`. Lo que funcionó:

```powershell
[IO.File]::WriteAllText($ruta, $json)
```

### 5.6 `FLOCI_HOSTNAME` sólo cambia el host de las URL que devuelve

Existe y es útil cuando Floci corre en Compose: hace que `FLOCI_BASE_URL` use el nombre del servicio
(`floci`) en vez de `localhost`, de modo que otros contenedores lo alcancen. **No** hace que Floci
escuche en otra interfaz ni resuelve el problema de redes de ECS/EC2.

---

## 6. Limpieza

Los contenedores y recursos creados por Floci **no** se van con `docker compose down`:

```powershell
docker ps -a --filter name=floci-aws-    # contenedores de ECS/EC2/RDS/...
docker network ls | Select-String floci
docker volume ls --filter label=floci=true
```

Borrado destructivo de todo lo que gestiona Floci:

```powershell
docker rm -f (docker ps -aq --filter name=floci-aws-)
docker volume prune --filter label=floci=true
docker network prune
```

> `docker volume prune --filter label=floci=true` borra **todos** los datos de Floci, incluidos los de
> otras instancias en el mismo Docker. Es lo correcto en un runner de CI y peligroso en la máquina de
> desarrollo.

---

## 7. Conclusión

Floci 2.4.0 es suficiente para el proyecto: cubre los siete servicios de `aws-local.md` §2 y deja
ensayar el ciclo completo de despliegue (construir, publicar en ECR, lanzar en ECS o EC2) sin cuenta
de AWS y sin coste. Lo que hay que corregir no es el emulador: es el **pipeline** que lo usa. Ver
[`cd-revision.md`](cd-revision.md).
