param(
  [switch]$Build
)

$ErrorActionPreference = 'Stop'

$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$composeFile = Join-Path $deployDir '..\docker-compose.yml'
$envFile = Join-Path $deployDir '..\.env'
$envExample = Join-Path $deployDir '..\.env.example'

if (-not (Test-Path $envFile)) {
  Copy-Item $envExample $envFile
  Write-Host "已创建 .env，请先编辑: $envFile"
  exit 1
}

$composeArgs = @('-f', $composeFile, '--env-file', $envFile)
if ($Build) {
  docker compose @composeArgs up -d --build
} else {
  docker compose @composeArgs up -d
}

docker compose @composeArgs ps
