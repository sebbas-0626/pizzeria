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

# --- Docker disponible -----------------------------------------------------------
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Error 'Docker no esta instalado o no esta en el PATH.'
}
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error 'El motor de Docker no responde. Abrir Docker Desktop y reintentar.'
}

# --- Arranque --------------------------------------------------------------------
Write-Host 'Levantando PostgreSQL y la aplicacion...' -ForegroundColor Cyan
Write-Host 'La primera ejecucion descarga dependencias y compila: puede tardar varios minutos.'
Write-Host ''

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

if ($LASTEXITCODE -ne 0) {
    Write-Error 'docker compose devolvio un codigo de error. Revisar el log de arriba.'
}

if ($Detach -or $Wait) {
    Write-Host ''
    Write-Host 'Estado de los servicios:' -ForegroundColor Cyan
    docker compose -f $compose ps
    Write-Host ''
    Write-Host 'API      : http://localhost:8080/actuator/health'
    Write-Host 'Swagger  : http://localhost:8080/swagger-ui/index.html  (perfil local)'
    Write-Host 'Parar    : ./scripts/dev-down.ps1'
}