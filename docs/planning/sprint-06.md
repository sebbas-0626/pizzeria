# Sprint 06 — Integración con S3 (imágenes de pizza)

> **Estado:** Planificado
> **Objetivo:** primera integración con un servicio de AWS, verificada localmente y **sin ningún coste**.
> **Historias:** US-050, US-051, US-052
> **Puntos:** 21
> **Documento efímero:** al terminar, lo relevante pasa al backlog y a `architecture.md`.

---

## 1. Advertencia de alcance

Esta integración **no es funcional en el MVP**: el MVP ya está terminado en el Sprint 05 sin imágenes.
El objetivo de estas historias es **aprender la forma correcta de integrar AWS** sin arrastrar el SDK al
dominio, que es el error más caro y más común en este tipo de proyecto.

**Regla que se instala desde US-050:** el backend debe seguir arrancando **sin** ningún servicio de AWS.
Si las pruebas de humo requieren el emulador para arrancar, la arquitectura está mal.

---

## 2. Historias del sprint

| ID | Título | Puntos | Riesgo |
|---|---|---|---|
| US-050 | Puerto de almacenamiento y adaptador local por defecto | 5 | Acoplar el dominio al SDK |
| US-051 | Adaptador S3 verificado contra el emulador | 8 | Confundir emulador con S3 real |
| US-052 | Imágenes de pizza gestionadas por el administrador | 8 | Validación de archivos y SSRF |

---

## 3. US-050 — Puerto de almacenamiento

### Tareas

- [ ] `catalog/application/port/out/ImageStorage.java` — interfaz con `store`, `presign` y `delete`
- [ ] Implementación por defecto: `NoOpImageStorage` o `LocalFileSystemImageStorage`
- [ ] Configuración por perfil: local (ficheros) / emulador (S3) / **sin AWS** (por defecto)
- [ ] Test ArchUnit que prohíbe `software.amazon.awssdk` en `domain` y en `application`

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | El puerto está en `application.port.out` | Inspección |
| 2 | El perfil por defecto **no** toca ningún servicio externo | Arranque sin red |
| 3 | **El backend arranca con las variables de AWS ausentes** | Prueba de humo |
| 4 | Ningún tipo del SDK de AWS aparece en `domain` ni en `application` | Test ArchUnit |
| 5 | Cambiar de implementación no toca el caso de uso | Diff del adaptador |

### La pregunta que esta historia responde

> *«¿Dónde meto la llamada a S3?»* — Respuesta: en `adapters.out.storage`, implementando un puerto que el
> caso de uso consume. Si algún día el almacenamiento cambia a otro proveedor, el caso de uso no cambia.

---

## 4. US-051 — Adaptador S3 verificado contra el emulador

### Tareas

- [ ] `adapters/out/storage/S3ImageStorage.java` con el SDK v2
- [ ] Creación idempotente del bucket al arrancar (sólo en `local`)
- [ ] Configuración de `endpointOverride` por variable de entorno
- [ ] Credenciales de prueba; **ninguna** real en el repositorio
- [ ] Caducidad configurable de las URLs prefirmadas
- [ ] **Tarea de verificación:** comprobar si `LocalStackContainer` de Testcontainers funciona con la
      imagen de Floci, o si hace falta `GenericContainer` con el puerto 4566
- [ ] Prueba de integración contra el emulador

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | `PUT` de un objeto funciona contra el emulador | Prueba de integración |
| 2 | `GET` y la URL prefirmada funcionan | Prueba de integración |
| 3 | La URL prefirmada caduca según lo configurado | Prueba con reloj controlado o caducidad corta |
| 4 | Crear el bucket dos veces no falla (idempotencia) | Prueba |
| 5 | El endpoint viene de variable de entorno | Revisión de configuración |
| 6 | Ninguna credencial real en el repositorio | `gitleaks detect` |

### Sobre el emulador — leer con atención

**Floci es un simulador, no S3.** Lo que se puede aprender aquí:

| Se puede practicar | No se puede practicar aquí |
|---|---|
| Forma de la API (subir, leer, generar URL prefirmada) | Políticas IAM reales |
| Separación del código detrás de un puerto | Comportamiento exacto de permisos y políticas de bucket |
| Patrón de configuración por variable de entorno | Rendimiento, límites y costes reales |
| Integración de tests sin servicios externos | Coste, facturación, disponibilidad |

**La alternativa si algo falla:** `GenericContainer` con `floci/floci:<versión fijada>` exponiendo el
puerto 4566. Se decide en la tarea de verificación, no antes.

**Fijar la versión del emulador.** Nunca `latest`. La versión se documenta en el ADR-0006 con la fecha
en que se comprobó.

---

## 5. US-052 — Imágenes de pizza gestionadas por el administrador

### Tareas

- [ ] `POST /api/v1/admin/pizzas/{id}/image` con `multipart/form-data`
- [ ] Validación de tipo MIME y tamaño máximo (5 MB)
- [ ] Rechazo de ejecutables y de tipos no permitidos (lista blanca, no negra)
- [ ] Reemplazo de la imagen anterior
- [ ] `imageUrl` prefirmado en la respuesta del catálogo público
- [ ] Desactivación de la imagen al desactivar la pizza
- [ ] Prueba de autorización: sin ADMIN → 403

### Criterios de aceptación

| # | Criterio | Verificación |
|---|---|---|
| 1 | Un ADMIN sube una imagen válida → se guarda y se devuelve la clave | Prueba de integración |
| 2 | Tipo MIME no permitido → 422 | Prueba |
| 3 | Archivo de más de 5 MB → 422 | Prueba |
| 4 | Subir otra imagen reemplaza la anterior | Prueba |
| 5 | El catálogo público devuelve una URL prefirmada válida | Prueba |
| 6 | Sin ADMIN → 403 | Prueba |
| 7 | **El cliente no puede indicar una URL arbitraria** | Prueba que intenta enviar una URL externa |
| 8 | Desactivar la pizza desactiva su imagen | Prueba |

### Nota de seguridad: el criterio 7

Si el endpoint aceptara una URL y el servidor la descargara, cualquiera podría usarlo para que el backend
acceda a la red interna (**SSRF**). El diseño correcto es **subir el archivo**, no indicar una dirección.
El criterio 7 documenta y prueba esa decisión.

---

## 6. Archivos creados o modificados

```text
catalog/
├── application/port/out/ImageStorage.java
└── adapters/out/storage/S3ImageStorage.java, NoOpImageStorage.java, S3BucketInitializer.java

config/         S3Properties (@ConfigurationProperties)
infra/local/    docker-compose.yml (servicio floci added)
scripts/        init-bucket.ps1 (opcional)
docs/adr/0006-floci-como-emulador-aws.md
```

---

## 7. Criterio de salida del sprint

- [ ] El backend **arranca sin ninguna variable de AWS**
- [ ] Un ADMIN sube una imagen y aparece en el catálogo público
- [ ] Prueba de integración verde contra el emulador
- [ ] Test ArchUnit verde: el SDK de AWS no está en `domain` ni en `application`
- [ ] Versión del emulador **fijada y documentada**
- [ ] La prueba de humo del perfil por defecto sigue verde
- [ ] `gitleaks detect` sin hallazgos
- [ ] `./mvnw verify` en verde
- [ ] US-050, US-051 y US-052 en `Hecho`

---

## 8. Dependencias y riesgos

| | |
|---|---|
| **Depende de** | Sprint 05 |
| **Bloquea a** | Sprint 07 (usa el mismo patrón de puerto y adaptador) |
| **Riesgo** | Introducir el SDK de AWS en `domain` «sólo para leer la clave» → el test ArchUnit lo detecta |
| **Riesgo** | Que el arranque dependa del emulador → contradice RNF-05 |
| **Riesgo** | Confundir el comportamiento del emulador con el real → la tabla de límites está en la historia |
| **Decisión abierta** | ⚙️ D14 (autorización AWS). **Este sprint NO crea ningún recurso real.** Usa sólo el emulador local |

---

## 9. Preguntas que este sprint deja abiertas a propósito

- ¿Qué ocurre si el emulador dice que sí y S3 real dice que no? → Se responde en el despliegue real.
- ¿Cómo se rota la configuración de S3 sin reiniciar? → Fuera de alcance.
- ¿Quién es el dueño del coste de las imágenes? → Se responde al hablar de despliegue.

---

## 10. Registro de avance

| Fecha | Historia | Estado | Notas |
|---|---|---|---|
| — | — | — | Sprint no iniciado |