<#
.SYNOPSIS
    Gestión de los datos del entorno local: mirar la base, sembrarla, respaldarla,
    restaurarla y empezar de cero.

.DESCRIPTION
    PostgreSQL del entorno local vive DENTRO de un contenedor y su puerto NO se publica
    en el host (criterio de aceptación de US-002). Por eso no se puede abrir en un cliente
    gráfico del host apuntando a localhost:5432, y este script trabaja dentro del contenedor.

    Cuidado con un detalle que confunde: si hay un PostgreSQL instalado en Windows, está
    escuchando en 5432 y `localhost:5432` SÍ responde, pero es OTRO servidor distinto y sin
    relación con el del proyecto. Todos los comandos de aquí van dirigidos al contenedor de
    forma explícita, nunca a `localhost`.

    Los datos viven en el volumen Docker `pizzeria_postgres-data`, que sobrevive a
    `docker compose down` y desaparece con `down -v`. Ver docs/development/entorno-local.md.

.PARAMETER Accion
    Qué hacer. Obligatorio.

    shell    Abre psql dentro del contenedor. Sin argumentos, interactivo.
    seed     Carga infra/local/datos-dev.sql. Idempotente: no duplica ni borra nada.
    backup   Vuelca la base a infra/local/backups/ con la fecha en el nombre.
    restore  Restaura un volcado. Exige -Ruta.
    reset    DESTRUYE el volumen y lo vuelve a levantar desde cero. Exige -Force.

.PARAMETER Ruta
    Fichero de volcado para `backup` (opcional, se elige por defecto) o para
    `restore` (obligatorio).

.PARAMETER Force
    Confirmación explícita de la acción `reset`, que no tiene vuelta atrás.

.EXAMPLE
    ./scripts/db.ps1 -Accion shell
.EXAMPLE
    ./scripts/db.ps1 -Accion seed
.EXAMPLE
    ./scripts/db.ps1 -Accion backup
.EXAMPLE
    ./scripts/db.ps1 -Accion restore -Ruta infra/local/backups/pizzeria-20261017-120000.sql
.EXAMPLE
    ./scripts/db.ps1 -Accion reset -Force
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('shell', 'seed', 'backup', 'restore', 'reset')]
    [string]$Accion,

    [string]$Ruta,

    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $raiz 'infra/local/docker-compose.yml'
$datosDev = Join-Path $raiz 'infra/local/datos-dev.sql'
$dirBackups = Join-Path $raiz 'infra/local/backups'

if (-not (Test-Path -LiteralPath $compose)) {
    Write-Error "No se encuentra $compose"
}

# --- Credenciales: salen de .env, nunca están escritas aquí ------------------------
#
# Se leen del mismo archivo que usa Compose, para que este script no pueda trabajar con
# unas credenciales distintas de las que usa la aplicación. Si el archivo no existe, se
# avisa con la instrucción exacta en vez de fallar con un error de autenticación.
$envFile = Join-Path $raiz 'infra/local/.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Error @"
No existe infra\local\.env.
Se crea levantando el entorno por primera vez:
    ./scripts/dev-up.ps1 -Detach
"@
}

$variables = @{}
foreach ($linea in Get-Content -LiteralPath $envFile) {
    $recortada = $linea.Trim()
    if ($recortada.Length -eq 0 -or $recortada.StartsWith('#')) { continue }
    $partes = $recortada -split '=', 2
    if ($partes.Count -eq 2) { $variables[$partes[0]] = $partes[1] }
}

$usuario = $variables['POSTGRES_USER']
$base = $variables['POSTGRES_DB']
if (-not $usuario -or -not $base) {
    Write-Error 'infra\local\.env no declara POSTGRES_USER ni POSTGRES_DB.'
}

# --- Acciones destructivas: se validan ANTES de tocar nada ------------------------
#
# Este control estaba dentro del `switch` y usaba `Write-Error`. No funcionaba: dentro del
# bloque la preferencia es 'Continue' (para que el stderr de Docker no aborte el script), y con
# 'Continue' `Write-Error` ESCRIBE el error pero NO detiene la ejecución. `reset` sin -Force
# imprimía «Reset cancelado» y destruía el volumen igualmente, el 2026-10-17.
#
# La lección: una comprobación que debe IMPEDIR una acción va antes de la acción y lanza, no
# avisa. Aquí, fuera del try, y con `throw`.

if ($Accion -eq 'reset' -and -not $Force) {
    Write-Host 'ATENCION: se DESTRUYE el volumen de PostgreSQL.' -ForegroundColor Red
    Write-Host "Todos los datos de '$base' se pierden y no hay vuelta atras." -ForegroundColor Red
    Write-Host 'Si quieres conservarlos, haz antes: ./scripts/db.ps1 -Accion backup' -ForegroundColor Yellow
    Write-Host 'Si de verdad quieres empezar de cero, repite con -Force.' -ForegroundColor Yellow
    throw 'Reset cancelado. No se ha tocado nada.'
}

if ($Accion -eq 'restore' -and -not $Force) {
    Write-Host 'ATENCION: restaurar BORRA y recrea el esquema antes de cargar.' -ForegroundColor Yellow
    Write-Host "Se va a restaurar sobre '$base'. Los datos actuales se pierden." -ForegroundColor Yellow
    Write-Host 'Si de verdad quieres seguir, repite con -Force.' -ForegroundColor Yellow
    throw 'Restore cancelado. No se ha tocado nada.'
}

if ($Accion -eq 'restore' -and -not $Ruta) {
    throw 'La accion restore necesita -Ruta <fichero.sql>.'
}

# --- Blindaje de PowerShell 5.1 ---------------------------------------------------
#
# Con $ErrorActionPreference = 'Stop', cualquier salida por stderr de un comando nativo se
# convierte en un error TERMINANTE. `docker compose exec` escribe avisos por stderr en
# situaciones normales, así que sin esto el script abortaría aunque la operación fuese
# correcta. Se juzga por $LASTEXITCODE, que es lo que dice si el comando fue bien.
#
# Por eso, dentro del try, TODO fallo se lanza con `throw` y no con `Write-Error`: con la
# preferencia en 'Continue', Write-Error no detiene nada.
$preferenciaPrevia = $ErrorActionPreference
$ErrorActionPreference = 'Continue'

try {
    switch ($Accion) {

        'shell' {
            Write-Host "psql dentro del contenedor. Base '$base', usuario '$usuario'." -ForegroundColor Cyan
            Write-Host 'Para salir: \q' -ForegroundColor Cyan
            docker compose -f $compose exec db psql -U $usuario -d $base
            if ($LASTEXITCODE -ne 0) { throw 'psql devolvio un codigo de error.' }
        }

        'seed' {
            if (-not (Test-Path -LiteralPath $datosDev)) {
                throw "No se encuentra $datosDev"
            }
            # El esquema lo crea Flyway al ARRANCA LA APLICACIÓN, no este script. Si el volumen
            # se acaba de recrear (o la aplicación nunca llegó a levantar), la base está
            # vacía y el INSERT falla con `relation "pizzas" does not exist`: un mensaje de
            # PostgreSQL que no dice que lo que falta es levantar la aplicación. Se comprueba
            # antes de intentarlo y se dice qué hacer.
            $existeProducto = docker compose -f $compose exec -T db psql -U $usuario -d $base -tAc `
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'products';"
            if ($LASTEXITCODE -ne 0) {
                throw 'No se puede leer el esquema. El contenedor de PostgreSQL no responde: levanta el entorno con ./scripts/dev-up.ps1 -Detach'
            }
            if ($existeProducto.Trim() -ne '1') {
                throw @"
La tabla 'products' no existe: las migraciones no estan aplicadas en '$base'.

El esquema lo crea la APLICACION al arrancar (Flyway), no este script. Con un volumen recien
creado la base esta vacia y no hay nada que sembrar todavia. Haz esto:

    ./scripts/dev-up.ps1 -Detach   # levanta PostgreSQL y la aplicacion; Flyway aplica V1
    ./scripts/db.ps1  -Accion seed
"@
            }
            Write-Host 'Cargando datos de ejemplo (idempotente: no duplica ni borra nada)...' -ForegroundColor Cyan
            # -T desactiva el TTY, que es lo que permite redirigir el fichero por stdin.
            Get-Content -LiteralPath $datosDev -Raw |
                docker compose -f $compose exec -T db psql -U $usuario -d $base -v ON_ERROR_STOP=1
            if ($LASTEXITCODE -ne 0) {
                throw 'No se han podido cargar los datos de ejemplo.'
            }
            Write-Host 'Datos cargados. GET http://localhost:8080/api/v1/products' -ForegroundColor Green
        }

        'backup' {
            if (-not $Ruta) {
                $marca = Get-Date -Format 'yyyyMMdd-HHmmss'
                $Ruta = Join-Path $dirBackups "pizzeria-$marca.sql"
            }
            $carpeta = Split-Path -Parent $Ruta
            if (-not (Test-Path -LiteralPath $carpeta)) {
                New-Item -ItemType Directory -Path $carpeta -Force | Out-Null
            }

            Write-Host 'Volcando la base...' -ForegroundColor Cyan
            # pg_dump se ejecuta DENTRO del contenedor y la salida se guarda en el host. Al
            # reves, el contenedor no puede escribir en el disco de Windows.
            docker compose -f $compose exec -T db pg_dump -U $usuario -d $base --clean --if-exists |
                Out-File -LiteralPath $Ruta -Encoding utf8
            if ($LASTEXITCODE -ne 0) {
                throw 'pg_dump devolvio un codigo de error.'
            }

            $tamano = (Get-Item -LiteralPath $Ruta).Length
            Write-Host "Volcado guardado en $Ruta ($tamano bytes)." -ForegroundColor Green
            Write-Host 'Ojo: es una copia de datos reales. No la subas a ningun sitio.' -ForegroundColor Yellow
        }

        'restore' {
            if (-not (Test-Path -LiteralPath $Ruta)) {
                throw "No se encuentra $Ruta"
            }

            Write-Host 'ATENCION: esto BORRA y recrea el esquema antes de cargar.' -ForegroundColor Yellow
            Write-Host "Se va a restaurar sobre '$base'. Datos actuales: se pierden." -ForegroundColor Yellow

            # --clean --if-exists va dentro del volcado, así que el orden importa: primero el
            # propio dump ya trae los DROP. No se vacía la base desde aquí, para que un fallo
            # a mitad del volcado no deje la base vacía sin ninguna copia.
            Get-Content -LiteralPath $Ruta -Raw |
                docker compose -f $compose exec -T db psql -U $usuario -d $base -v ON_ERROR_STOP=1
            if ($LASTEXITCODE -ne 0) {
                throw 'No se ha restaurado el volcado.'
            }
            Write-Host 'Volcado restaurado.' -ForegroundColor Green
        }

        'reset' {
            docker compose -f $compose down -v --remove-orphans
            if ($LASTEXITCODE -ne 0) {
                throw 'docker compose down -v devolvio un codigo de error.'
            }
            Write-Host 'Volumen destruido. Levantando el entorno desde cero...' -ForegroundColor Cyan
            docker compose -f $compose up -d --wait
            if ($LASTEXITCODE -ne 0) {
                throw 'El entorno no ha levantado.'
            }
            Write-Host 'Entorno reiniciado. Schema migrado por Flyway y catalogo vacio.' -ForegroundColor Green
            Write-Host 'Para meter datos de ejemplo: ./scripts/db.ps1 -Accion seed' -ForegroundColor Cyan
        }
    }
}
finally {
    $ErrorActionPreference = $preferenciaPrevia
}