<#
.SYNOPSIS
    Para el entorno local.

.DESCRIPTION
    Por defecto conserva los datos de PostgreSQL, que es lo habitual mientras se
    desarrolla: se borra todo con -Volumen de forma explicita, porque borrar datos sin
    querer es mas molesto que tener que escribir una palabra.

.PARAMETER Volumen
    Destruye tambien los volumenes con nombre. Deja el entorno como recien clonado:
    es lo que hace falta cuando una migracion se aplico a medias o el esquema no cuadra.

.EXAMPLE
    ./scripts/dev-down.ps1
.EXAMPLE
    ./scripts/dev-down.ps1 -Volumen
#>
[CmdletBinding()]
param(
    [switch]$Volumen
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $raiz 'infra/local/docker-compose.yml'

if (-not (Test-Path -LiteralPath $compose)) {
    Write-Error "No se encuentra $compose"
}

# Mismo blindaje que dev-up.ps1: en PowerShell 5.1, con $ErrorActionPreference = 'Stop',
# cualquier salida por stderr de un comando nativo aborta el script. `docker compose down`
# escribe avisos por stderr en situaciones normales, así que el script terminaba con un error
# aunque hubiera parado bien. Se juzga por $LASTEXITCODE.
$preferenciaPrevia = $ErrorActionPreference
$ErrorActionPreference = 'Continue'

if ($Volumen) {
    Write-Host 'Parando y DESTRUYENDO los volumenes (se pierden los datos).' -ForegroundColor Yellow
    docker compose -f $compose down -v --remove-orphans
}
else {
    Write-Host 'Parando los servicios. Los datos de PostgreSQL se conservan.' -ForegroundColor Cyan
    docker compose -f $compose down --remove-orphans
}
$codigo = $LASTEXITCODE

$ErrorActionPreference = $preferenciaPrevia

if ($codigo -ne 0) {
    Write-Error 'docker compose devolvio un codigo de error.'
}

Write-Host 'Entorno local detenido.' -ForegroundColor Cyan