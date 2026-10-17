# AWS en local: el emulador Floci

> **Propósito:** cómo desarrollar y probar las integraciones de AWS sin cuenta de AWS, sin coste y sin
> pedir autorización.
>
> **Por qué existe:** el requisito **RNF-05** dice que el sistema debe arrancar localmente sin depender
> de ningún servicio de AWS, y `AGENTS.md` §4.9 prohíbe crear cualquier recurso con coste sin
> autorización escrita.
>
> **Decisión:** [`ADR-0006`](../adr/0006-floci-como-emulador-aws.md)
> **Estructura del código:** [`ADR-0004`](../adr/0004-estructura-de-paquetes-por-capas.md) · [`ADR-0005`](../adr/0005-los-puertos-se-declaran-en-el-dominio.md)
> **Reglas de integración AWS:** [`spring-conventions.md`](spring-conventions.md) §7
>
> ⚠️ **Nada de lo que hay aquí autoriza usar AWS real.** Ver §6.

---

## 1. Qué es

[Floci](https://floci.io) es un emulador de AWS que corre en un contenedor y habla la API de AWS de
verdad en `http://localhost:4566`. No es un doble de test: es un servidor.

```
tu aplicación ──► Floci (localhost:4566) ──► S3 · SQS · ECR · EC2 · IAM · CloudWatch
                        │
                        └── emulados localmente, dentro de tu máquina
```

Acepta **cualquier credencial**: `test` / `test` sirve. No hay que registrarse en nada.

---

## 2. Los servicios que usamos, y sólo esos

Floci soporta más de cien servicios. Este proyecto usa **siete**, y no hay que tocar ninguno más:

| Servicio | Para qué | Historias |
|---|---|---|
| **S3** | Imágenes de las pizzas | US-050…US-052 |
| **SQS** | Mensajería de eventos de pedido | US-060…US-062 |
| **ECR** | Publicar la imagen de producción | US-071 |
| **EC2** | Lanzar el contenedor para ensayar el despliegue | US-072 |
| **IAM** | Rol de mínimo privilegio para CI y despliegue | US-071, US-072 |
| **STS** | Credenciales temporales | US-071 |
| **CloudWatch** | Logs y alarmas | US-081 |

### Lo que deliberadamente NO usamos

| Servicio | Por qué no |
|---|---|
| **Lambda** | El proyecto no es serverless. La lógica va en el contenedor |
| **DynamoDB** | La base de datos es **PostgreSQL**. RDBMS relacional, elegido y documentado |
| **ECS / EKS** | El despliegue es un contenedor en EC2, orquestado por systemd |
| **RDS** | Se **evalúa** en US-082, no se usa. En local corre PostgreSQL en Docker |
| **CloudFront** | US-083 está marcada **Descartada**: el frontend no entra en el MVP |
| **Secrets Manager** | Sin credenciales de AWS que gestar mientras no haya despliegue real |

> **Emular un servicio que no usamos es ruido.** Se paga en tiempo de arranque y en recursos para
> comprobar algo que el proyecto no hace.

---

## 3. Cómo se levanta

**Hoy no está declarado en ninguna parte.** `infra/local/docker-compose.yml` contiene sólo `db` y `app`,
y el overlay `infra/local/docker-compose.aws.yml` **todavía no existe**. Los dos se crean en US-051.

La razón de que no esté en el compose principal es RNF-05: el perfil por defecto debe arrancar sin
AWS, y un servicio declarado en el fichero principal se levanta siempre. Por eso va en un **overlay
que se pide explícitamente** con un segundo `-f`.

```yaml
# Extracto del futuro infra/local/docker-compose.aws.yml (US-051).
floci:
  image: floci/floci:<VERSION_FIJADA>
  ports:
    - "4566:4566"
  environment:
    FLOCI_SERVICES_EC2_INSTANCE_RESOURCE_LIMITS: "false"
  volumes:
    - floci-data:/app/data
```

```powershell
docker compose -f infra/local/docker-compose.yml -f infra/local/docker-compose.aws.yml up -d
```

Se puede comprobar que el perfil por defecto **no** levanta nada de AWS:

```powershell
docker compose -f infra/local/docker-compose.yml up -d
docker compose -f infra/local/docker-compose.yml ps   # sólo db y app
```

### ⚠️ El tag `latest` está prohibido aquí

La documentación de Floci usa `floci/floci:latest`, y este proyecto **prohíbe los tags `latest`** para
que dos máquinas construyan lo mismo. Hay que fijar una versión exacta. **Cuál usar todavía no está
verificado**: es la primera tarea de US-051.

---

## 4. Configuración desde la aplicación

**El endpoint se inyecta por variable de entorno, nunca fijo en el código.** Es lo que permite que la
misma imagen funcione contra Floci en local y contra AWS real en producción, sin recompilar.

```yaml
# application-local.yml
aws:
  endpoint: ${AWS_ENDPOINT_URL:http://localhost:4566}
  region: ${AWS_REGION:us-east-1}
  access-key: ${AWS_ACCESS_KEY_ID:test}
  secret-key: ${AWS_SECRET_ACCESS_KEY:test}
```

```java
// infrastructure.adapter.out.storage.config.S3Config  (US-050)
// El SDK de AWS v2 vive AQUÍ y en ningún otro sitio: domain y application no lo pueden importar.

@Bean
public S3Client s3Client(AwsProperties propiedades) {
    return S3Client.builder()
            .endpointOverride(URI.create(propiedades.endpoint()))  // ← aquí está la diferencia con producción
            .region(Region.of(propiedades.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(propiedades.accessKey(), propiedades.secretKey())))
            .build();
}
```

Que el cliente sea un **bean** y no algo que cada adaptador construya por su cuenta es lo que permite
que el test de integración lo sustituya por uno apuntando al emulador ([ADR-0005](
../adr/0005-los-puertos-se-declaran-en-el-dominio.md) §3).

> **Trampa de AWS SDK v2.** `endpointOverride` **sólo** funciona con el cliente *service-specific*
> (`S3Client`, `SqsClient`). Con el `S3AsyncClient` y los clientes nuevos multipropósito **no funciona**.
> Hay que comprobarlo en US-051, no asumirlo.

### Credenciales

| Entorno | Credenciales |
|---|---|
| Local con Floci | `test` / `test`. No son secretos y **nunca** van al repositorio |
| Producción | **Nunca en el repositorio.** Llegan por variable de entorno del sistema o por un gestor de secretos |

`AGENTS.md` §4.7 y la regla de `.gitignore` lo prohíben explícitamente, y el job `Escaneo de secretos`
de CI lo verifica.

---

## 5. Emulación de EC2, en detalle

Es la parte que menos se conoce y la que más cambia la planificación, así que va con detalle.

`RunInstances` **no simula una instancia**: lanza un **contenedor Docker real**.

```
RunInstances(tipo=t3.large)  ──►  docker run --cpus=2 --memory=2048m <imagen del AMI>
```

| Comportamiento | Detalle |
|---|---|
| Ciclo de vida | `pending → running → stopping → stopped → shutting-down → terminated`, mapeado a `docker start/stop/rm` |
| Imagen | El **AMI** se resuelve a una imagen Docker mediante un catálogo. Un AMI desconocido cae a `amazonlinux:2023` |
| CPU | 1 vCPU = 1.000.000.000 de nanoCPUs. Si pides más núcleos de los que tiene el host, **recorta y avisa** |
| Memoria | Límite exacto en MiB del tipo de instancia |
| Metadatos | Proxy de `169.254.169.254` dentro del contenedor, para IMDS y credenciales del perfil |

### El límite de recursos, en la práctica

En una máquina de 8 núcleos:

| Tipo | vCPU | Resultado |
|---|---|---|
| `t3.small` | 1 | ✅ |
| `t3.medium` | 2 | ✅ |
| `t3.large` | 2 | ✅ |
| `t3.2xlarge` | 8 | ⚠️ Al límite del host |
| `m5.xlarge` | 4 | ✅ |

Si molesta: `FLOCI_SERVICES_EC2_INSTANCE_RESOURCE_LIMITS=false`. **Con el límite puesto** es más fiel a
EC2; **sin él** el arranque nunca falla por falta de recursos.

### ECR: se puede probar `docker push` de verdad

Floci emula ECR con un **registro OCI real** detrás. El cliente `docker` estándar funciona sin
configurar el demonio, porque Floci devuelve URIs que resuelven a loopback.

```bash
aws ecr create-repository --repository-name pizzeria/app --endpoint-url $AWS_ENDPOINT_URL
aws ecr get-login-password --endpoint-url $AWS_ENDPOINT_URL | docker login --username AWS --password-stdin \
    000000000000.dkr.ecr.us-east-1.localhost:4566
docker push 000000000000.dkr.ecr.us-east-1.localhost:4566/pizzeria/app:v1
```

**Esto permite ensayar US-071 entero antes de tocar AWS.**

---

## 6. La línea que no se cruza

| | Estado |
|---|---|
| Emulador local: S3, SQS, ECR, EC2, IAM, STS, CloudWatch | ✅ Sin cuenta, sin coste, sin autorización |
| **Recursos de AWS reales** | 🚫 **Requiere autorización escrita del responsable, presupuesto máximo y alarma de coste configurada ANTES de crear el primer recurso** |

`US-072` (despliegue en EC2), `US-081` (CloudWatch) y `US-082` (evaluar RDS) están marcadas
**Bloqueadas** en el backlog y así se quedan. El emulador reduce el riesgo técnico del despliegue; no
reduce su coste.

Lista de comprobación previa a cualquier despliegue: [`decisions-pending.md`](../planning/decisions-pending.md) D14.

---

## 7. Dónde vive AWS dentro del hexágono

Este es el punto donde la arquitectura hexagonal gana o pierde. **AWS es mundo exterior**: no es
negocio, y por eso va **detrás de un puerto**, nunca dentro del dominio ni de la aplicación.

```text
   ┌──────────────────────────────── EL HEXÁGONO ───────────────────────────────┐
   │                                                                            │
   │  domain/                          application/                             │
   │   ImagenStoragePort  ◄── interfaz ── SubirImagenUseCase                    │
   │   (no sabe qué es          │          (no sabe qué es S3)                   │
   │    un bucket)              │                                                    │
   │                            ▼                                                    │
   │              infrastructure.adapter.out.storage/                             │
   │               S3ImagenAdapter          ← el ÚNICO sitio con el SDK de AWS  │
   │               config/S3Config          ← aquí se construye el S3Client     │
   └────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
                              S3  ·  emulado por Floci
```

**La prueba de que está bien:** un test de `application` que sube una imagen **no levanta AWS, ni
sigue, ni sabe que existe**. Usa un doble de `ImagenStoragePort`. Si el caso de uso necesitara algo
de S3 que no esté en el puerto, el puerto está mal declarado — y eso lo detecta el compilador, no una
revisión a ojo.

**La prueba de que está mal:** un `import software.amazon.awssdk` en `domain`, en `application`, o en
`shared`. Eso es un fallo de build (`ArchitectureRulesTest`).

## 8. Reglas al integrar

| Regla | Detalle |
|---|---|
| El SDK de AWS **nunca** en `domain` ni en `application` | Sólo en `infrastructure.adapter.out.storage` y `..messaging` |
| Toda integración pasa por un **puerto** | `domain.port.out.ImagenStoragePort` define el contrato; `S3ImagenAdapter` lo implementa (ver §7) |
| El cliente del SDK es un **bean** | `storage/config/S3Config`. Que sea sustituible es lo que permite probar contra el emulador |
| **El perfil por defecto arranca sin AWS** | RNF-05. Es un criterio de aceptación, no una buena intención |
| Los tests de integración **levantan el emulador** | Nunca un doble. Un doble siempre funciona y enseña a asumir que AWS siempre funciona |
| Los tests de **aplicación** usan un doble del puerto | Ahí sí: no están probando AWS, están probando la orquestación |
| El endpoint va **por variable de entorno** | La misma imagen debe funcionar en local y en producción |
| Las claves de AWS **no están en el repositorio** | Ni de producción, ni de pruebas. El job de escaneo lo verifica |
| Ninguna URL de S3 la decide el cliente | US-052 lo prohíbe por SSRF. La genera el servidor y se prefirma |

---

## 9. Lo que todavía no está verificado

**Nada de este documento está probado.** Es investigación hecha con la documentación del fabricante,
no con el emulador delante. Se verifica en:

| Qué verificar | Dónde |
|---|---|
| Floci arranca en Docker Desktop sobre **Windows** | US-051 |
| Versión exacta de la imagen, **sin `latest`** | US-051 |
| `endpointOverride` funciona con los clientes que usemos | US-051 |
| Bucket creado idempotentemente al arrancar | US-051 |
| URL prefirmada con caducidad configurable | US-052 |
| Módulo de Testcontainers 2.x correcto | US-051 |
| `docker push` contra el ECR emulado | US-071 |

> **Si Floci no cumple**, el plan B de ADR-0006 es **LocalStack**, y habría que responder antes a la
> pregunta que sigue abierta en `spring-conventions.md` §6.3: ¿sigue siendo compatible su módulo de
> Testcontainers con la versión 2.x, que renombró todos sus módulos?

---

## 10. Referencias

- <https://floci.io/floci/getting-started/quick-start/> — arranque y configuración
- <https://floci.io/floci/services/> — servicios soportados
- <https://floci.io/floci/services/ec2/> — emulación de EC2
- <https://floci.io/floci/services/ecr/> — registro OCI detrás de ECR
- Decisión y alternativas: [`ADR-0006`](../adr/0006-floci-como-emulador-aws.md)
- Dónde vive el código: [`ADR-0004`](../adr/0004-estructura-de-paquetes-por-capas.md) §3 · [`ADR-0005`](../adr/0005-los-puertos-se-declaran-en-el-dominio.md) §3