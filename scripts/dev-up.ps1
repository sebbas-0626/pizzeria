<#
.SYNOPSIS
    Levanta el entorno local completo: PostgreSQL y la aplicación.

.DESCRIPTION
    Un solo comando para no depender de recordar variables de entorno. Es exactamente
    el fallo que se produjo al arrancar a mano: sin las variables, la aplicación intenta
    conectarse al PostgreSQL del sistema en lugar del de Docker y falla al arrancar.

    No se publica el puerto de PostgreSQL. Sólo la API, en http://localhost:8080.

.PARAMETER Detach
    Devuelve el control en cuanto los servicios están healthy. Es el modo por defecto.

.PARAMETER Wait
    Espera a que ambos servicios estén healthy antes de devolver el control.

.EXAMPLE
    ./scripts/dev-up.ps1
.EXAMPLE
    ./scripts/dev-up.ps1 -Wait
#>
[CmdletBinding()]
param(
    [switch]$Detach,
    [switch]$Wait
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $raiz 'infra/local/docker-compose.yml'

if (-not (Test-Path -LiteralPath $compose)) {
    Write-Error "No se encuentra $compose"
}

# --- .env: se crea desde la plantilla si no existe -------------------------------
# Se copia porque el archivo .env está en .gitignore y no puede venir del repositorio.
# Vive junto a docker-compose.yml: Compose busca el .env en el directorio del compose.
$dirCompose = Join-Path $raiz 'infra/local'
$envFile = Join-Path $dirCompose '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    $plantilla = Join-Path $dirCompose '.env.example'
    if (-not (Test-Path -LiteralPath $plantilla)) {
        Write-Error "No se encuentra $plantilla"
    }
    Copy-Item -LiteralPath $plantilla -Destination $envFile
    Write-Host "Creado infra\local\.env a partir de .env.example." -ForegroundColor Yellow
    Write-Host "Revisa la contrasena: sirve solo en esta maquina." -ForegroundColor Yellow
}

# --- .env: se valida antes de llamar a Docker --------------------------------------
#
# El 2026-10-17 una linea ajena se coló en este archivo y el laboratorio dejó de levantar. El
# sintoma de Docker Compose era 'key cannot contain a space', que no dice qué línea es ni por qué.
# Nada del repositorio escribe aquí: el archivo está en .gitignore. Lo único que se puede hacer
# desde el proyecto es avisar antes, y con un mensaje que sí diga la línea.
#
# El mensaje NO imprime el contenido de la línea offender, porque un .env puede contener una
# contraseña o un token y un error de validación no es el sitio donde publicarlo.
$patronClave = '^[A-Za-z_][A-Za-z0-9_]*='
$lineas = Get-Content -LiteralPath $envFile
$ofensivas = @()
for ($i = 0; $i -lt $lineas.Count; $i++) {
    $linea = $lineas[$i].Trim()
    if ($linea.Length -eq 0 -or $linea.StartsWith('#')) { continue }
    if ($linea -notmatch $patronClave) {
        $ofensivas += "  linea $($i + 1) ($($linea.Length) caracteres)"
    }
}

if ($ofensivas.Count -gt 0) {
    Write-Host ''
    Write-Host "infra\local\.env tiene lineas que Docker Compose no puede leer:" -ForegroundColor Red
    $ofensivas | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    Write-Host ''
    Write-Host 'Docker compose responderia "key cannot contain a space", que no dice que linea es.' -ForegroundColor Yellow
    Write-Host 'Borra esas lineas, o regenera el archivo desde la plantilla:' -ForegroundColor Yellow
    Write-Host '    Copy-Item infra\local\.env.example infra\local\.env' -ForegroundColor Yellow
    Write-Host ''
    Write-Error 'Abortado: no se llama a docker compose con un .env invalido.'
}

# --- Docker disponible -----------------------------------------------------------
#
# `docker info` escribe avisos por stderr (el de "daemon is not using the default seccomp
# profile" aparece en cualquier instalación que no tenga ese perfil). En PowerShell 5.1, con
# `$ErrorActionPreference = 'Stop'`, la salida de un comando nativo por stderr se convierte en
# un error TERMINANTE: el script abortaba en este punto y el laboratorio no llegaba a levantar,
# con un mensaje que hablaba de un comando nativo y no de Docker. Se restauró la preferencia
# alrededor de la llamada y se juzga por `$LASTEXITCODE`, que es lo que dice si Docker responde.
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Error 'Docker no esta instalado o no esta en el PATH.'
}

$preferenciaPrevia = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
docker info *> $null
$codigoDocker = $LASTEXITCODE
$ErrorActionPreference = $preferenciaPrevia

if ($codigoDocker -ne 0) {
    Write-Error 'El motor de Docker no responde. Abrir Docker Desktop y reintentar.'
}

# --- Arranque --------------------------------------------------------------------
#
# Los tres modos usan el mismo criterio de acierto: el código que devuelve Compose. Por lo
# mismo que arriba, la salida por stderr de un comando nativo no debe abortar el script, así
# que la preferencia se restaura alrededor de la llamada.
Write-Host 'Levantando PostgreSQL y la aplicacion...' -ForegroundColor Cyan
Write-Host 'La primera ejecucion descarga dependencias y compila: puede tardar varios minutos.'
Write-Host ''

$preferenciaPrevia = $ErrorActionPreference
$ErrorActionPreference = 'Continue'

if ($Wait) {
    docker compose -f $compose up --build -d --wait
}
elseif ($Detach) {
    docker compose -f $compose up --build -d
}
else {
    # Sin -d: los logs se ven en pantalla. Es lo comod para desarrollar.
    docker compose -f $compose up --build
}
$codigoCompose = $LASTEXITCODE

$ErrorActionPreference = $preferenciaPrevia

if ($codigoCompose -ne 0) {
    Write-Error 'docker compose devolvio un codigo de error. Revisar el log de arriba.'
}

if ($Detach -or $Wait) {
    Write-Host ''
    Write-Host 'Estado de los servicios:' -ForegroundColor Cyan
    # Mismo blindaje que arriba: `compose ps` también puede escribir avisos por stderr.
    $preferenciaPrevia = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    docker compose -f $compose ps
    $ErrorActionPreference = $preferenciaPrevia
    Write-Host ''
    Write-Host 'API      : http://localhost:8080/actuator/health'
    Write-Host 'Swagger  : http://localhost:8080/swagger-ui/index.html  (perfil local)'
    Write-Host 'Parar    : ./scripts/dev-down.ps1'
}