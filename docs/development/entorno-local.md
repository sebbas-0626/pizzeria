# Entorno local: contenedores y volúmenes

> **Fuente de verdad del laboratorio local.** Complementa a
> [`aws-local.md`](aws-local.md), que es del emulador de AWS.
>
> Última actualización: **2026-10-17**

---

## 1. Qué hay, exactamente

| Contenedor | Imagen | Puerto en el host | Datos |
|---|---|---|---|
| `pizzeria-app-1` | `maven:3.10.0-eclipse-temurin-21` | `8080` | Volumen `pizzeria_app-target` |
| `pizzeria-db-1` | `postgres:17-alpine` | **ninguno** | Volumen `pizzeria_postgres-data` |

| Volumen | Qué guarda | ¿Lo pierdo con `down`? |
|---|---|---|
| `pizzeria_postgres-data` | La base de datos: esquema, datos, historial de Flyway | **No.** Sólo con `down -v` |
| `pizzeria_app-target` | `target/` compilado dentro del contenedor | **No** |
| `pizzeria_maven-cache` | `~/.m2`: dependencias descargadas | **No** |

**El puerto de PostgreSQL no se publica**, y es deliberado (criterio de aceptación de US-002): nada del
host alcanza la base de datos. Sólo se publica la API, en el 8080.

### Por qué la app usa la imagen de Maven y no un Dockerfile

No hay Dockerfile propio todavía: el multi-stage de producción es **US-070**, en el sprint 8. Para
desarrollar se quiere recargar código al instante, y `spring-boot:run` con el fuente montado lo da sin
compilar dos veces.

**Consecuencia práctica que sorprende:** después de cambiar código, `--build` **no hace nada** y la
aplicación sigue con la versión anterior. Hay que reiniciar el servicio:

```powershell
docker compose -f infra/local/docker-compose.yml restart app
```

---

## 2. ⚠️ Hay otro PostgreSQL en tu máquina

**Decisión: sólo se usa la base del contenedor.** El PostgreSQL que tengas instalado en Windows, si lo
tienes, **no se usa para nada** en este proyecto y no se toca.

Si lo tienes instalado, está escuchando en el `5432` del host, así que **`localhost:5432` responde**. Y es
**otro servidor distinto**, sin esquema de la aplicación y sin relación con el del proyecto.

La trampa era esta: el perfil `local` tenía `POSTGRES_HOST` con **valor por defecto `localhost`**. Al
arrancar la aplicación fuera de Docker —un `spring-boot:run` a mano, que es lo primero que se prueba
cuando algo falla— se connectaba **sin avisar** al PostgreSQL de Windows, y el error que salía era
`FATAL: la autenticación password falló`. Parece un problema de credenciales; era un problema de
**destino**: se estaba hablando con el servidor equivocado.

**Cerrado el 2026-10-17**: `POSTGRES_HOST` y `POSTGRES_PORT` ya no tienen valor por defecto, así que
arrancar fuera del laboratorio **no arranca**. La URL se queda con el marcador sin sustituir y el driver la
rechaza (`Driver org.postgresql.Driver claims to not accept jdbcUrl`). El mensaje no nombra la variable que
falta, y es deliberado aceptarlo: es preferible un error poco claro a conectarse a la base equivocada.

Y por eso todos los comandos de este documento van dirigidos al contenedor de forma explícita y **nunca a
`localhost`**, y por eso `db.ps1` lee las credenciales de `.env` en vez de tenerlas escritas.

| Lo que quieres | Cómo |
|---|---|
| Ver la base del proyecto | `./scripts/db.ps1 -Accion shell` |
| Trabajar en tu PostgreSQL local | Es otro proyecto. No mezcles ambos |

---

## 3. Los datos

### Un solo script, cinco acciones

```powershell
./scripts/db.ps1 -Accion shell                       # psql interactivo dentro del contenedor
./scripts/db.ps1 -Accion seed                        # carga datos de ejemplo (idempotente)
./scripts/db.ps1 -Accion backup                      # vuelca a infra/local/backups/
./scripts/db.ps1 -Accion restore -Ruta <fichero> -Force
./scripts/db.ps1 -Accion reset -Force                # DESTRUYE el volumen y levanta de cero
```

`reset` y `restore` **exigen `-Force`** y avisan antes de hacer nada. Sin `-Force` se niegan y no tocan
nada: una comprobación que debe impedir una acción destructiva va **antes** de la acción.

### Datos de ejemplo

`infra/local/datos-dev.sql` carga un catálogo con los seis casos interesantes del catálogo:

| Pizza | Caso que representa |
|---|---|
| Margarita | Los tres tamaños disponibles: comprueba el orden SMALL→MEDIUM→LARGE |
| Capricciosa | MEDIUM disponible y LARGE agotado: la pizza sigue saliendo, el grande no |
| Diavola | Un solo tamaño |
| Bufala | Activa con su **única** variante agotada: **no** sale en el catálogo público (RN-DS-02) |
| Vegetariana | Activa y **sin ninguna variante**: tampoco sale (RN-DS-02) |
| Retirada | Dada de baja, con variante disponible: no sale, pero sí con `availableOnly=false` |

```powershell
./scripts/db.ps1 -Accion seed
curl http://localhost:8080/api/v1/pizzas
# -> 3 pizzas: Capricciosa, Diavola, Margarita
```

Es **idempotente**: cargarlo dos veces no duplica nada. Y **no borra** lo que haya, sólo añade. Para
empezar de cero, `reset`.

### Respaldar antes de tocar nada

```powershell
./scripts/db.ps1 -Accion backup
# -> infra/local/backups/pizzeria-20261017-141410.sql
```

Un volcado es **una copia de datos reales**: incluye hashes de contraseña y pedidos. `infra/local/backups/`
está en `.gitignore` y no se versiona nunca.

### Si el esquema se aplicó a medias

Síntoma: la aplicación no arranca con `Schema "public" is up to date` en falso, o `ddl-auto=validate`
falla contra una tabla que no cuadra. El arreglo es empezar de cero, y **el esquema lo vuelve a crear
Flyway** al arrancar:

```powershell
./scripts/db.ps1 -Accion reset -Force
```

---

## 4. Comandos que no necesitan el script

```powershell
# Estado
docker compose -f infra/local/docker-compose.yml ps

# Recargar código (después de compilar: --build no hace nada aquí)
docker compose -f infra/local/docker-compose.yml restart app

# Parar conservando los datos / destruyéndolos
./scripts/dev-down.ps1
./scripts/dev-down.ps1 -Volumen

# Volcar a fichero, a mano
docker compose -f infra/local/docker-compose.yml exec -T db `
    pg_dump -U pizzeria_dev -d pizzeria > volcado.sql

# Ver el esquema que hay realmente
docker compose -f infra/local/docker-compose.yml exec db `
    psql -U pizzeria_dev -d pizzeria -c "\dt"
```

**Al escribir un volcado desde Windows, el `>` produce UTF-16** y `psql` no lo entiende. Usa
`Out-File -Encoding utf8`, que es lo que hace `db.ps1 -Accion backup`.

---

## 5. Ver los datos en un cliente SQL (DBeaver, pgAdmin…)

**En el laboratorio de desarrollo, no.** Su puerto no se publica y DBeaver habla TCP. Es un criterio de
aceptación de US-002, no una casualidad.

**Pero hay un stack hecho exactamente para esto:** `infra/local/docker-compose.test.yml` publica
`5435:5432` porque *«lo necesita un proceso que corre fuera de Compose (por ejemplo, un cliente SQL o una
herramienta de inspección)»*.

```powershell
./scripts/dev-down.ps1                      # el laboratorio de desarrollo NO puede seguir levantado
docker compose -f infra/local/docker-compose.yml `
               -f infra/local/docker-compose.test.yml up -d --wait
```

Conexión en DBeaver:

| Campo | Valor |
|---|---|
| Host | `127.0.0.1` |
| Puerto | **5435** (no 5432: ahí está el PostgreSQL de Windows) |

> **El puerto se cambia en un solo sitio**: `infra/local/docker-compose.test.yml`, la línea `ports` del
> servicio `db`. Para elegir otro, primero se comprueba que está libre con
> `Get-NetTCPConnection -LocalPort <puerto> -State Listen` (sin salida = libre) y luego se actualiza esta tabla.
| Base de datos | `pizzeria` |
| Usuario | `pizzeria_dev` |
| Contraseña | La de `infra/local/.env` |
| Driver | PostgreSQL |

Para volver al laboratorio de desarrollo:

```powershell
./scripts/dev-up.ps1 -Detach
```

### Dos cosas que conviene saber antes de usarlo

**Los datos son desechables.** Este stack **no tiene volumen**: al parar y volver a levantar, la base
vuelve a estar vacía y el esquema lo recrea Flyway. Es intencionado —un esquema a medias o datos de otra
ejecución no deben falsear una inspección— pero significa que lo que mires ahí no es lo que hay en el
laboratorio de desarrollo. Para ver *esos* datos hay que usar `db.ps1 -Accion shell`.

**Los dos stacks comparten nombre de proyecto.** `docker-compose.yml` declara `name: pizzeria`, así que
el stack de inspección **reemplaza los contenedores** del de desarrollo: no conviven. Por eso el primer
comando para el de desarrollo es `dev-down.ps1`.

### Lo que NO se puede

| No se puede | Por qué |
|---|---|
| Conectar un cliente gráfico al laboratorio de desarrollo | Su puerto no se publica (US-002). Hay que usar el stack de inspección, y son datos distintos |
| Conectarse por SSH a la base | Mismo motivo |
| Añadir un segundo contenedor de base de datos | El compose tiene uno, y `dev-up.ps1` lo levanta siempre |
| Usar H2 o una base en memoria | Miente sobre tipos numéricos y restricciones |