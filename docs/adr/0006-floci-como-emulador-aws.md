# ADR-0006 · Floci como emulador de AWS

- **Estado:** Aceptada
- **Ratificada:** Sprint 0 (2026-10-11)
- **Documentada:** Sprint 1 (2026-10-11) · US-006 / preparación de S6–S8
- **Decidido por:** responsable del proyecto
- **ADRs relacionados:** ADR-0010 (versiones de Java y Spring Boot) · ADR-0008 (monolito modular)
- **Decide:** con qué emulador se desarrollan y prueban las integraciones de AWS en local

> **Nota de procedencia.** Esta ADR estaba citada desde el Sprint 0 en `AGENTS.md` §2 y en
> `spring-conventions.md` §7, pero nunca se redactó. Se escribe ahora, en el Sprint 1, para dejar
> constancia de lo que ya se decidió y de lo que aún **no** está verificado. No introduce ninguna
> decisión nueva.

---

## 1. Contexto

El proyecto usa AWS en dos sitios:

| Uso | Historias |
|---|---|
| **S3** para las imágenes de las pizzas | US-050, US-051, US-052 |
| **SQS** para la mensajería de eventos de pedido | US-060, US-061, US-062 |
| **ECR** y **EC2** para publicar y desplegar | US-071, US-072 |

El requisito **RNF-05** es explícito: *«el sistema arranca localmente sin depender de ningún servicio de
AWS»*. Y `AGENTS.md` §4.9 va más lejos: **ningún recurso de AWS con coste sin autorización escrita del
responsable**.

Eso deja una tensión que hay que resolver: hay que programar contra la API de AWS, pero no se puede
llamar a la API real.

---

## 2. La decisión

**Usar [Floci](https://floci.io) como emulador local de AWS**, escuchando en `http://localhost:4566`,
con credenciales de prueba y el endpoint inyectado por variable de entorno.

| Aspecto | Valor |
|---|---|
| Imagen | `floci/floci` (nativa) o `floci/floci:latest-compat` (con AWS CLI y boto3 dentro) |
| Puerto | `4566` |
| Región | `us-east-1` |
| Credenciales | **Cualquiera.** `test` / `test` |
| Endpoint | Variable de entorno, nunca fijo en el código |
| Datos | Volumen montado en `/app/data` |

---

## 3. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| **LocalStack Community** | Es la alternativa más evidente y la que más tiempo lleva. Es la razón de que `spring-conventions.md` §6.3 ainda mencione `LocalStackContainer`: la pregunta que había que responder era si su módulo de Testcontainers sigue siendo compatible con **Testcontainers 2.x**, que renombró todos sus módulos. Sin verificar, no se elige. **Queda como plan B si Floci falla en US-051** |
| **Moto** | Es una librería de Python, no un emulador de red. Habría que envolverla y además **no emula S3 con fidelidad** ni EC2 |
| **Nada emulado: un `S3Client` falso en las pruebas** | Es más rápido, pero deja de probar lo que de verdad importa: los permisos, las URLs prefirmadas, el manejo de errores del SDK. Un doble que siempre funciona enseña a asumir que AWS siempre funciona |
| **AWS real desde el principio** | Prohibido. Coste, y contradice RNF-05 y `AGENTS.md` §4.9 |

---

## 4. Por qué Floci y no otro

| Criterio | Floci |
|---|---|
| Servicios que necesitamos | S3, SQS, **ECR**, **EC2**, IAM, STS, CloudWatch |
| **ECR** | Emulado con un **registro OCI real** detrás: `docker push` funciona tal cual, sin configurar el demonio |
| **EC2** | `RunInstances` lanza un **contenedor Docker real** por instancia, con límites de CPU y memoria según el tipo de instancia y ciclo de vida completo |
| Testcontainers | Tiene integración propia, en coherencia con la regla de este proyecto de probar contra algo real y nunca contra una base en memoria |
| Interfaz | Consola web para inspeccionar lo que se ha creado |

**El argumento decisivo son ECR y EC2.** Poder **ensayar el ciclo completo de despliegue en local**
—construir la imagen, publicarla en un registro, lanzarla como instancia y hablar con S3 y SQS
emulados— convierte US-072 de un salto al vacío en un paso ya probado. Un emulador que sólo sabe
responder S3 y SQS habría dejado el despliegue entero sin verificar.

---

## 5. Consecuencias

### Positivas

- Se pueden desarrollar y probar las integraciones de AWS **sin cuenta, sin coste y sin autorización**.
- El arranque por defecto **no depende de AWS**, como exige RNF-05.
- La forma de ejecutar es idéntica en local y en EC2: la imagen es la misma.
- Se puede ensayo el despliegue completo antes de tocar nada real.

### Negativas y costes asumidos

- **Un emulador no es AWS.** Puede divergir en el comportamiento de una firma, de una URL prefirmada o
  de un límite. Contra eso sólo hay una defensa: **las pruebas de integración tienen que levantar el
  emulador**, no usar dobles, y hay que contrastar contra AWS real antes de desplegar (S8).
- **Sin versionar las imágenes se rompe una regla del proyecto.** La documentación de Floci usa
  `floci/floci:latest`, y este proyecto **prohíbe los tags `latest`**. Habrá que fijar una versión
  exacta cuando se integre. Queda anotado en US-051.
- **Añade una dependencia de infraestructura** en el entorno local: si Floci no arranca, el laboratorio
  de S6–S7 no funciona.

### Lo que esta ADR **no** autoriza

> **Nada de esto habilita el uso de AWS real.**

ECR, EC2, S3 y SQS reales siguen requiriendo **autorización escrita del responsable, presupuesto
máximo y alarma de coste configurada antes de crear el primer recurso** (`AGENTS.md` §4.9,
`decisions-pending.md` D14). US-072, US-081 y US-082 permanecen **bloqueadas** hasta entonces.

El emulador reduce el riesgo de un despliegue, pero no su coste. **Nada se despliega sin autorización.**

---

## 6. Pendiente de verificación

Nada de lo anterior está probado todavía. Se verificará en las historias que lo usan:

| Qué | Dónde |
|---|---|
| El emulador arranca en Docker Desktop sobre **Windows** | US-051 |
| Nomenclatura real de los módulos en Testcontainers 2.x: ¿sirve `LocalStackContainer` o hace falta `GenericContainer`? | US-051 |
| Versión exacta de la imagen, fijada y **sin `latest`** | US-051 |
| El bucket se crea idempotentemente al arrancar | US-051 |
| URLs prefirmadas con la caducidad configurable | US-052 |
| `docker push` contra el ECR emulado | US-071 |

---

## 7. Referencia

- Guía práctica: [`docs/development/aws-local.md`](../development/aws-local.md)
- <https://floci.io/floci/services/> — servicios soportados
- <https://floci.io/floci/services/ec2/> — emulación de EC2
- <https://floci.io/floci/getting-started/quick-start/> — arranque y configuración