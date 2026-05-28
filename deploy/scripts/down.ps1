$ErrorActionPreference = 'Stop'

$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$composeFile = Join-Path $deployDir '..\docker-compose.yml'
$envFile = Join-Path $deployDir '..\.env'

if (-not (Test-Path $envFile)) {
  Write-Host ".env 不存在: $envFile"
  exit 1
}

$composeArgs = @('-f', $composeFile, '--env-file', $envFile)
docker compose @composeArgs down
